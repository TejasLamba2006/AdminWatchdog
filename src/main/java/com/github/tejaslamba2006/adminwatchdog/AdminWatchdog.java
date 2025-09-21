package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.plugin.java.JavaPlugin;

public class AdminWatchdog extends JavaPlugin {

    private static AdminWatchdog instance;
    private DiscordManager dm;
    private ConfigManager configManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        saveResource("messages.yml", false);

        instance = this;
        configManager = new ConfigManager(this);
        dm = new DiscordManager(this);
        updateChecker = new UpdateChecker(this);

        getServer().getPluginManager().registerEvents(new CommandListener(this), this);

        Commands commandHandler = new Commands(this);
        this.getCommand("adminwatchdog").setExecutor(commandHandler);
        this.getCommand("adminwatchdog").setTabCompleter(commandHandler);

        // Start update checker
        updateChecker.startUpdateChecker();

        getLogger().info(configManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.stopUpdateChecker();
        }
        MinecraftApiHelper.shutdown();
        getLogger().info(configManager.getMessage("plugin.disabled"));
    }

    public static AdminWatchdog getInstance() {
        return instance;
    }

    public DiscordManager getDiscord() {
        return dm;
    }

    public DiscordManager getDiscordManager() {
        return dm;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}