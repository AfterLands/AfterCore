package com.afterlands.core.diagnostics.commands;

import com.afterlands.core.concurrent.SchedulerService;
import com.afterlands.core.diagnostics.DiagnosticsService;
import com.afterlands.core.diagnostics.DiagnosticsSnapshot;
import com.afterlands.core.diagnostics.DiagnosticsSnapshot.*;
import com.afterlands.core.metrics.MetricsService;
import com.afterlands.core.metrics.MetricsSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Comando /acore para diagnóstico e health check.
 *
 * <p>Subcomandos:
 * <ul>
 *   <li>/acore status - Dependências, versões, flags</li>
 *   <li>/acore db - Database info e ping</li>
 *   <li>/acore threads - Thread pool info</li>
 *   <li>/acore system - System info (JVM, OS, memória)</li>
 *   <li>/acore metrics - Métricas de performance</li>
 *   <li>/acore all - Todas as informações</li>
 * </ul>
 * </p>
 */
public final class ACoreCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final DiagnosticsService diagnostics;
    private final SchedulerService scheduler;
    private final MetricsService metrics;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "status", "db", "threads", "system", "metrics", "memory", "all"
    );

    public ACoreCommand(@NotNull Plugin plugin,
                       @NotNull DiagnosticsService diagnostics,
                       @NotNull SchedulerService scheduler,
                       @NotNull MetricsService metrics) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.scheduler = scheduler;
        this.metrics = metrics;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                            @NotNull Command command,
                            @NotNull String label,
                            @NotNull String[] args) {
        if (!sender.hasPermission("aftercore.admin")) {
            sender.sendMessage(ChatColor.RED + "Sem permissão.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "status" -> showStatus(sender);
            case "db" -> showDatabase(sender);
            case "threads" -> showThreads(sender);
            case "system" -> showSystem(sender);
            case "metrics" -> showMetrics(sender);
            case "memory" -> showMemory(sender);
            case "all" -> showAll(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                     @NotNull Command command,
                                     @NotNull String alias,
                                     @NotNull String[] args) {
        if (!sender.hasPermission("aftercore.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) {
                    matches.add(sub);
                }
            }
            return matches;
        }

        return List.of();
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== AfterCore Diagnostics ===");
        sender.sendMessage(ChatColor.YELLOW + "/acore status " + ChatColor.GRAY + "- Dependências e versões");
        sender.sendMessage(ChatColor.YELLOW + "/acore db " + ChatColor.GRAY + "- Database info e ping");
        sender.sendMessage(ChatColor.YELLOW + "/acore threads " + ChatColor.GRAY + "- Thread pool info");
        sender.sendMessage(ChatColor.YELLOW + "/acore system " + ChatColor.GRAY + "- System info (JVM, OS)");
        sender.sendMessage(ChatColor.YELLOW + "/acore metrics " + ChatColor.GRAY + "- Performance metrics");
        sender.sendMessage(ChatColor.YELLOW + "/acore memory " + ChatColor.GRAY + "- Memory leak detection");
        sender.sendMessage(ChatColor.YELLOW + "/acore all " + ChatColor.GRAY + "- Todas as informações");
    }

    private void showStatus(@NotNull CommandSender sender) {
        DiagnosticsSnapshot snapshot = diagnostics.captureSnapshot();

        sender.sendMessage(ChatColor.GOLD + "=== Status ===");
        sender.sendMessage(ChatColor.YELLOW + "Plugin: " + ChatColor.WHITE +
                plugin.getDescription().getName() + " v" + plugin.getDescription().getVersion());

        sender.sendMessage(ChatColor.YELLOW + "Dependências:");
        for (Map.Entry<String, DependencyInfo> entry : snapshot.dependencies().entrySet()) {
            DependencyInfo dep = entry.getValue();
            String status = dep.present()
                    ? ChatColor.GREEN + "✓ " + dep.name() + " v" + dep.version()
                    : ChatColor.RED + "✗ " + dep.name() + " (não encontrado)";
            sender.sendMessage("  " + status);
        }

        // Database status resumido
        DatabaseInfo db = snapshot.databaseInfo();
        String dbStatus = db.enabled()
                ? (db.initialized() ? ChatColor.GREEN + "✓ Habilitado" : ChatColor.RED + "✗ Erro: " + db.lastError())
                : ChatColor.GRAY + "Desabilitado";
        sender.sendMessage(ChatColor.YELLOW + "Database: " + dbStatus);
    }

    private void showDatabase(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Database ===");
        sender.sendMessage(ChatColor.GRAY + "Coletando informações...");

        // Capturar snapshot inicial
        DiagnosticsSnapshot snapshot = diagnostics.captureSnapshot();
        DatabaseInfo db = snapshot.databaseInfo();

        sender.sendMessage(ChatColor.YELLOW + "Enabled: " + ChatColor.WHITE + db.enabled());
        sender.sendMessage(ChatColor.YELLOW + "Initialized: " + ChatColor.WHITE + db.initialized());

        if (db.type() != null) {
            sender.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + db.type());
        }

        if (db.lastError() != null) {
            sender.sendMessage(ChatColor.RED + "Last Error: " + db.lastError());
        }

        // Pool stats
        if (db.poolStats() != null) {
            PoolStats stats = db.poolStats();
            sender.sendMessage(ChatColor.YELLOW + "Pool Stats:");
            sender.sendMessage("  " + ChatColor.GRAY + "Total: " + ChatColor.WHITE + stats.totalConnections());
            sender.sendMessage("  " + ChatColor.GRAY + "Active: " + ChatColor.WHITE + stats.activeConnections());
            sender.sendMessage("  " + ChatColor.GRAY + "Idle: " + ChatColor.WHITE + stats.idleConnections());
            sender.sendMessage("  " + ChatColor.GRAY + "Awaiting: " + ChatColor.WHITE + stats.threadsAwaitingConnection());
        }

        // Ping assíncrono
        if (db.enabled() && db.initialized()) {
            sender.sendMessage(ChatColor.YELLOW + "Testando conexão...");
            scheduler.ioExecutor().execute(() -> {
                long pingMs = diagnostics.pingDatabase();
                scheduler.runSync(() -> {
                    if (pingMs >= 0) {
                        String color = pingMs < 10 ? ChatColor.GREEN.toString()
                                : pingMs < 50 ? ChatColor.YELLOW.toString()
                                : ChatColor.RED.toString();
                        sender.sendMessage(ChatColor.YELLOW + "Ping: " + color + pingMs + "ms");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Ping: FAILED");
                    }
                });
            });
        }
    }

    private void showThreads(@NotNull CommandSender sender) {
        DiagnosticsSnapshot snapshot = diagnostics.captureSnapshot();
        ThreadPoolInfo threads = snapshot.threadPoolInfo();

        sender.sendMessage(ChatColor.GOLD + "=== Thread Pools ===");
        sender.sendMessage(ChatColor.YELLOW + "IO Threads: " + ChatColor.WHITE + threads.ioThreads() +
                ChatColor.GRAY + " (" + threads.ioPoolStatus() + ")");
        sender.sendMessage(ChatColor.YELLOW + "CPU Threads: " + ChatColor.WHITE + threads.cpuThreads() +
                ChatColor.GRAY + " (" + threads.cpuPoolStatus() + ")");

        sender.sendMessage(ChatColor.GRAY + "Nota: Thread pools são fixed-size. " +
                "Para métricas detalhadas, use /acore metrics quando disponível.");
    }

    private void showSystem(@NotNull CommandSender sender) {
        DiagnosticsSnapshot snapshot = diagnostics.captureSnapshot();
        SystemInfo sys = snapshot.systemInfo();

        sender.sendMessage(ChatColor.GOLD + "=== System Info ===");
        sender.sendMessage(ChatColor.YELLOW + "Java Version: " + ChatColor.WHITE + sys.javaVersion());
        sender.sendMessage(ChatColor.YELLOW + "OS: " + ChatColor.WHITE + sys.osName() + " " + sys.osVersion());
        sender.sendMessage(ChatColor.YELLOW + "Cores: " + ChatColor.WHITE + sys.availableProcessors());

        sender.sendMessage(ChatColor.YELLOW + "Memory:");
        sender.sendMessage("  " + ChatColor.GRAY + "Max: " + ChatColor.WHITE + sys.maxMemoryMb() + " MB");
        sender.sendMessage("  " + ChatColor.GRAY + "Total: " + ChatColor.WHITE + sys.totalMemoryMb() + " MB");
        sender.sendMessage("  " + ChatColor.GRAY + "Free: " + ChatColor.WHITE + sys.freeMemoryMb() + " MB");

        long usedMb = sys.totalMemoryMb() - sys.freeMemoryMb();
        double usagePercent = (usedMb * 100.0) / sys.totalMemoryMb();
        String color = usagePercent < 70 ? ChatColor.GREEN.toString()
                : usagePercent < 85 ? ChatColor.YELLOW.toString()
                : ChatColor.RED.toString();
        sender.sendMessage("  " + ChatColor.GRAY + "Used: " + color + usedMb + " MB " +
                "(" + String.format("%.1f", usagePercent) + "%)");
    }

    private void showMetrics(@NotNull CommandSender sender) {
        MetricsSnapshot snapshot = metrics.snapshot();

        sender.sendMessage(ChatColor.GOLD + "=== Metrics ===");

        if (snapshot.counters().isEmpty() && snapshot.timers().isEmpty() && snapshot.gauges().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Nenhuma métrica registrada ainda.");
            return;
        }

        if (!snapshot.counters().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Counters:");
            snapshot.counters().forEach((name, value) ->
                    sender.sendMessage("  " + ChatColor.GRAY + name + ": " + ChatColor.WHITE + value));
        }

        if (!snapshot.timers().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Timers:");
            snapshot.timers().forEach((name, stats) ->
                    sender.sendMessage("  " + ChatColor.GRAY + name + ": " + ChatColor.WHITE +
                            "count=" + stats.count() +
                            ", avg=" + String.format("%.2f", stats.avgMs()) + "ms" +
                            ", min=" + String.format("%.2f", stats.minMs()) + "ms" +
                            ", max=" + String.format("%.2f", stats.maxMs()) + "ms"));
        }

        if (!snapshot.gauges().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Gauges:");
            snapshot.gauges().forEach((name, value) ->
                    sender.sendMessage("  " + ChatColor.GRAY + name + ": " + ChatColor.WHITE +
                            String.format("%.2f", value)));
        }
    }

    private void showMemory(@NotNull CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Memory Leak Detection ===");
        sender.sendMessage(ChatColor.GRAY + "Verificando componentes do Inventory Framework...");

        // Executar async para não bloquear
        scheduler.ioExecutor().execute(() -> {
            try {
                // Importar MemoryLeakDetector
                com.afterlands.core.inventory.diagnostics.MemoryLeakDetector detector =
                    new com.afterlands.core.inventory.diagnostics.MemoryLeakDetector(
                        com.afterlands.core.api.AfterCore.get()
                    );

                com.afterlands.core.inventory.diagnostics.MemoryLeakDetector.MemoryLeakReport report =
                    detector.checkForLeaks();

                // Retornar ao main thread para enviar mensagens
                scheduler.runSync(() -> {
                    // Heap stats
                    sender.sendMessage(ChatColor.YELLOW + "Heap Usage:");
                    sender.sendMessage("  " + ChatColor.GRAY + "Used: " + ChatColor.WHITE +
                        (report.heapUsed / 1024 / 1024) + " MB / " +
                        (report.heapMax / 1024 / 1024) + " MB");

                    String color = report.heapUsagePercent < 70 ? ChatColor.GREEN.toString()
                            : report.heapUsagePercent < 85 ? ChatColor.YELLOW.toString()
                            : ChatColor.RED.toString();
                    sender.sendMessage("  " + ChatColor.GRAY + "Usage: " + color +
                        String.format("%.1f%%", report.heapUsagePercent));

                    // Health status
                    String healthColor = report.isHealthy() ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
                    sender.sendMessage(ChatColor.YELLOW + "Health: " + healthColor +
                        (report.isHealthy() ? "✓ HEALTHY" : "✗ LEAKS DETECTED"));

                    // Full report
                    sender.sendMessage(ChatColor.GRAY + "Use console para relatório completo.");
                    plugin.getLogger().info("\n" + report.format());
                });

            } catch (Exception e) {
                scheduler.runSync(() -> {
                    sender.sendMessage(ChatColor.RED + "Erro ao verificar memória: " + e.getMessage());
                    plugin.getLogger().warning("Memory leak detection failed: " + e.getMessage());
                });
            }
        });
    }

    private void showAll(@NotNull CommandSender sender) {
        showStatus(sender);
        sender.sendMessage("");
        showDatabase(sender);
        sender.sendMessage("");
        showThreads(sender);
        sender.sendMessage("");
        showSystem(sender);
        sender.sendMessage("");
        showMetrics(sender);
        sender.sendMessage("");
        showMemory(sender);
    }
}
