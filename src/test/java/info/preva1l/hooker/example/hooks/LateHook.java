package info.preva1l.hooker.example.hooks;

import info.preva1l.hooker.HookOrder;
import info.preva1l.hooker.annotation.Hook;
import info.preva1l.hooker.annotation.OnStart;
import info.preva1l.hooker.annotation.Reloadable;

/**
 * Created on 10/03/2025
 *
 * @author Preva1l
 */
@Hook(id = "lateHook", order = HookOrder.LATE)
@Reloadable(async = true)
public class LateHook {
    @OnStart
    public void onStart() {
        System.out.println("lateHook is started!");
    }

    // @OnStop is optional!
}
