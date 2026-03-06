package com.cmenu.ui;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerCamera;
import com.cmenu.ui.layout.MenuLayout;
import com.cmenu.ui.section.PlayerLocationOverride;
import com.cmenu.ui.section.Section;
import com.cmenu.ui.section.SectionManager;
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
import org.bukkit.util.Transformation;
import space.arim.morepaperlib.MorePaperLib;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;
import me.clip.placeholderapi.PlaceholderAPI;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CursorMenuPlugin extends JavaPlugin {
    public static boolean creatureSpawnLimitEnabled;
    public static int creatureSpawnLimitRadius;
    private final Map<String, Set<Long>> forcedLoadedChunks = new HashMap<>();
    // 存储需要持续加载的区块 (世界名 -> 区块坐标集合)
    private final Map<String, Set<Long>> persistentChunks = new HashMap<>();
    // 区块加载任务的ID，用于取消任务
    private int chunkLoaderTaskId = -1;
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
    public static Map<Player, ArmorStand> playerCursors = new HashMap<>();
    private final Map<Player, List<TextDisplay>> playerDisplays = new HashMap<>();
    private final Map<Player, ItemDisplay> playerItemDisplays = new HashMap<>();
    public static Map<Player, Location> playerLocations = new HashMap<>();
    public static Map<Player, Pig> playerSit = new HashMap<>();

    public static Map<Player, Location> cursorExactLocations = new HashMap<>();
    public static HashSet<String> playingSound = new HashSet<>();
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

    private Map<Player, String> currentPlayerMenus = new HashMap<>();
    public Map<Player, MenuLayout> selectedLayouts = new HashMap<>();

    public String getCurrentPlayerMenu(Player player) {
        return currentPlayerMenus.get(player);
    }

    public MenuLayout getSelectedLayout(Player player) {
        return selectedLayouts.get(player);
    }

    public ItemDisplayManager getItemDisplayManager() {
        return itemDisplayManager;
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
    }


    // ===================== 配置合并工具 =====================
    private void mergeYamlFile(String fileName) {
        File file = new File(getDataFolder(), fileName);
        if (!file.exists()) {
            saveResource(fileName, false);
            return;
        }

        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(getResource(fileName), java.nio.charset.StandardCharsets.UTF_8)
        );

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

        loadConfig();

        getServer().getPluginManager().registerEvents(new TeleportListener(this), this);

        itemDisplayManager = new ItemDisplayManager(this);


        registerUseEntityPacketListener();

        startChunkLoaderTask();

        getServer().getPluginManager().registerEvents(new CreatureSpawnListener(), this);

        getServer().getPluginManager().registerEvents(new SessionCleanupListener(), this);

        getServer().getPluginManager().registerEvents((Listener)new MenuListener(this), this);

        getServer().getPluginManager().registerEvents(new AttackBreakListener(), this);

        // 注册玩家加入事件监听器
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);


        Bukkit.getPluginCommand("cursormenu").setExecutor(new Commands());

        textDisplayManager = new TextDisplayManager(this);


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
        String[] langFiles = {"lang/zh_cn.yml", "lang/en_us.yml", "lang/ru_ru.yml"};
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
                float yaw = loc.getYaw();
                float pitch = loc.getPitch();

                updateCursorPosition(player, yaw, pitch);
            }
        }, 0L, 1L);
    }

    @Override
    public void onDisable() {
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


    public void reloadPluginConfig() {
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

        purgeAllEntities();

        playerCursors.clear();
        playerDisplays.clear();
        playerItemDisplays.clear();
        playerLocations.clear();
        playerSit.clear();
        playingSound.clear();
        sectionManager.clear();

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
        updatePersistentChunks();
        // 重新加载生物生成限制配置
        creatureSpawnLimitEnabled = getConfig().getBoolean("creature-spawn-limits.enabled", false);
        creatureSpawnLimitRadius = getConfig().getInt("creature-spawn-limits.radius", 10);
    }



    private void loadConfig() {

        reloadConfig();

        File config = new File(this.getDataFolder(), "config.yml");
        if (!config.exists()) {
            saveDefaultConfig();
        }

        cameraBlockCheckEnabled = getConfig().getBoolean("camera-block-check.enabled", false);
        cameraBlockCheckRadius = getConfig().getInt("camera-block-check.radius", 5);
        cursorZOffset = getConfig().getDouble("cursor-item.z-offset", 0.0);
        cursorX = getConfig().getDouble("cursor-item.x", 0.0);
        cursorY = getConfig().getDouble("cursor-item.y", 0.0);
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
        updatePersistentChunks();
    }


    public void setupCursor(Player player, String key) {
        if (playerCursors.containsKey(player)) {
            stopCursor(player, false);
        }

        currentPlayerMenus.put(player, key);
        Section section = sectionManager.get(key);
        PlayerLocationOverride.apply(player, section);
        World world = Bukkit.getWorld(section.world);
        if (world == null) {
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
                t.setTransformation(trans);
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
                player.sendMessage(ChatColor.GREEN + "Cursor menu activated!");
            }
            textDisplayManager.showTextDisplays(player, key);
            clearBlockingBlocks(player);
            
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
            player.setInvisible(false);
            if (usePumpkinOverlay) {
                restoreHelmet(player);
            }
            player.setCollidable(true);

            if (player.hasMetadata("cursor_original_gamemode")) {
                player.setGameMode(GameMode.SURVIVAL);
                player.removeMetadata("cursor_original_gamemode", this);
            }

            if (cleanLocation) {
                Location originalLoc = playerLocations.remove(player);
                if (originalLoc != null) {
                    originalLoc.setYaw(exitYaw);
                    originalLoc.setPitch(exitPitch);
                    player.teleport(originalLoc);
                    sendCameraPacket(player, player);
                } else {
                    // ✅ 防止 originalLoc 为 null 时视角错乱
                    Location fallback = player.getLocation();
                    fallback.setYaw(exitYaw);
                    fallback.setPitch(exitPitch);
                    player.teleport(fallback);
                    sendCameraPacket(player, player);
                }
            }

            if (debugMode) {
                player.sendMessage(ChatColor.RED + "Cursor menu deactivated!");
            }
            textDisplayManager.clearPlayerDisplays(player.getUniqueId());
            sendCameraPacket(player, player);
            restoreBlocks(player);
            
            // 清理光标位置缓存
            cursorExactLocations.remove(player);
        }, 5L); // 延迟 5 tick
    }

    private ArmorStand spawnCursorArmorStand(Location location) {
        ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);
        setTeleportDurationSafe(armorStand, 2); // 设置为2以确保平滑移动
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
        Transformation transformation = itemDisplay.getTransformation();
        transformation.getScale().set(cursorScale);
        itemDisplay.setTransformation(transformation);
        player.showEntity(this,itemDisplay);
        setTeleportDurationSafe(itemDisplay, 2); // 设置为2以确保平滑移动
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

        // 计算屏幕坐标
        double screenX = (yaw / (90D / maxX)) + cursorX;
        double screenY = (pitch / (90D / maxY)) + cursorY;
        
        // 应用光标移动范围限制
        if (cursorMovementRangeEnabled) {
            // 限制光标移动范围，但不强制"环绕"
            screenX = Math.max(cursorMovementRangeXMin, Math.min(cursorMovementRangeXMax, screenX));
            screenY = Math.max(cursorMovementRangeYMin, Math.min(cursorMovementRangeYMax, screenY));
        }

        Vector offset = right.multiply(screenX).add(up.multiply(-screenY));
        Location hudPos = base.clone()
                .add(dir.multiply(section.distance + cursorZOffset))
                .add(offset);

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



    private void runLayout(Player player,String key) {
        MenuLayout menuLayout = sectionManager.getLayout(key);
        menuLayout.runCommand(player);
    }


    private void registerUseEntityPacketListener() {
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
                    Player player = (Player) event.getPlayer();
                    if (!playerCursors.containsKey(player)) return;
                    event.setCancelled(true);

                    WrapperPlayClientInteractEntity interactEntity = new WrapperPlayClientInteractEntity(event);

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

                            double distance = buttonLoc.distance(cursorLoc);
                            if (distance < 0.8 && distance < minDistance) {
                                minDistance = distance;

                                String key = menuKey + ":" + layout.key;

                                TextDisplay textDisplay = world.getEntitiesByClass(TextDisplay.class).stream()
                                        .filter(e -> key.equals(e.getName()))
                                        .findFirst()
                                        .orElse(null);

                                closest = textDisplay;
                            }
                        }

                        if (closest != null) {
                            String key = closest.getName();
                            MenuLayout layout = sectionManager.getLayout(key);
                            if (layout != null) {
                                selectedLayouts.put(player, layout);
                                layout.runCommand(player);
                            }
                        }
                    });
                }
            }
        });
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

        // 创建带绑定诅咒的南瓜头
        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = pumpkin.getItemMeta();
        meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS); // 隐藏附魔光效（可选）
        pumpkin.setItemMeta(meta);

        player.getInventory().setHelmet(pumpkin);
    }

    private void restoreHelmet(Player player) {
        if (!player.hasMetadata(HELMET_META_KEY)) return;

        for (var value : player.getMetadata(HELMET_META_KEY)) {
            if (value.getOwningPlugin() == this) {
                ItemStack original = (ItemStack) value.value();
                player.getInventory().setHelmet(original);
                break;
            }
        }
        player.removeMetadata(HELMET_META_KEY, this);
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
            cleanup(e.getPlayer());
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        public void onJoin(PlayerJoinEvent e) {
            Player player = e.getPlayer();
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
            if (!playerCursors.containsKey(player)) return;

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
            if (usePumpkinOverlay) restoreHelmet(player);

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
        }
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
                            // 处理占位符
                            String processedCmd = cmd.replaceAll("%player%", player.getName());
                            if (hasPAPI) {
                                processedCmd = PlaceholderAPI.setPlaceholders(player, processedCmd);
                            }

                            // 执行命令
                            if (processedCmd.toLowerCase().startsWith("[console]")) {
                                String finalCmd = processedCmd.replaceAll("\\[console\\]", "").trim();
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
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
                        }
                    }
                }, null, delayTicks);
            }
        }
    }

    private void saveDefaultMenuFiles() {
        String[] defaultMenuFiles = {
                "menu/example.yml",
                // ✅ 在 resources/menu/ 里的文件名都列出来
        };

        for (String fileName : defaultMenuFiles) {
            File file = new File(getDataFolder(), fileName);
            if (!file.exists()) {
                file.getParentFile().mkdirs(); // 创建 menu 文件夹
                saveResource(fileName, false);
                getLogger().info("已生成默认菜单文件: " + fileName);
            }
        }
    }

    private void startChunkLoaderTask() {
        // 停止旧任务
        stopChunkLoaderTask();

        // 初始化强制加载（后续通过updatePersistentChunks更新）
        updatePersistentChunks();

        // 可选：每30秒验证一次区块状态（防止意外卸载）
        chunkLoaderTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this::updatePersistentChunks, 0L, 600L); // 30秒一次
    }

    // 添加新方法：停止区块加载任务
    private void stopChunkLoaderTask() {
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

        // 取消定时任务
        if (chunkLoaderTaskId != -1) {
            Bukkit.getScheduler().cancelTask(chunkLoaderTaskId);
            chunkLoaderTaskId = -1;
        }
    }

    // 添加新方法：更新需要持续加载的区块列表
    private void updatePersistentChunks() {
        // 1. 先记录当前所有强制加载的区块（用于后续清理）
        Map<String, Set<Long>> oldChunks = new HashMap<>();
        // 关键：对内部集合也做深拷贝，避免修改原集合
        for (Map.Entry<String, Set<Long>> entry : forcedLoadedChunks.entrySet()) {
            oldChunks.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        // 2. 计算新的需要加载的区块（完全新建，不修改旧集合）
        Map<String, Set<Long>> newChunks = new HashMap<>();
        for (Section section : sectionManager.getAll().values()) {
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

        if (debugMode) {
            getLogger().info("已更新强制加载区块：共 " + forcedLoadedChunks.size() + " 个世界，" +
                    forcedLoadedChunks.values().stream().mapToInt(Set::size).sum() + " 个区块");
        }
    }

}