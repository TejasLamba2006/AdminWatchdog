package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MinecraftApiHelper {

    private static final String IMAGE_BASE_URL = "https://mc.nerothe.com/img/1.21.8";
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "AdminWatchdog-ApiHelper");
        thread.setDaemon(true);
        return thread;
    });
    private static final String DEFAULT_DESCRIPTION = "Item taken from creative inventory";

    public MinecraftApiHelper(AdminWatchdog plugin) {
    }

    public CompletableFuture<ItemData> getItemData(ItemStack item) {
        return CompletableFuture.completedFuture(createItemData(item));
    }

    private ItemData createItemData(ItemStack item) {
        String formattedName = formatItemName(item.getType());
        String imageUrl = getImageUrl(item.getType());

        return new ItemData(
                formattedName,
                DEFAULT_DESCRIPTION,
                imageUrl,
                false,
                false);
    }

    private String formatItemName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder formatted = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c == ' ') {
                formatted.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formatted.append(c);
            }
        }

        return formatted.toString();
    }

    private String getImageUrl(Material material) {
        return IMAGE_BASE_URL + "/minecraft_" + material.name().toLowerCase() + ".png";
    }

    public static void shutdown() {
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
    }

    public static class ItemData {
        public final String name;
        public final String description;
        public final String imageUrl;
        public final boolean renewable;
        public final boolean fromApi;

        public ItemData(String name, String description, String imageUrl, boolean renewable, boolean fromApi) {
            this.name = name;
            this.description = description;
            this.imageUrl = imageUrl;
            this.renewable = renewable;
            this.fromApi = fromApi;
        }
    }
}