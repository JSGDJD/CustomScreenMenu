package com.example.ui.layout;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

import static com.example.ui.CursorMenuPlugin.*;

public class MenuLayout {

    public double x;

    public double y;

    public double z;

    public String name;

    public List<String> command;

    public boolean stop;

    public String key;

    public boolean teleportBool;

    public Location teleportLoc;

    public boolean stopCommandBool;

    public List<String> stopCommands;

    private boolean isClick;

    public boolean teleportOriginal;

    public MenuLayout(String key,String name, List<String> command, boolean stop, double x, double y, double z,boolean teleportBool,boolean tBack,Location teleportLoc, boolean stopCommandBool, List<String> stopCommands) {
        this.key = key;
        this.name = name;
        this.command = command;
        this.stop = stop;
        this.x = x;
        this.y = y;
        this.z = z;
        this.isClick = false;
        this.teleportBool = teleportBool;
        this.teleportLoc = teleportLoc;
        this.stopCommandBool = stopCommandBool;
        this.stopCommands = stopCommands;
        this.teleportOriginal = tBack;
    }

    public void runCommand(Player player) {
        if(this.isClick) {
            return;
        }
        this.isClick = true;
        for(String cmd : command) {
            cmd = cmd.replaceAll("%player%", player.getName());
            if(hasPAPI){
                cmd = PlaceholderAPI.setPlaceholders(player, cmd);
            }
            if(cmd.toLowerCase().startsWith("[console]")) {
                cmd = cmd.replaceAll("\\[console\\]", "").trim();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else if(cmd.toLowerCase().startsWith("[op]")) {
                cmd = cmd.replaceAll("\\[op\\]", "").trim();
                if(player.isOp()){
                    player.performCommand(cmd);
                    break;
                }
                try {
                    player.setOp(true);
                    player.performCommand(cmd);
                } finally {
                    player.setOp(false);
                }
            } else {
                if(cmd.toLowerCase().startsWith("[player]")) {
                    cmd = cmd.replaceAll("\\[player\\]", "").trim();
                }
                player.performCommand(cmd);
            }
        }
        if(stop){
            foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                plugin.stopCursor(player,false);
                this.teleport(player);
                this.runStopCommand(player);
            },null,2);
        }
        foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
            this.isClick = false;
        },null,15);
    }

    private void teleport(Player player) {
        if(this.teleportBool) {
            if(this.teleportOriginal){
                player.teleport(playerLocations.get(player));
                playerLocations.remove(player);
            }
            player.teleport(teleportLoc);
        }
    }

    private void runStopCommand(Player player) {
        if(this.stopCommandBool) {
            for(String cmd : stopCommands) {
                cmd = cmd.replaceAll("%player%", player.getName());
                if(hasPAPI){
                    cmd = PlaceholderAPI.setPlaceholders(player, cmd);
                }
                if(cmd.toLowerCase().startsWith("[console]")) {
                    cmd = cmd.replaceAll("\\[console\\]", "").trim();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } else if(cmd.toLowerCase().startsWith("[op]")) {
                    cmd = cmd.replaceAll("\\[op\\]", "").trim();
                    if(player.isOp()){
                        player.performCommand(cmd);
                        break;
                    }
                    try {
                        player.setOp(true);
                        player.performCommand(cmd);
                    } finally {
                        player.setOp(false);
                    }
                } else {
                    if(cmd.toLowerCase().startsWith("[player]")) {
                        cmd = cmd.replaceAll("\\[player\\]", "").trim();
                    }
                    player.performCommand(cmd);
                }
            }
        }
    }
}

