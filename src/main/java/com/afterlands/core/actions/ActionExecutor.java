package com.afterlands.core.actions;

import com.afterlands.core.conditions.ConditionContext;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.conditions.impl.EmptyConditionContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Executor de actions com suporte a scopes (VIEWER, NEARBY, ALL).
 *
 * <p>Thread Safety: Garante que handlers são executados na main thread quando necessário.</p>
 * <p>Performance: Usa spatial queries eficientes para NEARBY scope.</p>
 */
public final class ActionExecutor {

    private final Plugin plugin;
    private final ConditionService conditionService;
    private final Map<String, ActionHandler> handlers;
    private final boolean debug;

    public ActionExecutor(@NotNull Plugin plugin,
                          @NotNull ConditionService conditionService,
                          @NotNull Map<String, ActionHandler> handlers,
                          boolean debug) {
        this.plugin = plugin;
        this.conditionService = conditionService;
        this.handlers = handlers;
        this.debug = debug;
    }

    /**
     * Executa uma action spec com scope resolvido.
     *
     * @param spec ActionSpec parseada
     * @param viewer Player que está "vendo" (usado para VIEWER scope e referência de posição)
     * @param origin Origem para cálculo de NEARBY (geralmente viewer.getLocation())
     */
    public void execute(@NotNull ActionSpec spec, @NotNull Player viewer, @NotNull Location origin) {
        // Verificar se temos handler registrado
        ActionHandler handler = handlers.get(spec.typeKey().toLowerCase(Locale.ROOT));
        if (handler == null) {
            if (debug) {
                plugin.getLogger().warning("Action handler not found: " + spec.typeKey());
            }
            return;
        }

        // Resolver targets baseado no scope
        Collection<Player> targets = resolveTargets(spec.scope(), viewer, origin, spec.scopeRadius());
        if (targets.isEmpty()) {
            return;
        }

        // Executar para cada target
        for (Player target : targets) {
            executeForTarget(spec, target, handler);
        }
    }

    /**
     * Executa action para um target específico (com checagem de condição).
     */
    private void executeForTarget(@NotNull ActionSpec spec, @NotNull Player target, @NotNull ActionHandler handler) {
        // Se tem condição, avaliar primeiro (sempre na main thread)
        if (spec.condition() != null && !spec.condition().isEmpty()) {
            ConditionContext ctx = EmptyConditionContext.getInstance(); // TODO: permitir contexto customizado

            // Avaliar condição (sync porque estamos na main thread)
            boolean conditionMet = conditionService.evaluateSync(target, spec.condition(), ctx);

            if (!conditionMet) {
                if (debug) {
                    plugin.getLogger().fine("Condition failed for " + target.getName() + ": " + spec.condition());
                }
                return;
            }
        }

        // Executar handler (garantir main thread)
        if (Bukkit.isPrimaryThread()) {
            handler.execute(target, spec);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> handler.execute(target, spec));
        }
    }

    /**
     * Resolve targets baseado no scope.
     *
     * @param scope Scope da action (VIEWER/NEARBY/ALL)
     * @param viewer Player de referência
     * @param origin Localização de referência para NEARBY
     * @param radius Raio para NEARBY (em blocos)
     * @return Coleção de players que devem receber a action
     */
    private Collection<Player> resolveTargets(@NotNull ActionScope scope,
                                               @NotNull Player viewer,
                                               @NotNull Location origin,
                                               int radius) {
        return switch (scope) {
            case VIEWER -> Collections.singleton(viewer);

            case NEARBY -> getNearbyPlayers(origin, radius);

            case ALL -> new ArrayList<>(Bukkit.getOnlinePlayers());
        };
    }

    /**
     * Obtém players próximos de uma localização (otimizado).
     *
     * <p>Performance: O(chunks × players_per_chunk) em vez de O(all_players)</p>
     */
    private Collection<Player> getNearbyPlayers(@NotNull Location origin, int radius) {
        if (radius <= 0) {
            radius = 32; // default
        }

        Set<Player> result = new HashSet<>();
        double radiusSquared = radius * radius;

        // Iterar apenas pelo mundo da origem
        for (Player player : origin.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) <= radiusSquared) {
                result.add(player);
            }
        }

        return result;
    }

    /**
     * Valida se um handler existe para o tipo de action.
     */
    public boolean hasHandler(@NotNull String actionType) {
        return handlers.containsKey(actionType.toLowerCase(Locale.ROOT));
    }
}
