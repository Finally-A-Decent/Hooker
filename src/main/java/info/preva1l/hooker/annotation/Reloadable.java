package info.preva1l.hooker.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Reloadable {
    /**
     * Hooks that have blocking enable logic may be loaded async.
     *
     * <p>
     *     If async is <b>true</b> the hook is loaded on the AsyncScheduler. Otherwise, it's loaded on the main thread/global region scheduler.
     * </p>
     *
     * @return true to load the hook async.
     */
    boolean async() default false;
}
