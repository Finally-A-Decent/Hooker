package info.preva1l.hooker.example;

import info.preva1l.hooker.Hooker;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
public class MyPlugin extends JavaPlugin {

    @Override
    public void onLoad() {
        Hooker.register(this, "info.preva1l.hooker.example.hooks");

        Hooker.requirement(
                "config",
                value -> switch (value) {
                    case "test-hook" -> true;
                    default -> false;
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
