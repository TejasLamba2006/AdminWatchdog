package com.github.tejaslamba2006.adminwatchdog;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.CompletableFuture;

public final class MinecraftApiHelper {

    private static final String IMAGE_BASE_URL = "https://mc.nerothe.com/img/1.21.8";
    private static final String DEFAULT_DESCRIPTION = "Item taken from creative inventory";

    public static void shutdown() {
        // ponytail: no resources to release; item data is computed on demand from Material
    }

    private ItemData createItemData(ItemStack item) {
        String formattedName = formatItemName(item.getType());
        String imageUrl = getImageUrl(item.getType());

        return new ItemData(formattedName, DEFAULT_DESCRIPTION, imageUrl);
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
        return CompletableFuture.completedFuture(createItemData(item));
    }

    public record ItemData(String name, String description, String imageUrl) {
    }
}
