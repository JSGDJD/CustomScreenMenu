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
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import space.arim.morepaperlib.MorePaperLib;

import java.io.File;
import java.util.*;

public class CursorMenuPlugin extends JavaPlugin {

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

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        itemDisplayManager = new ItemDisplayManager(this);
        protocolManager = ProtocolLibrary.getProtocolManager();
        foliaLib = new MorePaperLib(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            hasPAPI = true;
        }

        loadConfig();

        registerUseEntityPacketListener();

        getServer().getPluginManager().registerEvents((Listener)new MenuListener(this), this);

        Bukkit.getPluginCommand("cursormenu").setExecutor(new Commands());


        getLogger().info("CursorMenuPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
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
        getLogger().info("CursorMenuPlugin has been disabled!");
    }


    public void reloadPluginConfig() {
        reloadConfig(); // reload config
        if (itemDisplayManager != null) {
            itemDisplayManager.reloadConfig();
        }
        // reload
        playerCursors.clear();
        playerDisplays.clear();
        playerItemDisplays.clear();
        sectionManager.clear();

        // reload
        loadConfig();

        getLogger().info("Config has been successfully reloaded.");
    }

    private void loadConfig() {
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
        cursorModelData = getConfig().getInt("cursor-item.cursor-model-data", 0);
        maxX = getConfig().getDouble("cursor-item.max-x");
        maxY = getConfig().getDouble("cursor-item.max-y");
        runDelay = getConfig().getInt("join-run.delay", 0);

        for(String key : getConfig().getConfigurationSection("menu").getKeys(false)) {
            String world = getConfig().getString("menu." + key + ".camera-position.world");
            double dtc = getConfig().getDouble("menu." + key + ".camera-position.distance");
            double x = getConfig().getDouble("menu." + key + ".camera-position.x");
            double y = getConfig().getDouble("menu." + key + ".camera-position.y");
            double z = getConfig().getDouble("menu." + key + ".camera-position.z");

            Section section = new Section(dtc,world,x,y,z);
            for(String layout : getConfig().getConfigurationSection("menu." + key + ".layout").getKeys(false)) {
                double lz = getConfig().getDouble("menu." + key + ".layout." + layout + ".z", 0.0);
                String display = getConfig().getString("menu." + key + ".layout." + layout + ".name");
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
                MenuLayout lay = new MenuLayout(layout,display,cmd,stop,lx,ly,lz,tbool,tback,loc,stopBool,stopCmd);
                section.add(layout,lay);
            }
            sectionManager.addSection(key,section);
        }
    }

    public void setupCursor(Player player,String key) {
        Section section = sectionManager.get(key);
        World world = Bukkit.getWorld(section.world);
        if (world == null) {
            getLogger().warning("World " + section.world + " not found!");
            return;
        }

        Location loc = player.getLocation();
        loc.setPitch(0);
        loc.setYaw(0);
        player.teleport(loc);
        playerLocations.put(player,loc);

        playingSound.add(player.getName());
        player.stopAllSounds();
        if(soundLoop) {
            foliaLib.scheduling().entitySpecificScheduler(player).runAtFixedRate(task -> {
                if(!playingSound.contains(player.getName())) {
                    task.cancel();
                    return;
                }
                player.stopAllSounds();
                player.playSound(player,Sound.valueOf(soundName.replaceAll("minecraft:","")),soundVolume,soundPitch);
            },null,1,20 * soundRate);
        } else {
            player.playSound(player,Sound.valueOf(soundName.replaceAll("minecraft:","")),soundVolume,soundPitch);
        }


        Location location = new Location(world, section.cameraX,section.cameraY + 1, section.cameraZ + (section.distance) + cursorZOffset);
        Pig player_loc = spawnPlayerLocStand(new Location(world, section.cameraX, section.cameraY, section.cameraZ));
        player_loc.addPassenger(player);
        playerSit.put(player, player_loc);

        ArmorStand cursor = spawnCursorArmorStand(location);
        playerCursors.put(player, cursor);

        List<TextDisplay> textDisplays = new ArrayList<TextDisplay>();
        for(MenuLayout layout : section.layouts.values()) {
            TextDisplay t = spawnCursorTextDisplay(player,location,key,layout.key,layout.name,layout.x,layout.y,layout.z);
            textDisplays.add(t);
        }
        playerDisplays.put(player, textDisplays);

        ItemDisplay itemDisplay = spawnCursorItemDisplay(player,location);
        playerItemDisplays.put(player, itemDisplay);

        mountPlayerToVehicle(player, cursor);

        foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
            player.setInvisible(true);
            player.setCollidable(false);
            player_loc.addPassenger(player);
            sendCameraPacket(player, player_loc);
        },null,2);

        if (debugMode) {
            player.sendMessage(ChatColor.GREEN + "Cursor menu activated!");
        }
    }

    public void stopCursor(Player player,boolean cleanLocation) {
        Pig sit = playerSit.remove(player);
        if(sit != null){
            sit.remove();
        }

        ArmorStand cursor = playerCursors.remove(player);
        if (cursor != null) {
            cursor.remove();
        }

        List<TextDisplay> list = playerDisplays.remove(player);
        if (list != null && !list.isEmpty()) {
            for (TextDisplay display : list) {
                display.remove();
            }
        }

        ItemDisplay itemDisplay = playerItemDisplays.remove(player);
        if (itemDisplay != null) {
            itemDisplay.remove();
        }

        if(cleanLocation) {
            playerLocations.remove(player);
        }

        playingSound.remove(player.getName());
        player.stopAllSounds();
        sendCameraPacket(player,player);
        player.setInvisible(false);
        player.setCollidable(true);

        if (debugMode) {
            player.sendMessage(ChatColor.RED + "Cursor menu deactivated!");
        }
    }

    private ArmorStand spawnCursorArmorStand(Location location) {
        ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);  // Giữ cho ArmorStand như một "dấu hiệu"
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
        protocolManager.addPacketListener(new PacketAdapter(this, PacketType.Play.Client.LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                if (!playerCursors.containsKey(player)) return;

                final float yaw = normalizeYaw(event.getPacket().getFloat().read(0));
                final float pitch = event.getPacket().getFloat().read(1);

                foliaLib.scheduling().regionSpecificScheduler(player.getLocation()).run(task -> {
                    updateCursorPosition(player, yaw, pitch);
                });

                event.setCancelled(true);
            }
        });
    }

    private double calculateCursor(double original,double calc) {
        if(calc <= 0){
            return original + Math.abs(calc);
        } else {
            return original - Math.abs(calc);
        }
    }

    private void updateCursorPosition(Player player, float yaw, float pitch) {
        ItemDisplay itemDisplay = playerItemDisplays.get(player);
        if (itemDisplay == null) return;

        itemDisplay.setRotation(yaw, pitch);

        ArmorStand cursor = playerCursors.get(player);
        if (cursor != null) {
            Location cursorLocation = cursor.getLocation();
            cursorLocation.setX(calculateCursor(cursorLocation.getX(),(yaw / (90D / maxX))));
            cursorLocation.setY(calculateCursor(cursorLocation.getY(),(pitch / (90D / maxY))));

//            cursorLocation.setYaw(yaw);
//            cursorLocation.setPitch(pitch);
//
//            Vector direction = cursorLocation.getDirection();
//            Location itemLocation = cursorLocation.add(direction.multiply(0.5)); // Đẩy item display ra trước
            itemDisplay.teleport(cursorLocation);
        }
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
                            runLayout(player,textDisplay.getCustomName());
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
}
