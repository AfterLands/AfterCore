package com.afterlands.core.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Helper para obter o AfterCore via Bukkit ServicesManager.
 *
 * <p>Evita singletons estáticos no plugin em si, mas ainda permite acesso rápido.</p>
 */
public final class AfterCore {
    private static volatile AfterCoreAPI cached;

    private AfterCore() {}

    @NotNull
    public static AfterCoreAPI get() {
        AfterCoreAPI local = cached;
        if (local != null) {
            return local;
        }

        RegisteredServiceProvider<AfterCoreAPI> rsp =
                Bukkit.getServer().getServicesManager().getRegistration(AfterCoreAPI.class);
        if (rsp == null || rsp.getProvider() == null) {
            throw new IllegalStateException("AfterCoreAPI não registrado no ServicesManager. O AfterCore está habilitado?");
        }

        AfterCoreAPI provider = rsp.getProvider();
        cached = provider;
        return provider;
    }

    /**
     * Invalida o cache (útil em reload /disable->enable).
     */
    public static void invalidate() {
        cached = null;
    }
}

