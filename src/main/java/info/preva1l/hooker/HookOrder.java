package info.preva1l.hooker;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
public enum HookOrder {
    /**
     * The hook will get loaded when the plugin is loaded.
     *
     * <p>Hooks using this should usually not depend on other plugins as their classes will not be loaded yet.</p>
     */
    LOAD,
    /**
     * The hook will get loaded when the plugin is enabled.
     *
     * <p>This is the default and most hooks will use this.</p>
     */
    ENABLE,
    /**
     * The hook will get loaded in the post enable stage. (5 seconds after the server is declared started)
     *
     * <p>The use-case for this is minimal, but generally for lightweight less important hooks.</p>
     */
    LATE
}
