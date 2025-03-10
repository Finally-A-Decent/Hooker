package info.preva1l.hooker;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import info.preva1l.hooker.example.MyPlugin;
import info.preva1l.hooker.example.hooks.LateHook;
import info.preva1l.hooker.example.hooks.OnEnableHook;
import info.preva1l.hooker.example.hooks.OnLoadHook;
import org.junit.jupiter.api.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@DisplayName("Hooker Tests")
public class HookerTests {
    private static ServerMock serverMock;
    private static MyPlugin plugin;

    @BeforeAll
    @DisplayName("Test Plugin Setup")
    public static void setUpPlugin() {
        serverMock = MockBukkit.mock();
        MockBukkit.createMockPlugin("PlaceholderAPI");
        plugin = MockBukkit.load(MyPlugin.class);
        serverMock.getScheduler().waitAsyncTasksFinished();
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Test Plugin Enables")
    public void testPluginEnables() {
        Assertions.assertTrue(plugin.isEnabled());
    }

    @Test
    @DisplayName("Test Hook Gets Loaded On Load")
    public void testLoadingOnLoad() {
        Assertions.assertTrue(Hooker.getHook(OnLoadHook.class).isPresent());
    }

    @Test
    @DisplayName("Test Hook Gets Loaded On Enable")
    public void testLoadingOnEnable() {
        Assertions.assertTrue(Hooker.getHook(OnEnableHook.class).isPresent());
    }

    @Test
    @DisplayName("Test Hook Reloading")
    public void testReloadHooks() {
        AtomicBoolean completed = new AtomicBoolean(false);

        CompletableFuture.runAsync(() -> {
            // we need to continue the ticking on a diff thread while we join the future
            while (!completed.get()) {
                serverMock.getScheduler().performOneTick();
            }
        });

        Assertions.assertDoesNotThrow(() -> {
            Hooker.reload().join();
            completed.set(true);
        });
    }

    @Test
    @DisplayName("Test Hooks Get Disabled")
    public void testHooksGetDisabled() {
        serverMock.getPluginManager().disablePlugin(plugin);

        Assertions.assertFalse(Hooker.getHook(OnLoadHook.class).isPresent());
        Assertions.assertFalse(Hooker.getHook(OnEnableHook.class).isPresent());
        Assertions.assertFalse(Hooker.getHook(LateHook.class).isPresent());
    }
}