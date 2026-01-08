package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para aplicar efeitos de poção.
 *
 * <p>Formato: {@code potion: <type> <duration> <amplifier> [ambient] [particles]}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Todos os tipos de {@link PotionEffectType}</li>
 *     <li>Duração em ticks (20 ticks = 1 segundo)</li>
 *     <li>Amplifier (nível do efeito, 0-based: 0 = nível 1, 1 = nível 2, etc.)</li>
 *     <li>Ambient e particles opcionais (padrão: false, true)</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * potion: SPEED 200 0              # Speed I por 10 segundos
 * potion: REGENERATION 100 1        # Regeneration II por 5 segundos
 * potion: ABSORPTION 600 3 false false  # Absorption IV por 30s, sem ambient/particles
 * potion: INVISIBILITY 1200 0       # Invisibility I por 60 segundos
 * </pre>
 *
 * <p>Para remover efeito: use duração 0</p>
 * <pre>
 * potion: SPEED 0 0                 # Remove Speed
 * </pre>
 */
public final class PotionHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String args = spec.rawArgs();
        if (args == null || args.isEmpty()) {
            return;
        }

        // Parse: type duration amplifier [ambient] [particles]
        // Support both space and semicolon separators for backward compatibility
        String[] parts = args.split("[\\s;]+");
        if (parts.length < 3) {
            return; // Mínimo: type duration amplifier
        }

        String typeName = parts[0].toUpperCase();
        PotionEffectType type = PotionEffectType.getByName(typeName);
        if (type == null) {
            return; // Tipo inválido
        }

        int duration;
        int amplifier;
        try {
            duration = Integer.parseInt(parts[1]);
            amplifier = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        // Remover efeito se duração = 0
        if (duration <= 0) {
            target.removePotionEffect(type);
            return;
        }

        boolean ambient = false;
        boolean particles = true;

        if (parts.length >= 4) {
            ambient = Boolean.parseBoolean(parts[3]);
        }

        if (parts.length >= 5) {
            particles = Boolean.parseBoolean(parts[4]);
        }

        // Aplicar efeito
        PotionEffect effect = new PotionEffect(type, duration, amplifier, ambient, particles);
        target.addPotionEffect(effect, true); // Override existing
    }
}
