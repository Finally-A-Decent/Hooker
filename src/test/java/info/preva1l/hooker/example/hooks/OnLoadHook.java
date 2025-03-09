package info.preva1l.hooker.example.hooks;

import info.preva1l.hooker.HookOrder;
import info.preva1l.hooker.annotation.*;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@Hook(id = "onLoadHook", order = HookOrder.LOAD)
@Reloadable(async = true)
@Require(type = "config", value = "test-hook")
public class OnLoadHook {
    @OnStart
    public void onStart() {
        System.out.println("onLoadHook is started!");
        // do stuff
    }

    @OnStop
    public void onStop() {
        System.out.println("onLoadHook is stopped!");
    }
}
