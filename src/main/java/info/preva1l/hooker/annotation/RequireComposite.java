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
public @interface RequireComposite {
    /**
     * An array of {@link Require} to support {@link java.lang.annotation.Repeatable}.
     *
     * @return the composite.
     */
    Require[] value();
}
