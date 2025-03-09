package info.preva1l.hooker.example;

import info.preva1l.hooker.Hooker;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import java.io.File;

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
                value -> true
        );

        Hooker.load();
    }

    @Override
    public void onEnable() {
        Hooker.enable();
    }
}
