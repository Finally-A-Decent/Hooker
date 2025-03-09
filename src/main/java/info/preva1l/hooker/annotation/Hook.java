package info.preva1l.hooker.annotation;

import info.preva1l.hooker.HookOrder;

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
public @interface Hook {
    /**
     * The hook's id.
     */
    String id();

    /**
     * When should the hook get loaded.
     */
    HookOrder order() default HookOrder.ENABLE;
}
