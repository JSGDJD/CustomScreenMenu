package com.cmenu.ui.util;

import com.cmenu.ui.CursorMenuPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 悬停音效管理器
 * 实现鼠标悬停在TextDisplay上时播放特定音效
 */
public class HoverSoundManager {

    // 存储每个玩家对每个TextDisplay的最后悬停时间（用于冷却）
    private final Map<UUID, Map<UUID, Long>> lastPlayTimes = new ConcurrentHashMap<>();
    
    // 备选音效列表（当配置的音效不可用时使用）
    private static final String[] FALLBACK_SOUNDS = {
        "block.note.pling",
        "block.note.harp",
        "ui.button.click",
        "entity.experience_orb.pickup"
    };

    private final CursorMenuPlugin plugin;

    public HoverSoundManager(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 检查并播放悬停音效
     * 
     * @param player 玩家
     * @param display TextDisplay实体
     * @param soundName 音效名称
     * @param volume 音量 (0.0 - 1.0)
     * @param pitch 音调 (0.5 - 2.0)
     * @param cooldownTicks 冷却时间(ticks)
     */
    public void tryPlayHoverSound(Player player, TextDisplay display, 
                                   String soundName, float volume, float pitch, long cooldownTicks) {
        if (player == null || !player.isOnline() || display == null || !display.isValid()) {
            return;
        }
        
        if (soundName == null || soundName.isEmpty()) {
            return;
        }

        UUID playerId = player.getUniqueId();
        UUID displayId = display.getUniqueId();
        
        // 检查冷却时间
        Map<UUID, Long> playerTimes = lastPlayTimes.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
        long lastPlayTime = playerTimes.getOrDefault(displayId, 0L);
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastPlayTime < cooldownTicks * 50L) {  // 转换为毫秒
            return;  // 还在冷却中，不播放
        }

        // 尝试播放音效
        boolean success = playSound(player, display.getLocation(), soundName, volume, pitch);
        
        // 如果失败，尝试备选音效
        if (!success) {
            for (String fallback : FALLBACK_SOUNDS) {
                success = playSound(player, display.getLocation(), fallback, volume, pitch);
                if (success) break;
            }
            
            if (!success) {
                plugin.getLogger().warning("无法播放悬停音效: " + soundName + " (所有备选音效也失败)");
            }
        }

        // 更新最后播放时间
        if (success) {
            playerTimes.put(displayId, currentTime);
        }
    }

    /**
     * 播放指定音效
     * 
     * @return 是否成功播放
     */
    private boolean playSound(Player player, Location location, String soundName, float volume, float pitch) {
        try {
            Sound sound;
            try {
                sound = Sound.valueOf(soundName.toUpperCase());
            } catch (IllegalArgumentException e) {
                // 音效名称无效，使用字符串方式播放
                player.playSound(location, soundName, volume, pitch);
                return true;
            }
            
            player.playSound(location, sound, volume, pitch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 清理指定玩家的数据
     */
    public void cleanupPlayer(UUID playerId) {
        lastPlayTimes.remove(playerId);
    }

    /**
     * 清理所有数据
     */
    public void cleanupAll() {
        lastPlayTimes.clear();
    }

    /**
     * 获取指定玩家的悬停音效冷却状态
     */
    public long getRemainingCooldown(Player player, TextDisplay display) {
        if (player == null || display == null) return 0L;
        
        UUID playerId = player.getUniqueId();
        UUID displayId = display.getUniqueId();
        
        Map<UUID, Long> playerTimes = lastPlayTimes.get(playerId);
        if (playerTimes == null) return 0L;
        
        Long lastPlayTime = playerTimes.get(displayId);
        if (lastPlayTime == null) return 0L;
        
        return Math.max(0L, System.currentTimeMillis() - lastPlayTime);
    }

    /**
     * 重置指定玩家的所有冷却时间
     */
    public void resetCooldowns(Player player) {
        if (player == null) return;
        lastPlayTimes.remove(player.getUniqueId());
    }
}
