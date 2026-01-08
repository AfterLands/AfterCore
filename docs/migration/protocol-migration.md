# ProtocolService Migration Guide

## Overview

This guide helps you migrate from direct ProtocolLib packet interceptors to the centralized `ProtocolService` pipeline in AfterCore.

**Problem it solves:**
- Multiple plugins intercepting MAP_CHUNK packets = conflicts
- Race conditions when plugins override each other's block mutations
- Packet spam on chunk loads (login/teleport)
- No deterministic merge strategy

**AfterCore's solution:**
- Single MAP_CHUNK interceptor for all plugins
- Priority-based merge (deterministic "last wins")
- Built-in debouncing/batching
- Metrics for conflicts and performance

---

## Benefits of Migration

- **Zero Conflicts**: One pipeline, deterministic merge
- **Better Performance**: Debounced packet sending reduces spam
- **Unified Metrics**: Track mutations and conflicts via `/acore protocol`
- **Graceful Degradation**: Works without ProtocolLib (no-op)
- **Cleaner Code**: Just implement `ChunkMutationProvider`, no packet handling

---

## Current State (v0.1.0)

**IMPORTANT:** The full ProtocolService pipeline is **not yet implemented** in AfterCore v0.1.0.

**What exists now:**
- `ProtocolService` interface and stub implementation
- `ChunkMutationProvider` contract
- Registration API: `registerChunkProvider(provider)`
- Graceful degradation when ProtocolLib is absent

**What's coming (v0.4):**
- Actual MAP_CHUNK/MAP_CHUNK_BULK listener
- Debouncing/batching per player
- Deterministic merge algorithm
- MULTI_BLOCK_CHANGE packet construction
- Metrics and diagnostics

**For now**, this guide prepares you for the migration by:
1. Explaining the new architecture
2. Showing how to implement `ChunkMutationProvider`
3. Documenting the migration path

---

## Architecture

### Before (AfterBlockState - direct ProtocolLib)

```java
public class BlockStateProtocolListener {
    private final ProtocolManager protocolManager;

    public void register() {
        protocolManager.addPacketListener(new PacketAdapter(
            plugin,
            ListenerPriority.HIGH,
            PacketType.Play.Server.MAP_CHUNK
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                PacketContainer packet = event.getPacket();

                // Extract chunk coords
                int chunkX = packet.getIntegers().read(0);
                int chunkZ = packet.getIntegers().read(1);

                // Get state targets for this chunk
                List<StateTarget> targets = getTargetsInChunk(player, chunkX, chunkZ);

                if (!targets.isEmpty()) {
                    // Send MULTI_BLOCK_CHANGE after MAP_CHUNK
                    sendBlockChanges(player, chunkX, chunkZ, targets);
                }
            }
        });
    }

    private void sendBlockChanges(Player player, int chunkX, int chunkZ, List<StateTarget> targets) {
        // 50+ lines of packet construction...
    }
}
```

**Problems:**
1. If AfterBlockAnimations also intercepts MAP_CHUNK, packets conflict
2. Each plugin sends separate MULTI_BLOCK_CHANGE packets
3. No coordination = race conditions
4. No debouncing = packet spam on teleport

### After (AfterCore pipeline)

```java
public class BlockStateChunkProvider implements ChunkMutationProvider {
    private final AfterBlockState plugin;

    public BlockStateChunkProvider(AfterBlockState plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return "afterblockstate";
    }

    @Override
    public int priority() {
        return 100; // Higher = applied later (wins conflicts)
    }

    @Override
    public @NotNull List<BlockMutation> mutationsForChunk(
            @NotNull Player player,
            @NotNull World world,
            int chunkX,
            int chunkZ) {

        // Return mutations from cache (don't block main thread!)
        List<StateTarget> targets = plugin.getStateManager()
            .getCachedTargetsInChunk(player, world, chunkX, chunkZ);

        return targets.stream()
            .map(target -> new BlockMutation(
                target.getX(),
                target.getY(),
                target.getZ(),
                target.getFakeMaterial(),
                target.getFakeData()
            ))
            .toList();
    }
}

// Register in onEnable
core.protocol().registerChunkProvider(new BlockStateChunkProvider(this));
```

**Benefits:**
1. AfterCore merges all providers' mutations deterministically
2. Single MULTI_BLOCK_CHANGE packet per chunk
3. Debouncing handled by core
4. No packet code needed!

---

## Migration Steps

### Step 1: Implement ChunkMutationProvider

**ChunkMutationProvider interface:**
```java
public interface ChunkMutationProvider {
    @NotNull String id();          // Unique identifier
    int priority();                 // Merge order (higher = later)
    @NotNull List<BlockMutation> mutationsForChunk(
        @NotNull Player player,
        @NotNull World world,
        int chunkX,
        int chunkZ
    );
}
```

**BlockMutation structure:**
```java
public record BlockMutation(
    int x,              // World coordinates
    int y,
    int z,
    Material material,  // Fake block type
    byte data           // Fake block data (1.8.8)
) {}
```

**Example implementation:**
```java
public class MyChunkProvider implements ChunkMutationProvider {
    private final MyPlugin plugin;
    private final LoadingCache<ChunkKey, List<BlockData>> cache;

    public MyChunkProvider(MyPlugin plugin) {
        this.plugin = plugin;

        // Cache block data per chunk to avoid DB queries on every chunk send
        this.cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(key -> loadBlocksFromDatabase(key));
    }

    @Override
    public @NotNull String id() {
        return "myplugin";
    }

    @Override
    public int priority() {
        return 100; // Adjust based on desired merge order
    }

    @Override
    public @NotNull List<BlockMutation> mutationsForChunk(
            @NotNull Player player,
            @NotNull World world,
            int chunkX,
            int chunkZ) {

        ChunkKey key = ChunkKey.of(world.getName(), chunkX, chunkZ);

        // Get from cache (fast, non-blocking)
        List<BlockData> blocks = cache.getIfPresent(key);
        if (blocks == null || blocks.isEmpty()) {
            return Collections.emptyList();
        }

        // Filter blocks visible to this player
        return blocks.stream()
            .filter(block -> isVisibleTo(player, block))
            .map(block -> new BlockMutation(
                block.getX(),
                block.getY(),
                block.getZ(),
                block.getFakeMaterial(),
                block.getFakeData()
            ))
            .toList();
    }

    private boolean isVisibleTo(Player player, BlockData block) {
        // Your permission/condition logic
        return player.hasPermission("myplugin.see." + block.getType());
    }

    private List<BlockData> loadBlocksFromDatabase(ChunkKey key) {
        // Load async initially, return empty for now
        plugin.getCore().sql().supplyAsync(conn -> {
            // Query database for blocks in this chunk
            return queryBlocks(conn, key);
        }).thenAccept(blocks -> {
            cache.put(key, blocks);
        });

        return Collections.emptyList();
    }
}
```

---

### Step 2: Register Provider

**In your plugin's onEnable:**
```java
@Override
public void onEnable() {
    AfterCoreAPI core = AfterCore.get();

    // Register chunk provider
    MyChunkProvider provider = new MyChunkProvider(this);
    core.protocol().registerChunkProvider(provider);

    // Start protocol service
    core.protocol().start();
}
```

**In your plugin's onDisable:**
```java
@Override
public void onDisable() {
    // AfterCore handles cleanup, but you can stop explicitly:
    AfterCore.get().protocol().stop();
}
```

---

### Step 3: Remove Direct ProtocolLib Listeners

**Before:**
```java
@Override
public void onEnable() {
    ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(...) {
        // Direct packet interception
    });
}

@Override
public void onDisable() {
    ProtocolLibrary.getProtocolManager().removePacketListener(listener);
}
```

**After:**
```java
// No direct ProtocolLib usage needed!
// Just implement ChunkMutationProvider and register it.
```

---

## Priority System

**How priority works:**
- Providers are sorted by priority (ascending order)
- Mutations are merged in order: **last wins** for conflicting blocks
- Higher priority = applied later = overrides lower priority

**Example priorities:**
```java
// Low priority (applied first, can be overridden)
public int priority() { return 50; }

// Medium priority (default)
public int priority() { return 100; }

// High priority (applied last, wins conflicts)
public int priority() { return 200; }
```

**Use case - layered mutations:**
```
Priority  50: Base terrain modifications (AfterTerrainPlugin)
Priority 100: Player-specific blocks (AfterBlockState)
Priority 200: Temporary visual effects (AfterBlockAnimations)
```

If all three plugins want to modify block (100, 64, 100):
1. Terrain plugin applies first: STONE
2. BlockState overrides: GLASS (player sees GLASS state)
3. BlockAnimations overrides: GLOWSTONE (animation wins)

**Result:** Player sees GLOWSTONE at (100, 64, 100)

---

## Caching Strategy (Critical!)

**NEVER query database in `mutationsForChunk()`** - this is called on packet send and MUST be fast!

### Pattern 1: Pre-load on Chunk Load

```java
@EventHandler
public void onChunkLoad(ChunkLoadEvent event) {
    Chunk chunk = event.getChunk();
    ChunkKey key = ChunkKey.of(chunk);

    // Load async, populate cache
    plugin.getCore().sql().supplyAsync(conn -> {
        return queryBlocksInChunk(conn, key);
    }).thenAccept(blocks -> {
        cache.put(key, blocks);
    });
}
```

### Pattern 2: Load on Player Join

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    Location loc = player.getLocation();

    // Pre-load chunks around player
    int playerChunkX = loc.getBlockX() >> 4;
    int playerChunkZ = loc.getBlockZ() >> 4;

    for (int dx = -3; dx <= 3; dx++) {
        for (int dz = -3; dz <= 3; dz++) {
            ChunkKey key = ChunkKey.of(
                loc.getWorld().getName(),
                playerChunkX + dx,
                playerChunkZ + dz
            );

            loadChunkAsync(key);
        }
    }
}
```

### Pattern 3: Lazy Load + Empty Fallback

```java
@Override
public @NotNull List<BlockMutation> mutationsForChunk(...) {
    ChunkKey key = ChunkKey.of(world.getName(), chunkX, chunkZ);

    // Return cached or empty
    List<BlockData> cached = cache.getIfPresent(key);
    if (cached == null) {
        // Trigger async load for next time
        loadChunkAsync(key);
        return Collections.emptyList();
    }

    return cached.stream()
        .map(this::toMutation)
        .toList();
}
```

---

## Per-Player vs Global Mutations

### Global Mutations (all players see the same)

```java
@Override
public @NotNull List<BlockMutation> mutationsForChunk(
        @NotNull Player player, @NotNull World world, int chunkX, int chunkZ) {

    // Ignore player parameter - all players see same blocks
    ChunkKey key = ChunkKey.of(world.getName(), chunkX, chunkZ);
    return globalCache.get(key).stream()
        .map(this::toMutation)
        .toList();
}
```

**Example use case:** Terrain modifications, server-wide custom blocks

### Per-Player Mutations (player-specific)

```java
@Override
public @NotNull List<BlockMutation> mutationsForChunk(
        @NotNull Player player, @NotNull World world, int chunkX, int chunkZ) {

    UUID uuid = player.getUniqueId();
    ChunkKey key = ChunkKey.of(world.getName(), chunkX, chunkZ);

    // Use per-player cache
    return perPlayerCache.get(uuid).stream()
        .filter(block -> block.isInChunk(chunkX, chunkZ))
        .filter(block -> block.isVisibleTo(player)) // Permission check
        .map(this::toMutation)
        .toList();
}
```

**Example use case:** AfterBlockState (player sees different block states based on their flags)

---

## Thread Safety

### Safe (called on main thread by AfterCore):
```java
@Override
public @NotNull List<BlockMutation> mutationsForChunk(...) {
    // This runs on main thread (packet send context)
    // Safe to:
    // - Read from thread-safe cache (Caffeine)
    // - Access Player object
    // - Call Bukkit APIs (if needed)

    // NOT safe to:
    // - Block on I/O (database, file)
    // - Long computations
}
```

### Async Updates (when data changes):
```java
public void updateBlock(UUID playerId, BlockData block) {
    // Update cache async
    core.scheduler().ioExecutor().execute(() -> {
        // Update database
        updateDatabase(playerId, block);

        // Update cache (thread-safe)
        cache.invalidate(ChunkKey.of(block));

        // Trigger chunk refresh for online player
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            refreshChunk(player, block.getChunkX(), block.getChunkZ());
        }
    });
}

private void refreshChunk(Player player, int chunkX, int chunkZ) {
    // Force re-send chunk (causes AfterCore pipeline to re-run)
    player.getWorld().refreshChunk(chunkX, chunkZ);
}
```

---

## Debugging and Metrics

### Future `/acore protocol` command (v0.4):

```
/acore protocol status
> Providers: 3 registered
> - afterblockstate (priority 100): 1,245 mutations/minute
> - afterblockanimations (priority 200): 342 mutations/minute
> - customterrain (priority 50): 89 mutations/minute
>
> Conflicts: 23 in last minute (1.8% conflict rate)
> Packets sent: 156 MULTI_BLOCK_CHANGE in last minute
> Avg mutations per packet: 8.2
```

### Logging in Your Provider:

```java
@Override
public @NotNull List<BlockMutation> mutationsForChunk(...) {
    List<BlockMutation> mutations = // ... generate mutations

    if (plugin.isDebugEnabled()) {
        plugin.getLogger().info(String.format(
            "[%s] Chunk (%d, %d): %d mutations for %s",
            id(), chunkX, chunkZ, mutations.size(), player.getName()
        ));
    }

    return mutations;
}
```

---

## Migration Checklist

- [ ] Implemented `ChunkMutationProvider` interface
- [ ] Chose appropriate priority level
- [ ] Implemented caching strategy (no DB in `mutationsForChunk`)
- [ ] Registered provider in `onEnable()`
- [ ] Removed direct ProtocolLib packet listeners
- [ ] Tested with multiple players in same chunk
- [ ] Verified graceful degradation when ProtocolLib is absent
- [ ] Added debug logging for troubleshooting
- [ ] Handled per-player vs global mutations correctly
- [ ] Tested conflict resolution with other providers (if applicable)

---

## Common Patterns

### Pattern 1: State-Based Mutations (AfterBlockState)

```java
public class StateChunkProvider implements ChunkMutationProvider {
    private final LoadingCache<UUID, PlayerStateData> playerStates;

    @Override
    public @NotNull List<BlockMutation> mutationsForChunk(
            @NotNull Player player, @NotNull World world, int chunkX, int chunkZ) {

        PlayerStateData data = playerStates.getIfPresent(player.getUniqueId());
        if (data == null) return Collections.emptyList();

        return data.getActiveStates().stream()
            .filter(state -> state.isInChunk(chunkX, chunkZ))
            .flatMap(state -> state.getBlocksInChunk(chunkX, chunkZ).stream())
            .map(block -> new BlockMutation(
                block.x, block.y, block.z,
                state.getFakeMaterial(),
                state.getFakeData()
            ))
            .toList();
    }
}
```

### Pattern 2: Animation-Based Mutations (AfterBlockAnimations)

```java
public class AnimationChunkProvider implements ChunkMutationProvider {
    private final Map<UUID, List<ActiveAnimation>> activeAnimations;

    @Override
    public int priority() {
        return 200; // High priority to override other providers
    }

    @Override
    public @NotNull List<BlockMutation> mutationsForChunk(
            @NotNull Player player, @NotNull World world, int chunkX, int chunkZ) {

        List<ActiveAnimation> anims = activeAnimations.get(player.getUniqueId());
        if (anims == null || anims.isEmpty()) return Collections.emptyList();

        int currentFrame = getCurrentFrame();

        return anims.stream()
            .filter(anim -> anim.isInChunk(chunkX, chunkZ))
            .flatMap(anim -> anim.getBlocksAtFrame(currentFrame).stream())
            .map(block -> new BlockMutation(
                block.x, block.y, block.z,
                block.material, block.data
            ))
            .toList();
    }

    private int getCurrentFrame() {
        // Calculate based on server tick or animation timer
        return (int) ((System.currentTimeMillis() / 50) % 100);
    }
}
```

### Pattern 3: Region-Based Mutations

```java
public class RegionChunkProvider implements ChunkMutationProvider {
    private final LoadingCache<ChunkKey, List<RegionBlock>> regionBlocks;

    @Override
    public @NotNull List<BlockMutation> mutationsForChunk(
            @NotNull Player player, @NotNull World world, int chunkX, int chunkZ) {

        ChunkKey key = ChunkKey.of(world.getName(), chunkX, chunkZ);
        List<RegionBlock> blocks = regionBlocks.getIfPresent(key);

        if (blocks == null) {
            return Collections.emptyList();
        }

        // Filter by permission
        return blocks.stream()
            .filter(block -> player.hasPermission("region.see." + block.getRegionId()))
            .map(block -> new BlockMutation(
                block.getX(), block.getY(), block.getZ(),
                block.getMaterial(), block.getData()
            ))
            .toList();
    }
}
```

---

## Troubleshooting

### "Mutations not appearing"

**Possible causes:**
1. Provider not registered
2. Empty cache (data not loaded yet)
3. Lower priority (overridden by another provider)
4. ProtocolLib not installed (graceful degradation)

**Debug steps:**
```java
// Add logging to mutationsForChunk
plugin.getLogger().info("Mutations for chunk " + chunkX + ", " + chunkZ + ": " + mutations.size());

// Check if provider is registered
core.protocol().registerChunkProvider(provider);
plugin.getLogger().info("Registered provider: " + provider.id());
```

### "Conflicts with another plugin"

**Cause:** Another provider has higher priority and overrides your blocks

**Solution:** Adjust priority or coordinate with other plugin authors

```java
@Override
public int priority() {
    return 150; // Higher than default 100, lower than 200
}
```

### "Performance degradation"

**Cause:** `mutationsForChunk()` is doing heavy work

**Solutions:**
1. Move data loading to async (chunk load event, player join)
2. Use Caffeine cache with proper eviction
3. Limit mutation count per chunk (AfterCore may add limits in future)

```java
// Bad: Query DB on every chunk send
List<BlockMutation> mutations = queryDatabase(chunkX, chunkZ); // BLOCKS!

// Good: Return cached data
List<BlockMutation> mutations = cache.getIfPresent(key);
if (mutations == null) {
    loadAsync(key); // Load for next time
    return Collections.emptyList();
}
```

### "Chunk not refreshing after data update"

**Cause:** Cache updated but client not notified

**Solution:** Force chunk refresh

```java
public void updatePlayerState(Player player, StateData data) {
    // Update cache
    cache.put(player.getUniqueId(), data);

    // Refresh affected chunks
    Set<ChunkKey> affectedChunks = data.getAffectedChunks();
    for (ChunkKey key : affectedChunks) {
        player.getWorld().refreshChunk(key.x(), key.z());
    }
}
```

---

## Future Enhancements (Post v0.4)

When the full pipeline is implemented, expect:

1. **Metrics Dashboard**: Detailed conflict and performance metrics
2. **Rate Limiting**: Automatic throttling of excessive mutations
3. **Priority Visualization**: Debug tool to see merge order
4. **Chunk Mutation Events**: Hook into pipeline lifecycle
5. **Built-in Debouncing Config**: Tune batching behavior

---

## See Also

- [SqlService Migration Guide](sql-migration.md)
- [ConditionService Migration Guide](condition-migration.md)
- [ActionService Migration Guide](action-migration.md)
- [Spatial Utilities Guide](../spatial-utils.md) (for ChunkKey usage)
