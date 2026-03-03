package com.afterlands.core.redis.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.redis.*;
import com.afterlands.core.redis.lock.RedisLock;
import com.afterlands.core.redis.lock.impl.RedisLockManager;
import com.afterlands.core.redis.pubsub.NoOpRedisPubSub;
import com.afterlands.core.redis.pubsub.RedisPubSub;
import com.afterlands.core.redis.pubsub.impl.JedisPubSubManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Datasource Redis individual baseado em Jedis.
 *
 * <p>
 * Representa um único pool de conexões Redis com suporte a
 * Standalone, Sentinel e Cluster. Espelha o padrão do
 * {@link com.afterlands.core.database.impl.HikariSqlDataSource}.
 * </p>
 *
 * @since 1.8.0
 */
public final class JedisRedisDataSource implements RedisDataSource {

    private final String name;
    private final Plugin plugin;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final boolean debug;
    private final RedisTopology topology;

    private volatile JedisPool jedisPool;            // STANDALONE
    private volatile JedisSentinelPool sentinelPool;  // SENTINEL
    private volatile JedisCluster jedisCluster;       // CLUSTER
    private volatile JedisPubSubManager pubsubManager;
    private volatile RedisLockManager lockManager;
    private volatile boolean enabled;

    public JedisRedisDataSource(
            @NotNull String name,
            @NotNull Plugin plugin,
            @NotNull SchedulerService scheduler,
            @NotNull ConfigurationSection dsConfig,
            @Nullable ConfigurationSection poolDefaults,
            boolean debug) {
        this.name = Objects.requireNonNull(name, "name");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.debug = debug;

        String topologyStr = dsConfig.getString("topology", "standalone");
        this.topology = RedisTopology.fromString(topologyStr);

        initialize(dsConfig, poolDefaults);
    }

    private void initialize(@NotNull ConfigurationSection dsConfig,
                            @Nullable ConfigurationSection poolDefaults) {
        try {
            JedisPoolConfig poolConfig = buildPoolConfig(poolDefaults, dsConfig.getConfigurationSection("pool"));
            int timeout = resolveInt(poolDefaults, dsConfig.getConfigurationSection("pool"),
                    "timeout-ms", 3000);

            switch (topology) {
                case STANDALONE -> initStandalone(dsConfig, poolConfig, timeout);
                case SENTINEL -> initSentinel(dsConfig, poolConfig, timeout);
                case CLUSTER -> initCluster(dsConfig, poolConfig, timeout);
            }

            // Pub/Sub
            ConfigurationSection pubsubConfig = dsConfig.getConfigurationSection("pubsub");
            boolean pubsubEnabled = pubsubConfig != null && pubsubConfig.getBoolean("enabled", true);
            if (pubsubEnabled && topology != RedisTopology.CLUSTER) {
                this.pubsubManager = new JedisPubSubManager(name, logger, scheduler, this::getResource, debug);
                this.pubsubManager.start();
            }

            // Locks
            ConfigurationSection lockConfig = dsConfig.getConfigurationSection("lock");
            long lockTtl = lockConfig != null ? lockConfig.getLong("default-ttl-ms", 30000) : 30000;
            long lockRetry = lockConfig != null ? lockConfig.getLong("retry-delay-ms", 100) : 100;
            int lockMaxRetries = lockConfig != null ? lockConfig.getInt("max-retries", 3) : 3;
            this.lockManager = new RedisLockManager(
                    logger, scheduler, topology, this::getResource, jedisCluster,
                    debug, lockTtl, lockRetry, lockMaxRetries);

            enabled = true;
            logger.info("Redis datasource '" + name + "' (" + topology + ") inicializado.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Falha ao inicializar Redis datasource '" + name + "'", e);
            enabled = false;
            throw new RuntimeException("Falha ao inicializar Redis datasource '" + name + "'", e);
        }
    }

    private void initStandalone(@NotNull ConfigurationSection dsConfig,
                                @NotNull JedisPoolConfig poolConfig, int timeout) {
        ConfigurationSection cfg = dsConfig.getConfigurationSection("standalone");
        if (cfg == null) cfg = dsConfig; // fallback to root

        String host = cfg.getString("host", "localhost");
        int port = cfg.getInt("port", 6379);
        String password = cfg.getString("password", "");
        int database = cfg.getInt("database", 0);
        boolean ssl = cfg.getBoolean("ssl", false);

        if (password != null && password.isEmpty()) password = null;

        this.jedisPool = new JedisPool(poolConfig, host, port, timeout, password, database, ssl);
    }

    private void initSentinel(@NotNull ConfigurationSection dsConfig,
                              @NotNull JedisPoolConfig poolConfig, int timeout) {
        ConfigurationSection cfg = dsConfig.getConfigurationSection("sentinel");
        if (cfg == null) {
            throw new IllegalArgumentException("Redis datasource '" + name + "': sentinel config missing");
        }

        String masterName = cfg.getString("master-name", "mymaster");
        List<String> nodeList = cfg.getStringList("nodes");
        String password = cfg.getString("password", "");
        int database = cfg.getInt("database", 0);

        if (password != null && password.isEmpty()) password = null;

        Set<String> sentinels = new LinkedHashSet<>(nodeList);
        this.sentinelPool = new JedisSentinelPool(masterName, sentinels, poolConfig, timeout, password, database);
    }

    private void initCluster(@NotNull ConfigurationSection dsConfig,
                             @NotNull JedisPoolConfig poolConfig, int timeout) {
        ConfigurationSection cfg = dsConfig.getConfigurationSection("cluster");
        if (cfg == null) {
            throw new IllegalArgumentException("Redis datasource '" + name + "': cluster config missing");
        }

        List<String> nodeList = cfg.getStringList("nodes");
        String password = cfg.getString("password", "");
        int maxAttempts = cfg.getInt("max-attempts", 5);

        if (password != null && password.isEmpty()) password = null;

        Set<HostAndPort> clusterNodes = new LinkedHashSet<>();
        for (String node : nodeList) {
            String[] parts = node.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
            clusterNodes.add(new HostAndPort(host, port));
        }

        GenericObjectPoolConfig<Connection> genericConfig = new GenericObjectPoolConfig<>();
        genericConfig.setMaxTotal(poolConfig.getMaxTotal());
        genericConfig.setMaxIdle(poolConfig.getMaxIdle());
        genericConfig.setMinIdle(poolConfig.getMinIdle());
        genericConfig.setTestOnBorrow(poolConfig.getTestOnBorrow());
        this.jedisCluster = new JedisCluster(clusterNodes, timeout, timeout, maxAttempts, password, genericConfig);
    }

    private JedisPoolConfig buildPoolConfig(@Nullable ConfigurationSection defaults,
                                            @Nullable ConfigurationSection overrides) {
        JedisPoolConfig config = new JedisPoolConfig();

        int maxTotal = resolveInt(defaults, overrides, "max-total", 16);
        int maxIdle = resolveInt(defaults, overrides, "max-idle", 8);
        int minIdle = resolveInt(defaults, overrides, "min-idle", 2);
        boolean testOnBorrow = resolveBoolean(defaults, overrides, "test-on-borrow", true);

        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setTestOnBorrow(testOnBorrow);

        if (debug) {
            logger.info("[Redis] Pool config for '" + name + "': maxTotal=" + maxTotal +
                    ", maxIdle=" + maxIdle + ", minIdle=" + minIdle);
        }

        return config;
    }

    private int resolveInt(@Nullable ConfigurationSection defaults,
                           @Nullable ConfigurationSection overrides,
                           @NotNull String key, int fallback) {
        int value = defaults != null ? defaults.getInt(key, fallback) : fallback;
        return overrides != null ? overrides.getInt(key, value) : value;
    }

    private boolean resolveBoolean(@Nullable ConfigurationSection defaults,
                                   @Nullable ConfigurationSection overrides,
                                   @NotNull String key, boolean fallback) {
        boolean value = defaults != null ? defaults.getBoolean(key, fallback) : fallback;
        return overrides != null ? overrides.getBoolean(key, value) : value;
    }

    /**
     * Obtém uma conexão Jedis do pool (Standalone ou Sentinel).
     */
    private Jedis getResource() {
        if (jedisPool != null) return jedisPool.getResource();
        if (sentinelPool != null) return sentinelPool.getResource();
        throw new IllegalStateException("No Jedis pool available for datasource '" + name + "'");
    }

    // ==================== Metadata ====================

    @Override
    public @NotNull String name() {
        return name;
    }

    @Override
    public @NotNull RedisTopology topology() {
        return topology;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isInitialized() {
        return enabled && (jedisPool != null || sentinelPool != null || jedisCluster != null);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> isAvailable() {
        if (!isInitialized()) return CompletableFuture.completedFuture(false);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String result = doPing();
                return "PONG".equals(result);
            } catch (Exception e) {
                if (debug) {
                    logger.log(Level.WARNING, "Redis ping failed for '" + name + "'", e);
                }
                return false;
            }
        }, scheduler.ioExecutor());
    }

    private String doPing() {
        return switch (topology) {
            case STANDALONE, SENTINEL -> {
                try (Jedis jedis = getResource()) {
                    yield jedis.ping();
                }
            }
            case CLUSTER -> {
                // Ping one node via cluster connection
                Map<String, ConnectionPool> nodes = jedisCluster.getClusterNodes();
                if (nodes.isEmpty()) yield "ERROR";
                ConnectionPool firstPool = nodes.values().iterator().next();
                try (Connection conn = firstPool.getResource()) {
                    Jedis jedis = new Jedis(conn);
                    yield jedis.ping();
                }
            }
        };
    }

    @Override
    public @NotNull Map<String, Object> getPoolStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("datasource", name);
        stats.put("topology", topology.name());

        try {
            switch (topology) {
                case STANDALONE -> {
                    if (jedisPool != null) {
                        stats.put("active", jedisPool.getNumActive());
                        stats.put("idle", jedisPool.getNumIdle());
                        stats.put("waiters", jedisPool.getNumWaiters());
                        stats.put("max_total", jedisPool.getMaxTotal());
                    }
                }
                case SENTINEL -> {
                    if (sentinelPool != null) {
                        stats.put("active", sentinelPool.getNumActive());
                        stats.put("idle", sentinelPool.getNumIdle());
                        stats.put("waiters", sentinelPool.getNumWaiters());
                    }
                }
                case CLUSTER -> {
                    if (jedisCluster != null) {
                        stats.put("cluster_nodes", jedisCluster.getClusterNodes().size());
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get Redis pool stats for '" + name + "'", e);
        }
        return stats;
    }

    @Override
    public void close() {
        enabled = false;

        // 1. Pub/Sub first
        JedisPubSubManager psm = pubsubManager;
        if (psm != null) {
            try { psm.shutdown(); } catch (Throwable ignored) {}
            pubsubManager = null;
        }

        // 2. Pools
        JedisPool jp = jedisPool;
        if (jp != null) {
            try { jp.close(); } catch (Throwable ignored) {}
            jedisPool = null;
        }

        JedisSentinelPool sp = sentinelPool;
        if (sp != null) {
            try { sp.close(); } catch (Throwable ignored) {}
            sentinelPool = null;
        }

        JedisCluster jc = jedisCluster;
        if (jc != null) {
            try { jc.close(); } catch (Throwable ignored) {}
            jedisCluster = null;
        }

        lockManager = null;

        logger.info("Redis datasource '" + name + "' closed.");
    }

    // ==================== Raw Access ====================

    @Override
    public <T> CompletableFuture<T> execute(@NotNull RedisFunction<Jedis, T> fn) {
        Objects.requireNonNull(fn, "fn");
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        if (topology == RedisTopology.CLUSTER) {
            return failedFuture("execute(Jedis) not supported in CLUSTER mode — use typed methods");
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = getResource()) {
                return fn.apply(jedis);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Void> run(@NotNull RedisConsumer<Jedis> fn) {
        Objects.requireNonNull(fn, "fn");
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        if (topology == RedisTopology.CLUSTER) {
            return failedFuture("run(Jedis) not supported in CLUSTER mode — use typed methods");
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = getResource()) {
                fn.accept(jedis);
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, scheduler.ioExecutor());
    }

    @Override
    public <T> CompletableFuture<List<T>> pipeline(@NotNull RedisFunction<Pipeline, List<T>> fn) {
        Objects.requireNonNull(fn, "fn");
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        if (topology == RedisTopology.CLUSTER) {
            return failedFuture("pipeline() not supported in CLUSTER mode");
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = getResource()) {
                Pipeline pipeline = jedis.pipelined();
                List<T> result = fn.apply(pipeline);
                pipeline.sync();
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, scheduler.ioExecutor());
    }

    // ==================== String Operations ====================

    @Override
    public CompletableFuture<@Nullable String> get(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doGet(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Void> set(@NotNull String key, @NotNull String value) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> { doSet(key, value); return null; }, scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Void> setex(@NotNull String key, long seconds, @NotNull String value) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> { doSetex(key, seconds, value); return null; }, scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Boolean> setnx(@NotNull String key, @NotNull String value) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doSetnx(key, value), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> incr(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doIncr(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> incrBy(@NotNull String key, long amount) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doIncrBy(key, amount), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> decr(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doDecr(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Boolean> exists(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doExists(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> del(@NotNull String... keys) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doDel(keys), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Boolean> expire(@NotNull String key, long seconds) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doExpire(key, seconds), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> ttl(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doTtl(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<List<String>> mget(@NotNull String... keys) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doMget(keys), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Void> mset(@NotNull String... keysValues) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> { doMset(keysValues); return null; }, scheduler.ioExecutor());
    }

    // ==================== Hash Operations ====================

    @Override
    public CompletableFuture<@Nullable String> hget(@NotNull String key, @NotNull String field) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doHget(key, field), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Void> hset(@NotNull String key, @NotNull String field, @NotNull String value) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> { doHset(key, field, value); return null; }, scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Void> hmset(@NotNull String key, @NotNull Map<String, String> hash) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> { doHmset(key, hash); return null; }, scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Map<String, String>> hgetAll(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doHgetAll(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> hdel(@NotNull String key, @NotNull String... fields) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doHdel(key, fields), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Boolean> hexists(@NotNull String key, @NotNull String field) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doHexists(key, field), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> hincrBy(@NotNull String key, @NotNull String field, long amount) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doHincrBy(key, field, amount), scheduler.ioExecutor());
    }

    // ==================== List Operations ====================

    @Override
    public CompletableFuture<Long> lpush(@NotNull String key, @NotNull String... values) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doLpush(key, values), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> rpush(@NotNull String key, @NotNull String... values) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doRpush(key, values), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<@Nullable String> lpop(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doLpop(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<@Nullable String> rpop(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doRpop(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<List<String>> lrange(@NotNull String key, long start, long stop) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doLrange(key, start, stop), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> llen(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doLlen(key), scheduler.ioExecutor());
    }

    // ==================== Set Operations ====================

    @Override
    public CompletableFuture<Long> sadd(@NotNull String key, @NotNull String... members) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doSadd(key, members), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> srem(@NotNull String key, @NotNull String... members) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doSrem(key, members), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Set<String>> smembers(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doSmembers(key), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Boolean> sismember(@NotNull String key, @NotNull String member) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doSismember(key, member), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> scard(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doScard(key), scheduler.ioExecutor());
    }

    // ==================== Sorted Set Operations ====================

    @Override
    public CompletableFuture<Long> zadd(@NotNull String key, double score, @NotNull String member) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doZadd(key, score, member), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> zrem(@NotNull String key, @NotNull String... members) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doZrem(key, members), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<List<String>> zrange(@NotNull String key, long start, long stop) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doZrange(key, start, stop), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<List<String>> zrevrange(@NotNull String key, long start, long stop) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doZrevrange(key, start, stop), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<@Nullable Double> zscore(@NotNull String key, @NotNull String member) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doZscore(key, member), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<@Nullable Long> zrank(@NotNull String key, @NotNull String member) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doZrank(key, member), scheduler.ioExecutor());
    }

    @Override
    public CompletableFuture<Long> zcard(@NotNull String key) {
        if (!isInitialized()) return failedFuture("Datasource '" + name + "' not initialized");
        return CompletableFuture.supplyAsync(() -> doZcard(key), scheduler.ioExecutor());
    }

    // ==================== Pub/Sub ====================

    @Override
    public @NotNull RedisPubSub pubsub() {
        JedisPubSubManager psm = pubsubManager;
        return psm != null ? psm : new NoOpRedisPubSub();
    }

    // ==================== Distributed Locks ====================

    @Override
    public CompletableFuture<Optional<RedisLock>> tryLock(@NotNull String key, long ttlMs) {
        RedisLockManager lm = lockManager;
        if (lm == null) return CompletableFuture.completedFuture(Optional.empty());
        return lm.tryLock(key, ttlMs);
    }

    @Override
    public CompletableFuture<Boolean> unlock(@NotNull RedisLock lock) {
        RedisLockManager lm = lockManager;
        if (lm == null) return CompletableFuture.completedFuture(false);
        return lm.unlock(lock);
    }

    // ==================== Internal do* methods (topology-aware) ====================

    private String doGet(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.get(key); } }
            case CLUSTER -> jedisCluster.get(key);
        };
    }

    private void doSet(String key, String value) {
        switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { j.set(key, value); } }
            case CLUSTER -> jedisCluster.set(key, value);
        }
    }

    private void doSetex(String key, long seconds, String value) {
        switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { j.setex(key, seconds, value); } }
            case CLUSTER -> jedisCluster.setex(key, seconds, value);
        }
    }

    private boolean doSetnx(String key, String value) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.setnx(key, value) == 1L; } }
            case CLUSTER -> jedisCluster.setnx(key, value) == 1L;
        };
    }

    private long doIncr(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.incr(key); } }
            case CLUSTER -> jedisCluster.incr(key);
        };
    }

    private long doIncrBy(String key, long amount) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.incrBy(key, amount); } }
            case CLUSTER -> jedisCluster.incrBy(key, amount);
        };
    }

    private long doDecr(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.decr(key); } }
            case CLUSTER -> jedisCluster.decr(key);
        };
    }

    private boolean doExists(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.exists(key); } }
            case CLUSTER -> jedisCluster.exists(key);
        };
    }

    private long doDel(String... keys) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.del(keys); } }
            case CLUSTER -> jedisCluster.del(keys);
        };
    }

    private boolean doExpire(String key, long seconds) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.expire(key, seconds) == 1L; } }
            case CLUSTER -> jedisCluster.expire(key, seconds) == 1L;
        };
    }

    private long doTtl(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.ttl(key); } }
            case CLUSTER -> jedisCluster.ttl(key);
        };
    }

    private List<String> doMget(String... keys) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.mget(keys); } }
            case CLUSTER -> jedisCluster.mget(keys);
        };
    }

    private void doMset(String... keysValues) {
        switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { j.mset(keysValues); } }
            case CLUSTER -> jedisCluster.mset(keysValues);
        }
    }

    // Hash
    private String doHget(String key, String field) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.hget(key, field); } }
            case CLUSTER -> jedisCluster.hget(key, field);
        };
    }

    private void doHset(String key, String field, String value) {
        switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { j.hset(key, field, value); } }
            case CLUSTER -> jedisCluster.hset(key, field, value);
        }
    }

    private void doHmset(String key, Map<String, String> hash) {
        switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { j.hmset(key, hash); } }
            case CLUSTER -> jedisCluster.hmset(key, hash);
        }
    }

    private Map<String, String> doHgetAll(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.hgetAll(key); } }
            case CLUSTER -> jedisCluster.hgetAll(key);
        };
    }

    private long doHdel(String key, String... fields) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.hdel(key, fields); } }
            case CLUSTER -> jedisCluster.hdel(key, fields);
        };
    }

    private boolean doHexists(String key, String field) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.hexists(key, field); } }
            case CLUSTER -> jedisCluster.hexists(key, field);
        };
    }

    private long doHincrBy(String key, String field, long amount) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.hincrBy(key, field, amount); } }
            case CLUSTER -> jedisCluster.hincrBy(key, field, amount);
        };
    }

    // List
    private long doLpush(String key, String... values) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.lpush(key, values); } }
            case CLUSTER -> jedisCluster.lpush(key, values);
        };
    }

    private long doRpush(String key, String... values) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.rpush(key, values); } }
            case CLUSTER -> jedisCluster.rpush(key, values);
        };
    }

    private String doLpop(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.lpop(key); } }
            case CLUSTER -> jedisCluster.lpop(key);
        };
    }

    private String doRpop(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.rpop(key); } }
            case CLUSTER -> jedisCluster.rpop(key);
        };
    }

    private List<String> doLrange(String key, long start, long stop) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.lrange(key, start, stop); } }
            case CLUSTER -> jedisCluster.lrange(key, start, stop);
        };
    }

    private long doLlen(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.llen(key); } }
            case CLUSTER -> jedisCluster.llen(key);
        };
    }

    // Set
    private long doSadd(String key, String... members) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.sadd(key, members); } }
            case CLUSTER -> jedisCluster.sadd(key, members);
        };
    }

    private long doSrem(String key, String... members) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.srem(key, members); } }
            case CLUSTER -> jedisCluster.srem(key, members);
        };
    }

    private Set<String> doSmembers(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.smembers(key); } }
            case CLUSTER -> jedisCluster.smembers(key);
        };
    }

    private boolean doSismember(String key, String member) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.sismember(key, member); } }
            case CLUSTER -> jedisCluster.sismember(key, member);
        };
    }

    private long doScard(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.scard(key); } }
            case CLUSTER -> jedisCluster.scard(key);
        };
    }

    // Sorted Set
    private long doZadd(String key, double score, String member) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.zadd(key, score, member); } }
            case CLUSTER -> jedisCluster.zadd(key, score, member);
        };
    }

    private long doZrem(String key, String... members) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.zrem(key, members); } }
            case CLUSTER -> jedisCluster.zrem(key, members);
        };
    }

    private List<String> doZrange(String key, long start, long stop) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> {
                try (Jedis j = getResource()) { yield new ArrayList<>(j.zrange(key, start, stop)); }
            }
            case CLUSTER -> new ArrayList<>(jedisCluster.zrange(key, start, stop));
        };
    }

    private List<String> doZrevrange(String key, long start, long stop) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> {
                try (Jedis j = getResource()) { yield new ArrayList<>(j.zrevrange(key, start, stop)); }
            }
            case CLUSTER -> new ArrayList<>(jedisCluster.zrevrange(key, start, stop));
        };
    }

    private Double doZscore(String key, String member) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.zscore(key, member); } }
            case CLUSTER -> jedisCluster.zscore(key, member);
        };
    }

    private Long doZrank(String key, String member) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.zrank(key, member); } }
            case CLUSTER -> jedisCluster.zrank(key, member);
        };
    }

    private long doZcard(String key) {
        return switch (topology) {
            case STANDALONE, SENTINEL -> { try (Jedis j = getResource()) { yield j.zcard(key); } }
            case CLUSTER -> jedisCluster.zcard(key);
        };
    }

    // ==================== Utility ====================

    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<T> failedFuture(String message) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        cf.completeExceptionally(new IllegalStateException(message));
        return cf;
    }
}
