package com.cmenu.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;

public class TeleportListener implements Listener {

    private final CursorMenuPlugin plugin;

    public TeleportListener(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // 如果玩家当前在菜单中
        if (plugin.playerCursors.containsKey(player)) {
            // 清理菜单（不传送回原位）
            plugin.stopCursor(player, false);
        }
    }
}