package com.github.tejaslamba2006.adminwatchdog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Commands implements TabExecutor {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "version", "v", "ver", "reload", "rl", "update", "checkupdate");

    private final AdminWatchdog plugin;

    public Commands(AdminWatchdog plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().getMessage("commands.usage"));
            return true;
        }

        String subcommand = args[0].toLowerCase();

        if (subcommand.equals("version") || subcommand.equals("v") || subcommand.equals("ver")) {
            String version = plugin.getPluginMeta().getVersion();
            String message = plugin.getConfigManager().getMessage("commands.version", "%version%", version);
            sender.sendMessage(message);
            return true;
        }

        if (subcommand.equals("reload") || subcommand.equals("rl")) {
            if (!sender.hasPermission("adminwatchdog.reload")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("commands.no-permission"));
                return true;
            }

            try {
                plugin.getConfigManager().reloadConfigs();
                sender.sendMessage(plugin.getConfigManager().getMessage("commands.reload-success"));
            } catch (Exception e) {
                sender.sendMessage(plugin.getConfigManager().getMessage("commands.reload-failed"));
                plugin.getLogger().severe("Error reloading config: " + e.getMessage());
            }
            return true;
        }

        if (subcommand.equals("update") || subcommand.equals("checkupdate")) {
            if (!sender.hasPermission("adminwatchdog.update.check")) {
                sender.sendMessage(plugin.getConfigManager().getMessage("commands.no-permission"));
                return true;
            }

            sender.sendMessage(plugin.getConfigManager().getMessage("update.check-start"));

            plugin.getUpdateChecker().checkForUpdatesSync().thenAccept(result -> {
                if (result.hasError()) {
                    String message = plugin.getConfigManager().getMessage("update.check-failed", "%error%",
                            result.getError());
                    sender.sendMessage(message);
                } else if (result.isUpdateAvailable()) {
                    String message = plugin.getConfigManager().getMessage("update.available",
                            "%current%", result.getCurrentVersion(),
                            "%latest%", result.getLatestVersion());
                    sender.sendMessage(message);
                    sender.sendMessage("Download: " + result.getDownloadUrl());
                } else {
                    String message = plugin.getConfigManager().getMessage("update.up-to-date", "%current%",
                            result.getCurrentVersion());
                    sender.sendMessage(message);
                }
            }).exceptionally(ex -> {
                String message = plugin.getConfigManager().getMessage("update.check-failed", "%error%",
                        ex.getMessage());
                sender.sendMessage(message);
                return null;
            });

            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            String current = args[0].toLowerCase();

            for (String option : SUB_COMMANDS) {
                if (option.startsWith(current)) {
                    result.add(option);
                }
            }
            return result;
        }

        return new ArrayList<>();
    }

}
