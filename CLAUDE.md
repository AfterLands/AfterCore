# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**AfterCore** is a library plugin for AfterLands Minecraft server (Paper/Spigot 1.8.8 + Java 21). It provides shared infrastructure services to eliminate duplication across multiple gameplay plugins in a high-performance environment (500+ CCU targeting constant 20 TPS).

**Critical**: This is a **library/dependency plugin**, not a gameplay plugin. It provides APIs and services for other plugins to consume.

## Build & Development Commands

### Build
```bash
mvn clean package
```
Output: `target/AfterCore-0.1.0.jar`

### Testing
No test framework is configured yet. When tests are added, use:
```bash
mvn test
```

### Dependencies
The project uses Maven shade plugin to relocate internal dependencies:
- HikariCP → `com.afterlands.core.libs.hikari`
- Caffeine → `com.afterlands.core.libs.caffeine`
- MySQL Connector → `com.afterlands.core.libs.mysql`

**Important**: The public API must NEVER expose these relocated types.

## Architecture

### Service Discovery Pattern
AfterCore registers `AfterCoreAPI` in Bukkit's `ServicesManager`. Consumer plugins access it via:

```java
AfterCoreAPI core = AfterCore.get();
core.sql().runAsync(conn -> { /* query */ });
```

The helper `AfterCore.get()` caches internally and invalidates on enable/disable.

### Core Services

The plugin exposes 10 core services through `AfterCoreAPI`:

1. **SchedulerService** (`scheduler()`) - Thread pools and sync/async execution
   - `ioExecutor()` - For blocking I/O (DB, filesystem)
   - `cpuExecutor()` - For CPU-bound tasks
   - `runSync()` / `runLaterSync()` - Main thread execution

2. **SqlService** (`sql()`) - Database connection pooling and async queries
   - Supports MySQL (production) and SQLite (dev/local)
   - All operations return `CompletableFuture<T>`
   - Migration system via `registerMigration(id, migration)`

3. **ConditionService** (`conditions()`) - Unified condition evaluation engine
   - Replaces duplicate condition engines from AfterBlockState and AfterMotion
   - Supports AND/OR/NOT, numeric comparisons, string operations
   - Integrates with PlaceholderAPI (main thread only) and custom variable providers

4. **ActionService** (`actions()`) - Action parsing and execution with multiple dialects
   - SimpleKV dialect: `"action: args"` style (AfterBlockAnimations)
   - MotionDSL dialect: `"@tick:20 [?cond] action[scope]: args"` (AfterMotion)
   - Registry-based handler system (no switch/case)

5. **ProtocolService** (`protocol()`) - ProtocolLib packet manipulation coordination
   - Graceful degradation if ProtocolLib not present
   - Chunk mutation pipeline to prevent conflicts between plugins
   - Currently a stub; full MAP_CHUNK implementation pending

6. **ConfigService** (`config()`) - Configuration management with validation
   - Auto-update system preserving user values
   - Schema validation with detailed error paths

7. **MessageService** (`messages()`) - Message handling

8. **CommandService** (`commands()`) - Command registration framework (stub)

9. **DiagnosticsService** (`diagnostics()`) - Runtime diagnostics and health checks
   - Captures system snapshots (dependencies, DB, threads, memory)
   - Powers `/acore` command for runtime inspection

10. **MetricsService** (`metrics()`) - Lightweight metrics collection
    - Counters, timers, gauges with minimal overhead
    - Thread-safe atomic operations
    - Snapshot export for monitoring

### Service Initialization Order

See `AfterCorePlugin.onEnable()`:
1. Config validation and update (fail-fast on critical errors)
2. Scheduler
3. Config & Messages
4. SqlService (with config reload)
5. Conditions
6. Actions
7. Commands
8. Protocol (calls `start()` with graceful degradation)
9. Diagnostics
10. Metrics
11. Register in ServicesManager

## Mandatory Design Principles

### 1. Main Thread Sacred
**ZERO blocking operations on the main thread.** All I/O (DB, filesystem, network) must be async.

- ❌ **NEVER**: `sql.getConnection()` on main thread
- ✅ **ALWAYS**: `sql.runAsync(conn -> { ... })`

Exception: PlaceholderAPI expansion **must** run on main thread (Bukkit API requirement).

### 2. Graceful Degradation
If optional dependencies (ProtocolLib, PlaceholderAPI) are missing:
- Degrade functionality, don't crash
- Log clear warnings
- Example: `DefaultProtocolService` returns empty mutations if ProtocolLib absent

### 3. Thread Safety
All service implementations must be thread-safe. Document which methods require main thread.

### 4. No NMS Without Justification
Maintain Bukkit/Spigot 1.8 API compatibility. Use ProtocolLib for packet manipulation. NMS requires explicit justification and fallback plan.

## Database Architecture

### Current: Single Datasource
Configuration in `config.yml`:
- `database.type`: `mysql` or `sqlite`
- Connection pooling via HikariCP (shaded)
- Async-only: all queries use `supplyAsync()` / `runAsync()`

### Future: Multi-Datasource Registry (Planned)
To support multiple databases/hosts (e.g., separate analytics DB):
- `SqlRegistry` service for named datasources
- Example: `primary`, `abs`, `analytics`, `local_cache`
- See `implementation_plan.md` section 3 for migration strategy

### Migrations
Plugins register migrations at startup:
```java
core.sql().registerMigration("abs:001_create_flags", migration);
```

**Rules**:
- Must be idempotent: `CREATE TABLE IF NOT EXISTS`
- Never run on main thread
- Fail fast with clear logging
- Plugin should degrade if migration fails

## Code Style & Patterns

### Thread Pool Sizing
From `config.yml`:
- `concurrency.io-threads`: Default 8 (adjust per hardware)
- `concurrency.cpu-threads`: Default 4

### Error Handling with CoreResult
The `CoreResult<T>` pattern is fully implemented for predictable error handling without exceptions:

```java
// Creating results
CoreResult<PlayerData> result = CoreResult.ok(data);
CoreResult<PlayerData> error = CoreResult.err(CoreErrorCode.DB_UNAVAILABLE, "Connection timeout");

// Using results with pattern matching (Java 21)
return switch (result) {
    case CoreResult.Ok(var data) -> processData(data);
    case CoreResult.Err(var error) -> handleError(error);
};

// Functional operations
PlayerData data = result
    .map(d -> enrichData(d))
    .recover(err -> DEFAULT_DATA)
    .orElse(FALLBACK_DATA);

// Side effects
result.ifOk(data -> cache.put(uuid, data));
result.ifErr(error -> logger.warning(error.message()));
```

**Standard error codes** (`CoreErrorCode` enum):
- `DEPENDENCY_MISSING`, `DB_DISABLED`, `DB_UNAVAILABLE`
- `TIMEOUT`, `INVALID_CONFIG`, `NOT_ON_MAIN_THREAD`, `ON_MAIN_THREAD`
- `NOT_FOUND`, `FORBIDDEN`, `INVALID_ARGUMENT`, `INTERNAL_ERROR`, `UNKNOWN`

**Benefits**:
- Zero overhead in hot paths (no exceptions for predictable failures)
- Type-safe error handling with pattern matching
- Clear error codes for debugging

### Cache Strategy
Use Caffeine (shaded) with:
- Bounded max size
- TTL (time-to-live)
- Metrics when useful
- Example: conditions cache, action parse cache

### PlaceholderAPI Integration
**CRITICAL**: PlaceholderAPI methods are NOT thread-safe and must run on main thread.

```java
// ❌ WRONG
CompletableFuture.supplyAsync(() -> PlaceholderAPI.setPlaceholders(...))

// ✅ CORRECT
scheduler.runSync(() -> PlaceholderAPI.setPlaceholders(...))
```

## Migration from Legacy Plugins

Consumer plugins (AfterBlockState, AfterMotion, AfterBlockAnimations) are migrating to AfterCore. Key migrations:

1. **Database**: Remove local HikariCP setup, use `AfterCore.get().sql()`
2. **Conditions**: Remove duplicate condition engines, use `ConditionService`
3. **Actions**: Migrate to `ActionService` with appropriate dialect
4. **Protocol**: Register as `ChunkMutationProvider` instead of direct ProtocolLib listeners

See `implementation_plan.md` section 8 for detailed migration order.

## Performance Considerations

### TPS Budget
Target: 20 TPS constant at 500+ CCU

**Always document TPS impact** for new features:
- Main thread budget per tick
- Frequency of operations
- Async load (doesn't impact TPS but affects latency)

### Common Pitfalls
- Blocking `.get()` on CompletableFuture in main thread
- Unbounded caches (use `maximumSize()` + `expireAfterWrite()`)
- Reflection in hot paths (only use at startup/registration)
- Sync DB queries
- Heavy work in event handlers without async delegation

## Debugging & Diagnostics

### `/acore` Command (Implemented)
Runtime diagnostics command for system inspection:

**Available subcommands:**
- `/acore status` - Dependencies detected (ProtocolLib, PlaceholderAPI), versions, debug mode
- `/acore db` - Database enabled/initialized status, ping latency, pool statistics (active/idle connections)
- `/acore threads` - Thread pool info (io-threads, cpu-threads)
- `/acore system` - System info (JVM version, OS, memory usage with percentages, CPU cores)
- `/acore metrics` - MetricsService snapshot (counters, timers, gauges)
- `/acore all` - All diagnostic information in one view

**Permission**: `aftercore.admin`

**Example output:**
```
=== AfterCore Status ===
Debug: false
Dependencies:
  ✓ ProtocolLib 5.3.0
  ✗ PlaceholderAPI (not installed)

=== Database Info ===
Enabled: true | Initialized: true
Ping: 2.5ms
Pool: 2 active, 8 idle (max: 10)
```

### DiagnosticsService API
Programmatic access for plugins:
```java
DiagnosticsSnapshot snapshot = core.diagnostics().captureSnapshot();
CompletableFuture<Long> ping = core.diagnostics().pingDatabase();
```

## Action Handlers Disponíveis

O AfterCore fornece **12 handlers padrão** prontos para uso:

### Handlers Básicos
- **message** - Mensagens simples no chat
- **actionbar** - Mensagens na action bar (NMS 1.8.8 + fallback)
- **sound** - Sons padrão do Minecraft
- **resource_pack_sound** - Sons customizados de resource pack
- **title** - Títulos com timings configuráveis
- **teleport** - Teleporte (absoluto e relativo ~x ~y ~z)
- **potion** - Efeitos de poção
- **console** - Comandos executados como console
- **player_command** - Comandos executados como player

### Handlers de Mensagens Avançadas
- **centered_message** - Mensagens centralizadas no chat (pixel-perfect)
- **global_message** - Broadcast para todos os jogadores online
- **global_centered_message** - Broadcast centralizado para todos

Todos os handlers suportam:
- ✅ PlaceholderAPI (opcional, graceful degradation)
- ✅ Color codes (&a, &b, etc.)
- ✅ Múltiplas linhas (onde aplicável)
- ✅ Thread safety (main thread quando necessário)

## Utility Libraries

### String Utilities (`com.afterlands.core.util`)
Utilitários para manipulação de strings:

**StringUtil.centeredMessage(String)**
- Centraliza mensagens no chat do Minecraft
- Cálculo pixel-perfect baseado na fonte padrão
- Suporta color codes e formatação

```java
String centered = StringUtil.centeredMessage("&aOlá, Mundo!");
player.sendMessage(centered);
```

### Retry & Backoff (`com.afterlands.core.util.retry`)
Resilient execution with automatic retry logic:

```java
// Preset policies
RetryPolicy dbPolicy = RetryPolicy.defaultDatabasePolicy(); // 3 retries, exponential backoff
RetryPolicy aggressive = RetryPolicy.aggressivePolicy();     // 5 retries, shorter backoff

// Custom policy
RetryPolicy custom = RetryPolicy.builder()
    .maxRetries(3)
    .maxElapsedTime(Duration.ofSeconds(30))
    .backoff(Backoff.exponential(Duration.ofMillis(100), Duration.ofSeconds(10), 0.1))
    .retryOn(SQLException.class)
    .build();

// Execute with retry
RetryExecutor executor = new RetryExecutor(dbPolicy, logger, debug);
PlayerData data = executor.execute(() -> loadFromDatabase(uuid));
CompletableFuture<PlayerData> future = executor.executeAsync(() -> loadFromDatabase(uuid), ioExecutor);
```

**Backoff strategies**:
- `Backoff.exponential(base, max, jitter)` - Exponential with jitter (default: 100ms base, 10s max, 10% jitter)
- `Backoff.fixed(duration)` - Fixed delay
- `Backoff.linear(increment, max)` - Linear growth

### Rate Limiting (`com.afterlands.core.util.ratelimit`)
Token bucket rate limiting for player cooldowns:

```java
// Simple cooldown (1 operation per duration)
RateLimiter limiter = RateLimiter.simpleCooldown(Duration.ofSeconds(5));

// With burst capacity
RateLimiter limiter = RateLimiter.withBurst(
    2,                              // 2 tokens per second
    5,                              // max 5 tokens (burst)
    Duration.ofMinutes(5)           // expire after 5min inactive
);

// CooldownService for player-specific cooldowns
CooldownService cooldowns = new CooldownService();
if (cooldowns.tryAcquire(player, "teleport", Duration.ofSeconds(10))) {
    // Execute action
} else {
    Optional<Duration> remaining = cooldowns.tryAcquireWithRemaining(player, "teleport", Duration.ofSeconds(10));
    player.sendMessage("Wait " + CooldownService.formatDuration(remaining.get()));
}
```

### Metrics Collection (`com.afterlands.core.metrics`)
Lightweight metrics with minimal overhead:

```java
MetricsService metrics = core.metrics();

// Increment counters
metrics.increment("database.queries");
metrics.increment("cache.hits", 5);

// Record timing
metrics.recordTime("database.query.player", elapsedNanos);

// Convenient timer wrapper
PlayerData data = metrics.time("database.query.player", () -> loadPlayer(uuid));

// Set gauge values
metrics.gauge("players.online", Bukkit.getOnlinePlayers().size());

// Get snapshot
MetricsSnapshot snapshot = metrics.snapshot();
String formatted = snapshot.format(); // Human-readable output
```

## Important Files

- `pom.xml` - Maven build configuration, dependencies, shading rules
- `src/main/resources/plugin.yml` - Bukkit plugin metadata, soft dependencies
- `src/main/resources/config.yml` - Database, concurrency, debug settings (includes `config-version`)
- `implementation_plan.md` - Detailed implementation roadmap and migration strategy
- `afterlands-architect.md` - System architect role definition and design patterns
- `CLAUDE.md` - This file - guidance for Claude Code instances

## Configuration

### Database Configuration
```yaml
database:
  enabled: true
  type: mysql  # or sqlite
  mysql:
    host: localhost
    port: 3306
    database: afterlands
    username: root
    password: ""
  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout: 30000
    idle-timeout: 600000
    max-lifetime: 1800000
```

### Concurrency Configuration
```yaml
concurrency:
  io-threads: 8    # For DB/I/O operations
  cpu-threads: 4   # For CPU-bound tasks
```

### Debug Mode
```yaml
debug: false  # Enable verbose logging
```

## Language & Documentation

- Code: Java 21 features encouraged (records, switch expressions, pattern matching)
- Comments: Portuguese (BR) or English accepted
- Specs/Plans: Portuguese (BR) as per project standard
- Public API: English Javadoc recommended for broader compatibility
