# ActionService Migration Guide

## Overview

This guide helps you migrate action systems from AfterBlockAnimations and AfterMotion to the unified `ActionService` in AfterCore.

**Consolidates:**
- `AfterBlockAnimations`: Simple key-value actions (`"play_sound: LEVEL_UP, 1.0, 1.0"`)
- `AfterMotion`: Advanced DSL with triggers, conditions, and scopes (`"@tick:20 [?health > 10] title[NEARBY:30]: Welcome!"`)

---

## Benefits of Migration

- **Unified Parser**: One action parsing engine for all plugins
- **Dual Dialect Support**: Simple KV (default) + advanced Motion DSL
- **Extensible Registry**: No more giant switch/case per plugin
- **Condition Integration**: Built-in support via `ConditionService`
- **Scope Support**: VIEWER, NEARBY, ALL (Motion DSL)
- **Type Safety**: Structured `ActionSpec` instead of raw strings

---

## Dialect Overview

### SimpleKV Dialect (AfterBlockAnimations style)

**Format:** `<action_type>: <args>`

**Examples:**
```yaml
actions:
  - "play_sound: LEVEL_UP, 1.0, 1.0"
  - "send_message: &aWelcome to the server!"
  - "give_item: DIAMOND, 5"
  - "teleport: world, 0, 64, 0"
```

**With timing (frame-based or tick-based):**
```yaml
actions:
  - "time: 20, play_sound: CLICK"           # 20 ticks
  - "frame: 5, particle: FLAME, 10"         # Frame 5
  - "time: 60, send_message: 3 seconds!"    # 60 ticks
```

### MotionDSL Dialect (AfterMotion style)

**Format:** `@<trigger> [?<condition>] <action>[<scope>:<radius>]: <args>`

**Examples:**
```yaml
actions:
  - "@tick:20 play_sound: LEVEL_UP"                        # Tick 20, VIEWER scope
  - "@tick:40 [?%player_health% > 10] title: &aHealthy!"   # Conditional
  - "@event:interact message[NEARBY:30]: Someone clicked!" # Nearby players
  - "@spawn title[ALL]: &6Server Event!"                   # All online players
```

---

## Migration Steps

### Step 1: Understand Automatic Dialect Detection

AfterCore automatically detects which dialect to use:

```java
// In DefaultActionService.parse():
if (line.trim().startsWith("@")) {
    return motionDialect.parse(line);  // MotionDSL
} else {
    return simpleDialect.parse(line);   // SimpleKV
}
```

You **don't need to specify** the dialect - just write actions as before!

---

### Step 2: Remove Local Action Parsers

**Before (AfterBlockAnimations):**
```java
public class AnimationAction {
    private final String actionType;
    private final String args;
    private final int timeTicks;

    public static AnimationAction parse(String line) {
        // Manual parsing: "time: 20, play_sound: CLICK, 1.0, 1.0"
        String[] parts = line.split(",");
        // ... 30+ lines of brittle parsing logic
        return new AnimationAction(type, args, time);
    }

    public void execute(Player player) {
        switch (actionType) {
            case "play_sound":
                // Sound logic
                break;
            case "send_message":
                // Message logic
                break;
            // ... 20+ cases
        }
    }
}
```

**Before (AfterMotion):**
```java
public class MotionAction {
    public static MotionAction parse(String line) {
        // Regex parsing: "@tick:20 [?condition] action[NEARBY:30]: args"
        // ... 50+ lines of complex parsing
    }
}
```

**After (both plugins):**
```java
// No parsing code needed!
AfterCoreAPI core = AfterCore.get();
ActionSpec spec = core.actions().parse("play_sound: LEVEL_UP, 1.0, 1.0");

// Or for Motion DSL:
ActionSpec spec = core.actions().parse("@tick:20 [?health > 10] title: &aHello");
```

---

### Step 3: Register Action Handlers

Instead of a giant switch/case, register handlers by type:

**Before (switch/case hell):**
```java
public void executeAction(Player player, String actionType, String args) {
    switch (actionType) {
        case "play_sound":
            String[] soundArgs = args.split(",");
            player.playSound(player.getLocation(),
                Sound.valueOf(soundArgs[0]),
                Float.parseFloat(soundArgs[1]),
                Float.parseFloat(soundArgs[2]));
            break;

        case "send_message":
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', args));
            break;

        case "give_item":
            String[] itemArgs = args.split(",");
            Material mat = Material.valueOf(itemArgs[0]);
            int amount = Integer.parseInt(itemArgs[1]);
            player.getInventory().addItem(new ItemStack(mat, amount));
            break;

        // ... 20 more cases
    }
}
```

**After (clean registry):**
```java
public class YourPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        AfterCoreAPI core = AfterCore.get();

        // Register handlers
        core.actions().registerHandler("play_sound", new PlaySoundHandler());
        core.actions().registerHandler("send_message", new SendMessageHandler());
        core.actions().registerHandler("give_item", new GiveItemHandler());
        core.actions().registerHandler("teleport", new TeleportHandler());
        // ... clean and modular
    }
}

// Handler implementations
public class PlaySoundHandler implements ActionHandler {
    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        // spec.rawArgs() = "LEVEL_UP, 1.0, 1.0"
        String[] args = spec.rawArgs().split(",");

        Sound sound = Sound.valueOf(args[0].trim());
        float volume = Float.parseFloat(args[1].trim());
        float pitch = Float.parseFloat(args[2].trim());

        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}

public class SendMessageHandler implements ActionHandler {
    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        String message = ChatColor.translateAlternateColorCodes('&', spec.rawArgs());
        player.sendMessage(message);
    }
}

public class GiveItemHandler implements ActionHandler {
    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        String[] args = spec.rawArgs().split(",");
        Material material = Material.valueOf(args[0].trim());
        int amount = Integer.parseInt(args[1].trim());

        player.getInventory().addItem(new ItemStack(material, amount));
    }
}
```

---

### Step 4: Parse and Execute Actions

**Before (AfterBlockAnimations):**
```java
public class AnimationPlayer {
    public void playFrame(Player player, int frameIndex, List<String> actions) {
        for (String line : actions) {
            AnimationAction action = AnimationAction.parse(line);

            if (action.getFrameIndex() == frameIndex) {
                action.execute(player);
            }
        }
    }
}
```

**After:**
```java
public class AnimationPlayer {
    private final ActionService actionService;
    private final Map<String, ActionHandler> handlers;

    public AnimationPlayer(AfterCoreAPI core) {
        this.actionService = core.actions();
        // Handlers already registered in onEnable()
    }

    public void playFrame(Player player, int frameIndex, List<String> actionLines) {
        for (String line : actionLines) {
            ActionSpec spec = actionService.parse(line);
            if (spec == null) {
                plugin.getLogger().warning("Failed to parse action: " + line);
                continue;
            }

            // Check if this action should run at this frame
            if (spec.frameIndex() != null && spec.frameIndex() == frameIndex) {
                executeAction(player, spec);
            }
        }
    }

    private void executeAction(Player player, ActionSpec spec) {
        ActionHandler handler = getHandler(spec.typeKey());
        if (handler != null) {
            handler.execute(player, spec);
        } else {
            plugin.getLogger().warning("No handler for action type: " + spec.typeKey());
        }
    }

    private ActionHandler getHandler(String typeKey) {
        // Get from your registry (or core's future registry)
        return handlers.get(typeKey);
    }
}
```

**Before (AfterMotion):**
```java
public class ScenePlayer {
    public void tick(Player player, int currentTick) {
        for (MotionAction action : actions) {
            if (action.getTrigger().getTick() == currentTick) {
                if (action.hasCondition()) {
                    if (!conditionEvaluator.evaluate(player, action.getCondition())) {
                        continue;
                    }
                }

                executeWithScope(player, action);
            }
        }
    }

    private void executeWithScope(Player player, MotionAction action) {
        switch (action.getScope()) {
            case VIEWER:
                action.execute(player);
                break;
            case NEARBY:
                for (Player nearby : getNearby(player, action.getRadius())) {
                    action.execute(nearby);
                }
                break;
            case ALL:
                for (Player online : Bukkit.getOnlinePlayers()) {
                    action.execute(online);
                }
                break;
        }
    }
}
```

**After:**
```java
public class ScenePlayer {
    private final ActionService actionService;
    private final ConditionService conditionService;

    public ScenePlayer(AfterCoreAPI core) {
        this.actionService = core.actions();
        this.conditionService = core.conditions();
    }

    public void tick(Player player, int currentTick, List<String> actionLines) {
        for (String line : actionLines) {
            ActionSpec spec = actionService.parse(line);
            if (spec == null) continue;

            // Check trigger timing
            if (spec.trigger() != null && spec.trigger().getTick() != currentTick) {
                continue;
            }

            // Check condition (if present)
            if (spec.condition() != null) {
                conditionService.evaluate(player, spec.condition(), ConditionContext.EMPTY)
                    .thenAccept(result -> {
                        if (result) {
                            executeWithScope(player, spec);
                        }
                    });
            } else {
                executeWithScope(player, spec);
            }
        }
    }

    private void executeWithScope(Player player, ActionSpec spec) {
        Collection<? extends Player> targets = switch (spec.scope()) {
            case VIEWER -> List.of(player);
            case NEARBY -> getNearbyPlayers(player, spec.scopeRadius());
            case ALL -> Bukkit.getOnlinePlayers();
        };

        ActionHandler handler = getHandler(spec.typeKey());
        if (handler != null) {
            for (Player target : targets) {
                handler.execute(target, spec);
            }
        }
    }

    private Collection<Player> getNearbyPlayers(Player center, int radius) {
        return center.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distance(center.getLocation()) <= radius)
            .toList();
    }
}
```

---

## ActionSpec Structure

Every parsed action returns an `ActionSpec` with these fields:

```java
public final class ActionSpec {
    private final String typeKey;        // "play_sound", "send_message", etc.
    private final String rawArgs;        // "LEVEL_UP, 1.0, 1.0"

    // SimpleKV metadata
    private final Long timeTicks;        // From "time: 20, ..."
    private final Integer frameIndex;    // From "frame: 5, ..."

    // MotionDSL metadata
    private final ActionTrigger trigger; // @tick:20, @event:interact, etc.
    private final String condition;      // From [?condition]
    private final ActionScope scope;     // VIEWER, NEARBY, ALL
    private final int scopeRadius;       // If NEARBY

    private final String rawLine;        // Original unparsed line
}
```

**Access methods:**
```java
ActionSpec spec = actionService.parse("@tick:20 [?health > 10] title[NEARBY:30]: &aHello");

spec.typeKey();      // "title"
spec.rawArgs();      // "&aHello"
spec.trigger();      // ActionTrigger{type=TICK, tick=20}
spec.condition();    // "health > 10"
spec.scope();        // ActionScope.NEARBY
spec.scopeRadius();  // 30
spec.rawLine();      // Original line
```

---

## Example: Complete Migration

### Before (AfterBlockAnimations - all custom code)

**Config:**
```yaml
animations:
  welcome:
    frames:
      0:
        - "play_sound: LEVEL_UP, 1.0, 1.0"
        - "send_message: &aWelcome!"
      20:
        - "particle: FLAME, 10"
      40:
        - "title: &6Thanks for joining!"
```

**Code:**
```java
public class AnimationManager {
    private Map<String, Animation> animations = new HashMap<>();

    public void loadAnimations() {
        // Manual config parsing
        ConfigurationSection section = config.getConfigurationSection("animations");
        for (String key : section.getKeys(false)) {
            Animation anim = parseAnimation(section.getConfigurationSection(key));
            animations.put(key, anim);
        }
    }

    private Animation parseAnimation(ConfigurationSection section) {
        // 50+ lines of parsing logic
    }

    public void playAnimation(Player player, String animName) {
        Animation anim = animations.get(animName);
        if (anim == null) return;

        for (int frame = 0; frame <= anim.getDuration(); frame++) {
            int finalFrame = frame;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                List<AnimationAction> actions = anim.getFrame(finalFrame);
                for (AnimationAction action : actions) {
                    action.execute(player);
                }
            }, frame);
        }
    }
}
```

### After (using AfterCore)

**Config (unchanged):**
```yaml
animations:
  welcome:
    frames:
      0:
        - "play_sound: LEVEL_UP, 1.0, 1.0"
        - "send_message: &aWelcome!"
      20:
        - "particle: FLAME, 10"
      40:
        - "title: &6Thanks for joining!"
```

**Code:**
```java
public class AnimationManager {
    private final ActionService actionService;
    private final Map<String, Animation> animations = new HashMap<>();

    public AnimationManager(AfterCoreAPI core) {
        this.actionService = core.actions();
    }

    public void loadAnimations() {
        ConfigurationSection section = config.getConfigurationSection("animations");
        for (String key : section.getKeys(false)) {
            ConfigurationSection animSection = section.getConfigurationSection(key);
            ConfigurationSection framesSection = animSection.getConfigurationSection("frames");

            Map<Integer, List<ActionSpec>> frames = new HashMap<>();

            for (String frameKey : framesSection.getKeys(false)) {
                int frameIndex = Integer.parseInt(frameKey);
                List<String> lines = framesSection.getStringList(frameKey);

                List<ActionSpec> specs = lines.stream()
                    .map(actionService::parse)
                    .filter(Objects::nonNull)
                    .toList();

                frames.put(frameIndex, specs);
            }

            animations.put(key, new Animation(frames));
        }
    }

    public void playAnimation(Player player, String animName) {
        Animation anim = animations.get(animName);
        if (anim == null) return;

        for (Map.Entry<Integer, List<ActionSpec>> entry : anim.getFrames().entrySet()) {
            int frame = entry.getKey();
            List<ActionSpec> specs = entry.getValue();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (ActionSpec spec : specs) {
                    ActionHandler handler = getHandler(spec.typeKey());
                    if (handler != null) {
                        handler.execute(player, spec);
                    }
                }
            }, frame);
        }
    }
}

class Animation {
    private final Map<Integer, List<ActionSpec>> frames;

    public Animation(Map<Integer, List<ActionSpec>> frames) {
        this.frames = frames;
    }

    public Map<Integer, List<ActionSpec>> getFrames() {
        return frames;
    }
}
```

---

## Built-in vs Custom Handlers

### Option 1: Register Your Own Handlers

```java
// Register all your action types
core.actions().registerHandler("play_sound", new PlaySoundHandler());
core.actions().registerHandler("send_message", new SendMessageHandler());
core.actions().registerHandler("give_item", new GiveItemHandler());
// ... etc
```

### Option 2: Use AfterCore's Future Built-in Handlers (Planned)

In future versions, AfterCore may include common handlers:

```java
// Future API (planned):
core.actions().registerBuiltinHandlers(ActionHandlerRegistry.COMMON);
// Includes: play_sound, send_message, give_item, teleport, etc.
```

For now, each plugin registers its own handlers.

---

## Advanced: Custom Action Types

You can create plugin-specific action types:

**Example: AfterBlockState custom action**

```java
// Register
core.actions().registerHandler("abs_apply_state", new ApplyStateHandler(this));

// Use in config
actions:
  - "abs_apply_state: glass, 5, GLOWING"
```

**Handler implementation:**
```java
public class ApplyStateHandler implements ActionHandler {
    private final AfterBlockState plugin;

    public ApplyStateHandler(AfterBlockState plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        // spec.rawArgs() = "glass, 5, GLOWING"
        String[] args = spec.rawArgs().split(",");

        String blockType = args[0].trim();
        int radius = Integer.parseInt(args[1].trim());
        String state = args[2].trim();

        plugin.getStateManager().applyState(player, blockType, radius, state);
    }
}
```

---

## Condition Integration

MotionDSL actions can have inline conditions:

```yaml
actions:
  - "@tick:20 [?%player_health% > 10] title: &aYou're healthy!"
  - "@tick:40 [?%abs_flag:vip% == true] play_sound: LEVEL_UP"
```

When executing:

```java
if (spec.condition() != null) {
    core.conditions().evaluate(player, spec.condition(), ctx).thenAccept(result -> {
        if (result) {
            handler.execute(player, spec);
        }
    });
} else {
    handler.execute(player, spec);
}
```

---

## Scope Execution

MotionDSL supports scopes:

| Scope | Description | Example |
|-------|-------------|---------|
| `VIEWER` | Only the player (default) | `title: &aHello` |
| `NEARBY:<radius>` | Players within radius | `message[NEARBY:30]: Event!` |
| `ALL` | All online players | `broadcast[ALL]: Server restart!` |

**Implementation:**
```java
Collection<? extends Player> targets = switch (spec.scope()) {
    case VIEWER -> List.of(player);
    case NEARBY -> {
        int radius = spec.scopeRadius();
        yield player.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distance(player.getLocation()) <= radius)
            .toList();
    }
    case ALL -> Bukkit.getOnlinePlayers();
};

for (Player target : targets) {
    handler.execute(target, spec);
}
```

---

## Performance Tips

### Tip 1: Parse Once, Execute Many

```java
// Bad: Re-parsing every tick
for (int i = 0; i < 100; i++) {
    ActionSpec spec = actionService.parse("play_sound: CLICK");
    handler.execute(player, spec);
}

// Good: Parse once, reuse
ActionSpec spec = actionService.parse("play_sound: CLICK");
for (int i = 0; i < 100; i++) {
    handler.execute(player, spec);
}
```

### Tip 2: Cache Handler Lookups

```java
// Bad: Lookup handler every execution
ActionHandler handler = getHandler(spec.typeKey());

// Good: Cache handlers map
private final Map<String, ActionHandler> handlerCache = new ConcurrentHashMap<>();

public ActionHandler getHandler(String typeKey) {
    return handlerCache.computeIfAbsent(typeKey, k -> {
        // Lookup logic
    });
}
```

### Tip 3: Async Heavy Actions

```java
public class TeleportHandler implements ActionHandler {
    @Override
    public void execute(@NotNull Player player, @NotNull ActionSpec spec) {
        // Parse args (fast)
        String[] args = spec.rawArgs().split(",");
        String worldName = args[0].trim();
        double x = Double.parseDouble(args[1].trim());
        double y = Double.parseDouble(args[2].trim());
        double z = Double.parseDouble(args[3].trim());

        // Heavy operation: load chunk if needed (async)
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location loc = new Location(world, x, y, z);

        // Ensure chunk loaded before teleport
        world.getChunkAtAsync(loc).thenAccept(chunk -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.teleport(loc);
            });
        });
    }
}
```

---

## Migration Checklist

### AfterBlockAnimations Migration
- [ ] Removed local action parsing logic
- [ ] Registered all action handlers with `ActionService`
- [ ] Updated animation player to use `ActionSpec`
- [ ] Tested frame-based execution still works
- [ ] Verified timing (ticks/frames) preserved

### AfterMotion Migration
- [ ] Removed local MotionDSL parser
- [ ] Registered all action handlers
- [ ] Updated scene player to use `ActionSpec`
- [ ] Integrated conditions via `ConditionService`
- [ ] Verified scope execution (VIEWER/NEARBY/ALL) works
- [ ] Tested trigger timing (@tick, @event, etc.)

### Both Plugins
- [ ] No parsing code duplication
- [ ] Handlers are modular and testable
- [ ] Conditions integrated seamlessly
- [ ] Config format unchanged (backward compatible)
- [ ] Performance improved or maintained

---

## Common Patterns

### Pattern 1: Scheduled Action Execution

```java
public void scheduleAction(Player player, ActionSpec spec, long delayTicks) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        ActionHandler handler = getHandler(spec.typeKey());
        if (handler != null) {
            handler.execute(player, spec);
        }
    }, delayTicks);
}
```

### Pattern 2: Batch Action Execution

```java
public void executeAll(Player player, List<ActionSpec> specs) {
    for (ActionSpec spec : specs) {
        ActionHandler handler = getHandler(spec.typeKey());
        if (handler != null) {
            handler.execute(player, spec);
        } else {
            plugin.getLogger().warning("No handler for: " + spec.typeKey());
        }
    }
}
```

### Pattern 3: Conditional Action Chain

```java
public void executeChain(Player player, List<String> actionLines, ConditionContext ctx) {
    for (String line : actionLines) {
        ActionSpec spec = actionService.parse(line);
        if (spec == null) continue;

        if (spec.condition() != null) {
            conditionService.evaluate(player, spec.condition(), ctx).thenAccept(result -> {
                if (result) {
                    executeAction(player, spec);
                }
            });
        } else {
            executeAction(player, spec);
        }
    }
}
```

---

## Troubleshooting

### "Action failed to parse"

**Cause:** Invalid syntax

**Solutions:**
1. Check dialect (starts with `@` = MotionDSL, otherwise SimpleKV)
2. Verify syntax matches dialect format
3. Check logs for parsing errors
4. Test minimal action: `"send_message: test"`

### "No handler for action type"

**Cause:** Handler not registered

**Solutions:**
1. Verify `registerHandler()` was called in `onEnable()`
2. Check action type key matches (case-sensitive)
3. Use `/acore status` to list registered handlers (future feature)

### "Condition not evaluated"

**Cause:** ConditionService integration missing

**Solutions:**
1. Check if `spec.condition()` is not null
2. Use `conditionService.evaluate()` before executing
3. Verify condition syntax is valid

### "Scope not working"

**Cause:** Scope not implemented in execution logic

**Solutions:**
1. Check `spec.scope()` and `spec.scopeRadius()`
2. Implement target selection based on scope
3. Test with different scopes (VIEWER/NEARBY/ALL)

---

## See Also

- [SqlService Migration Guide](sql-migration.md)
- [ConditionService Migration Guide](condition-migration.md)
- [ProtocolService Migration Guide](protocol-migration.md)
