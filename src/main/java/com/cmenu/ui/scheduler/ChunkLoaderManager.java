package com.cmenu.ui.scheduler;

import com.cmenu.ui.CursorMenuPlugin;
import com.cmenu.ui.layout.MenuLayout;
import com.cmenu.ui.section.Section;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区块加载管理器
 * 负责管理菜单相关的区块加载和卸载
 */
public class ChunkLoaderManager {

    private final CursorMenuPlugin plugin;
    private BukkitTask chunkLoaderTask;

    // 存储需要持续加载的区块 (世界名 -> 区块坐标集合)
    private final Map<String, Set<Long>> forcedLoadedChunks = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> persistentChunks = new ConcurrentHashMap<>();

    // 区块加载任务的ID，用于取消任务
    private boolean isTaskRunning = false;

    public ChunkLoaderManager(CursorMenuPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动区块加载任务
     */
    public void startChunkLoaderTask() {
        // 停止旧任务
        stopChunkLoaderTask();

        // 初始化强制加载
        updatePersistentChunks();

        // 每30秒验证一次区块状态（防止意外卸载）
        chunkLoaderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updatePersistentChunks, 0L, 600L); // 30秒一次
        isTaskRunning = true;

        if (plugin.getConfig().getBoolean("Debug", false)) {
            plugin.getLogger().info("区块加载任务已启动");
        }
    }

    /**
     * 停止区块加载任务
     */
    public void stopChunkLoaderTask() {
        // 取消任务
        if (chunkLoaderTask != null) {
            chunkLoaderTask.cancel();
            chunkLoaderTask = null;
        }

        // 解除所有强制加载的区块
        for (Map.Entry<String, Set<Long>> entry : forcedLoadedChunks.entrySet()) {
            String worldName = entry.getKey();
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            for (long chunkKey : entry.getValue()) {
                int x = (int) (chunkKey >> 32);
                int z = (int) (chunkKey & 0xFFFFFFFFL);

                Chunk chunk = world.getChunkAt(x, z);
                if (chunk != null) {
                    chunk.setForceLoaded(false);
                }
            }
        }
        forcedLoadedChunks.clear();
        persistentChunks.clear();

        isTaskRunning = false;

        if (plugin.getConfig().getBoolean("Debug", false)) {
            plugin.getLogger().info("区块加载任务已停止");
        }
    }

    /**
     * 更新需要持续加载的区块列表
     */
    public void updatePersistentChunks() {
        // 1. 先记录当前所有强制加载的区块（用于后续清理）
        Map<String, Set<Long>> oldChunks = new HashMap<>();
        for (Map.Entry<String, Set<Long>> entry : forcedLoadedChunks.entrySet()) {
            oldChunks.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        // 2. 计算新的需要加载的区块（完全新建，不修改旧集合）
        Map<String, Set<Long>> newChunks = new HashMap<>();

        // 获取所有菜单部分
        Map<String, Section> allSections = plugin.sectionManager.getAll();
        for (Section section : allSections.values()) {
            World world = Bukkit.getWorld(section.world);
            if (world == null) continue;

            // 计算菜单相机位置所在的区块
            int chunkX = (int) Math.floor(section.cameraX / 16);
            int chunkZ = (int) Math.floor(section.cameraZ / 16);
            long chunkKey = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            newChunks.computeIfAbsent(section.world, k -> new HashSet<>()).add(chunkKey);

            // 处理菜单内其他元素的区块
            if (section.layouts != null) {
                for (MenuLayout layout : section.layouts.values()) {
                    int layoutChunkX = (int) Math.floor((section.cameraX + layout.x) / 16);
                    int layoutChunkZ = (int) Math.floor((section.cameraZ + layout.z) / 16);
                    long layoutChunkKey = ((long) layoutChunkX << 32) | (layoutChunkZ & 0xFFFFFFFFL);
                    newChunks.get(section.world).add(layoutChunkKey);
                }
            }
        }

        // 3. 对新增的区块：标记为强制加载
        for (Map.Entry<String, Set<Long>> entry : newChunks.entrySet()) {
            String worldName = entry.getKey();
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Set<Long> chunkKeys = entry.getValue();
            for (long chunkKey : chunkKeys) {
                int x = (int) (chunkKey >> 32);
                int z = (int) (chunkKey & 0xFFFFFFFFL);

                // 兼容所有版本的加载逻辑
                if (!world.isChunkLoaded(x, z)) {
                    world.loadChunk(x, z, true); // 加载区块
                    Chunk chunk = world.getChunkAt(x, z);
                    if (chunk != null) {
                        chunk.setForceLoaded(true); // 标记强制加载
                    }
                } else {
                    Chunk chunk = world.getChunkAt(x, z);
                    chunk.setForceLoaded(true);
                }

                // 记录到强制加载列表
                forcedLoadedChunks.computeIfAbsent(worldName, k -> new HashSet<>()).add(chunkKey);
            }
        }

        // 4. 单独处理需要移除的区块（与遍历新集合分离）
        for (Map.Entry<String, Set<Long>> entry : oldChunks.entrySet()) {
            String worldName = entry.getKey();
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            Set<Long> oldChunkKeys = entry.getValue();
            // 过滤出不在新集合中的区块（需要移除）
            for (long chunkKey : oldChunkKeys) {
                Set<Long> newChunkKeys = newChunks.getOrDefault(worldName, Collections.emptySet());
                if (!newChunkKeys.contains(chunkKey)) {
                    // 解除强制加载
                    int x = (int) (chunkKey >> 32);
                    int z = (int) (chunkKey & 0xFFFFFFFFL);
                    Chunk chunk = world.getChunkAt(x, z);
                    if (chunk != null) {
                        chunk.setForceLoaded(false);
                    }
                    // 从强制加载列表中移除
                    forcedLoadedChunks.getOrDefault(worldName, new HashSet<>()).remove(chunkKey);
                }
            }
        }

        // 清理空的世界条目
        forcedLoadedChunks.entrySet().removeIf(e -> e.getValue().isEmpty());

        if (plugin.getConfig().getBoolean("Debug", false)) {
            plugin.getLogger().info("已更新强制加载区块：共 " + forcedLoadedChunks.size() + " 个世界，" +
                    forcedLoadedChunks.values().stream().mapToInt(Set::size).sum() + " 个区块");
        }
    }

    /**
     * 获取当前强制加载的区块数量
     */
    public int getLoadedChunkCount() {
        return forcedLoadedChunks.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * 获取当前加载的世界数量
     */
    public int getLoadedWorldCount() {
        return forcedLoadedChunks.size();
    }

    /**
     * 检查任务是否正在运行
     */
    public boolean isTaskRunning() {
        return isTaskRunning;
    }
}
