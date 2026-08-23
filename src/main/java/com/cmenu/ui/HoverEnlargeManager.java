package com.cmenu.ui;

import com.cmenu.ui.CursorMenuPlugin;
import com.cmenu.ui.TextDisplayHitBox;
import com.cmenu.ui.layout.MenuLayout;
import com.cmenu.ui.section.Section;
import com.cmenu.ui.util.HoverSoundManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 悬停交互管理器
 * 负责处理文本显示的悬停放大效果和悬停音效
 */
public class HoverEnlargeManager {
    private final CursorMenuPlugin plugin;
    
    // 使用线程安全的ConcurrentHashMap
    private final Map<UUID, TextDisplay> enlargedTextDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, Vector3f> originalScales = new ConcurrentHashMap<>();
    
    // 悬停音效管理器
    private final HoverSoundManager hoverSoundManager;
    
    // 记录每个玩家当前悬停的TextDisplay（用于音效冷却）
    private final Map<UUID, TextDisplay> currentHovered = new ConcurrentHashMap<>();
    
    private BukkitRunnable hoverDetectionTask;

    public HoverEnlargeManager(CursorMenuPlugin plugin) {
        this.plugin = plugin;
        this.hoverSoundManager = new HoverSoundManager(plugin);
        startHoverDetection();
    }

    /**
     * 获取悬停音效管理器
     */
    public HoverSoundManager getHoverSoundManager() {
        return hoverSoundManager;
    }

    private void startHoverDetection() {
        hoverDetectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!plugin.playerCursors.containsKey(player)) continue;

                    Location cursorLoc = plugin.cursorExactLocations.get(player);
                    if (cursorLoc == null) continue;

                    String menuKey = plugin.getCurrentPlayerMenu(player);
                    if (menuKey == null) continue;

                    Section section = plugin.sectionManager.get(menuKey);
                    if (section == null) continue;

                    TextDisplay closestDisplay = null;
                    MenuLayout closestLayout = null;
                    double minDistance = Double.MAX_VALUE;

                    // 查找最近的文本显示
                    for (MenuLayout layout : section.layouts.values()) {
                        // 检查是否启用悬停放大或悬停音效
                        if (!layout.isHoverEnlargeEnabled() && layout.getHoverSound() == null) continue;

                        // 使用CursorMenuPlugin的方法获取对应的TextDisplay
                        TextDisplay display = plugin.getTextDisplayByKeys(player, menuKey, layout.key);

                        if (display != null && display.isValid() && TextDisplayHitBox.isInside(display, cursorLoc)) {
                            double distance = display.getLocation().distanceSquared(cursorLoc);
                            if (distance < minDistance) {
                                minDistance = distance;
                                closestDisplay = display;
                                closestLayout = layout;
                            }
                        }
                    }

                    // 处理悬停放大效果和悬停音效
                    UUID playerId = player.getUniqueId();
                    TextDisplay currentlyEnlarged = enlargedTextDisplays.get(playerId);
                    TextDisplay previouslyHovered = currentHovered.get(playerId);

                    if (closestDisplay != null && closestLayout != null) {
                        // 播放悬停音效（如果有配置）
                        String soundName = closestLayout.getHoverSound();
                        if (soundName != null && !soundName.isEmpty()) {
                            hoverSoundManager.tryPlayHoverSound(
                                player, 
                                closestDisplay, 
                                soundName,
                                closestLayout.getHoverSoundVolume(),
                                closestLayout.getHoverSoundPitch(),
                                closestLayout.getHoverSoundCooldown()
                            );
                        }

                        // 如果有新的最近显示项，放大它
                        if (currentlyEnlarged != closestDisplay && closestLayout.isHoverEnlargeEnabled()) {
                            // 重置之前放大的显示项
                            if (currentlyEnlarged != null) {
                                resetTextDisplaySize(currentlyEnlarged);
                                enlargedTextDisplays.remove(playerId);
                            }

                            // 放大新的显示项
                            enlargeTextDisplay(closestDisplay, closestLayout);
                            enlargedTextDisplays.put(playerId, closestDisplay);
                        }
                        
                        // 更新当前悬停状态
                        currentHovered.put(playerId, closestDisplay);
                        
                    } else if (currentlyEnlarged != null || previouslyHovered != null) {
                        // 如果没有最近的显示项，但有之前放大的项，则重置它
                        if (currentlyEnlarged != null) {
                            resetTextDisplaySize(currentlyEnlarged);
                            enlargedTextDisplays.remove(playerId);
                        }
                        
                        // 清除悬停状态
                        currentHovered.remove(playerId);
                    }
                }
            }
        };
        hoverDetectionTask.runTaskTimer(plugin, 0L, 2L); // 每2tick检查一次
    }

    /**
     * 放大文本显示
     */
    private void enlargeTextDisplay(TextDisplay textDisplay, MenuLayout layout) {
        // 首先确保重置任何现有的变换
        resetTextDisplaySize(textDisplay);

        Transformation oldTrans = textDisplay.getTransformation();
        Vector3f currentScale = oldTrans.getScale();

        // 保存原始尺寸（仅当尚未保存时）
        UUID displayId = textDisplay.getUniqueId();
        if (!originalScales.containsKey(displayId)) {
            originalScales.put(displayId, new Vector3f(currentScale));
        }

        // 获取放大倍数
        if (layout != null && layout.isHoverEnlargeEnabled()) {
            float scaleValue = (float) layout.getHoverEnlargeScale();
            Transformation newTrans = new Transformation(
                oldTrans.getTranslation(),
                oldTrans.getLeftRotation(),
                new Vector3f(currentScale.x * scaleValue, currentScale.y * scaleValue, currentScale.z * scaleValue),
                oldTrans.getRightRotation()
            );
            textDisplay.setTransformation(newTrans);
        }
    }

    /**
     * 重置文本显示大小
     */
    private void resetTextDisplaySize(TextDisplay textDisplay) {
        if (textDisplay == null || !textDisplay.isValid()) return;
        
        UUID displayId = textDisplay.getUniqueId();
        Vector3f originalScale = originalScales.remove(displayId);
        if (originalScale != null) {
            Transformation oldTrans = textDisplay.getTransformation();
            Transformation newTrans = new Transformation(
                oldTrans.getTranslation(),
                oldTrans.getLeftRotation(),
                originalScale,
                oldTrans.getRightRotation()
            );
            textDisplay.setTransformation(newTrans);
        }
    }

    public void cleanupPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        
        TextDisplay display = enlargedTextDisplays.remove(playerId);
        if (display != null) {
            resetTextDisplaySize(display);
        }
        
        currentHovered.remove(playerId);
        hoverSoundManager.cleanupPlayer(playerId);
    }

    public void cleanup() {
        // 停止检测任务
        if (hoverDetectionTask != null) {
            hoverDetectionTask.cancel();
            hoverDetectionTask = null;
        }

        // 重置所有放大的文本显示
        for (TextDisplay display : enlargedTextDisplays.values()) {
            resetTextDisplaySize(display);
        }
        enlargedTextDisplays.clear();
        originalScales.clear();
        currentHovered.clear();
        
        // 清理悬停音效数据
        hoverSoundManager.cleanupAll();
    }
}
