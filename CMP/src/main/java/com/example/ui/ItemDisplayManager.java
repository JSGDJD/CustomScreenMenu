package com.example.ui;

import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/* ------------- 辅助类 ------------- */
class PlayerDisplayPair {
    final Player player;
    final ItemDisplay display;
    final String itemId;
    PlayerDisplayPair(Player p, ItemDisplay d, String id) {
        player = p;
        display = d;
        itemId = id;
    }
}
class PlayerTaskPair {
    final Player player;
    final int positionTaskId;
    final int rotationTaskId;
    PlayerTaskPair(Player p, int posId, int rotId) {
        player = p;
        positionTaskId = posId;
        rotationTaskId = rotId;
    }
}

public class ItemDisplayManager implements Listener {

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;

    /* 线程安全容器 */
    private final List<PlayerDisplayPair> activeDisplays = Collections.synchronizedList(new ArrayList<>());
    private final List<PlayerTaskPair> displayTasks    = Collections.synchronizedList(new ArrayList<>());
    private final Map<Player, String>  playerActiveItems = new ConcurrentHashMap<>();
    private final Map<Player, Pig>     ridingPig        = new ConcurrentHashMap<>();

    public ItemDisplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "items.yml");
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /* ---------------- 配置 ---------------- */
    public void loadConfig() {
        if (!configFile.exists()) plugin.saveResource("items.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        Map<Player, String> temp = new HashMap<>(playerActiveItems);
        config = YamlConfiguration.loadConfiguration(configFile);
        temp.forEach((p, id) -> {
            hideItem(p);
            if (p.isOnline())
                Bukkit.getScheduler().runTaskLater(plugin, () -> showItem(p, id), 2L);
        });
    }

    private void setTeleportDurationSafe(ItemDisplay display, int duration) {
        try {
            Method method = ItemDisplay.class.getMethod("setTeleportDuration", int.class);
            method.invoke(display, duration);
        } catch (NoSuchMethodException ignored) {
            // 旧版本不支持的话，跳过
        } catch (Exception e) {
            plugin.getLogger().warning("无法设置 ItemDisplay 的 teleport duration: " + e.getMessage());
        }
    }


    /* ---------------- 显示 / 隐藏 ---------------- */
    public boolean showItem(Player player, String itemId) {
        hideItem(player);

        if (!config.contains("display-items." + itemId)) {
            player.sendMessage(ChatColor.RED + "物品ID不存在: " + itemId);
            return false;
        }

        try {
            String path = "display-items." + itemId;
            Material material = Material.valueOf(config.getString(path + ".material", "DIAMOND"));
            int cmd        = config.getInt(path + ".custom-model-data", 0);
            float scale    = (float) config.getDouble(path + ".scale", 0.3);
            double xOffset = config.getDouble(path + ".offset.x", 0);
            double yOffset = config.getDouble(path + ".offset.y", 0);
            double zOffset = config.getDouble(path + ".offset.z", 2);
            boolean glow   = config.getBoolean(path + ".glow", true);
            boolean rotate = config.getBoolean(path + ".rotate", false);
            float rotateSpeed = (float) config.getDouble(path + ".rotate-speed", 1.0);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null && cmd > 0) {
                meta.setCustomModelData(cmd);
                item.setItemMeta(meta);
            }

            Location eye = player.getEyeLocation();
            Vector dir   = eye.getDirection().normalize();
            Location loc = eye.clone()
                    .add(dir.multiply(zOffset))
                    .add(0, yOffset, 0)
                    .add(getRightVector(eye).multiply(xOffset));

            ItemDisplay display = player.getWorld().spawn(loc, ItemDisplay.class);
            display.setItemStack(item);
            display.setGlowing(glow);
            display.setViewRange(100);
            display.setPersistent(false);
            display.setBillboard(ItemDisplay.Billboard.FIXED);
            setTeleportDurationSafe(display, 2); // 平滑移动
            setScale(display, scale);

            activeDisplays.add(new PlayerDisplayPair(player, display, itemId));
            playerActiveItems.put(player, itemId);

            /* ---------- 同步位置更新 ---------- */
            int posTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || !display.isValid()) return;
                Location newEye = player.getEyeLocation();
                Vector newDir   = newEye.getDirection().normalize();
                Location newLoc = newEye.clone()
                        .add(newDir.multiply(zOffset))
                        .add(0, yOffset, 0)
                        .add(getRightVector(newEye).multiply(xOffset));
                display.teleport(newLoc);
            }, 0, 1).getTaskId();

            /* ---------- 同步旋转更新 ---------- */
            int rotTaskId = -1;
            if (rotate) {
                final float radPerTick = rotateSpeed * 0.0174533f;
                rotTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!player.isOnline() || !display.isValid()) return;
                    Transformation tr = display.getTransformation();
                    display.setTransformation(new Transformation(
                            tr.getTranslation(),
                            tr.getLeftRotation().rotateAxis(radPerTick, 0, 1, 0),
                            tr.getScale(),
                            tr.getRightRotation()
                    ));
                }, 0, 1).getTaskId();
            }

            displayTasks.add(new PlayerTaskPair(player, posTaskId, rotTaskId));
            player.sendMessage(ChatColor.GREEN + "已显示物品: " + itemId);

            mountPig(player);
            return true;
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "显示失败: " + e.getMessage());
            plugin.getLogger().warning("显示物品失败: " + e.getMessage());
            return false;
        }
    }

    public void hideItem(Player player) {
        dismountPig(player);

        displayTasks.removeIf(pair -> {
            if (pair.player.equals(player)) {
                Bukkit.getScheduler().cancelTask(pair.positionTaskId);
                if (pair.rotationTaskId != -1) {
                    Bukkit.getScheduler().cancelTask(pair.rotationTaskId);
                }
                return true;
            }
            return false;
        });

        activeDisplays.removeIf(pair -> {
            if (pair.player.equals(player)) {
                if (pair.display.isValid()) pair.display.remove();
                return true;
            }
            return false;
        });

        playerActiveItems.remove(player);
    }

    /* ---------------- 工具 ---------------- */
    private Vector getRightVector(Location loc) {
        float yawRad = (float) Math.toRadians(loc.getYaw());
        return new Vector(Math.cos(yawRad), 0, Math.sin(yawRad)).normalize();
    }

    private void setScale(ItemDisplay display, float scale) {
        if (hasNewScaleApi()) {
            Transformation tr = display.getTransformation();
            tr.getScale().set(scale, scale, scale);
            display.setTransformation(tr);
            return;
        }
        try {
            Method m = ItemDisplay.class.getMethod("setScale", float.class);
            m.invoke(display, scale);
        } catch (Exception ignored) {}
    }

    private static boolean hasNewScaleApi() {
        try {
            ItemDisplay.class.getMethod("getTransformation");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    public List<String> getAllItemIds() {
        return config.contains("display-items")
                ? new ArrayList<>(config.getConfigurationSection("display-items").getKeys(false))
                : new ArrayList<>();
    }

    /* ---------------- 骑猪/下猪 ---------------- */
    private void mountPig(Player player) {
        if (player.getVehicle() instanceof Pig pig && ridingPig.get(player) == pig) return;
        if (player.isInsideVehicle()) return;

        dismountPig(player);

        Location loc = player.getLocation();
        Pig pig = player.getWorld().spawn(loc, Pig.class, p -> {
            p.setAI(false);
            p.setAware(false);
            p.setSilent(true);
            p.setInvulnerable(true);
            p.setCollidable(false);
            p.setGravity(false);
            p.setInvisible(true);
        });
        ridingPig.put(player, pig);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pig.isValid() && player.isOnline()) pig.addPassenger(player);
        }, 1L);
    }

    private void dismountPig(Player player) {
        Pig pig = ridingPig.remove(player);
        if (pig != null && pig.isValid()) {
            pig.removePassenger(player);
            pig.remove();
        }
    }

    /* ---------------- 事件 ---------------- */
    @EventHandler
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p &&
                ridingPig.containsKey(p) && e.getVehicle().equals(ridingPig.get(p))) {
            hideItem(p);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        hideItem(e.getPlayer());
    }
}