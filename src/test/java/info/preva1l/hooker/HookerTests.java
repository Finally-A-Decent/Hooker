package info.preva1l.hooker;

import be.seeseemelk.mockbukkit.MockBukkit;
import info.preva1l.hooker.example.MyPlugin;
import info.preva1l.hooker.example.hooks.OnEnableHook;
import info.preva1l.hooker.example.hooks.OnLoadHook;
import org.junit.jupiter.api.*;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@DisplayName("Hooker Tests (1.17.1)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HookerTests {
    @BeforeAll
    @DisplayName("Test Plugin Setup")
    public static void setUpPlugin() {
        MockBukkit.mock();
        MockBukkit.createMockPlugin("PlaceholderAPI");
        MockBukkit.load(MyPlugin.class);
    }

    @AfterAll
    public static void unload() {
        MockBukkit.unmock();
    }

    @Order(1)
    @Test
    @DisplayName("Test Hook Gets Loaded On Load")
    public void testLoadingOnLoad() {
        Assertions.assertTrue(Hooker.getHook(OnLoadHook.class).isPresent());
    }

    @Order(2)
    @Test
    @DisplayName("Test Hook Gets Loaded On Enable")
    public void testLoadingOnEnable() {
        Assertions.assertTrue(Hooker.getHook(OnEnableHook.class).isPresent());
    }
}