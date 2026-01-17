package com.afterlands.core.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a subcommand group that collapses in the main help display.
 *
 * <p>
 * Groups are shown as a single entry with [!] marker in the main help,
 * indicating they have their own subcommand help (e.g., /cmd group help).
 * </p>
 *
 * <p>
 * Example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Command(name = "animations")
 * &#64;CommandGroup(prefix = "animation", description = "Gerenciar animações")
 * &#64;CommandGroup(prefix = "frame", description = "Gerenciar frames")
 * &#64;CommandGroup(prefix = "placement", description = "Gerenciar placements")
 * public class AnimationsCommand { ... }
 * }
 * </pre>
 *
 * <p>
 * Help output:
 * </p>
 * 
 * <pre>
 * ▪ /animations animation [!] - Gerenciar animações
 * ▪ /animations frame [!] - Gerenciar frames
 * ▪ /animations menu - Abre menu principal
 * ▪ /animations placement [!] - Gerenciar placements
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(CommandGroups.class)
public @interface CommandGroup {

    /**
     * The subcommand prefix that defines this group.
     * All subcommands starting with this prefix will be grouped.
     * <p>
     * Example: "animation" groups "animation create", "animation delete", etc.
     */
    String prefix();

    /**
     * Description shown in the main help for this group.
     */
    String description();
}
