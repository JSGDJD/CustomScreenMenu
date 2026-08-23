package com.cmenu.ui.util;

import com.cmenu.ui.CursorMenuPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 打字机效果管理器
 * 实现文字逐字显示的动态效果
 */
public class TypewriterEffect {

    private final Map<UUID, Integer> typewriterTasks = new ConcurrentHashMap<>();
    private final Map<UUID, String> fullTexts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentIndexes = new ConcurrentHashMap<>();

    private final CursorMenuPlugin plugin;

    public TypewriterEffect(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 对TextDisplay应用打字机效果
     */
    public void applyEffect(TextDisplay display, String fullText, int speed, int startDelay, String cursorChar) {
        if (display == null || !display.isValid() || fullText == null || fullText.isEmpty()) {
            return;
        }

        UUID displayId = display.getUniqueId();
        
        // 先取消之前的效果
        cancelEffectInternal(displayId);
        
        display.setText(cursorChar);
        fullTexts.put(displayId, fullText);
        currentIndexes.put(displayId, 0);

        BukkitRunnable task = new BukkitRunnable() {
            private boolean cursorVisible = true;
            private long tick = 0;
            private boolean completed = false;

            @Override
            public void run() {
                if (completed || !display.isValid()) {
                    cancel();
                    return;
                }

                tick++;
                
                // 处理开始延迟
                if (tick <= startDelay) {
                    if (tick % 10 == 0) {
                        cursorVisible = !cursorVisible;
                        display.setText(cursorVisible ? cursorChar : "");
                    }
                    return;
                }

                int currentIdx = currentIndexes.getOrDefault(displayId, 0);
                String text = fullTexts.get(displayId);
                
                if (text == null || currentIdx >= text.length()) {
                    // 打字完成
                    completed = true;
                    if (text != null) {
                        display.setText(text);
                    }
                    cancel();
                    return;
                }

                // 每隔speed ticks增加一个字符
                if ((tick - startDelay) % speed == 0 && currentIdx < text.length()) {
                    currentIdx++;
                    currentIndexes.put(displayId, currentIdx);
                }
                
                // 构建显示文本（复用字符串操作）
                String displayedText = text.substring(0, Math.min(currentIdx, text.length()));
                
                // 光标闪烁
                cursorVisible = !cursorVisible;
                if (currentIdx < text.length() || cursorVisible) {
                    display.setText(displayedText + cursorChar);
                } else {
                    display.setText(displayedText);
                }
            }

            @Override
            public synchronized void cancel() {
                if (!completed) {
                    completed = true;
                    super.cancel();
                }
            }
        };

        int taskId = task.runTaskTimer(plugin, 1L, 1L).getTaskId();
        typewriterTasks.put(displayId, taskId);
    }

    /**
     * 立即完成打字效果，显示完整文本
     */
    public void completeEffect(TextDisplay display) {
        if (display == null || !display.isValid()) return;
        
        UUID displayId = display.getUniqueId();
        String fullText = fullTexts.get(displayId);
        
        cancelEffect(display);
        
        if (fullText != null) {
            display.setText(fullText);
        }
    }

    /**
     * 取消指定TextDisplay的打字机效果
     */
    public void cancelEffect(TextDisplay display) {
        if (display == null) return;
        cancelEffectInternal(display.getUniqueId());
    }
    
    /**
     * 内部取消方法，避免重复调用
     */
    private void cancelEffectInternal(UUID displayId) {
        Integer taskId = typewriterTasks.remove(displayId);
        
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        
        fullTexts.remove(displayId);
        currentIndexes.remove(displayId);
    }

    /**
     * 清理所有打字机任务
     */
    public void cleanupAll() {
        for (Integer taskId : typewriterTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        typewriterTasks.clear();
        fullTexts.clear();
        currentIndexes.clear();
    }

    /**
     * 检查指定TextDisplay是否正在进行打字机效果
     */
    public boolean isRunning(TextDisplay display) {
        if (display == null) return false;
        return typewriterTasks.containsKey(display.getUniqueId());
    }
}
