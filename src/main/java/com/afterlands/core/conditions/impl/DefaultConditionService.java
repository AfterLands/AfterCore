package com.afterlands.core.conditions.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.conditions.ConditionContext;
import com.afterlands.core.conditions.ConditionService;
import com.afterlands.core.conditions.ConditionVariableProvider;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementação unificada (inspirada no ConditionEngine do AfterBlockState, com
 * operadores do AfterMotion).
 */
public final class DefaultConditionService implements ConditionService {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    private final Plugin plugin;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final boolean debug;

    private final Map<String, List<String>> conditionGroups = new ConcurrentHashMap<>();
    private final Map<String, ConditionVariableProvider> variableProviders = new ConcurrentHashMap<>();

    // cache do texto expandido (groups + variáveis) para reduzir custo em hot-path
    private final Cache<String, String> expansionCache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofSeconds(10))
            .recordStats()
            .build();

    // PlaceholderAPI via reflexão (optional)
    private volatile boolean placeholderApiAvailable;
    private volatile Method setPlaceholdersMethod;

    public DefaultConditionService(@NotNull Plugin plugin, @NotNull SchedulerService scheduler, boolean debug) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.scheduler = scheduler;
        this.debug = debug;

        initPlaceholderApi();

        // provider padrão: %abs_flag:key% (compat com AfterBlockState)
        registerVariableProvider("abs_flag", (player, namespace, key, ctx) -> ctx.getOrDefault(key, ""));
    }

    private void initPlaceholderApi() {
        try {
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
                placeholderApiAvailable = false;
                return;
            }
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            placeholderApiAvailable = true;
            logger.info("PlaceholderAPI enabled");
        } catch (Exception e) {
            placeholderApiAvailable = false;
            setPlaceholdersMethod = null;
            logger.log(Level.WARNING, "Falha ao iniciar PlaceholderAPI (reflection)", e);
        }
    }

    @Override
    public void setConditionGroups(@NotNull Map<String, List<String>> groups) {
        conditionGroups.clear();
        conditionGroups.putAll(groups);
        expansionCache.invalidateAll();
        if (debug)
            logger.info("conditionGroups=" + groups.size());
    }

    @Override
    public @NotNull Map<String, List<String>> getConditionGroups() {
        return new HashMap<>(conditionGroups); // Return a copy for safety
    }

    @Override
    public void registerVariableProvider(@NotNull String namespace, @NotNull ConditionVariableProvider provider) {
        variableProviders.put(namespace.toLowerCase(Locale.ROOT), provider);
        expansionCache.invalidateAll();
    }

    @Override
    public boolean evaluateSync(@NotNull Player player, @NotNull String expression, @NotNull ConditionContext ctx) {
        if (expression.isEmpty()) {
            return true;
        }
        try {
            String expanded = expandAndResolveCustom(player, expression, ctx);
            String resolved = resolvePlaceholderApiIfNeeded(player, expanded);
            boolean result = evaluateExpression(resolved);
            if (debug) {
                logger.info("[AfterCore][Condition] " + player.getName() + " | " + expression + " -> " + resolved
                        + " = " + result);
            }
            return result;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Erro avaliando condição: " + expression, e);
            return false;
        }
    }

    @Override
    public @NotNull CompletableFuture<Boolean> evaluate(@NotNull Player player, @NotNull String expression,
            @NotNull ConditionContext ctx) {
        if (!placeholderApiAvailable) {
            return CompletableFuture.completedFuture(evaluateSync(player, expression, ctx));
        }
        // PlaceholderAPI deve rodar na main thread.
        return scheduler.runSync(() -> {
        }).thenApply(v -> evaluateSync(player, expression, ctx));
    }

    private String expandAndResolveCustom(Player player, String expression, ConditionContext ctx) {
        String cacheKey = expression + "|" + System.identityHashCode(ctx);
        String cached = expansionCache.getIfPresent(cacheKey);
        if (cached != null)
            return cached;

        String expanded = expandGroups(expression);
        String resolvedCustom = resolveCustomVariables(player, expanded, ctx);
        expansionCache.put(cacheKey, resolvedCustom);
        return resolvedCustom;
    }

    private String expandGroups(String expression) {
        String result = expression;

        List<String> group = conditionGroups.get(expression);
        if (group != null && !group.isEmpty()) {
            result = group.size() == 1 ? group.get(0) : "(" + String.join(" AND ", group) + ")";
        }

        for (Map.Entry<String, List<String>> entry : conditionGroups.entrySet()) {
            String name = entry.getKey();
            if (!result.contains(name))
                continue;
            List<String> lines = entry.getValue();
            String replacement = lines.size() == 1 ? lines.get(0) : "(" + String.join(" AND ", lines) + ")";
            result = result.replace(name, replacement);
        }

        return result;
    }

    private String resolveCustomVariables(Player player, String text, ConditionContext ctx) {
        Matcher m = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String token = m.group(1); // inside %...%
            String replacement = null;

            int idx = token.indexOf(':');
            if (idx > 0) {
                String namespace = token.substring(0, idx).toLowerCase(Locale.ROOT);
                String key = token.substring(idx + 1);
                ConditionVariableProvider provider = variableProviders.get(namespace);
                if (provider != null) {
                    replacement = provider.resolve(player, namespace, key, ctx);
                }
            }

            if (replacement == null) {
                // mantém placeholder intacto para PlaceholderAPI/fallback resolver depois
                replacement = "%" + token + "%";
            }

            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String resolvePlaceholderApiIfNeeded(Player player, String text) {
        if (!placeholderApiAvailable || setPlaceholdersMethod == null) {
            return resolveBasicPlaceholders(text, player);
        }

        if (!text.contains("%")) {
            return text;
        }

        try {
            // PlaceholderAPI via reflection (sem hard-dependency)
            Object result = setPlaceholdersMethod.invoke(null, player, text);
            String resolved = result != null ? result.toString() : text;
            return resolveBasicPlaceholders(resolved, player);
        } catch (Exception e) {
            if (debug)
                logger.warning("PlaceholderAPI error: " + e.getMessage());
            return resolveBasicPlaceholders(text, player);
        }
    }

    /**
     * Fallback: placeholders básicos quando PlaceholderAPI não está presente.
     * Mantém compat com ideias do ConditionEvaluator do AfterMotion.
     */
    private String resolveBasicPlaceholders(String text, Player player) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = switch (placeholder.toLowerCase(Locale.ROOT)) {
                case "player", "player_name" -> player.getName();
                case "player_uuid" -> player.getUniqueId().toString();
                case "player_world" -> player.getWorld().getName();
                case "player_health" -> String.valueOf((int) player.getHealth());
                case "player_food" -> String.valueOf(player.getFoodLevel());
                case "player_level" -> String.valueOf(player.getLevel());
                case "player_is_op" -> player.isOp() ? "yes" : "no";
                case "player_is_flying" -> player.isFlying() ? "yes" : "no";
                case "player_is_sneaking" -> player.isSneaking() ? "yes" : "no";
                default -> "%" + placeholder + "%";
            };
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ===== Expression evaluation =====

    private boolean evaluateExpression(@NotNull String expression) {
        String exp = expression.trim();
        if (exp.isEmpty())
            return true;

        // NOT prefix (case-insensitive)
        if (exp.regionMatches(true, 0, "NOT ", 0, 4)) {
            return !evaluateExpression(exp.substring(4).trim());
        }

        // Resolve parentheses (inner-most)
        while (exp.contains("(")) {
            int start = exp.lastIndexOf('(');
            int end = exp.indexOf(')', start);
            if (end == -1) {
                throw new IllegalArgumentException("Parênteses não fechados em: " + exp);
            }
            String inner = exp.substring(start + 1, end);
            boolean innerResult = evaluateExpression(inner);
            exp = exp.substring(0, start) + innerResult + exp.substring(end + 1);
        }

        // OR (lower precedence)
        if (containsOperator(exp, " OR ")) {
            String[] parts = splitOperator(exp, " OR ");
            for (String p : parts) {
                if (evaluateExpression(p))
                    return true;
            }
            return false;
        }

        // AND (higher precedence)
        if (containsOperator(exp, " AND ")) {
            String[] parts = splitOperator(exp, " AND ");
            for (String p : parts) {
                if (!evaluateExpression(p))
                    return false;
            }
            return true;
        }

        // boolean literals
        if ("true".equalsIgnoreCase(exp))
            return true;
        if ("false".equalsIgnoreCase(exp))
            return false;

        // comparison
        ConditionComparison comparison = ConditionComparison.parse(exp);
        if (comparison != null) {
            return comparison.evaluate();
        }

        // truthy fallback
        return isTruthy(exp);
    }

    private boolean isTruthy(String value) {
        if (value == null)
            return false;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty())
            return false;
        return v.equals("yes") || v.equals("true") || v.equals("1");
    }

    private boolean containsOperator(@NotNull String expression, @NotNull String operator) {
        int depth = 0;
        int opLen = operator.length();
        String upper = expression.toUpperCase(Locale.ROOT);
        String opUpper = operator.toUpperCase(Locale.ROOT);

        for (int i = 0; i <= expression.length() - opLen; i++) {
            char c = expression.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
                depth--;
            else if (depth == 0 && upper.substring(i, i + opLen).equals(opUpper)) {
                return true;
            }
        }
        return false;
    }

    private String[] splitOperator(@NotNull String expression, @NotNull String operator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int last = 0;
        int opLen = operator.length();
        String upper = expression.toUpperCase(Locale.ROOT);
        String opUpper = operator.toUpperCase(Locale.ROOT);

        for (int i = 0; i <= expression.length() - opLen; i++) {
            char c = expression.charAt(i);
            if (c == '(')
                depth++;
            else if (c == ')')
                depth--;
            else if (depth == 0 && upper.substring(i, i + opLen).equals(opUpper)) {
                parts.add(expression.substring(last, i));
                last = i + opLen;
            }
        }
        parts.add(expression.substring(last));
        return parts.toArray(new String[0]);
    }
}
