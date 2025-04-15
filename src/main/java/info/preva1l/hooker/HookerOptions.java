package info.preva1l.hooker;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Created on 16/04/2025
 *
 * @author Preva1l
 */
public final class HookerOptions {
    public final Logger logger;
    public final boolean loadNow;
    public final String[] packages;
    public final Consumer<Runnable> asyncRunner;
    public final Consumer<Runnable> syncRunner;
    public final Consumer<Runnable> delayedRunner;

    public HookerOptions(String... packages) {
        this(Logger.getAnonymousLogger(), packages);
    }

    public HookerOptions(Logger logger, String... packages) {
        this(logger, false, packages);
    }

    public HookerOptions(Logger logger, boolean loadNow, String... packages) {
        this(logger, loadNow, CompletableFuture::runAsync, Runnable::run, Runnable::run, packages);
    }

    public HookerOptions(Logger logger,
                         boolean loadNow,
                         Consumer<Runnable> asyncRunner,
                         Consumer<Runnable> syncRunner,
                         Consumer<Runnable> delayedRunner,
                         String... packages
    ) {
        this.logger = logger;
        this.loadNow = loadNow;
        this.packages = packages;
        this.asyncRunner = asyncRunner;
        this.syncRunner = syncRunner;
        this.delayedRunner = delayedRunner;
    }
}
