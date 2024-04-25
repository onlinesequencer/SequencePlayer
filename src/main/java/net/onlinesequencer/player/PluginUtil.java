package net.onlinesequencer.player;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

import static net.onlinesequencer.player.SequencePlayer.plugin;

public class PluginUtil {
    static FileConfiguration config = plugin.getConfig();

    public static void setDataType(ItemMeta im, String key) {
        im.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "specialtype"),
                PersistentDataType.STRING, key
        );
    }

    public static String getDataType(ItemMeta im) {
        return Objects.requireNonNull(im)
                .getPersistentDataContainer()
                .get(
                        new NamespacedKey(plugin, "specialtype"),
                        PersistentDataType.STRING
                );
    }
}