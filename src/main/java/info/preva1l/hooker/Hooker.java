package info.preva1l.hooker;

import com.github.puregero.multilib.MultiLib;
import info.preva1l.hooker.annotation.*;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@SuppressWarnings("unused")
public final class Hooker implements Listener {
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
     * @param plugin your plugin instance
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
     * @param plugin your plugin instance
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
     */
    public static void reload() {
        if (instance == null) throw new IllegalStateException("You cannot reload hooks when Hooker is not initialized!");

        instance.reloadHooks();
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
        if (instance == null) throw new IllegalStateException("You cannot reload hooks when Hooker is not initialized!");

        Bukkit.getPluginManager().registerEvents(instance, instance.owningPlugin);
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

    /**
     * Register a custom requirement.
     *
     * @param requirement the type
     * @param predicate the checker
     */
    public static void requirement(String requirement, Predicate<String> predicate) {
        if (instance == null) throw new IllegalStateException("You cannot add a requirement when Hooker is not initialized!");

        if (instance.requirementRegistry.exists(requirement)) {
            throw new IllegalStateException("Requirement " + requirement + " already exists!");
        }
        instance.requirementRegistry.register(requirement, predicate);
    }

    @EventHandler
    private void onDisable(PluginDisableEvent event) {
        if (!event.getPlugin().equals(owningPlugin)) return;

        disableHooks();
    }

    private void reloadHooks() {
        for (Object hook : loadedHooks.values()) {
            for (Method method : hook.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(OnStop.class)) continue;
                Reloadable reloadable = hook.getClass().getAnnotation(Reloadable.class);
                if (reloadable == null) continue;
                method.setAccessible(true);
                if (reloadable.async()) {
                    MultiLib.getAsyncScheduler().runNow(owningPlugin, t -> {
                        try {
                            method.invoke(hook);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } else {
                    MultiLib.getGlobalRegionScheduler().run(owningPlugin, t -> {
                        try {
                            method.invoke(hook);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
                break;
            }
            loadedHooks.remove(hook.getClass());
        }
    }

    private int loadHooks(List<Class<?>> hooks) {
        int count = 0;
        for (Class<?> hookClass : hooks) {
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
                if (failed) continue;

                Object hook = hookClass.getDeclaredConstructor().newInstance();

                for (Method method : hookClass.getDeclaredMethods()) {
                    if (!method.isAnnotationPresent(OnStart.class)) continue;
                    method.invoke(hook);
                    break;
                }
                owningPlugin.getLogger().info("Loaded hook: " + hookAnnotation.id());
                loadedHooks.put(hookClass, hook);
                count++;
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
                throw new RuntimeException(ex);
            }
        }

        return count;
    }

    private void disableHooks() {
        for (Object hook : loadedHooks.values()) {
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
        }
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
        List<File> dirs = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }

        Map<Class<?>, HookOrder> classes = new HashMap<>();
        for (File directory : dirs) {
            classes.putAll(findClasses(directory, packageName));
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
                assert !file.getName().contains(".");
                classes.putAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                Class<?> found;
                try {
                    found = Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6));
                } catch (ClassNotFoundException ignored) {
                    continue;
                }

                Hook annotation = found.getAnnotation(Hook.class);

                if (annotation == null) continue;

                classes.put(found, annotation.order());
            }
        }
        return classes;
    }
}
