package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para tocar sons padrão do Minecraft.
 *
 * <p>Formato: {@code sound: SOUND_NAME [volume] [pitch]}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Todos os sons do enum {@link Sound}</li>
 *     <li>Volume (padrão: 1.0, range: 0.0-1.0)</li>
 *     <li>Pitch (padrão: 1.0, range: 0.5-2.0)</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * sound: LEVEL_UP
 * sound: CLICK 0.5 1.0
 * sound: NOTE_PLING 1.0 2.0
 * sound: ENDERMAN_TELEPORT 0.8 0.5
 * </pre>
 *
 * <p>Para sons customizados de resource pack, use {@code resource_pack_sound} em vez disso.</p>
 */
public final class SoundHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String args = spec.rawArgs();
        if (args == null || args.isEmpty()) {
            return;
        }

        // Parse: SOUND_NAME [volume] [pitch]
        // Support both space and semicolon separators for backward compatibility
        String[] parts = args.split("[\\s;]+");
        if (parts.length == 0) {
            return;
        }

        String soundName = parts[0].toUpperCase();
        float volume = 1.0f;
        float pitch = 1.0f;

        if (parts.length >= 2) {
            try {
                volume = Float.parseFloat(parts[1]);
                volume = Math.max(0.0f, Math.min(1.0f, volume)); // clamp 0-1
            } catch (NumberFormatException ignored) {
            }
        }

        if (parts.length >= 3) {
            try {
                pitch = Float.parseFloat(parts[2]);
                pitch = Math.max(0.5f, Math.min(2.0f, pitch)); // clamp 0.5-2.0
            } catch (NumberFormatException ignored) {
            }
        }

        // Resolver sound do enum
        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            // Som inválido, log warning para debug
            // Note: Using Bukkit logger since we don't have plugin reference
            org.bukkit.Bukkit.getLogger().warning("[AfterCore] Invalid sound name: " + soundName);
            return;
        }

        // Tocar som na localização do player
        target.playSound(target.getLocation(), sound, volume, pitch);
    }
}
