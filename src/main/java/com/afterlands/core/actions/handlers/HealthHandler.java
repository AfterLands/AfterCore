package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para definir a vida do player.
 *
 * <p>
 * Formato: {@code health: <value>}
 * </p>
 *
 * <p>
 * Exemplos:
 * </p>
 * 
 * <pre>
 * health: 20       # Vida máxima (10 corações)
 * health: 10       # 5 corações
 * health: 1        # Meio coração
 * </pre>
 */
public final class HealthHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String args = spec.rawArgs();
        if (args == null || args.isEmpty()) {
            return;
        }

        try {
            double health = Double.parseDouble(args.trim());
            target.setHealth(Math.max(0, Math.min(health, target.getMaxHealth())));
        } catch (NumberFormatException ignored) {
            // Invalid value, silently ignore
        }
    }
}
