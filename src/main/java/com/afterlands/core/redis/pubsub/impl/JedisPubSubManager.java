package com.afterlands.core.redis.pubsub.impl;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.redis.pubsub.RedisMessage;
import com.afterlands.core.redis.pubsub.RedisMessageListener;
import com.afterlands.core.redis.pubsub.RedisPubSub;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador de Pub/Sub com threads dedicadas para subscriber.
 *
 * <p>
 * Jedis {@code subscribe()} é bloqueante — usa daemon thread própria
 * (NÃO do ioExecutor, para não starvar I/O do DB).
 * </p>
 *
 * @since 1.8.0
 */
public final class JedisPubSubManager implements RedisPubSub {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);
    private static final long RECONNECT_DELAY_MS = 5000;

    private final String datasourceName;
    private final Logger logger;
    private final SchedulerService scheduler;
    private final Supplier<Jedis> jedisSupplier;
    private final boolean debug;

    private final Map<String, Set<RedisMessageListener>> channelListeners = new ConcurrentHashMap<>();
    private final Map<String, Set<RedisMessageListener>> patternListeners = new ConcurrentHashMap<>();

    private volatile InternalBridge bridge;
    private volatile Thread subscriberThread;
    private volatile boolean active = false;

    public JedisPubSubManager(
            @NotNull String datasourceName,
            @NotNull Logger logger,
            @NotNull SchedulerService scheduler,
            @NotNull Supplier<Jedis> jedisSupplier,
            boolean debug) {
        this.datasourceName = datasourceName;
        this.logger = logger;
        this.scheduler = scheduler;
        this.jedisSupplier = jedisSupplier;
        this.debug = debug;
    }

    public void start() {
        if (active) return;
        active = true;
        bridge = new InternalBridge();
        subscriberThread = new Thread(this::subscriberLoop,
                "AfterCore-Redis-PubSub-" + datasourceName + "-" + THREAD_COUNTER.incrementAndGet());
        subscriberThread.setDaemon(true);
        subscriberThread.start();

        if (debug) {
            logger.info("[Redis-PubSub] Started subscriber thread for '" + datasourceName + "'");
        }
    }

    private void subscriberLoop() {
        while (active) {
            try (Jedis jedis = jedisSupplier.get()) {
                if (debug) {
                    logger.info("[Redis-PubSub] Subscriber connected for '" + datasourceName + "'");
                }
                // subscribe blocks until unsubscribe is called
                // We start with a dummy channel to keep the connection alive
                jedis.subscribe(bridge, "__aftercore_keepalive_" + datasourceName);
            } catch (Exception e) {
                if (!active) break; // Shutdown in progress
                logger.log(Level.WARNING,
                        "[Redis-PubSub] Subscriber disconnected for '" + datasourceName + "', reconnecting in 5s...",
                        debug ? e : null);
            }

            if (active) {
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                // Recreate bridge for reconnect
                bridge = new InternalBridge();
            }
        }
    }

    @Override
    public CompletableFuture<Long> publish(@NotNull String channel, @NotNull String message) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisSupplier.get()) {
                return jedis.publish(channel, message);
            }
        }, scheduler.ioExecutor());
    }

    @Override
    public void subscribe(@NotNull String channel, @NotNull RedisMessageListener listener) {
        channelListeners.computeIfAbsent(channel, k -> new CopyOnWriteArraySet<>()).add(listener);
        InternalBridge b = bridge;
        if (b != null && b.isSubscribed()) {
            b.subscribe(channel);
        }
    }

    @Override
    public void psubscribe(@NotNull String pattern, @NotNull RedisMessageListener listener) {
        patternListeners.computeIfAbsent(pattern, k -> new CopyOnWriteArraySet<>()).add(listener);
        InternalBridge b = bridge;
        if (b != null && b.isSubscribed()) {
            b.psubscribe(pattern);
        }
    }

    @Override
    public void unsubscribe(@NotNull String channel, @NotNull RedisMessageListener listener) {
        Set<RedisMessageListener> listeners = channelListeners.get(channel);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                channelListeners.remove(channel);
                InternalBridge b = bridge;
                if (b != null && b.isSubscribed()) {
                    b.unsubscribe(channel);
                }
            }
        }
    }

    @Override
    public void punsubscribe(@NotNull String pattern, @NotNull RedisMessageListener listener) {
        Set<RedisMessageListener> listeners = patternListeners.get(pattern);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                patternListeners.remove(pattern);
                InternalBridge b = bridge;
                if (b != null && b.isSubscribed()) {
                    b.punsubscribe(pattern);
                }
            }
        }
    }

    @Override
    public void unsubscribeAll(@NotNull String channel) {
        channelListeners.remove(channel);
        InternalBridge b = bridge;
        if (b != null && b.isSubscribed()) {
            b.unsubscribe(channel);
        }
    }

    @Override
    public @NotNull Set<String> getSubscribedChannels() {
        return Collections.unmodifiableSet(channelListeners.keySet());
    }

    @Override
    public @NotNull Set<String> getSubscribedPatterns() {
        return Collections.unmodifiableSet(patternListeners.keySet());
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void shutdown() {
        active = false;
        InternalBridge b = bridge;
        if (b != null && b.isSubscribed()) {
            try {
                b.unsubscribe();
            } catch (Exception ignored) {}
        }
        Thread t = subscriberThread;
        if (t != null) {
            t.interrupt();
            try {
                t.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        channelListeners.clear();
        patternListeners.clear();
        bridge = null;
        subscriberThread = null;

        if (debug) {
            logger.info("[Redis-PubSub] Shutdown completed for '" + datasourceName + "'");
        }
    }

    /**
     * Bridge interna que estende JedisPubSub e despacha para os listeners registrados.
     */
    private class InternalBridge extends JedisPubSub {

        @Override
        public void onMessage(String channel, String message) {
            RedisMessage msg = new RedisMessage(channel, message, null);
            Set<RedisMessageListener> listeners = channelListeners.get(channel);
            if (listeners != null) {
                for (RedisMessageListener listener : listeners) {
                    try {
                        listener.onMessage(msg);
                    } catch (Exception e) {
                        logger.log(Level.WARNING,
                                "[Redis-PubSub] Error in listener for channel '" + channel + "'", e);
                    }
                }
            }
        }

        @Override
        public void onPMessage(String pattern, String channel, String message) {
            RedisMessage msg = new RedisMessage(channel, message, pattern);
            Set<RedisMessageListener> listeners = patternListeners.get(pattern);
            if (listeners != null) {
                for (RedisMessageListener listener : listeners) {
                    try {
                        listener.onMessage(msg);
                    } catch (Exception e) {
                        logger.log(Level.WARNING,
                                "[Redis-PubSub] Error in psubscribe listener for pattern '" + pattern + "'", e);
                    }
                }
            }
        }

        @Override
        public void onSubscribe(String channel, int subscribedChannels) {
            if (debug) {
                logger.info("[Redis-PubSub] Subscribed to '" + channel + "' (total: " + subscribedChannels + ")");
            }
        }

        @Override
        public void onUnsubscribe(String channel, int subscribedChannels) {
            if (debug) {
                logger.info("[Redis-PubSub] Unsubscribed from '" + channel + "' (total: " + subscribedChannels + ")");
            }
        }
    }
}
