package net.onlinesequencer.player.listener;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.JukeboxPlayableComponent;
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
            Material.MUSIC_DISC_RELIC,
            Material.MUSIC_DISC_CREATOR,
            Material.MUSIC_DISC_CREATOR_MUSIC_BOX,
            Material.MUSIC_DISC_PRECIPICE
    ));

    @EventHandler
    @SuppressWarnings("unused")
    public void onPrepareAnvil(PrepareAnvilEvent e) {
        AnvilInventory anvilInv = e.getInventory();

        ItemStack[] itemsInAnvil = anvilInv.getContents();

        if (
                (itemsInAnvil[0] != null && itemsInAnvil[0].getType().isRecord()) &&
                itemsInAnvil[1] == null
        ) {
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

            // TODO: this blocks the main thread--how do we request the info and still set the result?
            String urlStr = String.format("https://onlinesequencer.net/%s", sequenceId);
            URL url;
            try {
                url = new URL(urlStr);
            } catch (MalformedURLException ie) {
                return;
            }
            Document soup;
            try {
                // TODO: We should probably try to implement an endpoint to just return JSON
                //  instead of parsing the sequence page
                soup = Jsoup.parse(url, 5000);
            } catch (IOException ex) {
                throw new RuntimeException("Unable to parse sequence response, did the page format change? " +
                        ex.getMessage());
            }
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
            // Required to hide disc lore in older versions
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            // TODO: this will probably throw an error in older versions
            JukeboxPlayableComponent playable = meta.getJukeboxPlayable();
            if (playable != null) playable.setShowInTooltip(false);
            meta.setJukeboxPlayable(playable);
            ArrayList<String> lore = new ArrayList<>();
            lore.add(newName);
            meta.setLore(lore);
            e.setResult(item);
            item.setItemMeta(meta);
            // TODO: figure out compatibility
            anvilInv.setRepairCost(0);
        }
    }
}