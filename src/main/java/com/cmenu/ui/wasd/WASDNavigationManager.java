package com.cmenu.ui.wasd;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WASD导航管理器
 * 负责处理菜单中的WASD键盘导航和空格确认功能
 * 
 * 这是一个独立的模块，不修改CMP核心代码
 * 与原有光标控制系统并存，互不干扰
 */
public class WASDNavigationManager {

    private static WASDNavigationManager instance;
    private final JavaPlugin plugin;
    
    private WASDConfig config;
    private PacketListenerAbstract packetListener;
    private boolean registered = false;
    
    private WASDExpansion papiExpansion;
    
    private final Map<UUID, WASDSession> playerSessions = new ConcurrentHashMap<>();
    private final Map<UUID, Location[]> playerTextLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerSelectedIndex = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerSelectionCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> playerTextCommands = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerCurrentMenus = new ConcurrentHashMap<>();
    
    private static final long SELECTION_COOLDOWN_MS = 500;

    private WASDNavigationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = new WASDConfig(plugin);
    }

    public static synchronized WASDNavigationManager getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new WASDNavigationManager(plugin);
        }
        return instance;
    }

    public static WASDNavigationManager getInstance() {
        return instance;
    }

    /**
     * 初始化WASD导航模块
     */
    public void initialize() {
        if (registered) {
            return;
        }
        
        registerPacketListener();
        registerPAPIExpansion();
        registered = true;
        plugin.getLogger().info("[WASDNavigation] WASD导航模块已初始化");
    }

    /**
     * 注册PAPI扩展
     */
    private void registerPAPIExpansion() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                papiExpansion = new WASDExpansion(this);
                if (papiExpansion.register()) {
                    plugin.getLogger().info("[WASDNavigation] PAPI变量扩展已注册");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[WASDNavigation] 无法注册PAPI扩展: " + e.getMessage());
            }
        }
    }

    /**
     * 检查模块是否已注册
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * 注册数据包监听器
     */
    private void registerPacketListener() {
        packetListener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getUser() == null || event.getUser().getName() == null) {
                    return;
                }

                if (event.getPacketType() == PacketType.Play.Client.PLAYER_INPUT) {
                    String playerName = event.getUser().getName();
                    WrapperPlayClientPlayerInput inputPacket = new WrapperPlayClientPlayerInput(event);

                    boolean jump = inputPacket.isJump();
                    float sideways = 0.0f;
                    float forward = 0.0f;

                    if (inputPacket.isForward()) forward += 1.0f;
                    if (inputPacket.isBackward()) forward -= 1.0f;
                    if (inputPacket.isLeft()) sideways += 1.0f;
                    if (inputPacket.isRight()) sideways -= 1.0f;

                    float finalForward = forward;
                    float finalSideways = sideways;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player player = Bukkit.getPlayerExact(playerName);
                        if (player == null || !player.isOnline()) {
                            return;
                        }

                        UUID playerId = player.getUniqueId();
                        if (!isWASDEnabledForPlayer(playerId)) {
                            return;
                        }

                        handlePlayerInput(player, finalForward, finalSideways, jump);
                    });
                }
            }
        };

        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
    }

    /**
     * 处理玩家输入
     */
    private void handlePlayerInput(Player player, float forward, float sideways, boolean jump) {
        UUID playerId = player.getUniqueId();
        
        if (!playerSessions.containsKey(playerId)) {
            return;
        }

        if (jump) {
            executeSelectedCommand(player);
        }

        if (forward != 0 || sideways != 0) {
            handleNavigationInput(player, forward, sideways);
        }
    }

    /**
     * 处理导航输入
     */
    private void handleNavigationInput(Player player, float forward, float sideways) {
        UUID playerId = player.getUniqueId();
        
        if (isOnCooldown(playerId)) {
            return;
        }

        int currentIndex = playerSelectedIndex.getOrDefault(playerId, 0);
        int newIndex = -1;

        if (forward > 0) {
            newIndex = findPrevText(playerId, currentIndex);
        } else if (forward < 0) {
            newIndex = findNextText(playerId, currentIndex);
        } else if (sideways > 0) {
            newIndex = findLeftText(player, playerId, currentIndex);
        } else if (sideways < 0) {
            newIndex = findRightText(player, playerId, currentIndex);
        }

        if (newIndex != -1 && newIndex != currentIndex) {
            setSelectedIndex(player, newIndex);
            startCooldown(playerId);
        }
    }

    /**
     * 查找上方文本
     */
    private int findPrevText(UUID playerId, int currentIndex) {
        Location[] locations = playerTextLocations.get(playerId);
        if (locations == null || currentIndex < 0 || currentIndex >= locations.length) {
            return -1;
        }

        Location currentLoc = locations[currentIndex];
        if (currentLoc == null) return -1;

        double currentY = currentLoc.getY();
        double currentX = currentLoc.getX();
        double currentZ = currentLoc.getZ();

        int bestIndex = -1;
        double smallestYDiff = Double.MAX_VALUE;

        for (int i = 0; i < locations.length; i++) {
            if (i == currentIndex || locations[i] == null) continue;

            Location targetLoc = locations[i];
            double targetY = targetLoc.getY();
            double targetX = targetLoc.getX();
            double targetZ = targetLoc.getZ();

            if (Math.abs(targetX - currentX) > config.getHorizontalThreshold() ||
                Math.abs(targetZ - currentZ) > config.getHorizontalThreshold()) {
                continue;
            }

            double yDiff = targetY - currentY;

            if (yDiff > 0 && yDiff < smallestYDiff) {
                smallestYDiff = yDiff;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    /**
     * 查找下方文本
     */
    private int findNextText(UUID playerId, int currentIndex) {
        Location[] locations = playerTextLocations.get(playerId);
        if (locations == null || currentIndex < 0 || currentIndex >= locations.length) {
            return -1;
        }

        Location currentLoc = locations[currentIndex];
        if (currentLoc == null) return -1;

        double currentY = currentLoc.getY();
        double currentX = currentLoc.getX();
        double currentZ = currentLoc.getZ();

        int bestIndex = -1;
        double smallestYDiff = Double.MAX_VALUE;

        for (int i = 0; i < locations.length; i++) {
            if (i == currentIndex || locations[i] == null) continue;

            Location targetLoc = locations[i];
            double targetY = targetLoc.getY();
            double targetX = targetLoc.getX();
            double targetZ = targetLoc.getZ();

            if (Math.abs(targetX - currentX) > config.getHorizontalThreshold() ||
                Math.abs(targetZ - currentZ) > config.getHorizontalThreshold()) {
                continue;
            }

            double yDiff = currentY - targetY;

            if (yDiff > 0 && yDiff < smallestYDiff) {
                smallestYDiff = yDiff;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    /**
     * 查找左侧文本
     */
    private int findLeftText(Player player, UUID playerId, int currentIndex) {
        Location[] locations = playerTextLocations.get(playerId);
        if (locations == null || currentIndex < 0 || currentIndex >= locations.length) {
            return -1;
        }

        Location currentLoc = locations[currentIndex];
        if (currentLoc == null) return -1;

        org.bukkit.util.Vector playerDirection = player.getLocation().getDirection().setY(0).normalize();
        org.bukkit.util.Vector rightVector = playerDirection.clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize();
        org.bukkit.util.Vector leftVector = rightVector.clone().multiply(-1);

        int bestIndex = -1;
        double bestDotProduct = -1;

        for (int i = 0; i < locations.length; i++) {
            if (i == currentIndex || locations[i] == null) continue;

            Location targetLoc = locations[i];
            org.bukkit.util.Vector offset = targetLoc.toVector().subtract(currentLoc.toVector()).normalize();

            double dotProduct = offset.dot(leftVector);
            if (dotProduct > config.getDotProductThreshold() && dotProduct > bestDotProduct) {
                bestDotProduct = dotProduct;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    /**
     * 查找右侧文本
     */
    private int findRightText(Player player, UUID playerId, int currentIndex) {
        Location[] locations = playerTextLocations.get(playerId);
        if (locations == null || currentIndex < 0 || currentIndex >= locations.length) {
            return -1;
        }

        Location currentLoc = locations[currentIndex];
        if (currentLoc == null) return -1;

        org.bukkit.util.Vector playerDirection = player.getLocation().getDirection().setY(0).normalize();
        org.bukkit.util.Vector rightVector = playerDirection.clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize();

        int bestIndex = -1;
        double bestDotProduct = -1;

        for (int i = 0; i < locations.length; i++) {
            if (i == currentIndex || locations[i] == null) continue;

            Location targetLoc = locations[i];
            org.bukkit.util.Vector offset = targetLoc.toVector().subtract(currentLoc.toVector()).normalize();

            double dotProduct = offset.dot(rightVector);
            if (dotProduct > config.getDotProductThreshold() && dotProduct > bestDotProduct) {
                bestDotProduct = dotProduct;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    /**
     * 设置选中索引
     */
    private void setSelectedIndex(Player player, int index) {
        UUID playerId = player.getUniqueId();
        int oldIndex = playerSelectedIndex.getOrDefault(playerId, 0);
        playerSelectedIndex.put(playerId, index);

        WASDSession session = playerSessions.get(playerId);
        if (session != null) {
            session.onSelectionChanged(player, oldIndex, index);
        }

        // 更新PAPI变量中的位置
        if (papiExpansion != null) {
            Location[] locations = playerTextLocations.get(playerId);
            if (locations != null && index >= 0 && index < locations.length && locations[index] != null) {
                String menuKey = playerCurrentMenus.get(playerId);
                if (menuKey != null) {
                    papiExpansion.updatePlayerMenu(playerId, menuKey, locations[index]);
                }
            }
        }

        if (config.isDebugMode()) {
            plugin.getLogger().info("[WASDNavigation] 玩家 " + player.getName() + " 选中索引: " + index);
        }
    }

    /**
     * 执行选中的命令
     */
    private void executeSelectedCommand(Player player) {
        UUID playerId = player.getUniqueId();
        int selectedIndex = playerSelectedIndex.getOrDefault(playerId, -1);
        
        if (selectedIndex < 0) {
            return;
        }

        List<String> commands = playerTextCommands.get(playerId);
        if (commands == null || selectedIndex >= commands.size()) {
            return;
        }

        String command = commands.get(selectedIndex);
        if (command != null && !command.isEmpty()) {
            WASDSession session = playerSessions.get(playerId);
            if (session != null) {
                session.onExecuteCommand(player, command, selectedIndex);
            }
        }
    }

    /**
     * 检查是否在冷却中
     */
    private boolean isOnCooldown(UUID playerId) {
        return playerSelectionCooldown.getOrDefault(playerId, false);
    }

    /**
     * 开始冷却
     */
    private void startCooldown(UUID playerId) {
        playerSelectionCooldown.put(playerId, true);
        new BukkitRunnable() {
            @Override
            public void run() {
                playerSelectionCooldown.put(playerId, false);
            }
        }.runTaskLater(plugin, SELECTION_COOLDOWN_MS / 50);
    }

    /**
     * 检查玩家是否启用WASD导航
     */
    private boolean isWASDEnabledForPlayer(UUID playerId) {
        WASDSession session = playerSessions.get(playerId);
        return session != null && session.isEnabled();
    }

    /**
     * 获取玩家的WASD会话
     */
    public WASDSession getPlayerSession(UUID playerId) {
        return playerSessions.get(playerId);
    }

    /**
     * 获取玩家的当前菜单
     */
    public String getPlayerCurrentMenu(UUID playerId) {
        return playerCurrentMenus.get(playerId);
    }

    /**
     * 获取玩家的当前位置
     */
    public Location getPlayerCurrentLocation(UUID playerId) {
        String menuKey = playerCurrentMenus.get(playerId);
        if (menuKey == null) return null;
        
        int index = playerSelectedIndex.getOrDefault(playerId, -1);
        if (index < 0) return null;
        
        Location[] locations = playerTextLocations.get(playerId);
        if (locations == null || index >= locations.length) return null;
        
        return locations[index];
    }

    /**
     * 为玩家启动WASD导航会话
     */
    public void startSession(Player player, Location[] textLocations, List<String> commands, int initialIndex) {
        startSession(player, null, textLocations, commands, initialIndex);
    }

    /**
     * 为玩家启动WASD导航会话（带菜单名称）
     */
    public void startSession(Player player, String menuKey, Location[] textLocations, List<String> commands, int initialIndex) {
        UUID playerId = player.getUniqueId();
        
        WASDSession session = new WASDSession(plugin, player, true);
        playerSessions.put(playerId, session);
        playerTextLocations.put(playerId, textLocations);
        playerTextCommands.put(playerId, commands);
        playerSelectedIndex.put(playerId, initialIndex);
        playerSelectionCooldown.put(playerId, false);
        
        // 更新当前菜单信息
        if (menuKey != null) {
            playerCurrentMenus.put(playerId, menuKey);
            
            // 更新PAPI变量
            if (papiExpansion != null && textLocations != null && textLocations.length > 0) {
                papiExpansion.updatePlayerMenu(playerId, menuKey, textLocations[initialIndex >= 0 && initialIndex < textLocations.length ? initialIndex : 0]);
            }
        }

        if (config.isDebugMode()) {
            plugin.getLogger().info("[WASDNavigation] 为玩家 " + player.getName() + " 启动WASD导航会话，菜单: " + (menuKey != null ? menuKey : "未知"));
        }
    }

    /**
     * 更新玩家的文本位置
     */
    public void updateTextLocations(Player player, Location[] textLocations, List<String> commands) {
        UUID playerId = player.getUniqueId();
        playerTextLocations.put(playerId, textLocations);
        playerTextCommands.put(playerId, commands);
    }

    /**
     * 停止玩家的WASD导航会话
     */
    public void stopSession(Player player) {
        UUID playerId = player.getUniqueId();
        playerSessions.remove(playerId);
        playerTextLocations.remove(playerId);
        playerSelectedIndex.remove(playerId);
        playerSelectionCooldown.remove(playerId);
        playerTextCommands.remove(playerId);
        playerCurrentMenus.remove(playerId);
        
        // 清除PAPI变量
        if (papiExpansion != null) {
            papiExpansion.clearPlayerMenu(playerId);
        }

        if (config.isDebugMode()) {
            plugin.getLogger().info("[WASDNavigation] 停止玩家 " + player.getName() + " 的WASD导航会话");
        }
    }

    /**
     * 获取玩家当前选中的索引
     */
    public int getSelectedIndex(Player player) {
        return playerSelectedIndex.getOrDefault(player.getUniqueId(), -1);
    }

    /**
     * 获取配置
     */
    public WASDConfig getConfig() {
        return config;
    }

    /**
     * 重载配置
     */
    public void reloadConfig() {
        config.reload();
    }

    /**
     * 关闭模块
     */
    public void shutdown() {
        if (packetListener != null && registered) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }
        
        // 注销PAPI扩展
        if (papiExpansion != null) {
            try {
                papiExpansion.unregister();
            } catch (Exception e) {
                // 忽略错误
            }
        }
        
        playerSessions.clear();
        playerTextLocations.clear();
        playerSelectedIndex.clear();
        playerSelectionCooldown.clear();
        playerTextCommands.clear();
        playerCurrentMenus.clear();
        
        registered = false;
    }
}
