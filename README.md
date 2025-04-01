# Finally a Decent Hooker
*Funny right?*

Hooker is a simple to use but feature packed hooking library.

## Basic Usage

Registering in your main plugin class.
```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        // Here in the register we provide 2 things, first the plugin instance,
        // and then the package we want to scan for the hooks to load
        // don't worry, it scans sub packages aswell
        // You will also notice this boolean flag, we can ignore that for now
        Hooker.register(this, true, "me.developer.myplugin.hooks");
    }

    @Override
    public void onEnable() {
        Hooker.enable();
    }

    @Override
    public void onDisable() {
        Hooker.disable();
    }
}
```

Creating a hook class

Hooker makes use of the following annotations:
- `@Hook` (Required)
- `@OnStart` (Required)
- `@OnStop` (Optional)
- `@Reloadable` (Optional)
- `@Require` (Optional & Configurable)

```java
@Hook(
        id = "my-simple-hook",
        // hook order is optional and defaults to enable
        order = HookOrder.ENABLE
)
// if a hook is marked as reloadable it can either
// be reloaded on the main thread or on a separate thread
@Reloadable(async = true)
// By default, @Require checks to see if another plugin is installed and enabled
@Require("AnotherPlugin")
// But you can register custom checks
@Require(type = "config", "simplehook")
public class OnEnableHook {
    @OnStart
    public void onStart() {
        // your hooks startup code should run in here
        // hooks should not have constructors of any kind
    }

    @OnStop
    public void onStop() {
        // having a shutdown block is optional
    }
}
```

Registering custom `@Require` checks.
```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onLoad() {
        // instead of the simple register like last time
        // we use a slightly different flow
        //Hooker.register(this, true, "me.developer.myplugin.hooks");

        // as you can see we removed the true, which is a flag to load now
        // instead we manually load it further down
        Hooker.register(this, "me.developer.myplugin.hooks");

        Hooker.requirement(
                "config",
                value -> {
                    // if the value is simplehook, we allow it to load
                    // obviously you would want to implement something to check your config
                    if (value.equals("simplehook")) {
                        // return MyConfig.i().getHooks().isSimpleHookEnabled();
                        return true;
                    }
                    
                    return false;
                }
        );

        Hooker.load();
    }

    @Override
    public void onEnable() {
        Hooker.enable();
    }

    @Override
    public void onDisable() {
        Hooker.disable();
    }
}
```
