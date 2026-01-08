package com.afterlands.core.actions.handlers;

import com.afterlands.core.actions.ActionHandler;
import com.afterlands.core.actions.ActionSpec;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handler para tocar sons customizados de resource pack.
 *
 * <p>Formato: {@code resource_pack_sound: <sound> [volume] [pitch]}</p>
 * <p>Suporta:</p>
 * <ul>
 *     <li>Qualquer som customizado registrado no resource pack</li>
 *     <li>Volume (padrão: 1.0, range: 0.0-Float.MAX_VALUE)</li>
 *     <li>Pitch (padrão: 1.0, range: 0.5-2.0)</li>
 * </ul>
 *
 * <p>Exemplos:</p>
 * <pre>
 * resource_pack_sound: custom.boss.roar
 * resource_pack_sound: custom.ui.click 0.5 1.0
 * resource_pack_sound: custom.ambient.cave 0.3 0.8
 * resource_pack_sound: afterlands.sfx.levelup 1.0 1.2
 * </pre>
 *
 * <p><b>Diferença de {@code sound}:</b></p>
 * <ul>
 *     <li>sound: Usa enum {@link org.bukkit.Sound} (sons padrão do Minecraft)</li>
 *     <li>resource_pack_sound: Usa string literal (sons customizados do resource pack)</li>
 * </ul>
 *
 * <p><b>Importante:</b> O som só será ouvido se o player tiver o resource pack ativo.</p>
 */
public final class ResourcePackSoundHandler implements ActionHandler {

    @Override
    public void execute(@NotNull Player target, @NotNull ActionSpec spec) {
        String args = spec.rawArgs();
        if (args == null || args.isEmpty()) {
            return;
        }

        // Parse: sound_name [volume] [pitch]
        // Support both space and semicolon separators for backward compatibility
        String[] parts = args.split("[\\s;]+");
        if (parts.length == 0) {
            return;
        }

        String soundName = parts[0];
        float volume = 1.0f;
        float pitch = 1.0f;

        if (parts.length >= 2) {
            try {
                volume = Float.parseFloat(parts[1]);
                volume = Math.max(0.0f, volume); // Min 0, sem max (distance hearing)
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

        // Tocar som customizado usando string literal
        target.playSound(target.getLocation(), soundName, volume, pitch);
    }
}
