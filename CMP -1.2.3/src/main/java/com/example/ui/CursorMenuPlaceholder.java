package com.example.ui;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import com.example.ui.CursorMenuPlugin;
import com.example.ui.layout.MenuLayout;
import com.example.ui.section.Section;
import org.bukkit.util.Vector;

public class CursorMenuPlaceholder extends PlaceholderExpansion {

    private final CursorMenuPlugin plugin;

    public CursorMenuPlaceholder(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "cursormenu";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (!(offlinePlayer instanceof Player player)) {
            return "";
        }

        // 1. 检测当前使用的摄像机菜单
        if (params.equals("current_menu")) {
            return getCurrentMenu(player);
        }

        // 2. 检测当前点击的菜单选项
        if (params.equals("selected_option")) {
            return getSelectedOption(player);
        }

        // 3. 检测当前显示的物品ID
        if (params.equals("display_item_id")) {
            return getDisplayItemId(player);
        }

        // 4. 检测当前菜单所处世界
        if (params.equals("menu_world")) {
            return getMenuWorld(player);
        }

        // 5. 检测当前菜单文字按钮坐标 (x/y/z)
        if (params.startsWith("button_")) {
            String coord = params.split("_")[1];
            return getButtonCoordinate(player, coord);
        }

        // 6. 检测当前点击的菜单项 key
        if (params.equals("clicked_option")) {
            return getClickedOption(player);
        }
        // 7. 检测当前玩家是否处于攻击或破坏方块
        if (params.equals("is_attacking_or_breaking")) {
            return isAttackingOrBreaking(player) ? "true" : "false";
        }
        // 8. 检测当前玩家移动位置选择
        if (params.equals("hovered_option")) {
            return getHoveredOption(player);
        }

        return null;
    }

    private boolean isAttackingOrBreaking(Player player) {
        return player.hasMetadata("cursor_is_attacking_or_breaking");
    }

    private String getCurrentMenu(Player player) {
        return plugin.getCurrentPlayerMenu(player);
    }

    private String getSelectedOption(Player player) {
        MenuLayout selected = plugin.getSelectedLayout(player);
        return selected != null ? selected.name : "";
    }

    private String getDisplayItemId(Player player) {
        return plugin.getItemDisplayManager().getPlayerActiveItemId(player);
    }

    private String getMenuWorld(Player player) {
        String menuKey = plugin.getCurrentPlayerMenu(player);
        if (menuKey == null) return "";

        Section section = CursorMenuPlugin.sectionManager.get(menuKey);
        return section != null ? section.world : "";
    }

    private String getClickedOption(Player player) {
        MenuLayout selected = plugin.getSelectedLayout(player);
        return selected != null ? selected.key : "";
    }

    private String getButtonCoordinate(Player player, String coord) {
        MenuLayout selected = plugin.getSelectedLayout(player);
        if (selected == null) return "";

        switch (coord) {
            case "x": return String.valueOf(selected.x);
            case "y": return String.valueOf(selected.y);
            case "z": return String.valueOf(selected.z);
            default: return "";
        }
    }
    private String getHoveredOption(Player player) {
        String menuKey = plugin.getCurrentPlayerMenu(player);
        if (menuKey == null) return "";

        Section section = CursorMenuPlugin.sectionManager.get(menuKey);
        if (section == null) return "";

        ArmorStand cursor = CursorMenuPlugin.playerCursors.get(player);
        Pig camera = CursorMenuPlugin.playerSit.get(player);
        if (cursor == null || camera == null) return "";

        Location cursorLoc = cursor.getLocation();
        Vector cursorVec = cursorLoc.toVector();

        Location cameraLoc = camera.getLocation();
        Vector dir = cameraLoc.getDirection().normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Vector up = dir.getCrossProduct(right).multiply(-1);

        for (MenuLayout layout : section.layouts.values()) {
            Vector offset = dir.clone().multiply(layout.z)
                    .add(right.multiply(layout.x))
                    .add(up.multiply(layout.y));
            Vector layoutVec = cameraLoc.clone().add(offset).toVector();

            if (cursorVec.distance(layoutVec) < 0.8) {
                return layout.key;
            }
        }

        return "";
    }
}