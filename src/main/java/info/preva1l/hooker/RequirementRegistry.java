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

    /**
     * Check if a requirement exists.
     *
     * @param requirement the requirement type/key.
     * @return true if the requirement is registered.
     */
    public boolean exists(String requirement) {
        return requirements.containsKey(requirement);
    }

    /**
     * Parse a requirement and get the result.
     *
     * @param requirement the requirement type/key.
     * @param value the value to check.
     * @return if the requirement is met.
     */
    public boolean checkRequirement(String requirement, String value) {
        return requirements.containsKey(requirement) && requirements.get(requirement).test(value);
    }

    /**
     * Register a requirement.
     *
     * @param requirement the requirement type/key.
     * @param predicate the value parser.
     */
    public void register(String requirement, Predicate<String> predicate) {
        requirements.put(requirement, predicate);
    }
}
