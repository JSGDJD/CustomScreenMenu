package com.cmenu.ui;

import com.cmenu.ui.section.SectionManager;
import com.cmenu.ui.section.Section;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class CreatureSpawnListener implements Listener {

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // 检查是否启用了生物生成限制
        if (!CursorMenuPlugin.creatureSpawnLimitEnabled) {
            return;
        }

        // 忽略玩家生成的生物（如通过刷怪蛋生成）
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }

        Location spawnLocation = event.getLocation();
        SectionManager sectionManager = CursorMenuPlugin.sectionManager;

        // 检查是否在任何菜单的禁止生成范围内
        // 使用getAll()方法获取所有区域，并直接访问cameraX、cameraY、cameraZ字段
        for (Section section : sectionManager.getAll().values()) {
            // 直接访问Section类的公共字段cameraX、cameraY、cameraZ
            Location menuLocation = new Location(
                    spawnLocation.getWorld(),
                    section.cameraX,  // 替代 section.getX()
                    section.cameraY,  // 替代 section.getY()
                    section.cameraZ   // 替代 section.getZ()
            );

            // 验证世界是否一致，避免跨世界比较
            if (!spawnLocation.getWorld().getName().equals(section.world)) {
                continue;
            }

            // 如果生物生成位置在菜单的限制范围内，则取消生成
            if (menuLocation.distance(spawnLocation) <= CursorMenuPlugin.creatureSpawnLimitRadius) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
