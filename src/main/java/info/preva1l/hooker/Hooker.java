package info.preva1l.hooker;

import info.preva1l.hooker.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private final HookerOptions options;

    private final List<Class<?>> onLoadHooks = new ArrayList<>();
    private final List<Class<?>> onEnableHooks = new ArrayList<>();
    private final List<Class<?>> lateHooks = new ArrayList<>();

    private final Map<Class<?>, Object> loadedHooks = new HashMap<>();

    private Hooker(Class<?> clazz, HookerOptions options) {
        this.requirementRegistry = new RequirementRegistry();
        this.options = options;

        scanForHooks(clazz.getClassLoader());

        if (options.loadNow) {
            load();
        }
    }

    /**
     * Register hooker for your plugin.
     * <p>
     * This must be called before you want any hooks loaded (at the top of JavaPlugin#onLoad()
     * </p>
     *
     * @param clazz   your main class
     * @param options what options hooker will use
     */
    public static void register(Class<?> clazz, HookerOptions options) {
        if (instance != null) throw new IllegalStateException("Hooker is already registered!");

        instance = new Hooker(clazz, options);
    }

    /**
     * Register hooker for your plugin.
     * <p>
     * This must be called before you want any hooks loaded (at the top of JavaPlugin#onLoad()
     * </p>
     *
     * @param clazz   your main class
     * @param packages what packages hooker will scan for class annotated with the {@link Hook} annotation
     */
    public static void register(Class<?> clazz, String... packages) {
        if (instance != null) throw new IllegalStateException("Hooker is already registered!");

        instance = new Hooker(clazz, new HookerOptions(packages));
    }


    /**
     * Register hooker for your plugin.
     * <p>
     * This must be called before you want any hooks loaded (at the top of JavaPlugin#onLoad()
     * </p>
     *
     * @param plugin   your plugin instance
     * @param packages what packages hooker will scan for class annotated with the {@link Hook} annotation
     */
    // todo: add a module for this specifically so that we can have hooker as an independent woman once and forall
    public static void register(JavaPlugin plugin, String... packages) {
        if (instance != null) throw new IllegalStateException("Hooker is already registered!");

        instance = new Hooker(
                plugin.getClass(),
                new HookerOptions(
                        plugin.getLogger(),
                        false,
                        runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable),
                        runnable -> Bukkit.getScheduler().runTask(plugin, runnable),
                        runnable -> Bukkit.getScheduler().runTaskLater(plugin, runnable,60L),
                        packages
                )
        );
    }

    /**
     * Get a hook by the class if the hook is loaded.
     *
     * @param hookClass the hook to get
     * @param <T> the hook class
     * @return the hook if loaded, empty optional if not
     */
    public static <T> Optional<T> getHook(Class<T> hookClass) {
        if (instance == null) throw new IllegalStateException("You cannot get hooks when Hooker is not initialized!");

        for (Map.Entry<Class<?>, Object> hook : instance.loadedHooks.entrySet()) {
            if (hook.getKey() == hookClass) {
                Object value = hook.getValue();
                if (hookClass.isInstance(value)) return Optional.of(hookClass.cast(value));
            }
        }
        return Optional.empty();
    }

    /**
     * Gets all loaded hook objects.
     *
     * @return a list of all loaded hooks and the reference to their instance.
     */
    public static List<Object> getLoadedHooks() {
        return new ArrayList<>(instance.loadedHooks.values());
    }

    /**
     * Reloads any loaded hooks that are annotated with {@link Reloadable}
     * <p>
     * This method runs some tasks asynchronously (reloadable hooks marked as async),
     * this is why we return a completable future.
     * </p>
     *
     * @return returns a completable future that completes when all hooks are reloaded
     */
    public static CompletableFuture<Void> reload() {
        if (instance == null)
            throw new IllegalStateException("You cannot reload hooks when Hooker is not initialized!");

        return CompletableFuture.runAsync(() -> {
            instance.options.logger.info("Reloading hooks...");
            int count = instance.reloadHooks();
            instance.options.logger.info("Reloaded " + count + " hooks!");
        });
    }

    /**
     * Call this method in your JavaPlugin#onLoad() after you have registered Hooker and custom requirements
     */
    public static void load() {
        if (instance == null) throw new IllegalStateException("You cannot load hooks when Hooker is not initialized!");

        instance.options.logger.info("Loading onLoad hooks...");
        int count = instance.loadHooks(instance.onLoadHooks);
        instance.options.logger.info("Loaded " + count + " hooks!");
    }

    /**
     * Call this method at the top of JavaPlugin#onEnable()
     */
    public static void enable() {
        if (instance == null) throw new IllegalStateException("You cannot load hooks when Hooker is not initialized!");

        instance.options.logger.info("Loading onEnable hooks...");
        int count = instance.loadHooks(instance.onEnableHooks);
        instance.options.logger.info("Loaded " + count + " hooks!");

        instance.options.delayedRunner.accept(() -> {
            instance.options.logger.info("Loading late hooks...");
            int count2 = instance.loadHooks(instance.lateHooks);
            instance.options.logger.info("Loaded " + count2 + " hooks!");
        });
    }

    /**
     * Call this method at the top of JavaPlugin#onDisable()
     */
    public static void disable() {
        if (instance == null) return;

        instance.options.logger.info("Disabling hooks...");
        int count = instance.disableHooks();
        instance.options.logger.info("Disabled " + count + " hooks!");
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
            options.asyncRunner.accept(reloadTask(hook, startMethod, stopMethod, future));
        } else {
            options.syncRunner.accept(reloadTask(hook, startMethod, stopMethod, future));
        }

        future.thenAccept(result -> {
            if (result) {
                Hook hookAnnotation = hook.getClass().getAnnotation(Hook.class);
                options.logger.info("Reloaded hook: " + hookAnnotation.id());
            }
        });

        return future;
    }

    private Runnable reloadTask(
            Object hook,
            Method startMethod,
            Method stopMethod,
            CompletableFuture<Boolean> future
    ) {
        return () -> {
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
            options.logger.info("Loaded hook: " + hookAnnotation.id());
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
            options.logger.info("Disabled hook: " + hookAnnotation.id());
            count++;
        }
        return count;
    }

    private void scanForHooks(ClassLoader loader) {
        for (String pkg : options.packages) {
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
