package info.preva1l.hooker;

import com.github.puregero.multilib.MultiLib;
import com.github.puregero.multilib.regionized.RegionizedTask;
import info.preva1l.hooker.annotation.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@SuppressWarnings("unused")
public final class Hooker {
    private static Hooker instance;

    private final RequirementRegistry requirementRegistry;
    private final JavaPlugin owningPlugin;
    private final List<String> packages;

    private final List<Class<?>> onLoadHooks = new ArrayList<>();
    private final List<Class<?>> onEnableHooks = new ArrayList<>();
    private final List<Class<?>> lateHooks = new ArrayList<>();

    private final Map<Class<?>, Object> loadedHooks = new HashMap<>();

    private Hooker(JavaPlugin owningPlugin, boolean loadNow, String... packages) {
        this.requirementRegistry = new RequirementRegistry();
        this.owningPlugin = owningPlugin;
        this.packages = Arrays.asList(packages);

        scanForHooks(owningPlugin.getClass().getClassLoader());

        if (loadNow) {
            load();
        }
    }

    /**
     * Register hooker for your plugin.
     * </br>
     * This must be called before you want any hooks loaded (at the top of {@link JavaPlugin#onLoad()})
     *
     * @param plugin   your plugin instance
     * @param packages what packages hooker will scan for classes annotated with {@link Hook}
     */
    public static void register(JavaPlugin plugin, String... packages) {
        if (instance != null) throw new IllegalStateException("Hooker is already registered!");

        instance = new Hooker(plugin, false, packages);
    }

    /**
     * Register hooker for your plugin.
     * With the option to start loading hooks immediately instead of waiting for custom requirement registration.
     * </br>
     * This must be called before you want any hooks loaded (at the top of {@link JavaPlugin#onLoad()})
     *
     * @param plugin   your plugin instance
     * @param packages what packages hooker will scan for classes annotated with {@link Hook}
     */
    public static void register(JavaPlugin plugin, boolean loadNow, String... packages) {
        if (instance != null) throw new IllegalStateException("Hooker is already registered!");

        instance = new Hooker(plugin, loadNow, packages);
    }

    /**
     * Get a hook by the class if the hook is loaded.
     *
     * @param hookClass the hook to get
     * @return the hook if loaded, empty optional if not
     */
    public static <T> Optional<T> getHook(Class<T> hookClass) {
        if (instance == null) throw new IllegalStateException("You cannot get hooks when Hooker is not initialized!");

        for (Map.Entry<Class<?>, Object> hook : instance.loadedHooks.entrySet()) {
            if (hook.getKey() == hookClass) {
                //noinspection unchecked
                return Optional.of((T) hook.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Reloads any loaded hooks that are annotated with {@link Reloadable}
     * </br>
     * This method runs some tasks asynchronously (reloadable hooks marked as async),
     * this is why we return a completable future.
     *
     * @return returns a completable future that completes when all hooks are reloaded
     */
    public static CompletableFuture<Void> reload() {
        if (instance == null)
            throw new IllegalStateException("You cannot reload hooks when Hooker is not initialized!");

        return CompletableFuture.runAsync(() -> {
            instance.owningPlugin.getLogger().info("Reloading hooks...");
            int count = instance.reloadHooks();
            instance.owningPlugin.getLogger().info("Reloaded " + count + " hooks!");
        });
    }

    /**
     * Call this method in your {@link JavaPlugin#onLoad()} after you have registered Hooker and custom requirements
     */
    public static void load() {
        if (instance == null) throw new IllegalStateException("You cannot load hooks when Hooker is not initialized!");

        instance.owningPlugin.getLogger().info("Loading onLoad hooks...");
        int count = instance.loadHooks(instance.onLoadHooks);
        instance.owningPlugin.getLogger().info("Loaded " + count + " hooks!");
    }

    /**
     * Call this method at the top of {@link JavaPlugin#onEnable()}
     */
    public static void enable() {
        if (instance == null) throw new IllegalStateException("You cannot load hooks when Hooker is not initialized!");

        instance.owningPlugin.getLogger().info("Loading onEnable hooks...");
        int count = instance.loadHooks(instance.onEnableHooks);
        instance.owningPlugin.getLogger().info("Loaded " + count + " hooks!");

        MultiLib.getGlobalRegionScheduler().runDelayed(
                instance.owningPlugin,
                t -> {
                    instance.owningPlugin.getLogger().info("Loading late hooks...");
                    int count2 = instance.loadHooks(instance.lateHooks);
                    instance.owningPlugin.getLogger().info("Loaded " + count2 + " hooks!");
                },
                5 * 20L
        );
    }

    public static void disable() {
        if (instance == null) return;

        instance.owningPlugin.getLogger().info("Disabling hooks...");
        int count = instance.disableHooks();
        instance.owningPlugin.getLogger().info("Disabled " + count + " hooks!");
    }

    /**
     * Register a custom requirement.
     *
     * @param requirement the type
     * @param predicate   the checker
     */
    public static void requirement(String requirement, Predicate<String> predicate) {
        if (instance == null)
            throw new IllegalStateException("You cannot add a requirement when Hooker is not initialized!");

        if (instance.requirementRegistry.exists(requirement)) {
            throw new IllegalStateException("Requirement " + requirement + " already exists!");
        }
        instance.requirementRegistry.register(requirement, predicate);
    }

    private int reloadHooks() {
        int count = 0;
        count += reloadHooks(onEnableHooks);
        count += reloadHooks(lateHooks);
        return count;
    }

    private int reloadHooks(List<Class<?>> hooks) {
        int count = 0;
        for (Class<?> hookClass : hooks) {
            if (loadedHooks.containsKey(hookClass)) {
                if (reloadLoadedHook(loadedHooks.get(hookClass)).join()) count++;
                continue;
            }

            Reloadable reloadable = hookClass.getAnnotation(Reloadable.class);
            if (reloadable == null) continue;

            if (loadHook(hookClass)) count++;
        }
        return count;
    }

    private CompletableFuture<Boolean> reloadLoadedHook(Object hook) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Method tempMethod = null;
        Method tempMethod2 = null;
        Reloadable reloadable = hook.getClass().getAnnotation(Reloadable.class);
        if (reloadable == null) {
            future.complete(false);
            return future;
        }

        for (Method method : hook.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(OnStart.class)) {
                tempMethod = method;
            }
            if (method.isAnnotationPresent(OnStop.class)) {
                tempMethod2 = method;
            }
        }

        assert tempMethod != null;
        Method startMethod = tempMethod;
        startMethod.setAccessible(true);

        Method stopMethod = tempMethod2;
        if (stopMethod != null) {
            stopMethod.setAccessible(true);
        }

        if (reloadable.async()) {
            MultiLib.getAsyncScheduler().runNow(owningPlugin, reloadTask(hook, startMethod, stopMethod, future));
        } else {
            MultiLib.getGlobalRegionScheduler().run(owningPlugin, reloadTask(hook, startMethod, stopMethod, future));
        }

        future.thenAccept(result -> {
            if (result) {
                Hook hookAnnotation = hook.getClass().getAnnotation(Hook.class);
                owningPlugin.getLogger().info("Reloaded hook: " + hookAnnotation.id());
            }
        });

        return future;
    }

    private Consumer<RegionizedTask> reloadTask(
            Object hook,
            Method startMethod,
            Method stopMethod,
            CompletableFuture<Boolean> future
    ) {
        return t -> {
            try {
                if (stopMethod != null) {
                    stopMethod.invoke(hook);
                }
                Object response = startMethod.invoke(hook);
                if (response instanceof Boolean load) {
                    future.complete(load);
                } else {
                    future.complete(true);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private int loadHooks(List<Class<?>> hooks) {
        int count = 0;
        for (Class<?> hookClass : hooks) {
            if (loadHook(hookClass)) count++;
        }
        return count;
    }

    private boolean loadHook(Class<?> hookClass) {
        try {
            Hook hookAnnotation = hookClass.getAnnotation(Hook.class);
            Require[] requirements = hookClass.getAnnotationsByType(Require.class);

            boolean failed = false;
            for (Require requirement : requirements) {
                String type = requirement.type();
                String value = requirement.value();

                if (!requirementRegistry.checkRequirement(type, value)) {
                    failed = true;
                    break;
                }
            }
            if (failed) return false;

            Object hook = hookClass.getDeclaredConstructor().newInstance();

            boolean loaded = false;
            for (Method method : hookClass.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnStart.class)) continue;
                Object response = method.invoke(hook);
                if (response instanceof Boolean load) {
                    loaded = load;
                } else {
                    loaded = true;
                }
                break;
            }
            if (!loaded) return false;
            owningPlugin.getLogger().info("Loaded hook: " + hookAnnotation.id());
            loadedHooks.put(hookClass, hook);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
        return true;
    }

    private int disableHooks() {
        int count = 0;
        for (Object hook : new ArrayList<>(loadedHooks.values())) {
            for (Method method : hook.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnStop.class)) continue;
                try {
                    method.invoke(hook);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
            loadedHooks.remove(hook.getClass());
            Hook hookAnnotation = hook.getClass().getAnnotation(Hook.class);
            owningPlugin.getLogger().info("Disabled hook: " + hookAnnotation.id());
            count++;
        }
        return count;
    }

    private void scanForHooks(ClassLoader loader) {
        for (String pkg : packages) {
            try {
                getClasses(loader, pkg).forEach((hook, order) -> {
                    switch (order) {
                        case LOAD -> onLoadHooks.add(hook);
                        case ENABLE -> onEnableHooks.add(hook);
                        case LATE -> lateHooks.add(hook);
                    }
                });
            } catch (ClassNotFoundException ignored) {
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private Map<Class<?>, HookOrder> getClasses(ClassLoader classLoader, String packageName) throws ClassNotFoundException, IOException {
        if (classLoader == null) {
            return Map.of();
        }

        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        Map<Class<?>, HookOrder> classes = new HashMap<>();

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();

            if (resource.getProtocol().equals("jar")) {
                String jarFilePath = resource.getPath()
                        .substring(5, resource.getPath().indexOf("!"))
                        .replace("%20", " ");
                JarFile jarFile = new JarFile(jarFilePath);

                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(path) && entry.getName().endsWith(".class")) {
                        String className = entry.getName().substring(0, entry.getName().length() - 6).replace('/', '.');
                        try {
                            Class<?> clazz = classLoader.loadClass(className);

                            Hook annotation = clazz.getAnnotation(Hook.class);
                            if (annotation != null) {
                                classes.put(clazz, annotation.order());
                            }
                        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                        }
                    }
                }
                jarFile.close();
            } else {
                File directory = new File(resource.getFile());
                if (directory.exists()) {
                    classes.putAll(findClasses(directory, packageName));
                }
            }
        }

        return classes;
    }

    private Map<Class<?>, HookOrder> findClasses(File directory, String packageName) {
        Map<Class<?>, HookOrder> classes = new HashMap<>();
        if (!directory.exists()) {
            return classes;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return classes;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                classes.putAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String fileName = file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(packageName + '.' + fileName);

                    Hook annotation = clazz.getAnnotation(Hook.class);
                    if (annotation != null) {
                        classes.put(clazz, annotation.order());
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        return classes;
    }
}
