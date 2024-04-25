package net.onlinesequencer.player.commands;

import net.onlinesequencer.player.SequencePlayer;
import net.onlinesequencer.player.util.PlayerEngine;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandSequence implements CommandExecutor {
    HashSet<UUID> sequencePlayers = new HashSet<>();

    public CommandSequence(SequencePlayer plugin) {
        SequencePlayer.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String string, String[] args) {
        Player player = (Player) sender;

        if (sequencePlayers.contains(player.getUniqueId())) {
            sequencePlayers.remove(player.getUniqueId());
            sender.sendMessage(ChatColor.AQUA + "Stopping sequence.");
            return true;
        }
        int sequenceId;
        try {
            sequenceId = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Sequence ID must be an integer.");
            return true;
        }

        sequencePlayers.add(player.getUniqueId());

        PlayerEngine engine = new PlayerEngine();

        Thread thread = engine.getThread(player, sequenceId, () -> {
            if (!sequencePlayers.contains(player.getUniqueId())) {
                engine.stop();
            }
            return null;
        }, () -> {
            sequencePlayers.remove(player.getUniqueId());
            sender.sendMessage(ChatColor.AQUA + "Sequence ended.");
            return null;
        });
        thread.start();

        return true;
    }


}
