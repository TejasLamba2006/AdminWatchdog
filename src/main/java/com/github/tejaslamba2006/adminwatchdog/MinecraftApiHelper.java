package com.github.tejaslamba2006.adminwatchdog;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class MinecraftApiHelper {

    private static final String IMAGE_BASE_URL = "https://mc.nerothe.com/img/1.21.8";
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "AdminWatchdog-ApiHelper");
        thread.setDaemon(true);
        return thread;
    });
    private static final String DEFAULT_DESCRIPTION = "Item taken from creative inventory";

    private static final Cache<Material, ItemData> ITEM_CACHE = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    public static void shutdown() {
        if (!executor.isShutdown())
            executor.shutdown();
        ITEM_CACHE.invalidateAll();
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

    public CompletableFuture<ItemData> getItemData(ItemStack item) {
        Material material = item.getType();

        ItemData cached = ITEM_CACHE.getIfPresent(material);
        if (cached != null)
            return CompletableFuture.completedFuture(cached);

        ItemData itemData = createItemData(item);
        ITEM_CACHE.put(material, itemData);
        return CompletableFuture.completedFuture(itemData);
    }

    public record ItemData(String name, String description, String imageUrl, boolean renewable, boolean fromApi) {
    }
}