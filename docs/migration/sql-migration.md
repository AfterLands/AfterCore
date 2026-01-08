# SqlService Migration Guide

## Overview

This guide helps you migrate from local HikariCP implementations to the centralized `SqlService` provided by AfterCore.

## Benefits of Migration

- **Zero Duplication**: No more copy-pasted DataSource/Pool code
- **Unified Configuration**: Single `database` section in AfterCore's config
- **Built-in Health Checks**: Diagnostics via `/acore db`
- **Consistent Threading**: All queries run on AfterCore's `ioExecutor`
- **Migration Support**: Register DDL/migrations that run automatically
- **Graceful Degradation**: Plugins can detect when DB is disabled

---

## Migration Steps

### Step 1: Remove Local HikariCP Dependencies

**Before (plugin's pom.xml):**
```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
    <scope>compile</scope>
</dependency>
```

**After:**
```xml
<dependency>
    <groupId>com.afterlands</groupId>
    <artifactId>AfterCore</artifactId>
    <version>0.1.0</version>
    <scope>provided</scope>
</dependency>
```

Add to your `plugin.yml`:
```yaml
depend: [AfterCore]
```

---

### Step 2: Replace DataSource Initialization

**Before (typical local implementation):**
```java
public class AfterBlockState extends JavaPlugin {
    private HikariDataSource dataSource;

    @Override
    public void onEnable() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/afterlands");
        config.setUsername("root");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);

        this.dataSource = new HikariDataSource(config);

        // Run migrations
        createTablesIfNotExist();
    }

    @Override
    public void onDisable() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void createTablesIfNotExist() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS abs_player_flags (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "flags TEXT)");
        } catch (SQLException e) {
            getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }
}
```

**After (using AfterCore):**
```java
public class AfterBlockState extends JavaPlugin {
    private AfterCoreAPI core;

    @Override
    public void onEnable() {
        // Get AfterCore API
        this.core = AfterCore.get();

        // Check if SQL is enabled
        if (!core.sql().isEnabled()) {
            getLogger().warning("Database is disabled - running in degraded mode");
            return;
        }

        // Register migration (runs automatically after pool init)
        core.sql().registerMigration("abs:001_create_flags_table", conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS abs_player_flags (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "flags TEXT)");
                getLogger().info("Migration abs:001_create_flags_table completed");
            }
        });

        // Continue plugin initialization...
    }

    // No need to close - AfterCore manages lifecycle
}
```

---

### Step 3: Migrate Database Queries

**Before (blocking or manual async):**
```java
public class PlayerFlagRepository {
    private final HikariDataSource dataSource;

    public PlayerFlags loadFlags(UUID uuid) {
        // DANGER: This blocks the calling thread!
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT flags FROM abs_player_flags WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return parseFlags(rs.getString("flags"));
            }
            return new PlayerFlags();
        } catch (SQLException e) {
            logger.severe("Failed to load flags: " + e.getMessage());
            return new PlayerFlags();
        }
    }

    // Or manual async with BukkitScheduler:
    public void loadFlagsAsync(UUID uuid, Consumer<PlayerFlags> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerFlags flags = loadFlags(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(flags));
        });
    }
}
```

**After (using SqlService async helpers):**
```java
public class PlayerFlagRepository {
    private final SqlService sql;

    public PlayerFlagRepository(AfterCoreAPI core) {
        this.sql = core.sql();
    }

    /**
     * Load player flags asynchronously.
     *
     * @return CompletableFuture that completes on the IO executor
     */
    public CompletableFuture<PlayerFlags> loadFlags(UUID uuid) {
        return sql.supplyAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT flags FROM abs_player_flags WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return parseFlags(rs.getString("flags"));
                }
                return new PlayerFlags();
            }
        });
    }

    /**
     * Save player flags asynchronously.
     */
    public CompletableFuture<Void> saveFlags(UUID uuid, PlayerFlags flags) {
        return sql.runAsync(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO abs_player_flags (uuid, flags) VALUES (?, ?) " +
                    "ON DUPLICATE KEY UPDATE flags = ?")) {
                String serialized = serializeFlags(flags);
                ps.setString(1, uuid.toString());
                ps.setString(2, serialized);
                ps.setString(3, serialized);
                ps.executeUpdate();
            }
        });
    }

    private PlayerFlags parseFlags(String json) {
        // Your parsing logic
        return new PlayerFlags();
    }

    private String serializeFlags(PlayerFlags flags) {
        // Your serialization logic
        return "{}";
    }
}
```

---

### Step 4: Handle Async in Event Listeners

**Before (blocking on main thread - BAD!):**
```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();

    // DANGER: Blocks main thread!
    PlayerFlags flags = repository.loadFlags(uuid);
    applyFlags(event.getPlayer(), flags);
}
```

**After (async with cache):**
```java
public class PlayerFlagManager {
    private final PlayerFlagRepository repository;
    private final LoadingCache<UUID, PlayerFlags> cache;

    public PlayerFlagManager(AfterCoreAPI core) {
        this.repository = new PlayerFlagRepository(core);
        this.cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(uuid -> repository.loadFlags(uuid).join()); // For cache loader
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Check cache first (O(1), non-blocking)
        PlayerFlags cached = cache.getIfPresent(uuid);
        if (cached != null) {
            applyFlags(event.getPlayer(), cached);
            return;
        }

        // Cache miss: load async
        repository.loadFlags(uuid).thenAccept(flags -> {
            cache.put(uuid, flags);

            // Apply on main thread (player might have logged out)
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                applyFlags(player, flags);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerFlags flags = cache.getIfPresent(uuid);

        if (flags != null && flags.isDirty()) {
            // Save async on quit
            repository.saveFlags(uuid, flags).exceptionally(ex -> {
                plugin.getLogger().severe("Failed to save flags for " + uuid + ": " + ex.getMessage());
                return null;
            });
        }

        cache.invalidate(uuid);
    }
}
```

---

## Migration Naming Conventions

### Multi-Database Strategy (Recommended for Large Plugins)

If your plugin is large enough to justify a separate database/schema:

**AfterCore config.yml (future with SqlRegistry):**
```yaml
database:
  datasources:
    primary:
      enabled: true
      type: mysql
      mysql:
        host: localhost
        port: 3306
        database: afterlands        # Core/shared data
        username: root
        password: ""
      pool:
        maximum-pool-size: 10
        minimum-idle: 2

    abs:
      enabled: true
      type: mysql
      mysql:
        host: localhost
        port: 3306
        database: afterlands_abs    # AfterBlockState data
        username: root
        password: ""
      pool:
        maximum-pool-size: 6
        minimum-idle: 2
```

**Plugin code:**
```java
// When SqlRegistry is available (future):
SqlService absSql = core.sqlRegistry().get("abs");
```

### Single Database + Prefix Strategy (Recommended for Small Plugins)

For smaller plugins sharing the primary database:

**Table naming:**
- `abs_player_flags`
- `abs_state_targets`
- `motion_scenes`
- `motion_playback`
- `aba_placements`

**Migration IDs:**
- `abs:001_create_flags_table`
- `abs:002_add_index_on_uuid`
- `motion:001_create_scenes_table`

---

## Common Patterns

### Pattern 1: Load-on-Join, Save-on-Quit

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onJoin(PlayerJoinEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();

    repository.loadData(uuid).thenAccept(data -> {
        cache.put(uuid, data);
        applyData(Bukkit.getPlayer(uuid), data);
    });
}

@EventHandler
public void onQuit(PlayerQuitEvent event) {
    UUID uuid = event.getPlayer().getUniqueId();
    Data data = cache.getIfPresent(uuid);

    if (data != null && data.isDirty()) {
        repository.saveData(uuid, data);
    }

    cache.invalidate(uuid);
}
```

### Pattern 2: Periodic Auto-Save

```java
@Override
public void onEnable() {
    // Auto-save every 5 minutes
    Bukkit.getScheduler().runTaskTimerAsynchronously(this,
        this::saveAllDirtyData,
        20L * 60,      // 1 min delay
        20L * 60 * 5   // Every 5 min
    );
}

private void saveAllDirtyData() {
    cache.asMap().forEach((uuid, data) -> {
        if (data.isDirty()) {
            repository.saveData(uuid, data).exceptionally(ex -> {
                getLogger().severe("Auto-save failed for " + uuid);
                return null;
            });
        }
    });
}
```

### Pattern 3: Graceful Degradation (DB Disabled)

```java
@Override
public void onEnable() {
    if (!core.sql().isEnabled()) {
        getLogger().warning("Database disabled - features will be limited");
        this.repository = new InMemoryRepository(); // Fallback
        return;
    }

    this.repository = new SqlRepository(core.sql());
}
```

---

## Troubleshooting

### "Database is not enabled"

**Cause:** `database.enabled: false` in AfterCore's config.yml

**Solution:** Enable database in AfterCore config, or implement graceful degradation.

### "Failed to get connection from DataSource"

**Cause:** Pool exhausted or DB unreachable

**Solutions:**
1. Check `/acore db` for pool stats
2. Increase `maximum-pool-size` in AfterCore config
3. Check if queries are leaking connections (missing try-with-resources)
4. Verify DB is running and accessible

### "Migration failed"

**Cause:** SQL syntax error or permission issue

**Solutions:**
1. Check AfterCore logs for detailed error
2. Test migration SQL manually in your DB client
3. Ensure migration is idempotent (uses `IF NOT EXISTS`)
4. Check DB user has CREATE/ALTER permissions

### Connection Leaks

**Wrong:**
```java
Connection conn = sql.getConnection();
PreparedStatement ps = conn.prepareStatement("...");
// LEAK: connection never closed!
```

**Correct:**
```java
try (Connection conn = sql.getConnection();
     PreparedStatement ps = conn.prepareStatement("...")) {
    // Auto-closed
}

// Or use helpers:
sql.runAsync(conn -> {
    // Connection managed by AfterCore
});
```

---

## Advanced: Transactions

Currently, AfterCore's `SqlService` doesn't have a transaction helper. For now, manage manually:

```java
public CompletableFuture<Void> transferFunds(UUID from, UUID to, int amount) {
    return core.sql().runAsync(conn -> {
        conn.setAutoCommit(false);
        try {
            // Deduct from source
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE player_balance SET balance = balance - ? WHERE uuid = ?")) {
                ps.setInt(1, amount);
                ps.setString(2, from.toString());
                ps.executeUpdate();
            }

            // Add to target
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE player_balance SET balance = balance + ? WHERE uuid = ?")) {
                ps.setInt(1, amount);
                ps.setString(2, to.toString());
                ps.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    });
}
```

**Planned enhancement (future):**
```java
core.sql().inTransaction(conn -> {
    // Multiple statements here - auto commit/rollback
});
```

---

## Checklist

- [ ] Removed local HikariCP dependency from pom.xml
- [ ] Added AfterCore dependency and `depend: [AfterCore]` in plugin.yml
- [ ] Replaced DataSource initialization with `AfterCore.get().sql()`
- [ ] Registered all migrations using namespaced IDs (e.g., `abs:001_...`)
- [ ] Migrated all queries to async using `supplyAsync()` / `runAsync()`
- [ ] Implemented caching to avoid DB hits on every event
- [ ] No blocking DB calls in event handlers
- [ ] Implemented graceful degradation when DB is disabled
- [ ] Tested with AfterCore's `/acore db` command
- [ ] Verified no connection leaks (all use try-with-resources or helpers)

---

## See Also

- [ConditionService Migration Guide](condition-migration.md)
- [ActionService Migration Guide](action-migration.md)
- [ProtocolService Migration Guide](protocol-migration.md)
