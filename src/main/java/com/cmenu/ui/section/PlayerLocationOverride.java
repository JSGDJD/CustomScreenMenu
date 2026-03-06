package com.cmenu.ui.section;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlayerLocationOverride {

    private static boolean enabled = false;

    private PlayerLocationOverride() {}

    /**
     * 动态读取 config.yml 里的 use-player-location
     * 由 CursorMenuPlugin.loadConfig() 每 reload 一次就调用一次
     */
    public static void reload(boolean cfgValue) {
        enabled = cfgValue;
    }

    /**
     * 如果需要覆盖，就把 section 的相机坐标替换为玩家当前位置+朝向
     * 返回 true 代表已覆盖；false 代表保持原值
     */
    public static boolean apply(Player player, Section section) {
        if (!enabled) {
            return false;           // 保持原设定
        }
        Location loc = player.getLocation();
        section.world      = loc.getWorld().getName();
        section.cameraX    = loc.getX();
        section.cameraY    = loc.getY();
        section.cameraZ    = loc.getZ();
        section.yaw        = loc.getYaw();
        section.pitch      = loc.getPitch();
        return true;
    }
}