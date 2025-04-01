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
    /**
     * The type of requirement. Also known as the key.
     *
     * @return the type/key of the requirement.
     */
    String type() default "plugin";

    /**
     * The value of the requirement.
     *
     * @return the requirement value to check.
     */
    String value();
}
