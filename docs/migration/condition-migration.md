# ConditionService Migration Guide

## Overview

This guide helps you migrate from plugin-specific condition engines to the unified `ConditionService` in AfterCore.

**Consolidates:**
- `AfterBlockState`: `ConditionEngine` with groups and basic operators
- `AfterMotion`: `ConditionEvaluator` with string operators and PAPI integration

---

## Benefits of Migration

- **Zero Duplication**: One parser, one evaluator across all plugins
- **Unified Syntax**: Same expression language everywhere
- **Placeholder Support**: Automatic PlaceholderAPI integration (main-thread safe)
- **Custom Providers**: Register namespaced variables (`%abs_flag:key%`, `%motion_scene%`)
- **Performance**: Built-in caching for parsed AST and results
- **Extensibility**: Easy to add new operators or providers

---

## Syntax Comparison

### AfterBlockState (ConditionEngine)

**Before:**
```yaml
# AfterBlockState conditions.yml
groups:
  vip:
    - "permission.vip == true"
    - "rank >= 5"

  staff:
    - "permission.admin == true"
    - "NOT permission.banned == true"
```

**After (AfterCore):**
```java
// Programmatic registration
Map<String, List<String>> groups = Map.of(
    "vip", List.of(
        "permission.vip == true",
        "rank >= 5"
    ),
    "staff", List.of(
        "permission.admin == true",
        "NOT permission.banned == true"
    )
);

core.conditions().setConditionGroups(groups);
```

Or load from your plugin's config:
```yaml
# your-plugin/config.yml
condition-groups:
  vip:
    - "permission.vip == true"
    - "rank >= 5"
```

```java
ConfigurationSection section = getConfig().getConfigurationSection("condition-groups");
Map<String, List<String>> groups = new HashMap<>();
for (String key : section.getKeys(false)) {
    groups.put(key, section.getStringList(key));
}
core.conditions().setConditionGroups(groups);
```

### AfterMotion (ConditionEvaluator)

**Before:**
```yaml
# AfterMotion scene conditions
conditions:
  - "%player_health% > 10"
  - "%player_world% equals world"
  - "%player_name% contains Admin"
```

**After (same syntax, unified engine):**
```java
String expression = "%player_health% > 10 AND %player_world% equals world";
boolean result = core.conditions().evaluateSync(player, expression, ctx);
```

---

## Migration Steps

### Step 1: Remove Local Condition Engine

**Before (AfterBlockState):**
```java
public class ConditionEngine {
    private Map<String, List<String>> groups;

    public boolean evaluate(Player player, String expression, Map<String, String> variables) {
        // Custom parsing and evaluation logic
        // 50-100 lines of code per plugin!
    }
}
```

**Before (AfterMotion):**
```java
public class ConditionEvaluator {
    public boolean evaluate(Player player, String condition) {
        // Different parsing logic with PAPI
        // Another 50-100 lines duplicated!
    }
}
```

**After (both plugins):**
```java
AfterCoreAPI core = AfterCore.get();
// Just use core.conditions() - zero code duplication!
```

---

### Step 2: Migrate Condition Groups

**Before (AfterBlockState):**
```java
public class BlockStateManager {
    private ConditionEngine conditionEngine;

    public void loadConditions() {
        ConfigurationSection groupsSection = getConfig().getConfigurationSection("groups");
        // Manual parsing...
        conditionEngine.setGroups(groups);
    }

    public boolean checkState(Player player, String groupName) {
        return conditionEngine.evaluateGroup(player, groupName, variables);
    }
}
```

**After:**
```java
public class BlockStateManager {
    private final ConditionService conditions;

    public BlockStateManager(AfterCoreAPI core) {
        this.conditions = core.conditions();
    }

    public void loadConditions() {
        ConfigurationSection groupsSection = getConfig().getConfigurationSection("groups");
        Map<String, List<String>> groups = new HashMap<>();

        for (String key : groupsSection.getKeys(false)) {
            groups.put(key, groupsSection.getStringList(key));
        }

        conditions.setConditionGroups(groups);
    }

    public CompletableFuture<Boolean> checkState(Player player, String groupName,
                                                   ConditionContext ctx) {
        // Group expressions are implicitly AND-ed
        return conditions.evaluate(player, groupName, ctx);
    }
}
```

---

### Step 3: Register Custom Variable Providers

**Before (manual placeholder replacement):**
```java
public class AfterBlockState {
    public String replacePlaceholders(Player player, String text) {
        text = text.replace("%abs_flag:key%", getFlag(player, "key"));
        text = text.replace("%abs_state:block%", getState(player));
        // Dozens of manual replacements...
        return text;
    }
}
```

**After (clean provider registration):**
```java
public class AfterBlockState extends JavaPlugin {
    @Override
    public void onEnable() {
        AfterCoreAPI core = AfterCore.get();

        // Register custom variable provider for namespace "abs"
        core.conditions().registerVariableProvider("abs", new AbsVariableProvider(this));
    }
}

public class AbsVariableProvider implements ConditionVariableProvider {
    private final AfterBlockState plugin;

    public AbsVariableProvider(AfterBlockState plugin) {
        this.plugin = plugin;
    }

    @Override
    public @Nullable String resolve(@NotNull Player player, @NotNull String variable,
                                      @NotNull ConditionContext ctx) {
        // variable = "flag:key" or "state:block"
        if (variable.startsWith("flag:")) {
            String key = variable.substring(5);
            return plugin.getFlagManager().getFlag(player.getUniqueId(), key);
        }

        if (variable.startsWith("state:")) {
            String blockType = variable.substring(6);
            return plugin.getStateManager().getState(player, blockType);
        }

        return null; // Not handled by this provider
    }
}
```

Now you can use `%abs_flag:vip%` or `%abs_state:glass%` in any condition!

---

### Step 4: Migrate Condition Evaluation

**Before (AfterBlockState):**
```java
@EventHandler
public void onInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();

    Map<String, String> variables = new HashMap<>();
    variables.put("permission.vip", String.valueOf(player.hasPermission("vip")));
    variables.put("rank", String.valueOf(getRank(player)));

    if (conditionEngine.evaluate(player, "vip", variables)) {
        // Grant special interaction
    }
}
```

**After:**
```java
@EventHandler
public void onInteract(PlayerInteractEvent event) {
    Player player = event.getPlayer();

    ConditionContext ctx = createContext(player);

    // Async evaluation (thread-safe)
    conditions.evaluate(player, "vip", ctx).thenAccept(result -> {
        if (result) {
            // Grant special interaction (must run on main thread if needed)
            Bukkit.getScheduler().runTask(plugin, () -> {
                grantSpecialInteraction(player);
            });
        }
    });
}

private ConditionContext createContext(Player player) {
    Map<String, String> vars = new HashMap<>();
    vars.put("permission.vip", String.valueOf(player.hasPermission("vip")));
    vars.put("rank", String.valueOf(getRank(player)));

    return new SimpleConditionContext(vars);
}

// Simple implementation
class SimpleConditionContext implements ConditionContext {
    private final Map<String, String> variables;

    public SimpleConditionContext(Map<String, String> variables) {
        this.variables = variables;
    }

    @Override
    public @NotNull Map<String, String> variables() {
        return variables;
    }
}
```

**Before (AfterMotion - synchronous):**
```java
@EventHandler
public void onSceneCheck(CustomEvent event) {
    Player player = event.getPlayer();

    // Blocks main thread if using PlaceholderAPI!
    if (conditionEvaluator.evaluate(player, "%player_health% > 10")) {
        startScene(player);
    }
}
```

**After (thread-safe):**
```java
@EventHandler
public void onSceneCheck(CustomEvent event) {
    Player player = event.getPlayer();
    ConditionContext ctx = ConditionContext.EMPTY; // Or create with custom vars

    // Automatically runs on main thread when needed (for PAPI)
    conditions.evaluate(player, "%player_health% > 10", ctx).thenAccept(result -> {
        if (result) {
            startScene(player);
        }
    });
}
```

---

## Supported Operators

### Numeric Comparisons
```
%player_health% > 10
%player_level% >= 30
%rank% == 5
%deaths% != 0
%kills% < 100
%balance% <= 1000
```

### String Operators (AfterMotion compatibility + more)

| Operator | Description | Example |
|----------|-------------|---------|
| `equals` | Case-sensitive equality | `%player_world% equals world` |
| `!equals` | Case-sensitive inequality | `%player_world% !equals world_nether` |
| `equalsIgnoreCase` | Case-insensitive equality | `%player_name% equalsIgnoreCase admin` |
| `!equalsIgnoreCase` | Case-insensitive inequality | `%rank% !equalsIgnoreCase guest` |
| `contains` | Contains substring | `%player_name% contains VIP` |
| `!contains` | Does not contain | `%player_name% !contains Banned` |
| `startsWith` | Starts with prefix | `%player_world% startsWith world_` |
| `!startsWith` | Does not start with | `%message% !startsWith /` |
| `endsWith` | Ends with suffix | `%player_name% endsWith _Staff` |
| `!endsWith` | Does not end with | `%item% !endsWith _SWORD` |
| `matches` | Regex match | `%player_name% matches [A-Za-z0-9]+` |
| `!matches` | Regex no match | `%input% !matches .*hack.*` |
| `~` | Legacy contains (AfterMotion compat) | `%player_name% ~ Admin` |

### Logical Operators
```
%player_health% > 10 AND %player_level% >= 5
%permission.vip% == true OR %permission.mvp% == true
NOT %permission.banned% == true
(%health% > 5 AND %food% > 10) OR %permission.admin% == true
```

### Group References
```
# If you have a group "vip" defined:
conditions.evaluate(player, "vip", ctx);

# Groups are implicitly AND-ed:
vip:
  - "%player_level% >= 10"
  - "%player_permission% contains vip"
# Equivalent to: "%player_level% >= 10 AND %player_permission% contains vip"
```

---

## Placeholder Resolution Order

When evaluating an expression like `"%abs_flag:vip% == true AND %player_health% > 10"`:

1. **Custom Providers** (by namespace)
   - `%abs_flag:vip%` → `AbsVariableProvider.resolve(player, "flag:vip", ctx)`
   - `%motion_scene%` → `MotionVariableProvider.resolve(player, "scene", ctx)`

2. **PlaceholderAPI** (if installed) - **main thread only!**
   - `%player_health%` → PlaceholderAPI
   - `%vault_eco_balance%` → PlaceholderAPI

3. **Context Variables** (from `ConditionContext`)
   - Variables passed via `ctx.variables()`

4. **Fallback Placeholders** (when PAPI not available)
   - `%player_name%` → `player.getName()`
   - `%player_world%` → `player.getWorld().getName()`
   - `%player_health%` → `String.valueOf(player.getHealth())`

---

## Thread Safety

### Sync Evaluation (when you're already on main thread)

```java
// Safe ONLY if called from main thread
boolean result = conditions.evaluateSync(player, expression, ctx);
```

**Use when:**
- You're in an event handler (already on main thread)
- Expression doesn't use PlaceholderAPI
- You need immediate result

### Async Evaluation (recommended)

```java
// Always thread-safe - automatically switches to main thread when needed
CompletableFuture<Boolean> future = conditions.evaluate(player, expression, ctx);

future.thenAccept(result -> {
    if (result) {
        // Do something
    }
});
```

**Use when:**
- Expression might use PlaceholderAPI
- Called from async context
- Best practice for all cases

---

## Performance Tips

### Tip 1: Cache Parsed Expressions

AfterCore already caches parsed AST internally, but avoid re-parsing in hot loops:

**Bad:**
```java
for (Player player : onlinePlayers) {
    // Re-parses "vip" expression 500 times!
    conditions.evaluateSync(player, "vip", ctx);
}
```

**Good:**
```java
// Parse once, evaluate many times (internal cache handles this automatically)
// But still avoid unnecessary calls - use cached results when possible
```

### Tip 2: Batch Evaluations

```java
// Instead of evaluating one by one in a loop:
List<CompletableFuture<Boolean>> futures = new ArrayList<>();
for (Player player : players) {
    futures.add(conditions.evaluate(player, expression, ctx));
}

// Wait for all
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> {
        // All evaluations done
    });
```

### Tip 3: Use Context Variables for Derived Values

```java
// Compute expensive values once, pass via context
ConditionContext ctx = () -> {
    Map<String, String> vars = new HashMap<>();
    vars.put("faction_power", String.valueOf(computeFactionPower(player))); // Expensive
    vars.put("quest_progress", String.valueOf(getQuestProgress(player)));  // DB query
    return vars;
};

// Now %faction_power% and %quest_progress% are available in expressions
conditions.evaluate(player, "%faction_power% > 100", ctx);
```

---

## Migration Checklist

### AfterBlockState Migration
- [ ] Removed local `ConditionEngine` class
- [ ] Migrated condition groups to `setConditionGroups()`
- [ ] Registered custom provider for `abs` namespace
- [ ] Updated all `evaluate()` calls to use `ConditionService`
- [ ] Verified group evaluation works (implicitly AND-ed)

### AfterMotion Migration
- [ ] Removed local `ConditionEvaluator` class
- [ ] Migrated all string operators (contains, startsWith, etc.)
- [ ] Updated evaluation to use async `evaluate()` instead of sync
- [ ] Registered custom provider for `motion` namespace (if needed)
- [ ] Verified PlaceholderAPI placeholders still work

### Both Plugins
- [ ] No blocking evaluation on main thread
- [ ] Custom variables accessible via providers
- [ ] Performance improved (shared AST cache)
- [ ] Code duplication eliminated
- [ ] Tests pass with new ConditionService

---

## Common Patterns

### Pattern 1: Permission-Based Conditions

```java
public class PermissionConditionProvider implements ConditionVariableProvider {
    @Override
    public @Nullable String resolve(@NotNull Player player, @NotNull String variable,
                                      @NotNull ConditionContext ctx) {
        if (variable.startsWith("permission:")) {
            String perm = variable.substring(11);
            return String.valueOf(player.hasPermission(perm));
        }
        return null;
    }
}

// Register
core.conditions().registerVariableProvider("perm", new PermissionConditionProvider());

// Use
conditions.evaluate(player, "%perm:permission:vip.fly% == true", ctx);
```

### Pattern 2: Region-Based Conditions (WorldGuard)

```java
public class RegionConditionProvider implements ConditionVariableProvider {
    private final WorldGuardPlugin worldGuard;

    @Override
    public @Nullable String resolve(@NotNull Player player, @NotNull String variable,
                                      @NotNull ConditionContext ctx) {
        if (variable.equals("in_pvp_zone")) {
            return String.valueOf(isInPvpRegion(player));
        }
        if (variable.startsWith("region:")) {
            String regionName = variable.substring(7);
            return String.valueOf(isInRegion(player, regionName));
        }
        return null;
    }

    private boolean isInRegion(Player player, String regionName) {
        // WorldGuard integration
        return false;
    }

    private boolean isInPvpRegion(Player player) {
        return false;
    }
}

// Use
conditions.evaluate(player, "%wg:in_pvp_zone% == true", ctx);
```

### Pattern 3: Cached Context (Performance)

```java
public class PlayerConditionContext implements ConditionContext {
    private final Map<String, String> variables;

    public PlayerConditionContext(Player player, YourPlugin plugin) {
        this.variables = new HashMap<>();

        // Pre-compute expensive values
        variables.put("rank", String.valueOf(plugin.getRankManager().getRank(player)));
        variables.put("faction", plugin.getFactionManager().getFaction(player.getUniqueId()));
        variables.put("balance", String.valueOf(plugin.getEconomy().getBalance(player)));
    }

    @Override
    public @NotNull Map<String, String> variables() {
        return variables;
    }
}

// Use
ConditionContext ctx = new PlayerConditionContext(player, this);
conditions.evaluate(player, "%rank% >= 5 AND %balance% > 1000", ctx);
```

---

## Troubleshooting

### "Placeholder not resolved"

**Cause:** Placeholder not recognized by any provider

**Solutions:**
1. Check if PlaceholderAPI is installed (for PAPI placeholders)
2. Verify custom provider is registered for the namespace
3. Check provider's `resolve()` method handles the variable
4. Use `/acore status` to see registered providers (future feature)

### "Evaluation returns false unexpectedly"

**Cause:** Type mismatch or comparison error

**Debug:**
```java
conditions.evaluate(player, expression, ctx).thenAccept(result -> {
    plugin.getLogger().info("Expression: " + expression);
    plugin.getLogger().info("Result: " + result);
    plugin.getLogger().info("Context: " + ctx.variables());
});
```

**Common issues:**
- String vs numeric comparison: `"5" > 10` (false, parse to int first)
- Case sensitivity: use `equalsIgnoreCase` for case-insensitive checks
- Missing context variables

### "PlaceholderAPI placeholders not working"

**Cause:** Called from async thread or PAPI not installed

**Solutions:**
1. Use async `evaluate()` instead of `evaluateSync()`
2. Verify PlaceholderAPI is installed and enabled
3. Check placeholder is valid (test with `/papi parse <player> <placeholder>`)

---

## See Also

- [SqlService Migration Guide](sql-migration.md)
- [ActionService Migration Guide](action-migration.md)
- [ProtocolService Migration Guide](protocol-migration.md)
