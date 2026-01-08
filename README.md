# AfterCore

**AfterLands - Core Library Plugin**

A high-performance library plugin for Minecraft Paper/Spigot 1.8.8 (Java 21) that provides shared infrastructure services for the AfterLands server ecosystem.

## Overview

AfterCore centralizes common infrastructure to eliminate code duplication across gameplay plugins while maintaining 20 TPS at 500+ concurrent players.

**This is a library/dependency plugin** - it provides APIs and services, not gameplay features.

## Key Features

### 🎨 Inventory Framework (NEW in 1.0.0)
Complete GUI/inventory system for Minecraft 1.8.8 with high performance and rich features:

- **YAML-Based Configuration** - Define inventories without code
- **Intelligent Caching** - 80-90% cache hit rate with Caffeine
- **Hybrid Pagination** - 3 modes (NATIVE_ONLY, LAYOUT_ONLY, HYBRID) - 35x faster for large inventories
- **Tab System** - Multi-category navigation with circular support
- **Frame-Based Animations** - Smooth item/title cycling
- **Drag-and-Drop** - Full support with anti-dupe protection
- **Shared Inventories** - Multi-player sessions with real-time sync
- **Database Persistence** - Auto-save/load inventory state
- **PlaceholderAPI Integration** - All placeholders supported (optional dependency)
- **NBT Customization** - via NBTAPI
- **12 Built-in Actions** - message, sound, title, teleport, commands, and more

**Performance**: <50ms open latency, 20 TPS @ 500 CCU, ~70MB memory footprint

[Usage Examples](docs/USAGE_EXAMPLES.md) | [API Reference](docs/API_REFERENCE.md) | [Migration Guide](docs/MIGRATION_GUIDE.md)

### 🔧 Core Services
- **Async-First Database** - HikariCP pooling with MySQL/SQLite support, migration system
- **Unified Condition Engine** - Replaces duplicate condition systems across plugins
- **Multi-Dialect Action System** - Supports SimpleKV and MotionDSL action formats
- **Protocol Coordination** - Prevents packet conflicts between plugins (ProtocolLib integration)
- **Thread Management** - Shared thread pools for I/O and CPU-bound tasks

### 🛠️ Utilities
- **CoreResult<T>** - Type-safe error handling without exceptions
- **Retry/Backoff** - Resilient execution with configurable policies
- **Rate Limiting** - Token bucket rate limiter with player cooldown service
- **Metrics** - Lightweight counters/timers/gauges with minimal overhead
- **Diagnostics** - Runtime health checks and system inspection

### 📊 Diagnostics & Monitoring
- `/acore` command with multiple subcommands (status, db, threads, system, metrics, memory)
- Real-time database ping and connection pool stats
- Memory leak detection for inventory framework
- Memory usage, JVM info, dependency detection
- Performance metrics collection and export

## Quick Start

### Building
```bash
mvn clean package
```
Output: `target/AfterCore-1.0.0.jar`

### Dependencies
- **Java 21** (required)
- **Spigot/Paper 1.8.8** (provided by server)
- **ProtocolLib** (optional, graceful degradation)
- **PlaceholderAPI** (optional, graceful degradation)

### Configuration
See `src/main/resources/config.yml` for database and concurrency settings.

Key settings:
- `config-version: 1` - Config versioning (auto-updated)
- `database.type: mysql|sqlite` - Database backend
- `concurrency.io-threads: 8` - I/O thread pool size
- `debug: false` - Enable verbose logging

## Usage for Plugin Developers

### Getting the API
```java
AfterCoreAPI core = AfterCore.get();
```

### Inventory Framework

Create custom inventories via YAML configuration:

```yaml
# plugins/AfterCore/inventories.yml
inventories:
  shop:
    title: "&6&lShop"
    size: 54

    pagination:
      enabled: true
      mode: HYBRID
      items_per_page: 28

    items:
      - slot: 13
        material: DIAMOND_SWORD
        display_name: "&cEspada Lendária"
        lore:
          - "&7Preço: &a$1000"
        click_actions:
          - "console: give %player% diamond_sword 1"
          - "sound: LEVEL_UP"
```

Open inventories programmatically:

```java
InventoryService inv = core.inventory();
InventoryContext ctx = InventoryContext.builder(player)
    .withPlaceholder("player_level", String.valueOf(player.getLevel()))
    .build();

inv.openInventory(player, "shop", ctx);
```

See [USAGE_EXAMPLES.md](docs/USAGE_EXAMPLES.md) for 10+ practical examples.

### Database Operations
```java
// Async query
core.sql().runAsync(conn -> {
    try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM players WHERE uuid = ?")) {
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();
        // ... process results
    }
}).thenAccept(result -> {
    // Handle result on completion
});

// Register migrations
core.sql().registerMigration("myplugin:001_init", conn -> {
    try (Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE TABLE IF NOT EXISTS my_table (...)");
    }
});
```

### Error Handling
```java
CoreResult<PlayerData> result = loadPlayerData(uuid);

// Pattern matching (Java 21)
return switch (result) {
    case CoreResult.Ok(var data) -> processData(data);
    case CoreResult.Err(var error) -> {
        logger.warning("Failed to load: " + error.message());
        yield DEFAULT_DATA;
    }
};

// Functional style
PlayerData data = result
    .map(this::enrichData)
    .recover(err -> DEFAULT_DATA)
    .orElse(FALLBACK_DATA);
```

### Retry Logic
```java
RetryPolicy policy = RetryPolicy.defaultDatabasePolicy();
RetryExecutor executor = new RetryExecutor(policy, logger, false);

// Sync with retry
PlayerData data = executor.execute(() -> queryDatabase(uuid));

// Async with retry
CompletableFuture<PlayerData> future = executor.executeAsync(
    () -> queryDatabase(uuid),
    core.scheduler().ioExecutor()
);
```

### Rate Limiting
```java
CooldownService cooldowns = new CooldownService();

if (cooldowns.tryAcquire(player, "teleport", Duration.ofSeconds(10))) {
    // Perform teleport
} else {
    Optional<Duration> remaining = cooldowns.tryAcquireWithRemaining(player, "teleport", Duration.ofSeconds(10));
    player.sendMessage("Cooldown: " + CooldownService.formatDuration(remaining.get()));
}
```

### Metrics
```java
MetricsService metrics = core.metrics();

metrics.increment("events.player_join");
metrics.time("database.query.player", () -> loadPlayer(uuid));
metrics.gauge("cache.size", playerCache.size());

// Get snapshot
MetricsSnapshot snapshot = metrics.snapshot();
logger.info(snapshot.format());
```

### Conditions & Actions
```java
ConditionService conditions = core.conditions();
ActionService actions = core.actions();

// Evaluate conditions
ConditionContext ctx = new ConditionContext(player);
boolean result = conditions.evaluate("health > 10 AND has_permission:myplugin.use", ctx);

// Parse actions
ActionSpec spec = actions.parseAction("teleport: world,100,64,100");
```

## Design Principles

### Main Thread Sacred
**ZERO blocking I/O on the main thread.** All database, filesystem, and network operations must be async.

❌ **NEVER**:
```java
Connection conn = core.sql().getConnection(); // Blocks!
```

✅ **ALWAYS**:
```java
core.sql().runAsync(conn -> { /* query */ });
```

### Graceful Degradation
If optional dependencies (ProtocolLib, PlaceholderAPI) are missing, degrade functionality instead of crashing.

### Thread Safety
All services are thread-safe. Methods requiring main thread execution are documented.

**Exception**: PlaceholderAPI expansion must run on main thread (Bukkit requirement).

## Commands

### `/acore` - Diagnostics
**Permission**: `aftercore.admin`

Subcommands:
- `/acore status` - Dependencies, versions, debug mode
- `/acore db` - Database status, ping, pool stats
- `/acore threads` - Thread pool information
- `/acore system` - JVM, OS, memory, CPU cores
- `/acore metrics` - Metrics snapshot
- `/acore memory` - Memory leak detection (Inventory Framework)
- `/acore all` - All diagnostic information

## Documentation

### For Users
- **[USAGE_EXAMPLES.md](docs/USAGE_EXAMPLES.md)** - 10 practical examples
- **[API_REFERENCE.md](docs/API_REFERENCE.md)** - Complete API documentation
- **[MIGRATION_GUIDE.md](docs/MIGRATION_GUIDE.md)** - Migrate from AfterBlockAnimations
- **[CHANGELOG.md](CHANGELOG.md)** - Version history and release notes

### For Developers
- **[CLAUDE.md](CLAUDE.md)** - Development guide for Claude Code
- **[implementation_plan.md](implementation_plan.md)** - Detailed implementation roadmap
- **[afterlands-architect.md](afterlands-architect.md)** - System architecture guidelines

## Project Status

### ✅ Completed (v1.0.0)
- **Inventory Framework** - Complete GUI system with pagination, tabs, animations, drag-and-drop, shared inventories, persistence
- Core service framework (Scheduler, Config, Messages, SQL, Conditions, Actions, Protocol, Commands)
- Config validation and auto-update system
- CoreResult error handling pattern
- Retry/Backoff utilities
- Rate limiting and cooldown service
- Metrics collection
- Diagnostics service and `/acore` command
- Memory leak detection for inventories
- Comprehensive documentation (usage examples, API reference, migration guide)

### 🚧 In Progress
- Plugin migration (AfterBlockState, AfterMotion, AfterBlockAnimations)
- Full ProtocolLib chunk mutation pipeline

### 📋 Planned
- Multi-datasource registry (separate databases for analytics, etc.)
- Extended metrics (histograms, percentiles)
- Command framework expansion

## Performance Targets

- **20 TPS constant** at 500+ CCU
- **Main thread budget**: Document TPS impact for all features
- **Async-first**: No blocking operations on main thread
- **Bounded caches**: All caches have max size + TTL

## Contributing

This is an internal project for AfterLands. Follow the architecture guidelines in `afterlands-architect.md`.

## License

Internal use only - AfterLands server ecosystem.

---

**Version**: 1.0.0
**Minecraft**: 1.8.8 (Paper/Spigot)
**Java**: 21
**Build**: Maven
