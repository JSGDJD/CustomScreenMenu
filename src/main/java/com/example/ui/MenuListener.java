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
        if (joinRunBool) {
            foliaLib.scheduling().entitySpecificScheduler(event.getPlayer()).runDelayed(task -> {
                plugin.setupCursor(event.getPlayer(), joinRunSection);
            }, null, 15 + (20 * runDelay));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.stopCursor(event.getPlayer(), true);
    }

    @EventHandler
    public void onVehicleLeave(VehicleExitEvent event) {
        if (event.getExited() instanceof Player) {
            Player player = (Player) event.getExited();
            if (playerSit.containsKey(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onCommandCancel(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!playerSit.containsKey(player)) return;

        String message = event.getMessage().toLowerCase().trim();
        String command = message.startsWith("/") ? message.substring(1) : message;

        if (command.startsWith("cmenu") || command.startsWith("cursormenu")) {
            return;
        }

        for (String allowed : allowedCommands) {
            if (command.startsWith(allowed)) {
                return;
            }
        }

        String prefix = plugin.getConfig().getString("messages.prefix", "&c[CursorMenu] ");
        String blocked = plugin.getConfig().getString("messages.command_blocked", "&7该命令在菜单模式下不可用。");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + blocked));
        event.setCancelled(true);
    }
    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) {
            player.sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            "&a[CustomScreenMenu] &fThis version is open source and does not receive author support and assistance\n" +
                                    "&f插件版本：&e" + plugin.getDescription().getVersion() + "\n" +
                                    "&f作者：&e野比大雄\nQQ:3357153117\n" +
                                    "&fDiscord: https://discord.gg/YpZNACup"
                    )
            );
        }
    }
}