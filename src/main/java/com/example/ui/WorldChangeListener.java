package com.example.ui;

import com.example.ui.section.Section;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.entity.Player;

public class WorldChangeListener implements Listener {

    private final CursorMenuPlugin plugin;

    public WorldChangeListener(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String currentMenu = plugin.getCurrentPlayerMenu(player);

        if (currentMenu == null) return;

        Section section = CursorMenuPlugin.sectionManager.get(currentMenu);
        if (section == null) return;

        World world = Bukkit.getWorld(section.world);
        if (world == null || !player.getWorld().equals(world)) return;

        // 强制传送到配置中的摄像机位置
        Location cameraLoc = new Location(world, section.cameraX, section.cameraY, section.cameraZ, section.yaw, section.pitch);
        player.teleport(cameraLoc);

        // 重新初始化光标系统
        plugin.stopCursor(player, false); // 不清理原始位置
        plugin.setupCursor(player, currentMenu);
    }
}