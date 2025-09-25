package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.plugin.java.JavaPlugin;

public final class AdminWatchdog extends JavaPlugin {

    private static final String COMMAND_NAME = "adminwatchdog";
    private static AdminWatchdog instance;
    private DiscordManager discordManager;
    private ConfigManager configManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            saveResource("messages.yml", false);

            instance = this;
            configManager = new ConfigManager(this);
            discordManager = new DiscordManager(this);
            updateChecker = new UpdateChecker(this);

            getServer().getPluginManager().registerEvents(new CommandListener(this), this);

            Commands commandHandler = new Commands(this);
            if (this.getCommand(COMMAND_NAME) != null) {
                this.getCommand(COMMAND_NAME).setExecutor(commandHandler);
                this.getCommand(COMMAND_NAME).setTabCompleter(commandHandler);
            } else {
                getLogger().warning("Command '" + COMMAND_NAME + "' not found in plugin.yml!");
            }

            if (updateChecker != null) {
                updateChecker.startUpdateChecker();
            }

            if (configManager != null) {
                getLogger().info(configManager.getMessage("plugin.enabled"));
            }
        } catch (Exception e) {
            getLogger().severe("Failed to enable plugin: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (updateChecker != null) {
                updateChecker.stopUpdateChecker();
            }
            MinecraftApiHelper.shutdown();
            if (configManager != null) {
                getLogger().info(configManager.getMessage("plugin.disabled"));
            }
        } catch (Exception e) {
            getLogger().warning("Error during plugin shutdown: " + e.getMessage());
        } finally {
            instance = null;
        }
    }

    public static AdminWatchdog getInstance() {
        return instance;
    }

    public DiscordManager getDiscordManager() {
        return discordManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}