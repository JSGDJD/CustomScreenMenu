package com.example.ui;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import static com.example.ui.CursorMenuPlugin.*;

public class MenuListener implements Listener {

    private final CursorMenuPlugin plugin;

    public MenuListener(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if(joinRunBool){
            foliaLib.scheduling().entitySpecificScheduler(event.getPlayer()).runDelayed(task -> {
                plugin.setupCursor(event.getPlayer(),joinRunSection);
            },null,15 + (20 * runDelay));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.stopCursor(event.getPlayer(),true);
    }

    @EventHandler
    public void onVehicleLeave(VehicleExitEvent event) {
        if(event.getExited() instanceof Player){
            Player player = (Player) event.getExited();
            if(playerSit.containsKey(player)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onCommandCancel(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if(playerSit.containsKey(player)) {
            if(event.getMessage().toLowerCase().startsWith("/cmenu") || event.getMessage().toLowerCase().startsWith("/cursormenu")) {
                return;
            } else {
                String prefix = plugin.getConfig().getString("messages.prefix");
                String blocked = plugin.getConfig().getString("messages.command_blocked");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + blocked));
                event.setCancelled(true);
            }
        }
    }
}
