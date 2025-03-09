package info.preva1l.hooker.annotation;

import java.lang.annotation.*;

/**
 * Created on 9/03/2025
 *
 * @author Preva1l
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(RequireComposite.class)
public @interface Require {
    String type() default "plugin";

    String value();
}
