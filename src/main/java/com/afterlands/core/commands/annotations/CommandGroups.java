package com.afterlands.core.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation for multiple {@link CommandGroup} annotations.
 *
 * <p>
 * This is automatically used when multiple @CommandGroup annotations
 * are applied to the same class.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CommandGroups {

    /**
     * The command groups.
     */
    CommandGroup[] value();
}
