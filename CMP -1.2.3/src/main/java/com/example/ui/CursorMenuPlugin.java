package com.example.ui;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.PacketType;
import com.example.ui.layout.MenuLayout;
import com.example.ui.section.Section;
import com.example.ui.section.SectionManager;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import space.arim.morepaperlib.MorePaperLib;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.File;
import java.util.*;

public class CursorMenuPlugin extends JavaPlugin {

    public static TextDisplayManager textDisplayManager;
    public static double cursorZOffset;
    public static ItemDisplayManager itemDisplayManager;
    public static CursorMenuPlugin plugin;
    private ProtocolManager protocolManager;
    public static Map<Player, ArmorStand> playerCursors = new HashMap<>();
    private final Map<Player, List<TextDisplay>> playerDisplays = new HashMap<>();
    private final Map<Player, ItemDisplay> playerItemDisplays = new HashMap<>();
    public static Map<Player, Location> playerLocations = new HashMap<>();
    public static Map<Player, Pig> playerSit = new HashMap<>();
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

    // 存储当前玩家正在使用的菜单
    private Map<Player, String> currentPlayerMenus = new HashMap<>();
    // 存储当前玩家选中的选项
    public Map<Player, MenuLayout> selectedLayouts = new HashMap<>();

    // 添加getter方法
    public String getCurrentPlayerMenu(Player player) {
        return currentPlayerMenus.get(player);
    }

    public MenuLayout getSelectedLayout(Player player) {
        return selectedLayouts.get(player);
    }

    public ItemDisplayManager getItemDisplayManager() {
        return itemDisplayManager;
    }

    public static boolean textShadowEnabled;


    // 工具统一清理方法
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
        mergeYamlFile("items.yml");
        protocolManager = ProtocolLibrary.getProtocolManager();
        foliaLib = new MorePaperLib(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            hasPAPI = true;
        }

        loadConfig();

        itemDisplayManager = new ItemDisplayManager(this);

        registerUseEntityPacketListener();

        getServer().getPluginManager().registerEvents((Listener)new MenuListener(this), this);

        getServer().getPluginManager().registerEvents(new AttackBreakListener(), this);

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
        }, 0L, 1L); // 每 tick 执行
    }

    @Override
    public void onDisable() {

        purgeAllEntities();

        playerCursors.values().forEach(ArmorStand::remove);
        Bukkit.getOnlinePlayers().forEach(itemDisplayManager::hideItem);
        Collection<List<TextDisplay>> values = playerDisplays.values();
        if(!values.isEmpty()) {
            for(List<TextDisplay> displays : values) {
                for(TextDisplay display : displays) {
                    display.remove();
                }
            }
        }
        playerItemDisplays.values().forEach(ItemDisplay::remove);
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

        if (textDisplayManager != null) {
            textDisplayManager.reloadConfig();
        }

        ensureDefaultsOnReload(
                "config.yml",
                "items.yml",
                "lang/zh_cn.yml",
                "lang/en_us.yml",
                "lang/ru_ru.yml"
        );

        cursorModelData = getConfig().getInt("cursor-item.custom-model-data", 0);
        getLogger().info("配置已成功重载，所有菜单元素已刷新");
    }



    private void loadConfig() {

        reloadConfig();

        File config = new File(this.getDataFolder(), "config.yml");
        if (!config.exists()) {
            saveDefaultConfig();
        }

        cursorZOffset = getConfig().getDouble("cursor-item.z-offset", 0.0);
        soundLoop = getConfig().getBoolean("sound.loop.enabled");
        soundRate = getConfig().getInt("sound.loop.duration");
        soundName = getConfig().getString("sound.name");
        soundVolume = Float.parseFloat(getConfig().getString("sound.volume"));
        soundPitch = Float.parseFloat(getConfig().getString("sound.pitch"));
        debugMode = getConfig().getBoolean("Debug", false);
        joinRunBool = getConfig().getBoolean("join-run.enabled", false);
        joinRunSection = getConfig().getString("join-run.menu","test");
        cursorItem = getConfig().getString("cursor-item.material", "ARROW");
        cursorScale = getConfig().getDouble("cursor-item.scale",1);
        cursorModelData = getConfig().getInt("cursor-item.custom-model-data", 0);
        maxX = getConfig().getDouble("cursor-item.max-x");
        maxY = getConfig().getDouble("cursor-item.max-y");
        runDelay = getConfig().getInt("join-run.delay", 0);


        for(String key : getConfig().getConfigurationSection("menu").getKeys(false)) {
            String world = getConfig().getString("menu." + key + ".camera-position.world");
            double dtc = getConfig().getDouble("menu." + key + ".camera-position.distance");
            double x = getConfig().getDouble("menu." + key + ".camera-position.x");
            double y = getConfig().getDouble("menu." + key + ".camera-position.y");
            double z = getConfig().getDouble("menu." + key + ".camera-position.z");
            float yaw = (float) getConfig().getDouble("menu." + key + ".camera-position.yaw", 0.0);
            float pitch = (float) getConfig().getDouble("menu." + key + ".camera-position.pitch", 0.0);

            String perm = getConfig().getString("menu." + key + ".permission", "");
            Section section = new Section(dtc, world, x, y, z, yaw, pitch,  perm);
            for(String layout : getConfig().getConfigurationSection("menu." + key + ".layout").getKeys(false)) {
                double lz = getConfig().getDouble("menu." + key + ".layout." + layout + ".z", 0.0);
                String display = ChatColor.translateAlternateColorCodes('&',
                        Objects.requireNonNull(getConfig().getString("menu." + key + ".layout." + layout + ".name")));
                double lx = getConfig().getDouble("menu." + key + ".layout." + layout + ".x");
                double ly = getConfig().getDouble("menu." + key + ".layout." + layout + ".y");
                List<String> cmd = getConfig().getStringList("menu." + key + ".layout." + layout + ".command");
                boolean stop = getConfig().getBoolean("menu." + key + ".layout." + layout + ".stop-menu.enabled");
                boolean tbool = getConfig().getBoolean("menu." + key + ".layout." + layout + ".stop-menu.teleport.enabled");
                boolean tback = getConfig().getBoolean("menu." + key + ".layout." + layout + ".stop-menu.teleport.back-original");
                String tworld = getConfig().getString("menu." + key + ".layout." + layout + ".stop-menu.teleport.world","world");
                double tx = getConfig().getDouble("menu." + key + ".layout." + layout + ".stop-menu.teleport.x");
                double ty = getConfig().getDouble("menu." + key + ".layout." + layout + ".stop-menu.teleport.y");
                double tz = getConfig().getDouble("menu." + key + ".layout." + layout + ".stop-menu.teleport.z");
                boolean stopBool = getConfig().getBoolean("menu." + key + ".layout." + layout + ".stop-menu.command.enabled");
                List<String> stopCmd = getConfig().getStringList("menu." + key + ".layout." + layout + ".stop-menu.command.list");
                Location loc = new Location(Bukkit.getWorld(tworld), tx, ty, tz,0,0);
                double tiltX = getConfig().getDouble("menu." + key + ".layout." + layout + ".tilt.x", 0.0);
                double tiltY = getConfig().getDouble("menu." + key + ".layout." + layout + ".tilt.y", 0.0);
                double tiltZ = getConfig().getDouble("menu." + key + ".layout." + layout + ".tilt.z", 0.0);
                String buttonPerm = getConfig().getString("menu." + key + ".layout." + layout + ".permission", "");

                MenuLayout lay = new MenuLayout(layout, display, cmd, stop, lx, ly, lz,
                        tbool, tback, loc, stopBool, stopCmd,
                        (float) tiltX, (float) tiltY, (float) tiltZ, buttonPerm);
                section.add(layout,lay);
            }
            sectionManager.addSection(key,section);
        }
    }

    public void setupCursor(Player player, String key) {
        if (playerCursors.containsKey(player)) {
            stopCursor(player, false);
        }

        currentPlayerMenus.put(player, key);
        Section section = sectionManager.get(key);
        World world = Bukkit.getWorld(section.world);
        if (world == null) {
            getLogger().warning("World " + section.world + " not found!");
            return;
        }

        playerLocations.put(player, player.getLocation());

        Location targetLoc = new Location(world, section.cameraX, section.cameraY, section.cameraZ, section.yaw, section.pitch);


        player.teleport(targetLoc);

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

            Vector cursorOffset = dir.clone().multiply(section.distance + cursorZOffset);
            Location cursorLocation = cameraLocation.clone().add(cursorOffset);
            ArmorStand cursor = spawnCursorArmorStand(cursorLocation);
            playerCursors.put(player, cursor);

            List<TextDisplay> textDisplays = new ArrayList<>();
            for (MenuLayout layout : section.layouts.values()) {
                Vector textOffset = dir.clone().multiply(layout.z)
                        .add(right.multiply(layout.x))
                        .add(up.multiply(layout.y));
                Location textLocation = cameraLocation.clone().add(textOffset);

                TextDisplay t = world.spawn(textLocation, TextDisplay.class);
                t.setText(ColorParser.toLegacyString(layout.name));
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
                player.setInvisible(true);
                player.setCollidable(false);
                pig.addPassenger(player);
                sendCameraPacket(player, pig);
            }, null, 2L);

            if (debugMode) {
                player.sendMessage(ChatColor.GREEN + "Cursor menu activated!");
            }
            textDisplayManager.showTextDisplays(player, key);
        }, 10L); // 延迟 10 tick
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
            sendCameraPacket(player, player);
            player.setInvisible(false);
            player.setCollidable(true);

            if (player.hasMetadata("cursor_original_gamemode")) {
                player.setGameMode(GameMode.SURVIVAL);
                player.removeMetadata("cursor_original_gamemode", this);
            }

// 原来 cleanLocation 里的传送逻辑保持不变
            if (cleanLocation) {
                Location originalLoc = playerLocations.remove(player);
                if (originalLoc != null) {
                    player.teleport(originalLoc);
                }
            }

            if (debugMode) {
                player.sendMessage(ChatColor.RED + "Cursor menu deactivated!");
            }
            textDisplayManager.clearPlayerDisplays(player.getUniqueId());
        }, 5L); // 延迟 5 tick
    }

    private ArmorStand spawnCursorArmorStand(Location location) {
        ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);
        setTeleportDurationSafe(armorStand, 1);
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
        setTeleportDurationSafe(itemDisplay, 1);
        return itemDisplay;
    }

    private void sendCameraPacket(Player player, Entity entity) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.CAMERA);
            packet.getIntegers().write(0, entity.getEntityId());
            protocolManager.sendServerPacket(player, packet);
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

        double screenX = yaw   / (90D / maxX);
        double screenY = pitch / (90D / maxY);

        Vector offset = right.multiply(screenX).add(up.multiply(-screenY));
        Location hudPos = base.clone()
                .add(dir.multiply(section.distance + cursorZOffset))
                .add(offset);

        cursor.teleport(hudPos);
        itemDis.teleport(hudPos);
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
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.USE_ENTITY) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (!playerCursors.containsKey(player)) return;
                event.setCancelled(true);

//                PacketContainer packet = event.getPacket();
//                int entityId = packet.getIntegers().read(0);
//                Entity targetEntity = null;
//
//                for (World world : Bukkit.getWorlds()) {
//                    targetEntity = world.getEntities().stream().filter(e -> e.getEntityId() == entityId).findFirst().orElse(null);
//                    if (targetEntity != null) break;
//                }
//
//                if (targetEntity != null) {
//                    if (debugMode) {
//                        player.sendMessage(ChatColor.GREEN + "You interacted with: " + targetEntity.getType());
//                    }
//
//                    // Additional logic for interaction
//                }

                foliaLib.scheduling().regionSpecificScheduler(player.getLocation()).run(task -> {
                    PacketContainer packet = event.getPacket();
                    ItemDisplay itemDisplay = playerItemDisplays.get(player);
                    List<Entity> nearbyEntities = itemDisplay.getNearbyEntities(1, 0.3, 1);
                    for(Entity entity : nearbyEntities) {
                        if(entity instanceof TextDisplay) {
                            TextDisplay textDisplay = (TextDisplay) entity;
                            if(debugMode) {
                                getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + "[CursorMenu] Clicked text: " + textDisplay.getText());
                                getServer().getConsoleSender().sendMessage(ChatColor.YELLOW + "[CursorMenu] Clicked text: " + textDisplay.getCustomName());
                            }
                            String key = textDisplay.getCustomName();
                            if (key == null || !key.contains(":")) continue;
                            MenuLayout layout = sectionManager.getLayout(key);
                            if (layout != null) {
                                CursorMenuPlugin.plugin.selectedLayouts.put(player, layout);
                                layout.runCommand(player);
                            }
                            break;
                        }
                    }

//                    int entityId = packet.getIntegers().read(0);
//                    Entity targetEntity = null;
//                    targetEntity = player.getWorld().getEntities().stream().filter(e -> e.getEntityId() == entityId).findFirst().orElse(null);
//
//                    if (targetEntity != null) {
//                        System.out.println(targetEntity.getEntityId());
//                        System.out.println(targetEntity.getLocation().toString());
//                        if(targetEntity instanceof TextDisplay){
//                            TextDisplay textDisplay = (TextDisplay) targetEntity;
//                            System.out.println(textDisplay.getText());
//                        }
//                        if (debugMode) {
//                            System.out.println("You interacted with: " + targetEntity.getType());
//                            player.sendMessage(ChatColor.GREEN + "You interacted with: " + targetEntity.getType());
//                        }
//                        // Additional logic for interaction
//                    }
                });
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
}