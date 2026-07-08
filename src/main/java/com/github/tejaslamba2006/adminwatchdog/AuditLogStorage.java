package com.github.tejaslamba2006.adminwatchdog;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SQLite-backed audit trail. All access goes through a single background thread,
 * since SQLite serializes writes anyway and this avoids "database is locked" errors.
 */
public final class AuditLogStorage {

    private final AdminWatchdog plugin;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "AdminWatchdog-AuditLog");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public AuditLogStorage(AdminWatchdog plugin) {
        this.plugin = plugin;
        executor.execute(this::open);
        startRetentionTask();
    }

    private void open() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + new File(dataFolder, "adminwatchdog.db"));
            try (var statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS audit_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            ts INTEGER NOT NULL,
                            player TEXT,
                            type TEXT NOT NULL,
                            detail TEXT NOT NULL
                        )""");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_audit_log_player ON audit_log(player)");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to open audit log database: " + e.getMessage());
        }
    }

    public void record(String type, String player, String detail) {
        if (!plugin.getConfigManager().isDatabaseLoggingEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        executor.execute(() -> {
            if (connection == null) {
                return;
            }
            try (PreparedStatement statement = connection
                    .prepareStatement("INSERT INTO audit_log (ts, player, type, detail) VALUES (?, ?, ?, ?)")) {
                statement.setLong(1, now);
                statement.setString(2, player);
                statement.setString(3, type);
                statement.setString(4, detail);
                statement.executeUpdate();
            } catch (SQLException e) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().warning("Failed to write audit log entry: " + e.getMessage());
                }
            }
        });
    }

    public record Entry(long timestamp, String type, String detail) {
    }

    public CompletableFuture<List<Entry>> getRecentEntries(String player, int limit) {
        CompletableFuture<List<Entry>> future = new CompletableFuture<>();
        executor.execute(() -> {
            List<Entry> entries = new ArrayList<>();
            if (connection == null) {
                future.complete(entries);
                return;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT ts, type, detail FROM audit_log WHERE player = ? COLLATE NOCASE ORDER BY ts DESC LIMIT ?")) {
                statement.setString(1, player);
                statement.setInt(2, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new Entry(rs.getLong("ts"), rs.getString("type"), rs.getString("detail")));
                    }
                }
                future.complete(entries);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void startRetentionTask() {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> {
            int retentionDays = plugin.getConfigManager().getDatabaseRetentionDays();
            if (retentionDays <= 0 || connection == null) {
                return;
            }
            long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM audit_log WHERE ts < ?")) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            } catch (SQLException e) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().warning("Failed to clean up old audit log entries: " + e.getMessage());
                }
            }
        }, 1, 24, TimeUnit.HOURS);
    }

    public void shutdown() {
        executor.execute(() -> {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        });
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
