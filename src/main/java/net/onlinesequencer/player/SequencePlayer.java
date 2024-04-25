package net.onlinesequencer.player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import net.onlinesequencer.player.commands.*;
import net.onlinesequencer.player.listener.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Jukebox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class SequencePlayer extends JavaPlugin {

    public static SequencePlayer plugin;
    FileConfiguration config = getConfig();

    @Override
    public void onLoad() {
        plugin = this;
    }

    @Override
    public void onEnable() {
        final PluginManager pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new AnvilListener(), plugin);
        pm.registerEvents(new JukeboxListener(), plugin);

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.WORLD_EVENT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();

                if (packet.getIntegers().read(0).toString().equals("1010")) {
                    Jukebox jukebox = (Jukebox) packet.getBlockPositionModifier().read(0).toLocation(event.getPlayer().getWorld()).getBlock().getState();

                    if (!jukebox.getRecord().hasItemMeta()) return;

                    if (Objects.requireNonNull(jukebox.getRecord().getItemMeta()).getPersistentDataContainer().has(new NamespacedKey(plugin, "sequence"), PersistentDataType.INTEGER)) {
                        jukebox.stopPlaying();
                        event.setCancelled(true);
                    }
                }
            }
        });

        this.getCommand("sequence").setExecutor(new CommandSequence(plugin));

        saveDefaultConfig();
        saveConfig();
    }

    @Override
    public void onDisable() {
        saveDefaultConfig();
        saveConfig();
    }
}