package com.example.ui;

import com.example.ui.ColorParser;
import com.example.ui.CursorMenuPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TextDisplayManager {

    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;

    // 存储所有活动的文字显示
    private final Map<UUID, List<ActiveTextDisplay>> activeDisplays = new ConcurrentHashMap<>();
    private final Map<UUID, List<Integer>> activeTasks = new ConcurrentHashMap<>();

    public TextDisplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "text.yml");
        loadConfig();
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("text.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        activeDisplays.keySet().forEach(this::clearPlayerDisplays);
        activeDisplays.clear();
        activeTasks.clear();

        loadConfig();
    }

    public void showTextDisplays(Player player, String menuKey) {
        if (menuKey == null) return;

        // ✅ 添加这一行，避免重复创建
        if (activeDisplays.containsKey(player.getUniqueId())) {
            return;
        }

        // 清除现有显示
        clearPlayerDisplays(player.getUniqueId());

        // 获取配置的所有文字显示
        if (!config.contains("text-displays")) return;

        ConfigurationSection displays = config.getConfigurationSection("text-displays");
        for (String key : displays.getKeys(false)) {
            String path = "text-displays." + key;

            // 检查是否适用于当前菜单
            List<String> menus = config.getStringList(path + ".menus");
            if (!menus.contains(menuKey)) continue;

            // 读取配置
            String content = config.getString(path + ".content", "");
            List<String> contents = config.isList(path + ".content")
                    ? config.getStringList(path + ".content")
                    : Collections.singletonList(content);

            double scale = config.getDouble(path + ".scale", 1.0);
            double x = config.getDouble(path + ".position.x", 0);
            double y = config.getDouble(path + ".position.y", 0);
            double z = config.getDouble(path + ".position.z", 0);

            double tiltX = config.getDouble(path + ".tilt.x", 0);
            double tiltY = config.getDouble(path + ".tilt.y", 0);
            double tiltZ = config.getDouble(path + ".tilt.z", 0);

            String animType = config.getString(path + ".animation.type", "none");
            double animSpeed = config.getDouble(path + ".animation.speed", 1.0);
            double spacing = config.getDouble(path + ".animation.spacing", 0.5);
            String alignment = config.getString(path + ".alignment", "center");

            int duration = config.getInt(path + ".duration", 30) * 20; // 转换为ticks

            // 创建文字显示
            createTextDisplays(
                    player,
                    contents,
                    scale,
                    x, y, z,
                    tiltX, tiltY, tiltZ,
                    animType, animSpeed, spacing,
                    alignment,
                    duration
            );
        }
    }

    private void createTextDisplays(Player player, List<String> contents, double scale,
                                    double x, double y, double z,
                                    double tiltX, double tiltY, double tiltZ,
                                    String animType, double animSpeed, double spacing,
                                    String alignment, int duration) {

        List<ActiveTextDisplay> displays = new CopyOnWriteArrayList<>();
        List<Integer> tasks = new CopyOnWriteArrayList<>();

        World world = player.getWorld();
        Location baseLoc = player.getLocation();

        // 计算对齐偏移
        double yOffset = 0;
        if ("center".equals(alignment)) {
            yOffset = -(contents.size() - 1) * spacing / 2.0;
        } else if ("right".equals(alignment)) {
            yOffset = -(contents.size() - 1) * spacing;
        }

        for (int i = 0; i < contents.size(); i++) {
            String line = ColorParser.toLegacyString(contents.get(i));
            Location loc = baseLoc.clone()
                    .add(new Vector(x, y + yOffset + ((contents.size() - 1 - i) * spacing), z));

            TextDisplay display = world.spawn(loc, TextDisplay.class);
            display.setText(line);
            display.setCustomName("text-display-" + i);
            display.setCustomNameVisible(false);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            display.setDefaultBackground(false);
            display.setShadowed(true);
            display.setBillboard(TextDisplay.Billboard.CENTER);
            display.setVisibleByDefault(false);
            player.showEntity(CursorMenuPlugin.plugin, display);
            display.setInterpolationDuration(2);

            // 设置倾斜
            Transformation trans = display.getTransformation();
            trans.getScale().set(scale, scale, scale);
            display.setTransformation(trans);

            displays.add(new ActiveTextDisplay(display, loc.clone(), i));

            // 应用动画
            applyAnimation(display, animType, animSpeed, spacing, i, tasks);
        }

        activeDisplays.put(player.getUniqueId(), displays);
        activeTasks.put(player.getUniqueId(), tasks);

        // 设置自动移除
        Bukkit.getScheduler().runTaskLater(CursorMenuPlugin.plugin, () -> {
            clearPlayerDisplays(player.getUniqueId());
        }, duration);
    }

    private void applyAnimation(TextDisplay display, String animType, double speed,
                                double spacing, int index, List<Integer> tasks) {

        final long period = 1L; // 每 tick 更新
        final double step = speed * 0.05; // 速度因子

        switch (animType.toLowerCase()) {
            case "rotate": {
                new BukkitRunnable() {
                    float angle = 0;
                    @Override
                    public void run() {
                        if (!display.isValid()) {
                            this.cancel();
                            return;
                        }
                        angle += step;
                        Transformation t = display.getTransformation();
                        t.getLeftRotation().rotationYXZ(0, angle, 0);
                        display.setTransformation(t);
                    }
                }.runTaskTimer(CursorMenuPlugin.plugin, 0L, period);
                break;
            }

            case "up-down": {
                new BukkitRunnable() {
                    final Location start = display.getLocation().clone();
                    final double total = 2.0; // 上移距离
                    final long totalTicks = (long) (20 * 3 / speed); // 3秒
                    long tick = 0;

                    @Override
                    public void run() {
                        if (!display.isValid()) {
                            this.cancel();
                            return;
                        }
                        tick++;
                        float progress = Math.min(tick / (float) totalTicks, 1.0f);
                        double y = start.getY() + total * progress;

                        // 平滑坐标更新（无 teleport）—— 关键修复
                        Transformation t = display.getTransformation();
                        t.getTranslation().set(
                                (float) (start.getX() - display.getLocation().getX()),
                                (float) (y - display.getLocation().getY()),
                                (float) (start.getZ() - display.getLocation().getZ())
                        );
                        display.setTransformation(t);

                        if (tick >= totalTicks) this.cancel();
                    }
                }.runTaskTimer(CursorMenuPlugin.plugin, 0L, period);
                break;
            }

            case "left-right": {
                new BukkitRunnable() {
                    final Location start = display.getLocation().clone();
                    final double total = 2.0; // 左右距离
                    final long totalTicks = (long) (20 * 3 / speed);
                    long tick = 0;

                    @Override
                    public void run() {
                        if (!display.isValid()) {
                            this.cancel();
                            return;
                        }
                        tick++;
                        float progress = Math.min(tick / (float) totalTicks, 1.0f);
                        double x = start.getX() + total * Math.sin(progress * 2 * Math.PI);

                        Transformation t = display.getTransformation();
                        t.getTranslation().set(
                                (float) (x - display.getLocation().getX()),
                                0,
                                0
                        );
                        display.setTransformation(t);
                    }
                }.runTaskTimer(CursorMenuPlugin.plugin, 0L, period);
                break;
            }
        }
    }

    public void clearPlayerDisplays(UUID playerId) {
        // 清除任务
        List<Integer> tasks = activeTasks.remove(playerId);
        if (tasks != null) {
            tasks.forEach(Bukkit.getScheduler()::cancelTask);
        }

        // 清除实体
        List<ActiveTextDisplay> displays = activeDisplays.remove(playerId);
        if (displays != null) {
            displays.forEach(display -> {
                if (display.display.isValid()) {
                    display.display.remove();
                }
            });
        }
    }

    public void cleanup() {
        activeDisplays.keySet().forEach(this::clearPlayerDisplays);
    }

    private static class ActiveTextDisplay {
        final TextDisplay display;
        final Location originalLocation;
        final int index;

        ActiveTextDisplay(TextDisplay display, Location location, int index) {
            this.display = display;
            this.originalLocation = location;
            this.index = index;
        }
    }
    public boolean showTextDisplayById(Player player, String textId) {
        if (!config.contains("text-displays." + textId)) {
            return false;
        }

        ConfigurationSection section = config.getConfigurationSection("text-displays." + textId);

        List<String> contents = section.isList("content")
                ? section.getStringList("content")
                : Collections.singletonList(section.getString("content", ""));

        double scale = section.getDouble("scale", 1.0);
        double x = section.getDouble("position.x", 0);
        double y = section.getDouble("position.y", 0);
        double z = section.getDouble("position.z", 0);

        double tiltX = section.getDouble("tilt.x", 0);
        double tiltY = section.getDouble("tilt.y", 0);
        double tiltZ = section.getDouble("tilt.z", 0);

        String animType = section.getString("animation.type", "none");
        double animSpeed = section.getDouble("animation.speed", 1.0);
        double spacing = section.getDouble("animation.spacing", 0.5);
        String alignment = section.getString("alignment", "center");
        int duration = section.getInt("duration", 30) * 20;

        clearPlayerDisplays(player.getUniqueId());

        createTextDisplays(
                player,
                contents,
                scale,
                x, y, z,
                tiltX, tiltY, tiltZ,
                animType, animSpeed, spacing,
                alignment,
                duration
        );

        return true;
    }

    public List<String> getAllTextIds() {
        return config.contains("text-displays")
                ? new ArrayList<>(config.getConfigurationSection("text-displays").getKeys(false))
                : new ArrayList<>();
    }
}