package com.cmenu.ui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.cmenu.ui.layout.MenuLayout;
import com.cmenu.ui.section.PlayerLocationOverride;
import com.cmenu.ui.section.Section;
import com.cmenu.ui.section.SectionManager;
import com.cmenu.ui.npc.NPCModule;
import com.cmenu.ui.wasd.WASDModule;
import com.cmenu.ui.util.CommandUtils;
import com.cmenu.ui.scheduler.ChunkLoaderManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bstats.bukkit.Metrics;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import space.arim.morepaperlib.MorePaperLib;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CursorMenuPlugin extends JavaPlugin {
    public static boolean creatureSpawnLimitEnabled;
    public static int creatureSpawnLimitRadius;
    public static List<String> joinRunCommands = new ArrayList<>();
    public static boolean cameraBlockCheckEnabled;
    public static int cameraBlockCheckRadius;

    public static float exitYaw;
    public static float exitPitch;
    public static List<String> allowedCommands = new ArrayList<>();
    public static TextDisplayManager textDisplayManager;
    public static double cursorZOffset;
    public static double cursorX;

    public static double cursorY;
    public static ItemDisplayManager itemDisplayManager;

    public static CursorMenuPlugin plugin;
    // 将 ProtocolManager 替换为 PacketEvents 相关的字段
    // private ProtocolManager protocolManager;
    public static Map<Player, ArmorStand> playerCursors = new ConcurrentHashMap<>();
    private final Map<Player, List<TextDisplay>> playerDisplays = new ConcurrentHashMap<>();
    
    /**
     * 获取指定玩家的TextDisplay列表
     */
    public List<TextDisplay> getPlayerTextDisplays(Player player) {
        List<TextDisplay> displays = playerDisplays.get(player);
        return displays != null ? displays : Collections.emptyList();
    }
    
    /**
     * 根据菜单key和布局key获取对应的TextDisplay
     */
    public TextDisplay getTextDisplayByKeys(Player player, String menuKey, String layoutKey) {
        List<TextDisplay> displays = playerDisplays.get(player);
        if (displays == null) return null;
        
        String targetName = menuKey + ":" + layoutKey;
        for (TextDisplay display : displays) {
            String customName = display.getCustomName();
            if (customName != null && customName.equals(targetName)) {
                return display;
            }
        }
        return null;
    }
    private final Map<Player, ItemDisplay> playerItemDisplays = new ConcurrentHashMap<>();
    private final Map<Player, ItemDisplay> playerPumpkinDisplays = new ConcurrentHashMap<>();
    private final Map<Player, List<Integer>> playerMenuTextAnimationTasks = new ConcurrentHashMap<>();
    public static Map<Player, Location> playerLocations = new ConcurrentHashMap<>();
    public static Map<Player, Pig> playerSit = new ConcurrentHashMap<>();

    public static Map<Player, Location> cursorExactLocations = new ConcurrentHashMap<>();
    public static Set<String> playingSound = ConcurrentHashMap.newKeySet();
    private boolean debugMode;
    public static MorePaperLib foliaLib;
    public static SectionManager sectionManager = new SectionManager();
    public static boolean soundLoop;
    public static int soundRate;
    public static String soundName;
    public static float soundVolume;
    public static float soundPitch;
    public static boolean joinRunBool;
    public static String joinRunSection;
    public static String cursorItem;
    public static int cursorModelData;
    public static double maxX;
    public static double maxY;
    public static double cursorScale;
    public static double cursorSensitivity;
    public static double cursorSmoothing;
    public static int runDelay;
    public static boolean hasPAPI;
    public static boolean usePumpkinOverlay;

    // 添加光标移动范围限制的静态变量
    public static boolean cursorMovementRangeEnabled;
    public static double cursorMovementRangeXMin;
    public static double cursorMovementRangeXMax;
    public static double cursorMovementRangeYMin;
    public static double cursorMovementRangeYMax;

    // 添加光标默认位置的静态变量
    public static boolean cursorDefaultPositionEnabled;
    public static double cursorDefaultPositionX;
    public static double cursorDefaultPositionY;

    // 悬停放大管理器
    private HoverEnlargeManager hoverEnlargeManager;

    // 区块加载管理器
    private ChunkLoaderManager chunkLoaderManager;

    private Map<Player, String> currentPlayerMenus = new ConcurrentHashMap<>();
    public Map<Player, MenuLayout> selectedLayouts = new ConcurrentHashMap<>();

    // 用户数据管理器
    private UserDataManager userDataManager;

    // 数据库管理器
    private DatabaseManager databaseManager;
    
    // 管理员IP验证器
    private AdminIpValidator adminIpValidator;

    // 存储用户当前正在输入的字段
    private Map<UUID, String> currentPlayerInputFields = new ConcurrentHashMap<>();

    // 存储用户已经输入的数据
    private Map<UUID, Map<String, String>> userInputData = new ConcurrentHashMap<>();

    // 存储密码显示状态 (true表示显示明文，false表示显示星号)
    private Map<UUID, Boolean> passwordVisibility = new ConcurrentHashMap<>();
    
    // 存储已登录玩家的集合
    private Set<UUID> loggedInPlayers = ConcurrentHashMap.newKeySet();
    
    // IP绑定配置
    private static boolean ipBindingEnabled = true;
    private static boolean ipBindingStrict = false;
    private static boolean allowSameIpLogin = true;
    private static int lockoutDuration = 5;
    private static int maxLoginAttempts = 5;
    
    // IP白名单配置
    private static boolean ipWhitelistEnabled = false;
    private static List<String> ipWhitelist = new ArrayList<>();
    
    // IP黑名单配置
    private static boolean ipBlacklistEnabled = false;
    private static List<String> ipBlacklist = new ArrayList<>();
    // 按钮访问控制配置
    private List<String> loginRequiredNameTags = new ArrayList<>();
    private List<String> loginRequiredKeyTags = new ArrayList<>();
    private List<String> noDuplicateRegNameTags = new ArrayList<>();
    private List<String> noDuplicateRegKeyTags = new ArrayList<>();

    // 获取玩家是否已登录
    public boolean isPlayerLoggedIn(Player player) {
        return loggedInPlayers.contains(player.getUniqueId());
    }

    public void setPlayerLoggedIn(Player player, boolean loggedIn) {
        UUID playerId = player.getUniqueId();
        if (loggedIn) {
            loggedInPlayers.add(playerId);
        } else {
            loggedInPlayers.remove(playerId);
        }
    }
    
    /**
     * 检查按钮是否需要登录
     * @param name 按钮名称
     * @param key 按钮键名
     * @return 是否需要登录
     */
    public boolean requiresLogin(String name, String key) {
        // 检查名称是否包含需要登录的标识
        for (String tag : loginRequiredNameTags) {
            if (name != null && name.contains(tag)) {
                return true;
            }
        }
        
        // 检查键名是否包含需要登录的标识
        for (String tag : loginRequiredKeyTags) {
            if (key != null && key.contains(tag)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查按钮是否需要防止重复注册
     * @param name 按钮名称
     * @param key 按钮键名
     * @return 是否需要防止重复注册
     */
    public boolean requiresNotRegistered(String name, String key) {
        // 检查名称是否包含防止重复注册的标识
        for (String tag : noDuplicateRegNameTags) {
            if (name != null && name.contains(tag)) {
                return true;
            }
        }
        
        // 检查键名是否包含防止重复注册的标识
        for (String tag : noDuplicateRegKeyTags) {
            if (key != null && key.contains(tag)) {
                return true;
            }
        }
        
        return false;
    }
    
    // ==================== IP绑定配置方法 ====================
    
    /**
     * 检查IP绑定是否启用
     */
    public boolean isIpBindingEnabled() {
        return ipBindingEnabled;
    }
    
    /**
     * 检查IP绑定是否为严格模式
     */
    public boolean isIpBindingStrict() {
        return ipBindingStrict;
    }
    
    /**
     * 检查是否允许同一IP登录
     */
    public boolean isAllowSameIpLogin() {
        return allowSameIpLogin;
    }
    
    /**
     * 获取锁定时间（分钟）
     */
    public int getLockoutDuration() {
        return lockoutDuration;
    }
    
    /**
     * 获取最大登录尝试次数
     */
    public int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }
    
    // ==================== IP白名单和黑名单配置方法 ====================
    
    /**
     * 检查IP白名单是否启用
     */
    public boolean isIpWhitelistEnabled() {
        return ipWhitelistEnabled;
    }
    
    /**
     * 获取IP白名单列表
     */
    public List<String> getIpWhitelist() {
        return ipWhitelist;
    }
    
    /**
     * 检查IP黑名单是否启用
     */
    public boolean isIpBlacklistEnabled() {
        return ipBlacklistEnabled;
    }
    
    /**
     * 获取IP黑名单列表
     */
    public List<String> getIpBlacklist() {
        return ipBlacklist;
    }
    
    /**
     * 检查IP是否在白名单中
     */
    public boolean isIpInWhitelist(String ip) {
        if (!ipWhitelistEnabled || ipWhitelist.isEmpty()) {
            return false;
        }
        return matchesIpList(ip, ipWhitelist);
    }
    
    /**
     * 检查IP是否在黑名单中
     */
    public boolean isIpInBlacklist(String ip) {
        if (!ipBlacklistEnabled || ipBlacklist.isEmpty()) {
            return false;
        }
        return matchesIpList(ip, ipBlacklist);
    }
    
    /**
     * 检查IP是否匹配列表中的任何条目（支持CIDR格式）
     */
    private boolean matchesIpList(String ip, List<String> ipList) {
        if (ip == null || ipList == null) {
            return false;
        }
        
        for (String pattern : ipList) {
            pattern = pattern.trim();
            if (pattern.isEmpty()) {
                continue;
            }
            
            // 精确匹配
            if (pattern.equals(ip)) {
                return true;
            }
            
            // CIDR格式匹配
            if (pattern.contains("/")) {
                if (matchesCidr(ip, pattern)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检查IP是否匹配CIDR格式
     */
    private boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }
            
            String networkAddress = parts[0].trim();
            int prefixLength = Integer.parseInt(parts[1].trim());
            
            // 转换IP和子网掩码为字节数组
            byte[] ipBytes = ipToBytes(ip);
            byte[] networkBytes = ipToBytes(networkAddress);
            
            if (ipBytes == null || networkBytes == null || ipBytes.length != networkBytes.length) {
                return false;
            }
            
            // 计算子网掩码
            int totalBits = ipBytes.length * 8;
            if (prefixLength < 0 || prefixLength > totalBits) {
                return false;
            }
            
            // 检查IP是否在子网范围内
            for (int i = 0; i < ipBytes.length; i++) {
                int ipBit = (ipBytes[i] < 0 ? ipBytes[i] + 256 : ipBytes[i]);
                int networkBit = (networkBytes[i] < 0 ? networkBytes[i] + 256 : networkBytes[i]);
                
                int bitsInByte = Math.min(8, prefixLength - (i * 8));
                if (bitsInByte <= 0) {
                    break;
                }
                
                int mask = (1 << bitsInByte) - 1;
                int ipPrefix = ipBit & mask;
                int networkPrefix = networkBit & mask;
                
                if (ipPrefix != networkPrefix) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 将IP地址转换为字节数组
     */
    private byte[] ipToBytes(String ip) {
        if (ip == null || ip.isEmpty()) {
            return null;
        }
        
        try {
            // IPv4
            if (ip.contains(".") && !ip.contains(":")) {
                String[] octets = ip.split("\\.");
                if (octets.length != 4) {
                    return null;
                }
                byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) {
                    int octet = Integer.parseInt(octets[i].trim());
                    if (octet < 0 || octet > 255) {
                        return null;
                    }
                    bytes[i] = (byte) octet;
                }
                return bytes;
            }
            // IPv6
            else if (ip.contains(":") || ip.contains("::")) {
                return null; // IPv6支持可根据需要添加
            }
        } catch (NumberFormatException e) {
            return null;
        }
        
        return null;
    }

    public String getCurrentPlayerMenu(Player player) {
        return currentPlayerMenus.get(player);
    }

    public MenuLayout getSelectedLayout(Player player) {
        return selectedLayouts.get(player);
    }

    public ItemDisplayManager getItemDisplayManager() {
        return itemDisplayManager;
    }

    public UserDataManager getUserDataManager() {
        return userDataManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AdminIpValidator getAdminIpValidator() {
        return adminIpValidator;
    }

    private void clearMenuTextAnimationTasks(Player player) {
        List<Integer> tasks = playerMenuTextAnimationTasks.remove(player);
        if (tasks != null) {
            tasks.forEach(Bukkit.getScheduler()::cancelTask);
        }
    }

    private void applyMenuTextFloatingAnimation(Player player, TextDisplay display, MenuLayout layout) {
        if (player == null || display == null || layout == null || !layout.isFloatingAnimationEnabled()) {
            return;
        }

        String type = layout.getFloatingAnimationType();
        if (type == null || "none".equalsIgnoreCase(type)) {
            return;
        }

        final double speed = Math.max(0.01, layout.getFloatingAnimationSpeed());
        final double amplitude = Math.max(0.0, layout.getFloatingAnimationAmplitude());
        final boolean horizontal = "left-right".equalsIgnoreCase(type) || "float-x".equalsIgnoreCase(type);
        final boolean vertical = "up-down".equalsIgnoreCase(type) || "float-y".equalsIgnoreCase(type);
        final boolean rotate = "rotate".equalsIgnoreCase(type);

        if (!horizontal && !vertical && !rotate) {
            return;
        }

        if (rotate) {
            final float baseYaw = (float) Math.toRadians(layout.tiltY);
            final float basePitch = (float) Math.toRadians(layout.tiltX);
            final float baseRoll = (float) Math.toRadians(layout.tiltZ);

            int taskId = new BukkitRunnable() {
                float angle = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || !display.isValid() || !playerDisplays.getOrDefault(player, Collections.emptyList()).contains(display)) {
                        cancel();
                        return;
                    }

                    angle += speed * 0.05;
                    Transformation oldTrans = display.getTransformation();
                    display.setTransformation(new Transformation(
                            oldTrans.getTranslation(),
                            oldTrans.getLeftRotation().rotationYXZ(baseYaw + angle, basePitch, baseRoll),
                            oldTrans.getScale(),
                            oldTrans.getRightRotation()
                    ));
                }
            }.runTaskTimer(this, 0L, 1L).getTaskId();

            playerMenuTextAnimationTasks.computeIfAbsent(player, key -> new CopyOnWriteArrayList<>()).add(taskId);
            return;
        }

        final org.joml.Vector3f baseTranslation = new org.joml.Vector3f(display.getTransformation().getTranslation());

        int taskId = new BukkitRunnable() {
            long tick = 0;

            @Override
            public void run() {
                if (!player.isOnline() || !display.isValid() || !playerDisplays.getOrDefault(player, Collections.emptyList()).contains(display)) {
                    cancel();
                    return;
                }

                tick++;
                float offset = (float) (Math.sin(tick * speed * 0.05) * amplitude);
                Transformation oldTrans = display.getTransformation();
                org.joml.Vector3f translation = horizontal
                        ? new org.joml.Vector3f(baseTranslation.x + offset, baseTranslation.y, baseTranslation.z)
                        : new org.joml.Vector3f(baseTranslation.x, baseTranslation.y + offset, baseTranslation.z);

                display.setTransformation(new Transformation(
                        translation,
                        oldTrans.getLeftRotation(),
                        oldTrans.getScale(),
                        oldTrans.getRightRotation()
                ));
            }
        }.runTaskTimer(this, 0L, 1L).getTaskId();

        playerMenuTextAnimationTasks.computeIfAbsent(player, key -> new CopyOnWriteArrayList<>()).add(taskId);
    }

    // 获取玩家当前正在输入的字段
    public String getCurrentPlayerInputField(Player player) {
        return currentPlayerInputFields.get(player.getUniqueId());
    }

    // 设置玩家当前正在输入的字段
    public void setCurrentPlayerInputField(Player player, String field) {
        currentPlayerInputFields.put(player.getUniqueId(), field);
    }

    // 获取玩家已输入的数据
    public Map<String, String> getPlayerInputData(Player player) {
        UUID playerId = player.getUniqueId();
        return userInputData.computeIfAbsent(playerId, k -> new HashMap<>());
    }

    // 设置玩家输入的数据
    public void setPlayerInputData(Player player, String field, String value) {
        UUID playerId = player.getUniqueId();
        userInputData.computeIfAbsent(playerId, k -> new HashMap<>()).put(field, value);
    }

    // 获取密码显示状态
    public boolean getPasswordVisibility(Player player) {
        return passwordVisibility.getOrDefault(player.getUniqueId(), false); // 默认显示明文
    }

    // 切换密码显示状态
    public void togglePasswordVisibility(Player player) {
        UUID playerId = player.getUniqueId();
        boolean currentVisibility = passwordVisibility.getOrDefault(playerId, false);
        passwordVisibility.put(playerId, !currentVisibility);
    }

    private void purgeAllEntities() {
        // 1. 光标 & 摄像机
        playerCursors.values().forEach(e -> { if (e != null && !e.isDead()) e.remove(); });
        playerSit.values().forEach(e -> { if (e != null && !e.isDead()) e.remove(); });

        // 2. 文字显示
        playerDisplays.values().forEach(list -> list.forEach(e -> { if (e != null && !e.isDead()) e.remove(); }));

        // 3. 物品显示
        playerItemDisplays.values().forEach(e -> { if (e != null && !e.isDead()) e.remove(); });

        // 4. 物品管理器
        if (itemDisplayManager != null) Bukkit.getOnlinePlayers().forEach(itemDisplayManager::hideItem);

        // 5. 文字管理器
        if (textDisplayManager != null) textDisplayManager.cleanup();

        // 6. 悬停放大管理器
        if (hoverEnlargeManager != null) hoverEnlargeManager.cleanup();

        // 7. 数据库连接
        if (databaseManager != null) databaseManager.closeConnection();
    }


    // ===================== 配置合并工具 =====================
    private void mergeYamlFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
            return;
        }

        // 先尝试加载默认配置
        YamlConfiguration defaultConfig;
        try {
            defaultConfig = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(getResource(fileName), java.nio.charset.StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            // 如果无法加载默认配置，则创建一个空的配置
            getLogger().warning("无法加载默认配置 " + fileName + ": " + e.getMessage());
            defaultConfig = new YamlConfiguration();
        }

        YamlConfiguration userConfig = YamlConfiguration.loadConfiguration(file);

        for (String key : defaultConfig.getKeys(true)) {
            if (!userConfig.contains(key)) {
                userConfig.set(key, defaultConfig.get(key));
            }
        }

        if (defaultConfig.contains("version") && !userConfig.getString("version").equals(defaultConfig.getString("version"))) {
            userConfig.set("version", defaultConfig.getString("version"));
        }

        try {
            userConfig.save(file);
        } catch (java.io.IOException e) {
            getLogger().warning("无法保存合并后的 " + fileName + ": " + e.getMessage());
        }
    }

    /* ========== 缺失文件自动恢复工具 ========== */
    private void ensureDefaultsOnReload(String... files) {
        for (String name : files) {
            File file = new File(getDataFolder(), name);
            if (!file.exists()) {
                saveResource(name, false);
                getLogger().info("[CustomScreenMenu] 检测到缺失文件，已重新生成默认配置: " + name);
            }
        }
    }

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();

        mergeYamlFile("config.yml");

        // 初始化 PacketEvents API
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)
                .checkForUpdates(false)
                .bStats(true);
        PacketEvents.getAPI().load();

        // 注册BungeeCord/Velocity通道
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // protocolManager = ProtocolLibrary.getProtocolManager();
        foliaLib = new MorePaperLib(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            hasPAPI = true;
        }

        // 初始化语言配置
        reloadLangConfig();
        
        loadConfig();

        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);

        itemDisplayManager = new ItemDisplayManager(this);


        registerUseEntityPacketListener();

        // 初始化区块加载管理器
        chunkLoaderManager = new ChunkLoaderManager(this);
        chunkLoaderManager.startChunkLoaderTask();

        getServer().getPluginManager().registerEvents(new CreatureSpawnListener(), this);

        getServer().getPluginManager().registerEvents(new SessionCleanupListener(), this);

        getServer().getPluginManager().registerEvents((Listener)new MenuListener(this), this);

        getServer().getPluginManager().registerEvents(new AttackBreakListener(), this);

        // 注册玩家加入事件监听器
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);


        Bukkit.getPluginCommand("cursormenu").setExecutor(new Commands());

        textDisplayManager = new TextDisplayManager(this);

        // 初始化悬停放大管理器
        hoverEnlargeManager = new HoverEnlargeManager(this);

        // 初始化区块加载管理器
        chunkLoaderManager = new ChunkLoaderManager(this);
        chunkLoaderManager.startChunkLoaderTask();

        // 初始化数据库管理器
        databaseManager = new DatabaseManager(this);

        // 初始化用户数据管理器
        userDataManager = new UserDataManager(this, databaseManager);
        
        // 初始化管理员IP验证器
        adminIpValidator = new AdminIpValidator(this);

        // 初始化NPC镜像模块
        NPCModule.initialize(this);
        
        // 初始化bStats统计
        int pluginId = 30148; // bStats插件ID
        Metrics metrics = new Metrics(this, pluginId);

        // 初始化WASD导航模块
        WASDModule.initialize(this);


        getLogger().info("====================================");
        getLogger().info("CustomScreenMenu 插件已启动");
        getLogger().info("版本: " + getDescription().getVersion());
        getLogger().info("作者:野比大雄 " + getDescription().getAuthors());
        getLogger().info("感谢使用本插件！");
        getLogger().info("====================================");

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CursorMenuPlaceholder(this).register();
            getLogger().info("已注册PlaceholderAPI变量支持");
        }
        
        // 确保所有语言文件都被创建
        String[] langFiles = {"lang.yml", "lang/zh_cn.yml", "lang/en_us.yml", "lang/ru_ru.yml"};
        for (String file : langFiles) {
            File targetFile = new File(getDataFolder(), file);
            if (!targetFile.exists()) {
                saveResource(file, false);
            }
        }
        
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!playerCursors.containsKey(player)) continue;

                Location loc = player.getLocation();
                
                // 更新南瓜头位置
                ItemDisplay pumpkinDisplay = playerPumpkinDisplays.get(player);
                if (pumpkinDisplay != null && pumpkinDisplay.isValid()) {
                    Location pumpkinLoc = loc.clone().add(0, 1.7, 0);
                    pumpkinDisplay.teleport(pumpkinLoc);
                    pumpkinDisplay.setRotation(loc.getYaw(), loc.getPitch());
                }
            }
        }, 0L, 1L);
    }

    @Override
    public void onDisable() {
        // 关闭WASD导航模块
        WASDModule.shutdown();

        // 关闭NPC镜像模块
        NPCModule.shutdown();

        // 停止区块加载任务
        if (chunkLoaderManager != null) {
            chunkLoaderManager.stopChunkLoaderTask();
        }

        // 注销BungeeCord/Velocity通道
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");

        PacketEvents.getAPI().terminate();
        purgeAllEntities();

        getLogger().info("====================================");
        getLogger().info("CustomScreenMenu 插件已关闭");
        getLogger().info("版本: " + getDescription().getVersion());
        getLogger().info("感谢使用，下次再见！");
        getLogger().info("====================================");
    }


    // 添加语言配置
    private YamlConfiguration langConfig;
    
    // 获取语言配置中的消息
    public synchronized String getLangMessage(String key, String defaultValue) {
        if (langConfig == null) {
            reloadLangConfig();
        }
        // 确保每次获取消息时都从文件中重新加载配置
        return ChatColor.translateAlternateColorCodes('&', langConfig.getString(key, defaultValue));
    }
    
    // 重新加载语言配置
    public synchronized void reloadLangConfig() {
        File langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            saveResource("lang.yml", false);
        }
        mergeYamlFile("lang.yml");
        langConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    public void reloadPluginConfig() {
        Map<Player, String> menusToRestore = new HashMap<>(currentPlayerMenus);
        // 重载语言配置
        reloadLangConfig();

        getLogger().info("正在重载插件配置...");

        // 重载主配置
        reloadConfig();

        if (itemDisplayManager != null) {
            itemDisplayManager.reloadConfig();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (playerCursors.containsKey(player)) {

                ArmorStand cursor = playerCursors.remove(player);
                if (cursor != null && !cursor.isDead()) {
                    cursor.remove();
                }

                clearMenuTextAnimationTasks(player);
                List<TextDisplay> textDisplays = playerDisplays.remove(player);
                if (textDisplays != null) {
                    textDisplays.forEach(display -> {
                        if (display != null && !display.isDead()) {
                            // 取消打字机效果
                            textDisplayManager.getTypewriterEffect().cancelEffect(display);
                            display.remove();
                        }
                    });
                    textDisplays.clear();
                }

                ItemDisplay itemDisplay = playerItemDisplays.remove(player);
                if (itemDisplay != null && !itemDisplay.isDead()) {
                    itemDisplay.remove();
                }

                Pig sit = playerSit.remove(player);
                if (sit != null) {
                    if (sit.getPassengers().contains(player)) {
                        sit.removePassenger(player);
                    }
                    sit.remove();
                }

                playingSound.remove(player.getName());
                player.stopAllSounds();
                sendCameraPacket(player, player);
                player.setInvisible(false);
                player.setCollidable(true);

                Location originalLoc = playerLocations.remove(player);
                if (originalLoc != null) {
                    player.teleport(originalLoc);
                }

                // 清理悬停放大效果
                if (hoverEnlargeManager != null) {
                    hoverEnlargeManager.cleanupPlayer(player);
                }

                // 清理用户输入数据
                currentPlayerInputFields.remove(player.getUniqueId());
                userInputData.remove(player.getUniqueId());
                passwordVisibility.remove(player.getUniqueId());
                
                // 恢复阻挡方块
                restoreBlocks(player);
            }

            itemDisplayManager.hideItem(player);
        }

        for (List<TextDisplay> displays : playerDisplays.values()) {
            displays.forEach(display -> {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            });
        }

        if (textDisplayManager != null) {
            textDisplayManager.cleanup();
        }

        if (hoverEnlargeManager != null) {
            hoverEnlargeManager.cleanup();
        }

        purgeAllEntities();

        playerCursors.clear();
        playerDisplays.clear();
        playerItemDisplays.clear();
        playerLocations.clear();
        playerSit.clear();
        cursorExactLocations.clear();
        currentPlayerMenus.clear();
        selectedLayouts.clear();
        playingSound.clear();
        sectionManager.clear();
        currentPlayerInputFields.clear();
        userInputData.clear();
        passwordVisibility.clear();
        loggedInPlayers.clear();

        loadConfig();

        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.hasMetadata(HELMET_META_KEY)) {
                p.removeMetadata(HELMET_META_KEY, this);
            }
        });

        if (textDisplayManager != null) {
            textDisplayManager.reloadConfig();
        }

        ensureDefaultsOnReload(
                "config.yml",
                "items.yml",
                "lang.yml",
                "lang/zh_cn.yml",
                "lang/en_us.yml",
                "lang/ru_ru.yml",
                "commands.yml"
        );

        cursorModelData = getConfig().getInt("cursor-item.custom-model-data", 0);
        getLogger().info("配置已成功重载，所有菜单元素已刷新");
        File commandsFile = new File(getDataFolder(), "commands.yml");
        YamlConfiguration commandsConfig = YamlConfiguration.loadConfiguration(commandsFile);
        allowedCommands = commandsConfig.getStringList("allowed-commands");
        allowedCommands.replaceAll(String::toLowerCase);

        // 重新启动区块加载任务
        if (chunkLoaderManager != null) {
            chunkLoaderManager.startChunkLoaderTask();
        }

        // 重新加载生物生成限制配置
        creatureSpawnLimitEnabled = getConfig().getBoolean("creature-spawn-limits.enabled", false);
        creatureSpawnLimitRadius = getConfig().getInt("creature-spawn-limits.radius", 10);

        // 重新初始化悬停放大管理器
        if (hoverEnlargeManager != null) {
            hoverEnlargeManager.cleanup();
        }
        hoverEnlargeManager = new HoverEnlargeManager(this);

        // 重新初始化数据库管理器
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        databaseManager = new DatabaseManager(this);

        // 重新初始化用户数据管理器
        if (userDataManager != null) {
            // 注意：这里我们需要重新创建userDataManager
        }
        userDataManager = new UserDataManager(this, databaseManager);
        
        // 重新初始化管理员IP验证器
        if (adminIpValidator != null) {
            // 重新加载配置
            adminIpValidator.loadConfig();
        } else {
            adminIpValidator = new AdminIpValidator(this);
        }

        // 重载NPC镜像模块
        NPCModule.reload();

        // 重载WASD导航模块
        WASDModule.reload();

        // 为所有在线玩家重新创建菜单元素，以应用新的配置
        for (Player player : Bukkit.getOnlinePlayers()) {
            String currentMenu = menusToRestore.get(player);
            if (currentMenu != null) {
                // 为玩家重新创建菜单元素，使用新的配置
                setupCursor(player, currentMenu);
            }
        }
    }


    private void loadConfig() {

        reloadConfig();

        File config = new File(this.getDataFolder(), "config.yml");
        if (!config.exists()) {
            saveDefaultConfig();
        }

        // 加载按钮访问控制配置
        loadButtonAccessControlConfig();
        
        // 加载IP绑定配置
        loadIpBindingConfig();
        
        cameraBlockCheckEnabled = getConfig().getBoolean("camera-block-check.enabled", false);
        cameraBlockCheckRadius = getConfig().getInt("camera-block-check.radius", 5);
        cursorZOffset = getConfig().getDouble("cursor-item.z-offset", 0.0);
        cursorX = getConfig().getDouble("cursor-item.x", 0.0);
        cursorY = getConfig().getDouble("cursor-item.y", 0.0);
        // 默认 1.0 = 关闭滤波，避免输入延迟；仅在用户需要时手动调小
        cursorSmoothing = getConfig().getDouble("cursor-item.smoothing", 1.0);
        soundLoop = getConfig().getBoolean("sound.loop.enabled");
        soundRate = getConfig().getInt("sound.loop.duration");
        soundName = getConfig().getString("sound.name");
        soundVolume = Float.parseFloat(getConfig().getString("sound.volume"));
        soundPitch = Float.parseFloat(getConfig().getString("sound.pitch"));
        debugMode = getConfig().getBoolean("Debug", false);
        usePumpkinOverlay = getConfig().getBoolean("use-pumpkin-overlay", false);
        joinRunBool = getConfig().getBoolean("join-run.enabled", false);
        joinRunSection = getConfig().getString("join-run.menu","test");
        cursorItem = getConfig().getString("cursor-item.material", "ARROW");
        cursorScale = getConfig().getDouble("cursor-item.scale",1);
        cursorSensitivity = getConfig().getDouble("cursor-item.sensitivity", 1.0);
        cursorModelData = getConfig().getInt("cursor-item.custom-model-data", 0);
        maxX = getConfig().getDouble("cursor-item.max-x");
        maxY = getConfig().getDouble("cursor-item.max-y");
        // 读取光标移动范围限制配置
        cursorMovementRangeEnabled = getConfig().getBoolean("cursor-item.movement-range.enabled", false);
        cursorMovementRangeXMin = getConfig().getDouble("cursor-item.movement-range.x.min", -2.0);
        cursorMovementRangeXMax = getConfig().getDouble("cursor-item.movement-range.x.max", 2.0);
        cursorMovementRangeYMin = getConfig().getDouble("cursor-item.movement-range.y.min", -3.0);
        cursorMovementRangeYMax = getConfig().getDouble("cursor-item.movement-range.y.max", 3.0);
        // 读取光标默认位置配置
        cursorDefaultPositionEnabled = getConfig().getBoolean("cursor-item.default-position.enabled", false);
        cursorDefaultPositionX = getConfig().getDouble("cursor-item.default-position.x", 0.0);
        cursorDefaultPositionY = getConfig().getDouble("cursor-item.default-position.y", 0.0);
        runDelay = getConfig().getInt("join-run.delay", 0);
        joinRunCommands = getConfig().getStringList("join-run.commands");
        exitYaw = (float) getConfig().getDouble("exit-camera.yaw", 0.0);
        exitPitch = (float) getConfig().getDouble("exit-camera.pitch", 0.0);

        // 加载生物生成限制配置
        creatureSpawnLimitEnabled = getConfig().getBoolean("creature-spawn-limits.enabled", false);
        creatureSpawnLimitRadius = getConfig().getInt("creature-spawn-limits.radius", 10);

        PlayerLocationOverride.reload(getConfig().getBoolean("use-player-location", false));

        saveDefaultMenuFiles();//生成默认菜单配置
        sectionManager.loadAllMenuConfigs();

        File commandsFile = new File(getDataFolder(), "commands.yml");
        if (!commandsFile.exists()) {
            saveResource("commands.yml", false);
        }
        YamlConfiguration commandsConfig = YamlConfiguration.loadConfiguration(commandsFile);
        allowedCommands = commandsConfig.getStringList("allowed-commands");
        allowedCommands.replaceAll(String::toLowerCase);

        // 重新启动区块加载任务
        if (chunkLoaderManager != null) {
            chunkLoaderManager.startChunkLoaderTask();
        }
    }

    /**
     * 加载按钮访问控制配置
     */
    private void loadButtonAccessControlConfig() {
        // 加载需要登录的按钮标识配置
        loginRequiredNameTags = getConfig().getStringList("button-access-control.login-required.name-tags");
        loginRequiredKeyTags = getConfig().getStringList("button-access-control.login-required.key-tags");
        
        // 加载防止重复注册的按钮标识配置
        noDuplicateRegNameTags = getConfig().getStringList("button-access-control.no-duplicate-registration.name-tags");
        noDuplicateRegKeyTags = getConfig().getStringList("button-access-control.no-duplicate-registration.key-tags");
        
        // 如果配置为空，使用默认值
        if (loginRequiredNameTags.isEmpty()) {
            loginRequiredNameTags.add("[需要登录]");
            loginRequiredNameTags.add("[LOGIN_REQUIRED]");
        }
        
        if (loginRequiredKeyTags.isEmpty()) {
            loginRequiredKeyTags.add("login_required");
            loginRequiredKeyTags.add("需要登录");
        }
        
        if (noDuplicateRegNameTags.isEmpty()) {
            noDuplicateRegNameTags.add("[防止重复注册]");
            noDuplicateRegNameTags.add("[NO_DUPLICATE_REG]");
        }
        
        if (noDuplicateRegKeyTags.isEmpty()) {
            noDuplicateRegKeyTags.add("no_duplicate_reg");
            noDuplicateRegKeyTags.add("防止重复注册");
        }
    }

    /**
     * 加载IP绑定安全配置
     */
    private void loadIpBindingConfig() {
        ipBindingEnabled = getConfig().getBoolean("ip-binding.enabled", true);
        ipBindingStrict = getConfig().getBoolean("ip-binding.strict", false);
        allowSameIpLogin = getConfig().getBoolean("ip-binding.allow-same-ip-login", true);
        lockoutDuration = getConfig().getInt("ip-binding.lockout-duration", 5);
        maxLoginAttempts = getConfig().getInt("ip-binding.max-login-attempts", 5);
        
        // 加载IP白名单配置
        ipWhitelistEnabled = getConfig().getBoolean("ip-binding.whitelist.enabled", false);
        ipWhitelist = getConfig().getStringList("ip-binding.whitelist.ips");
        
        // 加载IP黑名单配置
        ipBlacklistEnabled = getConfig().getBoolean("ip-binding.blacklist.enabled", false);
        ipBlacklist = getConfig().getStringList("ip-binding.blacklist.ips");
        
        getLogger().info("IP绑定安全配置已加载 - 启用: " + ipBindingEnabled + ", 严格模式: " + ipBindingStrict);
        getLogger().info("IP白名单: " + (ipWhitelistEnabled ? "启用" : "禁用") + ", 规则数: " + ipWhitelist.size());
        getLogger().info("IP黑名单: " + (ipBlacklistEnabled ? "启用" : "禁用") + ", 规则数: " + ipBlacklist.size());
    }

    public void setupCursor(Player player, String key) {
        if (playerCursors.containsKey(player)) {
            stopCursor(player, false);
        }

        currentPlayerMenus.put(player, key);
        Section section = sectionManager.get(key);

        // 添加 null 检查以防止 NullPointerException
        if (section == null) {
            currentPlayerMenus.remove(player);
            getLogger().warning("Menu '" + key + "' not found! Please check your menu configuration.");
            player.sendMessage(getLangMessage("command.menu_not_found", "&c[CursorMenu] 菜单 '%menu%' 未找到，请检查配置文件！").replace("%menu%", key));
            return;
        }

        PlayerLocationOverride.apply(player, section);
        World world = Bukkit.getWorld(section.world);
        if (world == null) {
            currentPlayerMenus.remove(player);
            getLogger().warning("World " + section.world + " not found!");
            return;
        }

        playerLocations.put(player, player.getLocation());

        Location targetLoc = new Location(world, section.cameraX, section.cameraY, section.cameraZ, section.yaw, section.pitch);


        world.getChunkAt(targetLoc).load(true);

        foliaLib.scheduling().regionSpecificScheduler(targetLoc).runDelayed(task -> {
            if (!player.isOnline()) return;

            player.teleport(new Location(world, section.cameraX, section.cameraY, section.cameraZ, section.yaw, section.pitch));

            if (!player.getWorld().equals(world)) {
                player.teleport(targetLoc);
            }
            player.setMetadata("cursor_original_gamemode", new FixedMetadataValue(this, player.getGameMode().name()));
            player.setGameMode(GameMode.ADVENTURE);
            world.getChunkAt(targetLoc).load(true);
        }, 5L); // 延迟5tick

        player.setMetadata("cursor_original_gamemode", new FixedMetadataValue(this, player.getGameMode().name()));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        }, 1L);

        world.getChunkAt(targetLoc).load(true);

        foliaLib.scheduling().regionSpecificScheduler(targetLoc).runDelayed(task -> {
            if (!player.isOnline()) return;

            playingSound.add(player.getName());
            player.stopAllSounds();
            if (soundLoop) {
                foliaLib.scheduling().entitySpecificScheduler(player).runAtFixedRate(soundTask -> {
                    if (!playingSound.contains(player.getName())) {
                        soundTask.cancel();
                        return;
                    }
                    player.stopAllSounds();
                    player.playSound(player.getLocation(), soundName, soundVolume, soundPitch);
                }, null, 1, 20 * soundRate);
            } else {
                player.playSound(player.getLocation(), soundName, soundVolume, soundPitch);
            }

            /* ========== 生成所有实体 ========== */
            Location cameraLocation = new Location(world, section.cameraX, section.cameraY, section.cameraZ, section.yaw, section.pitch);
            Vector dir = cameraLocation.getDirection().normalize();
            Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
            Vector up = dir.getCrossProduct(right).multiply(-1);

            // 计算光标初始位置
            double initialScreenX = cursorX;
            double initialScreenY = cursorY;

            // 如果启用了默认位置，则使用默认位置
            if (cursorDefaultPositionEnabled) {
                initialScreenX = cursorDefaultPositionX;
                initialScreenY = cursorDefaultPositionY;
            }

            // 应用光标移动范围限制到默认位置
            if (cursorMovementRangeEnabled) {
                initialScreenX = Math.max(cursorMovementRangeXMin, Math.min(cursorMovementRangeXMax, initialScreenX));
                initialScreenY = Math.max(cursorMovementRangeYMin, Math.min(cursorMovementRangeYMax, initialScreenY));
            }

            Vector cursorOffset = dir.clone().multiply(section.distance + cursorZOffset)
                    .add(right.clone().multiply(initialScreenX))
                    .add(up.clone().multiply(-initialScreenY));

            Location cursorLocation = cameraLocation.clone().add(cursorOffset);
            ArmorStand cursor = spawnCursorArmorStand(cursorLocation);
            playerCursors.put(player, cursor);

            List<TextDisplay> textDisplays = new ArrayList<>();
            for (MenuLayout layout : section.layouts.values()) {
                Vector textOffset = dir.clone().multiply(layout.z)
                        .add(right.clone().multiply(layout.x))
                        .add(up.clone().multiply(layout.y));
                Location textLocation = cameraLocation.clone().add(textOffset);

                TextDisplay t = world.spawn(textLocation, TextDisplay.class);
                // 确保正确处理占位符
                String parsedName = parsePlaceholders(player, layout.name);
                t.setText(ColorParser.toLegacyString(parsedName));
                t.setCustomName(key + ":" + layout.key);
                t.setCustomNameVisible(false);
                t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                t.setDefaultBackground(false);
                t.setShadowed(true);
                t.setBillboard(Display.Billboard.CENTER);
                t.setVisibleByDefault(false);
                player.showEntity(this, t);
                textDisplays.add(t);

                float pitchRad = (float) Math.toRadians(layout.tiltX);
                float yawRad = (float) Math.toRadians(layout.tiltY);
                float rollRad = (float) Math.toRadians(layout.tiltZ);
                Transformation trans = t.getTransformation();
                trans.getLeftRotation().rotationYXZ(yawRad, pitchRad, rollRad);
                
                // 设置文本大小
                trans.getScale().set(layout.getTextSize());
                
                t.setTransformation(trans);
                applyMenuTextFloatingAnimation(player, t, layout);
            }
            playerDisplays.put(player, textDisplays);

            ItemDisplay itemDisplay = spawnCursorItemDisplay(player, cursorLocation);
            playerItemDisplays.put(player, itemDisplay);

            Pig pig = spawnPlayerLocStand(cameraLocation);
            pig.setRotation(section.yaw, section.pitch);
            playerSit.put(player, pig);

            mountPlayerToVehicle(player, cursor);

            foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(() -> {
                if (usePumpkinOverlay) {
                    storeHelmet(player);
                }
                player.setInvisible(true);
                player.setCollidable(false);
                pig.addPassenger(player);
                sendCameraPacket(player, pig);
            }, null, 2L);

            if (debugMode) {
                player.sendMessage(getLangMessage("menu.cursor_activated", "&a[CursorMenu] 光标菜单已激活!"));
            }
            textDisplayManager.showTextDisplays(player, key);
            clearBlockingBlocks(player);

            // 创建NPC镜像
            NPCModule.onMenuOpen(player, cameraLocation, section.yaw, section.pitch, key);

            // 启动WASD导航会话（如果菜单启用）
            if (section.wasdEnabled && WASDModule.isMenuEnabled(key)) {
                List<Location> textLocations = new ArrayList<>();
                List<String> commands = new ArrayList<>();
                List<Double> scales = new ArrayList<>();
                
                for (MenuLayout layout : section.layouts.values()) {
                    Vector textOffset = dir.clone().multiply(layout.z)
                            .add(right.clone().multiply(layout.x))
                            .add(up.clone().multiply(layout.y));
                    Location textLocation = cameraLocation.clone().add(textOffset);
                    textLocations.add(textLocation);
                    
                    List<String> layoutCommands = layout.getCommands();
                    if (layoutCommands != null && !layoutCommands.isEmpty()) {
                        commands.add(layoutCommands.get(0));
                    } else {
                        commands.add("");
                    }
                    scales.add(layout.getTextSize());
                }
                
                WASDModule.onMenuOpen(player, key, 
                    textLocations.toArray(new Location[0]), 
                    commands, 
                    textDisplays.toArray(new TextDisplay[0]), 
                    scales);
            }

            // 将初始位置添加到缓存中
            cursorExactLocations.put(player, cursorLocation.clone());
        }, 10L); // 延迟 10 tick

        if (section.autoCommandsEnabled && !section.autoCommands.isEmpty()) {
            for (int i = 0; i < section.autoCommands.size(); i++) {
                String cmd = section.autoCommands.get(i);
                long delay = i < section.autoCommandDelays.size()
                        ? section.autoCommandDelays.get(i)
                        : 20L;

                String processedCmd = parsePlaceholders(player, cmd);
                foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                    // 检查玩家是否仍在菜单中再执行命令
                    if (!player.isOnline() || !playerCursors.containsKey(player)) return;

                    if (processedCmd.toLowerCase().startsWith("[console]")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd.replaceAll("\\[console\\]", "").trim());
                    } else if (processedCmd.toLowerCase().startsWith("[op]")) {
                        String finalCmd = processedCmd.replaceAll("\\[op\\]", "").trim();
                        if (player.isOp()) {
                            player.performCommand(finalCmd);
                        } else {
                            try {
                                // 标记开始临时OP操作
                                AdminIpValidator.beginTemporaryOpOperation();
                                player.setOp(true);
                                player.performCommand(finalCmd);
                            } finally {
                                player.setOp(false);
                                // 标记结束临时OP操作
                                AdminIpValidator.endTemporaryOpOperation();
                            }
                        }
                    } else {
                        String finalCmd = processedCmd;
                        if (processedCmd.toLowerCase().startsWith("[player]")) {
                            finalCmd = processedCmd.replaceAll("\\[player\\]", "").trim();
                        }
                        player.performCommand(finalCmd);
                    }
                }, null, delay);
            }
        }
    }

    public void stopCursor(Player player, boolean cleanLocation) {
        foliaLib.scheduling().regionSpecificScheduler(player.getLocation()).runDelayed(task -> {
            Pig sit = playerSit.remove(player);
            if (sit != null) {
                if (sit.getPassengers().contains(player)) {
                    sit.removePassenger(player);
                }
                sit.remove();
            }

            ArmorStand cursor = playerCursors.remove(player);
            if (cursor != null && !cursor.isDead()) {
                cursor.remove();
            }

            clearMenuTextAnimationTasks(player);
            List<TextDisplay> textDisplays = playerDisplays.remove(player);
            if (textDisplays != null) {
                textDisplays.forEach(display -> {
                    if (display != null && !display.isDead()) {
                        display.remove();
                    }
                });
                textDisplays.clear();
            }

            ItemDisplay itemDisplay = playerItemDisplays.remove(player);
            if (itemDisplay != null && !itemDisplay.isDead()) {
                itemDisplay.remove();
            }

            playingSound.remove(player.getName());
            player.stopAllSounds();
            sendCameraPacket(player, player);
            player.setInvisible(false);
            player.setCollidable(true);

            // 只有在 cleanLocation 为 true 时才传送回原始位置
            if (cleanLocation) {
                Location originalLoc = playerLocations.remove(player);
                if (originalLoc != null) {
                    player.teleport(originalLoc);
                }

                // 清理悬停放大效果
                if (hoverEnlargeManager != null) {
                    hoverEnlargeManager.cleanupPlayer(player);
                }
            }

            // 检查玩家是否在登录或注册菜单中，如果是则清除密码数据
            String currentMenu = currentPlayerMenus.get(player);
            if ("登录菜单".equals(currentMenu) || "注册菜单".equals(currentMenu)) {
                Map<String, String> playerData = getPlayerInputData(player);
                playerData.remove("password");
                playerData.remove("confirm_password");
                
                // 同时清除Commands类中的用户输入数据
                Map<UUID, Map<String, String>> commandsUserInputData = Commands.getUserInputData();
                Map<String, String> commandsPlayerData = commandsUserInputData.get(player.getUniqueId());
                if (commandsPlayerData != null) {
                    commandsPlayerData.remove("password");
                    commandsPlayerData.remove("confirm_password");
                }
                
                // 清除密码可见性状态
                passwordVisibility.remove(player.getUniqueId());
            }

            if (debugMode) {
                player.sendMessage(getLangMessage("menu.cursor_deactivated", "&c[CursorMenu] 光标菜单已关闭!"));
            }
            textDisplayManager.clearPlayerDisplays(player.getUniqueId());
            sendCameraPacket(player, player);
            restoreBlocks(player);

            // 清理光标位置缓存
            cursorExactLocations.remove(player);

            // 清理用户输入数据
            currentPlayerInputFields.remove(player.getUniqueId());
            userInputData.remove(player.getUniqueId());
            passwordVisibility.remove(player.getUniqueId());
            Commands.getUserInputData().remove(player.getUniqueId());

            // 移除NPC镜像
            NPCModule.onMenuClose(player);

            // 停止WASD导航会话
            WASDModule.onMenuClose(player);
        }, 5L); // 延迟 5 tick
    }

    private ArmorStand spawnCursorArmorStand(Location location) {
        ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);
        setTeleportDurationSafe(armorStand, 2); // 恢复原版设置
        return armorStand;
    }

    private Pig spawnPlayerLocStand(Location location) {
        Pig armorStand = location.getWorld().spawn(location, Pig.class);
        armorStand.setGravity(false);
        armorStand.setAI(false);
        armorStand.setInvisible(true);
        armorStand.setCollidable(false);
        armorStand.setSilent(true);
        return armorStand;
    }

    private TextDisplay spawnCursorTextDisplay(Player player, Location location, String menu, String key, String string, double x, double y, double z) {
        Location new_loc = location.clone().add(x, y, z);
        TextDisplay textDisplay = location.getWorld().spawn(new_loc, TextDisplay.class);
        textDisplay.setCustomName(menu + ":" + key);
        textDisplay.setCustomNameVisible(false);
        textDisplay.setText(string);
        textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        textDisplay.setDefaultBackground(false);
        textDisplay.setBillboard(Display.Billboard.CENTER);
        textDisplay.setVisibleByDefault(false);
        player.showEntity(this, textDisplay);
        return textDisplay;
    }

    private ItemDisplay spawnCursorItemDisplay(Player player,Location location) {
        ItemDisplay itemDisplay = location.getWorld().spawn(location, ItemDisplay.class);
        ItemStack item = new ItemStack(Material.valueOf(cursorItem));
        ItemMeta itemMeta = item.getItemMeta();
        itemMeta.setCustomModelData(cursorModelData);
        item.setItemMeta(itemMeta);
        itemDisplay.setItemStack(item);
        itemDisplay.setBillboard(Display.Billboard.CENTER);
        itemDisplay.setRotation(location.getYaw(), location.getPitch());
        itemDisplay.setVisibleByDefault(false);
        
        Transformation oldTrans = itemDisplay.getTransformation();
        Transformation newTrans = new Transformation(
            oldTrans.getTranslation(),
            oldTrans.getLeftRotation(),
            new org.joml.Vector3f((float) cursorScale, (float) cursorScale, (float) cursorScale),
            oldTrans.getRightRotation()
        );
        itemDisplay.setTransformation(newTrans);
        player.showEntity(this,itemDisplay);
        
        // 恢复原来的 ArmorStand 一致的 teleportDuration
        setTeleportDurationSafe(itemDisplay, 2);
        return itemDisplay;
    }

    private void sendCameraPacket(Player player, Entity entity) {
        try {
            // 使用 PacketEvents 发送摄像机数据包
            WrapperPlayServerCamera cameraPacket = new WrapperPlayServerCamera(entity.getEntityId());
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, cameraPacket);
        } catch (Exception e) {
            getLogger().warning("Failed to send camera packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void mountPlayerToVehicle(Player player, Entity entity) {
    }

    private double calculateCursor(double original,double calc) {
        if(calc <= 0){
            return original + Math.abs(calc);
        } else {
            return original - Math.abs(calc);
        }
    }

    private void updateCursorPosition(Player player, float yaw, float pitch) {

        String menuKey = null;
        for (Map.Entry<String, Section> entry : sectionManager.getAll().entrySet()) {
            if (playerSit.containsKey(player) && playerSit.get(player).getWorld().getName().equals(entry.getValue().world)) {
                menuKey = entry.getKey();
                break;
            }
        }
        if (menuKey == null) return;

        Section section = sectionManager.get(menuKey);
        ArmorStand cursor   = playerCursors.get(player);
        ItemDisplay itemDis = playerItemDisplays.get(player);
        Pig camera          = playerSit.get(player);

        if (cursor == null || itemDis == null || camera == null) return;

        Location base = camera.getLocation();
        Vector dir  = base.getDirection().normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Vector up    = dir.getCrossProduct(right).multiply(-1);

        // 计算目标屏幕坐标，加入灵敏度设置
        double sensitivity = cursorSensitivity;
        double targetScreenX = (yaw / (90D / maxX)) * sensitivity + cursorX;
        double targetScreenY = (pitch / (90D / maxY)) * sensitivity + cursorY;

        // 应用光标移动范围限制
        if (cursorMovementRangeEnabled) {
            // 限制光标移动范围，但不强制"环绕"
            targetScreenX = Math.max(cursorMovementRangeXMin, Math.min(cursorMovementRangeXMax, targetScreenX));
            targetScreenY = Math.max(cursorMovementRangeYMin, Math.min(cursorMovementRangeYMax, targetScreenY));
        }

        Vector offset = right.multiply(targetScreenX).add(up.multiply(-targetScreenY));
        Location hudPos = base.clone()
                .add(dir.multiply(section.distance + cursorZOffset))
                .add(offset);

        // 使用最原生的传送方法
        cursor.teleport(hudPos);
        itemDis.teleport(hudPos);
        
        cursorExactLocations.put(player, hudPos.clone());
    }

    private float normalizeYaw(float yaw) {
        if (yaw < -90) yaw = -90;
        if (yaw > 90) yaw = 90;
        return yaw;
    }

    private float clampPitch(float pitch, float min, float max) {
        return Math.max(min, Math.min(max, pitch));
    }

    /**
     * 检查两个菜单是否在同一位置
     */
    private boolean isSameLocation(Section section1, Section section2) {
        if (!section1.world.equals(section2.world)) {
            return false;
        }

        double epsilon = 1e-6; // 浮点数比较的容差
        return section1.cameraX == section2.cameraX &&
                section1.cameraY == section2.cameraY &&
                section1.cameraZ == section2.cameraZ &&
                Math.abs(section1.yaw - section2.yaw) < epsilon &&
                Math.abs(section1.pitch - section2.pitch) < epsilon;
    }

    /**
     * 刷新菜单内容而不关闭菜单
     */
    private void refreshMenuContent(Player player, String newMenuKey, String oldMenuKey) {
        // 检查是否是从登录/注册菜单切换到其他菜单，如果是则清除密码数据
        if (("登录菜单".equals(oldMenuKey) || "注册菜单".equals(oldMenuKey)) && 
            !("登录菜单".equals(newMenuKey) || "注册菜单".equals(newMenuKey))) {
            
            // 清除密码相关数据
            Map<String, String> playerData = getPlayerInputData(player);
            playerData.remove("password");
            playerData.remove("confirm_password");
            
            // 同时清除Commands类中的用户输入数据
            Map<UUID, Map<String, String>> commandsUserInputData = Commands.getUserInputData();
            Map<String, String> commandsPlayerData = commandsUserInputData.get(player.getUniqueId());
            if (commandsPlayerData != null) {
                commandsPlayerData.remove("password");
                commandsPlayerData.remove("confirm_password");
            }
            
            // 清除密码可见性状态
            passwordVisibility.remove(player.getUniqueId());
        }
        
        // 更新当前菜单键
        currentPlayerMenus.put(player, newMenuKey);

        Section newSection = sectionManager.get(newMenuKey);
        if (newSection == null) return;

        // 保存当前输入字段状态
        String currentInputField = getCurrentPlayerInputField(player);
        
        // 移除旧的文字显示实体
        List<TextDisplay> oldTextDisplays = playerDisplays.get(player);
        if (oldTextDisplays != null) {
            clearMenuTextAnimationTasks(player);
            // 在主线程中移除实体以避免异步错误
            foliaLib.scheduling().entitySpecificScheduler(player).run(() -> {
                oldTextDisplays.forEach(display -> {
                    if (display != null && !display.isDead()) {
                        display.remove();
                    }
                });
                oldTextDisplays.clear();
            }, null);
        }

        // 创建新的文字显示实体
        List<TextDisplay> newTextDisplays = new ArrayList<>();
        World world = Bukkit.getWorld(newSection.world);
        if (world == null) return;

        Location cameraLocation = new Location(world, newSection.cameraX, newSection.cameraY, newSection.cameraZ, newSection.yaw, newSection.pitch);
        Vector dir = cameraLocation.getDirection().normalize();
        Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        Vector up = dir.getCrossProduct(right).multiply(-1);

        for (MenuLayout layout : newSection.layouts.values()) {
            Vector textOffset = dir.clone().multiply(layout.z)
                    .add(right.clone().multiply(layout.x))
                    .add(up.clone().multiply(layout.y));
            Location textLocation = cameraLocation.clone().add(textOffset);

            TextDisplay t = world.spawn(textLocation, TextDisplay.class);
            // 确保在刷新时也正确处理占位符
            String parsedName = parsePlaceholders(player, layout.name);
            String finalText = ColorParser.toLegacyString(parsedName);
            
            // 检查是否启用打字机效果
            if (layout.isTypewriterEnabled()) {
                t.setText(layout.getTypewriterCursorChar());
                textDisplayManager.getTypewriterEffect().applyEffect(
                    t, 
                    finalText, 
                    layout.getTypewriterSpeed(), 
                    layout.getTypewriterStartDelay(), 
                    layout.getTypewriterCursorChar()
                );
            } else {
                t.setText(finalText);
            }
            
            t.setCustomName(newMenuKey + ":" + layout.key);
            t.setCustomNameVisible(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setDefaultBackground(false);
            t.setShadowed(true);
            t.setBillboard(Display.Billboard.CENTER);
            t.setVisibleByDefault(false);
            player.showEntity(this, t);
            newTextDisplays.add(t);

            float pitchRad = (float) Math.toRadians(layout.tiltX);
            float yawRad = (float) Math.toRadians(layout.tiltY);
            float rollRad = (float) Math.toRadians(layout.tiltZ);
            Transformation oldTrans = t.getTransformation();
            Transformation newTrans = new Transformation(
                oldTrans.getTranslation(),
                oldTrans.getLeftRotation().rotationYXZ(yawRad, pitchRad, rollRad),
                new org.joml.Vector3f((float) layout.getTextSize(), (float) layout.getTextSize(), (float) layout.getTextSize()),
                oldTrans.getRightRotation()
            );
            t.setTransformation(newTrans);
            applyMenuTextFloatingAnimation(player, t, layout);
        }

        playerDisplays.put(player, newTextDisplays);

        // 恢复当前输入字段状态
        if (currentInputField != null) {
            setCurrentPlayerInputField(player, currentInputField);
        }

        // 更新文字显示管理器
        textDisplayManager.showTextDisplays(player, newMenuKey);

        // 执行新菜单的自动命令（如果有）
        if (newSection.autoCommandsEnabled && !newSection.autoCommands.isEmpty()) {
            for (int i = 0; i < newSection.autoCommands.size(); i++) {
                String cmd = newSection.autoCommands.get(i);
                long delay = i < newSection.autoCommandDelays.size()
                        ? newSection.autoCommandDelays.get(i)
                        : 20L;

                String processedCmd = parsePlaceholders(player, cmd);
                foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                    // 检查玩家是否仍在菜单中再执行命令
                    if (!player.isOnline() || !playerCursors.containsKey(player)) return;

                    if (processedCmd.toLowerCase().startsWith("[console]")) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd.replaceAll("\\[console\\]", "").trim());
                    } else if (processedCmd.toLowerCase().startsWith("[op]")) {
                        String finalCmd = processedCmd.replaceAll("\\[op\\]", "").trim();
                        if (player.isOp()) {
                            player.performCommand(finalCmd);
                        } else {
                            try {
                                player.setOp(true);
                                player.performCommand(finalCmd);
                            } finally {
                                player.setOp(false);
                            }
                        }
                    } else {
                        String finalCmd = processedCmd;
                        if (processedCmd.toLowerCase().startsWith("[player]")) {
                            finalCmd = processedCmd.replaceAll("\\[player\\]", "").trim();
                        }
                        player.performCommand(finalCmd);
                    }
                }, null, delay);
            }
        }
    }

    private void runLayout(Player player,String key) {
        MenuLayout menuLayout = sectionManager.getLayout(key);
        menuLayout.runCommand(player);
    }

    private void handleCursorClick(Player player) {
        foliaLib.scheduling().regionSpecificScheduler(player.getLocation()).run(task -> {
            Location cursorLoc = cursorExactLocations.get(player);
            if (cursorLoc == null) return;

            String menuKey = getCurrentPlayerMenu(player);
            if (menuKey == null) return;

            Section section = sectionManager.get(menuKey);
            if (section == null) return;

            World world = Bukkit.getWorld(section.world);
            if (world == null) return;

            TextDisplay closest = null;
            double minDistance = Double.MAX_VALUE;

            for (MenuLayout layout : section.layouts.values()) {
                Location cameraLoc = new Location(world, section.cameraX, section.cameraY, section.cameraZ, section.yaw, section.pitch);
                Vector dir = cameraLoc.getDirection().normalize();
                Vector right = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
                Vector up = dir.getCrossProduct(right).multiply(-1);

                Vector offset = dir.multiply(layout.z)
                        .add(right.multiply(layout.x))
                        .add(up.multiply(layout.y));
                Location buttonLoc = cameraLoc.clone().add(offset);

                String key = menuKey + ":" + layout.key;

                TextDisplay textDisplay = world.getEntitiesByClass(TextDisplay.class).stream()
                        .filter(e -> key.equals(e.getCustomName()))
                        .findFirst()
                        .orElse(null);

                if (textDisplay != null && TextDisplayHitBox.isInside(textDisplay, cursorLoc)) {
                    double distance = textDisplay.getLocation().distanceSquared(cursorLoc);
                    if (distance < minDistance) {
                        minDistance = distance;
                        closest = textDisplay;
                    }
                }
            }

            if (closest != null) {
                String key = closest.getCustomName();
                MenuLayout layout = sectionManager.getLayout(key);
                if (layout != null) {
                    selectedLayouts.put(player, layout);
                    layout.runCommand(player);
                }
            }
        });
    }


    private void registerUseEntityPacketListener() {
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.LOWEST) {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION || 
                    event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
                    Player player = (Player) event.getPlayer();
                    if (!playerCursors.containsKey(player)) return;
                    
                    // 获取玩家最新朝向
                    float yaw = 0, pitch = 0;
                    if (event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION) {
                        com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation packet = 
                            new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation(event);
                        yaw = packet.getYaw();
                        pitch = packet.getPitch();
                    } else {
                        com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation packet = 
                            new com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation(event);
                        yaw = packet.getYaw();
                        pitch = packet.getPitch();
                    }
                    
                    // 同步到主线程执行位置更新
                    final float finalYaw = yaw;
                    final float finalPitch = pitch;
                    foliaLib.scheduling().entitySpecificScheduler(player).run(task -> {
                        updateCursorPosition(player, finalYaw, finalPitch);
                    }, null);
                }
                
                if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    Player player = (Player) event.getPlayer();
                    if (!playerCursors.containsKey(player)) return;
                    event.setCancelled(true);
                    handleCursorClick(player);
                }
            }
        });
    }

    // 添加处理文本悬停放大的方法
    public void handleTextDisplayHover(Player player, TextDisplay textDisplay) {
        String menuKey = getCurrentPlayerMenu(player);
        if (menuKey == null || !textDisplay.getScoreboardTags().contains("hover_enlarge_processed")) {
            return;
        }

        MenuLayout layout = sectionManager.getLayout(textDisplay.getName());
        if (layout == null || !layout.isHoverEnlargeEnabled()) {
            return;
        }

        // 应用放大效果
        Transformation transformation = textDisplay.getTransformation();
        org.joml.Vector3f scale = transformation.getScale();
        scale.mul((float) layout.getHoverEnlargeScale());
        textDisplay.setTransformation(transformation);

        // 标记已处理，避免重复处理
        textDisplay.addScoreboardTag("hover_enlarge_processed");
    }

    // 添加重置文本显示大小的方法
    public void resetTextDisplaySize(Player player, TextDisplay textDisplay) {
        if (!textDisplay.getScoreboardTags().contains("hover_enlarge_processed")) {
            return;
        }

        String menuKey = getCurrentPlayerMenu(player);
        if (menuKey == null) {
            return;
        }

        MenuLayout layout = sectionManager.getLayout(textDisplay.getName());
        if (layout == null || !layout.isHoverEnlargeEnabled()) {
            return;
        }

        // 重置大小
        Transformation transformation = textDisplay.getTransformation();
        org.joml.Vector3f scale = transformation.getScale();
        scale.div((float) layout.getHoverEnlargeScale());
        textDisplay.setTransformation(transformation);

        // 移除处理标记
        textDisplay.removeScoreboardTag("hover_enlarge_processed");
    }

    /* ===== 减少延迟 ===== */
    private void setTeleportDurationSafe(Entity entity, int duration) {
        try {
            java.lang.reflect.Method method = entity.getClass().getMethod("setTeleportDuration", int.class);
            method.invoke(entity, duration);
        } catch (NoSuchMethodException ignored) {
            // 旧版本跳过
        } catch (Exception e) {
            getLogger().warning("无法设置 teleport duration: " + e.getMessage());
        }
    }


    /* ========== 攻击/破坏检测监听器 ========== */
    public static class AttackBreakListener implements Listener {

        @EventHandler
        public void onPlayerAnimation(org.bukkit.event.player.PlayerAnimationEvent event) {
            if (event.getAnimationType() == org.bukkit.event.player.PlayerAnimationType.ARM_SWING) {
                event.getPlayer().setMetadata("cursor_is_attacking_or_breaking",
                        new FixedMetadataValue(plugin, true));
                if (playerCursors.containsKey(event.getPlayer())) {
                    event.setCancelled(true);
                    plugin.handleCursorClick(event.getPlayer());
                }
            }
        }

        @EventHandler
        public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
            if (event.getDamager() instanceof Player player) {
                player.setMetadata("cursor_is_attacking_or_breaking",
                        new FixedMetadataValue(plugin, true));
            }
        }

        @EventHandler
        public void onBlockDamage(org.bukkit.event.block.BlockDamageEvent event) {
            Player player = event.getPlayer();
            if (player != null) {
                player.setMetadata("cursor_is_attacking_or_breaking",
                        new FixedMetadataValue(plugin, true));
            }
        }

        @EventHandler
        public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
            Player player = event.getPlayer();
            if (player.hasMetadata("cursor_is_attacking_or_breaking")) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.removeMetadata("cursor_is_attacking_or_breaking", plugin);
                }, 1L);
            }
        }
    }
    private static final String HELMET_META_KEY = "cursor_original_helmet";

    public void storeHelmet(Player player) {
        // 保存原始头盔
        ItemStack original = player.getInventory().getHelmet();
        player.setMetadata(HELMET_META_KEY, new FixedMetadataValue(this, original));

        // 创建南瓜头物品
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); // 隐藏附魔光效（可选）
        pumpkin.setItemMeta(meta);

        // 在玩家头顶生成ItemDisplay实体
        Location location = player.getLocation().clone().add(0, 1.7, 0); // 调整位置到玩家头顶
        ItemDisplay pumpkinDisplay = location.getWorld().spawn(location, ItemDisplay.class);
        pumpkinDisplay.setItemStack(pumpkin);
        pumpkinDisplay.setBillboard(Display.Billboard.FIXED); // 保持固定朝向
        pumpkinDisplay.setRotation(location.getYaw(), location.getPitch());
        pumpkinDisplay.setVisibleByDefault(false); // 默认对所有玩家不可见
        pumpkinDisplay.setPersistent(false);
        pumpkinDisplay.setInvulnerable(true);
        pumpkinDisplay.setGravity(false);
        
        // 只让当前玩家看到这个南瓜头
        player.showEntity(this, pumpkinDisplay);
        
        // 隐藏其他玩家看到的这个南瓜头
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != player) {
                other.hideEntity(this, pumpkinDisplay);
            }
        }
        
        // 存储这个南瓜头实体
        playerPumpkinDisplays.put(player, pumpkinDisplay);
    }

    private void restoreHelmet(Player player) {
        // 移除南瓜头ItemDisplay实体
        ItemDisplay pumpkinDisplay = playerPumpkinDisplays.remove(player);
        if (pumpkinDisplay != null && pumpkinDisplay.isValid()) {
            pumpkinDisplay.remove();
        }

        // 如果有保存的原始头盔，则恢复它
        if (player.hasMetadata(HELMET_META_KEY)) {
            for (var value : player.getMetadata(HELMET_META_KEY)) {
                if (value.getOwningPlugin() == this) {
                    ItemStack original = (ItemStack) value.value();
                    player.getInventory().setHelmet(original);
                    break;
                }
            }
            player.removeMetadata(HELMET_META_KEY, this);
        }
    }

    public static String parsePlaceholders(Player player, String text) {
        if (text == null) return "";
        if (hasPAPI && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    /**
     * 当玩家离线瞬间立即把菜单相关的东西全部清掉
     */
    private class SessionCleanupListener implements Listener {

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onQuit(PlayerQuitEvent e) {
            cleanup(e.getPlayer());
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onKick(PlayerKickEvent e) {
            if (playerCursors.containsKey(e.getPlayer()) && isSelfInteractKick(e.getReason())) {
                e.setCancelled(true);
                return;
            }

            cleanup(e.getPlayer());
        }

        private boolean isSelfInteractKick(String reason) {
            return reason != null && reason.toLowerCase(Locale.ROOT).contains("cannot interact with self");
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onJoin(PlayerJoinEvent e) {
            Player player = e.getPlayer();
            // 玩家每次加入服务器时都清除其登录状态，确保需要重新登录
            setPlayerLoggedIn(player, false);
            
            // 确保新加入的玩家看不到其他玩家的南瓜头
            for (ItemDisplay pumpkinDisplay : playerPumpkinDisplays.values()) {
                if (pumpkinDisplay != null && pumpkinDisplay.isValid()) {
                    player.hideEntity(CursorMenuPlugin.this, pumpkinDisplay);
                }
            }
            
            // 强制恢复，无论是否进入过菜单
            if (player.hasMetadata("cursor_original_gamemode")) {
                String mode = player.getMetadata("cursor_original_gamemode").get(0).asString();
                try {
                    player.setGameMode(GameMode.valueOf(mode));
                } catch (Exception ex) {
                    player.setGameMode(GameMode.SURVIVAL);
                }
                player.removeMetadata("cursor_original_gamemode", CursorMenuPlugin.this);
            } else {
                // 默认恢复为生存模式
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        /**
         * 同步清理，必须在主线程直接跑
         */
        private void cleanup(Player player) {
            // 检查玩家是否在登录或注册菜单中，如果是则清除密码数据
            String currentMenu = currentPlayerMenus.get(player);
            if ("登录菜单".equals(currentMenu) || "注册菜单".equals(currentMenu)) {
                Map<String, String> playerData = getPlayerInputData(player);
                playerData.remove("password");
                playerData.remove("confirm_password");
                
                // 同时清除Commands类中的用户输入数据
                Map<UUID, Map<String, String>> commandsUserInputData = Commands.getUserInputData();
                Map<String, String> commandsPlayerData = commandsUserInputData.get(player.getUniqueId());
                if (commandsPlayerData != null) {
                    commandsPlayerData.remove("password");
                    commandsPlayerData.remove("confirm_password");
                }
                
                // 清除密码可见性状态
                passwordVisibility.remove(player.getUniqueId());
            }
            
            // 确保玩家退出时清除登录状态
            setPlayerLoggedIn(player, false);
            
            if (!playerCursors.containsKey(player)) {
                clearPlayerSessionReferences(player);
                return;
            }

            // 1. 取猪，把玩家踢下来
            Pig pig = playerSit.remove(player);
            if (pig != null) {
                pig.removePassenger(player);
                pig.remove();
            }

            // 2. 光标
            ArmorStand cursor = playerCursors.remove(player);
            if (cursor != null) cursor.remove();

            // 3. 文字显示
            List<TextDisplay> texts = playerDisplays.remove(player);
            if (texts != null) texts.forEach(Entity::remove);

            // 4. 物品显示
            ItemDisplay item = playerItemDisplays.remove(player);
            if (item != null) item.remove();

            // 5. 声音
            playingSound.remove(player.getName());
            player.stopAllSounds();

            // 6. 头盔
            // 总是尝试恢复头盔，无论usePumpkinOverlay设置如何
            // 这样可以确保即使在菜单运行过程中启用南瓜头，退出时也能正确清理
            restoreHelmet(player);

            // 7. 可见性、碰撞
            player.setInvisible(false);
            player.setCollidable(true);

            // 8. 游戏模式
            if (player.hasMetadata("cursor_original_gamemode")) {
                String mode = player.getMetadata("cursor_original_gamemode").get(0).asString();
                player.setGameMode(GameMode.valueOf(mode));
                player.removeMetadata("cursor_original_gamemode", CursorMenuPlugin.this);
            } else {
                player.setGameMode(GameMode.SURVIVAL);
            }

            // 9. 摄像机切回玩家本身
            sendCameraPacket(player, player);

            // 10. 清掉记录
            playerLocations.remove(player);
            cursorExactLocations.remove(player);
            currentPlayerMenus.remove(player);
            selectedLayouts.remove(player);
            currentPlayerInputFields.remove(player.getUniqueId());
            userInputData.remove(player.getUniqueId());
            passwordVisibility.remove(player.getUniqueId());
        }
    }

    private void clearPlayerSessionReferences(Player player) {
        playerLocations.remove(player);
        cursorExactLocations.remove(player);
        currentPlayerMenus.remove(player);
        selectedLayouts.remove(player);
        currentPlayerInputFields.remove(player.getUniqueId());
        userInputData.remove(player.getUniqueId());
        passwordVisibility.remove(player.getUniqueId());
        Commands.getUserInputData().remove(player.getUniqueId());
        playingSound.remove(player.getName());
        setPlayerLoggedIn(player, false);
    }

    private File getPlayerBlockFile(Player player) {
        File folder = new File(getDataFolder(), "blockcache");
        if (!folder.exists()) folder.mkdirs();
        return new File(folder, player.getUniqueId() + ".yml");
    }

    private void clearBlockingBlocks(Player player) {
        if (!cameraBlockCheckEnabled) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        File file = getPlayerBlockFile(player);
        YamlConfiguration config = new YamlConfiguration();

        // 定义不可清除的方块类型
        Set<Material> skip = EnumSet.of(
                Material.CHEST,
                Material.TRAPPED_CHEST,
                Material.ENDER_CHEST,
                Material.BARREL,
                Material.HOPPER,
                Material.FURNACE,
                Material.BLAST_FURNACE,
                Material.SMOKER,
                Material.BREWING_STAND,
                // 所有颜色的潜影盒
                Material.SHULKER_BOX,
                Material.WHITE_SHULKER_BOX,
                Material.ORANGE_SHULKER_BOX,
                Material.MAGENTA_SHULKER_BOX,
                Material.LIGHT_BLUE_SHULKER_BOX,
                Material.YELLOW_SHULKER_BOX,
                Material.LIME_SHULKER_BOX,
                Material.PINK_SHULKER_BOX,
                Material.GRAY_SHULKER_BOX,
                Material.LIGHT_GRAY_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX,
                Material.PURPLE_SHULKER_BOX,
                Material.BLUE_SHULKER_BOX,
                Material.BROWN_SHULKER_BOX,
                Material.GREEN_SHULKER_BOX,
                Material.RED_SHULKER_BOX,
                Material.BLACK_SHULKER_BOX
        );

        int r = cameraBlockCheckRadius;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Location l = loc.clone().add(x, y, z);
                    Block block = world.getBlockAt(l);
                    Material type = block.getType();

                    // 跳过空气和敏感容器
                    if (type.isAir() || skip.contains(type)) continue;

                    String key = l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
                    config.set(key + ".world", l.getWorld().getName());
                    config.set(key + ".type", block.getBlockData().getAsString());
                    block.setType(Material.AIR, false); // 不触发物理更新
                }
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().warning("无法保存方块缓存文件: " + e.getMessage());
        }
    }

    private void restoreBlocks(Player player) {
        if (!cameraBlockCheckEnabled) return;

        File file = getPlayerBlockFile(player);
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            String[] parts = key.split(",");
            if (parts.length != 3) continue;

            World world = Bukkit.getWorld(config.getString(key + ".world"));
            if (world == null) continue;

            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            BlockData data = Bukkit.createBlockData(config.getString(key + ".type"));
            world.getBlockAt(x, y, z).setBlockData(data, false);
        }

        file.delete();
    }

    private class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();

            // 每次玩家加入服务器时都清除其登录状态，确保需要重新登录
            setPlayerLoggedIn(player, false);

            // 检查是否启用了加入执行功能
            if (joinRunBool) {
                // 转换延迟时间（配置中是秒，这里转为ticks，1秒=20ticks）
                long delayTicks = (long) runDelay * 20;

                // 延迟执行
                foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                    if (player.isOnline()) {
                        // 1. 执行菜单（原有功能）
                        if (sectionManager.hasSection(joinRunSection)) {
                            setupCursor(player, joinRunSection);
                        } else {
                            getLogger().warning("配置的加入执行菜单 '" + joinRunSection + "' 不存在！");
                        }

                        // 2. 执行自定义命令（新增功能）
                        for (String cmd : joinRunCommands) {
                            CommandUtils.executeCommand(player, cmd);
                        }
                    }
                }, null, delayTicks);
            }
        }
    }

    private void saveDefaultMenuFiles() {
        String[] defaultMenuFiles = {
                "menu/example.yml",
                "menu/login_register.yml",
                "menu/login_menu.yml",
                // ✅ 在 resources/menu/ 里的文件名都列出来
        };

        for (String fileName : defaultMenuFiles) {
            File file = new File(getDataFolder(), fileName);
            if (!file.exists()) {
                file.getParentFile().mkdirs(); // 创建 menu 文件夹
                try {
                    saveResource(fileName, false);
                    getLogger().info("已生成默认菜单文件: " + fileName);
                } catch (IllegalArgumentException e) {
                    // 资源不存在时，静默跳过
                }
            }
        }
    }

    /**
     * 发送ActionBar消息给玩家
     * @param player 要发送消息的玩家
     * @param message 消息内容
     */
    public void sendActionBarMessage(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
}
