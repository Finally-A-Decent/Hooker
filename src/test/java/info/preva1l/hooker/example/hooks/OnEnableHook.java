package info.preva1l.hooker.example.hooks;

import info.preva1l.hooker.HookOrder;
import info.preva1l.hooker.annotation.*;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@Hook(id = "onEnableHook")
@Reloadable
@Require("PlaceholderAPI")
public class OnEnableHook {
    @OnStart
    public void onStart() {
        System.out.println("onEnableHook is started!");
        // Load placeholder api stuff
    }

    @OnStop
    public void onStop() {
        System.out.println("onEnableHook is stopped!");
    }
}
