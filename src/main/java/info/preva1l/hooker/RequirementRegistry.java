package info.preva1l.hooker;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
public final class RequirementRegistry {
    private final Map<String, Predicate<String>> requirements = new HashMap<>();

    RequirementRegistry() {
        register("plugin", value -> {
            var plugin = Bukkit.getPluginManager().getPlugin(value);
            return plugin != null && plugin.isEnabled();
        });
    }

    public boolean exists(String requirement) {
        return requirements.containsKey(requirement);
    }

    public boolean checkRequirement(String requirement, String value) {
        return requirements.containsKey(requirement) && requirements.get(requirement).test(value);
    }

    public void register(String requirement, Predicate<String> predicate) {
        requirements.put(requirement, predicate);
    }
}
