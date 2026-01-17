package com.afterlands.core.commands.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a subcommand handler.
 *
 * <p>
 * The method must have
 * {@link com.afterlands.core.commands.execution.CommandContext}
 * as its first parameter. Additional parameters can be annotated with
 * {@link Arg}
 * and {@link Flag} for automatic parsing.
 * </p>
 *
 * <p>
 * Special values:
 * </p>
 * <ul>
 * <li>{@code ""} or {@code "default"} - Executed when no subcommand is
 * specified</li>
 * <li>Nested paths: {@code "config reload"} - Creates nested subcommand</li>
 * </ul>
 *
 * <p>
 * Example:
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Subcommand("give")
 * &#64;Description("Give items to a player")
 * &#64;Permission("myplugin.give")
 * public void give(
 *         CommandContext ctx,
 *         &#64;Arg("player") Player target,
 *         @Arg(value = "amount", defaultValue = "1") int amount,
 *         &#64;Flag("silent") boolean silent) {
 *     // Implementation
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subcommand {

    /**
     * Subcommand name(s). Use space-separated for nested subcommands.
     *
     * <p>
     * Examples:
     * </p>
     * <ul>
     * <li>{@code "reload"} - /cmd reload</li>
     * <li>{@code "config reload"} - /cmd config reload</li>
     * <li>{@code ""} or {@code "default"} - /cmd (root handler)</li>
     * </ul>
     */
    String value();

    /**
     * Alternative names for this subcommand.
     */
    String[] aliases() default {};

    /**
     * Description shown in help output.
     */
    String description() default "";

    /**
     * Plain usage pattern for error messages (e.g., "&lt;player&gt;
     * &lt;amount&gt;").
     * Used in {usage} placeholder in error messages.
     * If empty, auto-generated from arguments.
     */
    String usage() default "";

    /**
     * Colored usage hint shown in help (e.g., "&c&lt;player&gt; &d[amount]").
     * Supports color codes for visual distinction.
     * If empty, falls back to usage() value.
     */
    String usageHelp() default "";
}
