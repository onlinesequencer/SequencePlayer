package net.onlinesequencer.player.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import static net.onlinesequencer.player.SequencePlayer.plugin;

public class AnvilListener implements Listener {

    static List<Material> DISCS = new ArrayList<>(Arrays.asList(
            Material.MUSIC_DISC_5,
            Material.MUSIC_DISC_13,
            Material.MUSIC_DISC_BLOCKS,
            Material.MUSIC_DISC_CAT,
            Material.MUSIC_DISC_CHIRP,
            Material.MUSIC_DISC_FAR,
            Material.MUSIC_DISC_MALL,
            Material.MUSIC_DISC_MELLOHI,
            Material.MUSIC_DISC_STAL,
            Material.MUSIC_DISC_STRAD,
            Material.MUSIC_DISC_WAIT,
            Material.MUSIC_DISC_WARD,
            Material.MUSIC_DISC_PIGSTEP,
            Material.MUSIC_DISC_OTHERSIDE,
            Material.MUSIC_DISC_RELIC
    ));

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent e) throws IOException {
        AnvilInventory anvilInv = e.getInventory();

        ItemStack[] itemsInAnvil = anvilInv.getContents();

        if (
                (itemsInAnvil[0] != null && itemsInAnvil[0].getType().isRecord()) &&
                itemsInAnvil[1] == null
        ) {
            ItemStack slot1 = itemsInAnvil[0];
            String rename = e.getInventory().getRenameText();
            if (rename == null) {
                return;
            }

            int sequenceId;
            try {
                sequenceId = Integer.parseInt(rename);
            } catch (NumberFormatException ie) {
                return;
            }

            Material mat = DISCS.get(sequenceId % (DISCS.size() - 1));

            ItemStack item = new ItemStack(mat);

            ItemMeta meta = item.getItemMeta();
            assert meta != null;

            String urlStr = String.format("https://onlinesequencer.net/%s", sequenceId);
            URL url;
            try {
                url = new URL(urlStr);
            } catch (MalformedURLException ie) {
                return;
            }
            Document soup = Jsoup.parse(url, 5000);
            Element title = soup.getElementsByClass("info-sidebar-title").first();
            if (title == null) return;
            String titleText = title.text();

            Element details = soup.getElementsByClass("info-sidebar-details").first();
            if (details == null) return;
            Element author = details.getElementsByTag("a").first();
            if (author == null) return;
            String authorText = author.text();

            String newName = String.format("%s%s%s - %s", ChatColor.RESET, ChatColor.GRAY, authorText, titleText);

            meta.setDisplayName(null);
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey(plugin, "sequence"), PersistentDataType.INTEGER, sequenceId);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            ArrayList<String> lore = new ArrayList<>();
            lore.add(newName);
            meta.setLore(lore);
            e.setResult(item);
            item.setItemMeta(meta);
            anvilInv.setRepairCost(0);
        }
    }
}