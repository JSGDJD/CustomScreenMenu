package com.cmenu.ui;

import com.cmenu.ui.ColorParser;
import com.cmenu.ui.CursorMenuPlugin;
import com.cmenu.ui.layout.MenuLayout;
import com.cmenu.ui.util.HoverSoundManager;
import com.cmenu.ui.util.TypewriterEffect;
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
import org.bukkit.scheduler.BukkitTask;

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

    // 打字机效果管理器
    private final TypewriterEffect typewriterEffect;

    public TextDisplayManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "text.yml");
        this.typewriterEffect = new TypewriterEffect(CursorMenuPlugin.plugin);
        loadConfig();
    }

    /**
     * 获取打字机效果管理器
     */
    public TypewriterEffect getTypewriterEffect() {
        return typewriterEffect;
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
        
        // 清理打字机效果
        typewriterEffect.cleanupAll();

        loadConfig();
    }

    public void showTextDisplays(Player player, String menuKey) {
        if (menuKey == null) return;
        if (activeDisplays.containsKey(player.getUniqueId())) {
            return;
        }

        clearPlayerDisplays(player.getUniqueId());

        if (!config.contains("text-displays")) return;

        ConfigurationSection displays = config.getConfigurationSection("text-displays");
        for (String key : displays.getKeys(false)) {
            String path = "text-displays." + key;

            List<String> menus = config.getStringList(path + ".menus");
            if (!menus.contains(menuKey)) continue;

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
            boolean animEnabled = config.getBoolean(path + ".animation.enabled", !"none".equalsIgnoreCase(animType));
            double animSpeed = config.getDouble(path + ".animation.speed", 1.0);
            double animAmplitude = config.getDouble(path + ".animation.amplitude", 0.25);
            double animPhaseOffset = config.getDouble(path + ".animation.phase-offset", 0.0);
            double spacing = config.getDouble(path + ".animation.spacing", 0);
            String alignment = config.getString(path + ".alignment", "center");

            int duration = config.getInt(path + ".duration", 30) * 20; // 转换为ticks

            // 打字机效果配置（从text.yml读取）
            boolean typewriterEnabled = config.getBoolean(path + ".typewriter.enabled", false);
            int typewriterSpeed = Math.max(1, config.getInt(path + ".typewriter.speed", 2));
            int typewriterStartDelay = Math.max(0, config.getInt(path + ".typewriter.start-delay", 0));
            String typewriterCursorChar = config.getString(path + ".typewriter.cursor-char", "|");

            createTextDisplays(
                    player,
                    contents,
                    scale,
                    x, y, z,
                    tiltX, tiltY, tiltZ,
                    animEnabled, animType, animSpeed, animAmplitude, animPhaseOffset, spacing,
                    alignment,
                    duration,
                    typewriterEnabled, typewriterSpeed, typewriterStartDelay, typewriterCursorChar
            );
        }
    }

    private void createTextDisplays(Player player, List<String> contents, double scale,
                                    double x, double y, double z,
                                    double tiltX, double tiltY, double tiltZ,
                                    boolean animEnabled, String animType, double animSpeed,
                                    double animAmplitude, double animPhaseOffset, double spacing,
                                    String alignment, int duration,
                                    boolean typewriterEnabled, int typewriterSpeed, 
                                    int typewriterStartDelay, String typewriterCursorChar) {

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
            String rawLine = contents.get(i);
            String line = ColorParser.toLegacyString(CursorMenuPlugin.parsePlaceholders(player, rawLine));
            Location loc = baseLoc.clone()
                    .add(new Vector(x, y + yOffset + ((contents.size() - 1 - i) * spacing), z));

            TextDisplay display = world.spawn(loc, TextDisplay.class);
            
            // 如果启用打字机效果，初始设置为空或仅光标
            if (typewriterEnabled) {
                display.setText(typewriterCursorChar);
                // 应用打字机效果
                typewriterEffect.applyEffect(display, line, typewriterSpeed, typewriterStartDelay, typewriterCursorChar);
            } else {
                display.setText(line);
            }
            
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
            Transformation oldTrans = display.getTransformation();
            Transformation newTrans = new Transformation(
                oldTrans.getTranslation(),
                oldTrans.getLeftRotation(),
                new org.joml.Vector3f((float) scale, (float) scale, (float) scale),
                oldTrans.getRightRotation()
            );
            display.setTransformation(newTrans);

            displays.add(new ActiveTextDisplay(display, loc.clone(), i));

            // 应用动画
            applyAnimation(display, animEnabled, animType, animSpeed, animAmplitude, animPhaseOffset, i, tasks);
        }

        activeDisplays.put(player.getUniqueId(), displays);
        activeTasks.put(player.getUniqueId(), tasks);

        // 设置自动移除
        BukkitTask durationTask = Bukkit.getScheduler().runTaskLater(CursorMenuPlugin.plugin, () -> {
            clearPlayerDisplays(player.getUniqueId());
        }, duration);
        tasks.add(durationTask.getTaskId());
    }

    /**
     * 使用MenuLayout配置创建文本显示（支持菜单按钮的打字机效果）
     */
    public void createTextDisplayWithLayout(Player player, String text, Location loc, 
                                             float scale, MenuLayout layout) {
        if (player == null || loc == null || layout == null) return;

        String parsedText = ColorParser.toLegacyString(CursorMenuPlugin.parsePlaceholders(player, text));
        World world = loc.getWorld();
        if (world == null) return;

        TextDisplay display = world.spawn(loc, TextDisplay.class);
        
        // 检查是否启用打字机效果
        if (layout.isTypewriterEnabled()) {
            display.setText(layout.getTypewriterCursorChar());
            typewriterEffect.applyEffect(
                display, 
                parsedText, 
                layout.getTypewriterSpeed(),
                layout.getTypewriterStartDelay(),
                layout.getTypewriterCursorChar()
            );
        } else {
            display.setText(parsedText);
        }

        display.setCustomName(layout.key);
        display.setCustomNameVisible(false);
        display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
        display.setDefaultBackground(false);
        display.setShadowed(true);
        display.setBillboard(TextDisplay.Billboard.CENTER);
        display.setVisibleByDefault(false);
        player.showEntity(CursorMenuPlugin.plugin, display);
        display.setInterpolationDuration(2);

        // 设置缩放和倾斜
        Transformation oldTrans = display.getTransformation();
        Transformation newTrans = new Transformation(
            oldTrans.getTranslation(),
            oldTrans.getLeftRotation().rotationYXZ(
                (float) Math.toRadians(layout.tiltX),
                (float) Math.toRadians(layout.tiltY),
                (float) Math.toRadians(layout.tiltZ)
            ),
            new org.joml.Vector3f((float) layout.getTextSize(), (float) layout.getTextSize(), (float) layout.getTextSize()),
            oldTrans.getRightRotation()
        );
        display.setTransformation(newTrans);
    }

    /**
     * 完成指定玩家的打字机效果
     */
    public void completeTypewriterEffects(UUID playerId) {
        List<ActiveTextDisplay> displays = activeDisplays.get(playerId);
        if (displays != null) {
            displays.forEach(ad -> {
                if (ad.display.isValid() && typewriterEffect.isRunning(ad.display)) {
                    typewriterEffect.completeEffect(ad.display);
                }
            });
        }
    }

    /**
     * 取消指定玩家的打字机效果
     */
    public void cancelTypewriterEffects(UUID playerId) {
        List<ActiveTextDisplay> displays = activeDisplays.get(playerId);
        if (displays != null) {
            displays.forEach(ad -> {
                if (ad.display.isValid()) {
                    typewriterEffect.cancelEffect(ad.display);
                }
            });
        }
    }

    private void applyAnimation(TextDisplay display, boolean enabled, String animType, double speed,
                                double amplitude, double phaseOffset, int index, List<Integer> tasks) {

        if (!enabled || animType == null || "none".equalsIgnoreCase(animType)) {
            return;
        }

        final long period = 1L;
        final double safeSpeed = Math.max(0.01, speed);
        final double safeAmplitude = Math.max(0.0, amplitude);
        final double step = safeSpeed * 0.05;

        switch (animType.toLowerCase()) {
            case "rotate": {
                BukkitTask task = new BukkitRunnable() {
                    float angle = 0;
                    long tick = 0;

                    @Override
                    public void run() {
                        if (!display.isValid()) {
                            this.cancel();
                            return;
                        }
                        angle += step;
                        Transformation oldT = display.getTransformation();
                        Transformation newT = new Transformation(
                            oldT.getTranslation(),
                            oldT.getLeftRotation().rotationYXZ(0, angle, 0),
                            oldT.getScale(),
                            oldT.getRightRotation()
                        );
                        display.setTransformation(newT);
                    }
                }.runTaskTimer(CursorMenuPlugin.plugin, 0L, period);
                tasks.add(task.getTaskId());
                break;
            }

            case "float-y":
            case "up-down": {
                BukkitTask task = new BukkitRunnable() {
                    final org.joml.Vector3f baseTranslation = new org.joml.Vector3f(display.getTransformation().getTranslation());
                    final double phase = phaseOffset * index;
                    long tick = 0;

                    @Override
                    public void run() {
                        if (!display.isValid()) {
                            this.cancel();
                            return;
                        }
                        tick++;
                        float offset = (float) (Math.sin((tick * step) + phase) * safeAmplitude);

                        Transformation oldT = display.getTransformation();
                        Transformation newT = new Transformation(
                            new org.joml.Vector3f(baseTranslation.x, baseTranslation.y + offset, baseTranslation.z),
                            oldT.getLeftRotation(),
                            oldT.getScale(),
                            oldT.getRightRotation()
                        );
                        display.setTransformation(newT);
                    }
                }.runTaskTimer(CursorMenuPlugin.plugin, 0L, period);
                tasks.add(task.getTaskId());
                break;
            }

            case "__old-float-y":
            case "__old-up-down": {
                BukkitTask task = new BukkitRunnable() {
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
                        long tick = 1;
                        float progress = Math.min(tick / (float) totalTicks, 1.0f);
                        double y = start.getY() + total * progress;

                        Transformation oldT = display.getTransformation();
                        Transformation newT = new Transformation(
                            new org.joml.Vector3f(
                                (float) (start.getX() - display.getLocation().getX()),
                                (float) (y - display.getLocation().getY()),
                                (float) (start.getZ() - display.getLocation().getZ())
                            ),
                            oldT.getLeftRotation(),
                            oldT.getScale(),
                            oldT.getRightRotation()
                        );
                        display.setTransformation(newT);

                        if (tick >= totalTicks) this.cancel();
                    }
                }.runTaskTimer(CursorMenuPlugin.plugin, 0L, period);
                break;
            }

            case "float-x":
            case "left-right": {
                BukkitTask task = new BukkitRunnable() {
                    final org.joml.Vector3f baseTranslation = new org.joml.Vector3f(display.getTransformation().getTranslation());
                    final double phase = phaseOffset * index;
                    long tick = 0;

                    @Override
                    public void run() {
                        if (!display.isValid()) {
                            this.cancel();
                            return;
                        }
                        tick++;
                        float offset = (float) (Math.sin((tick * step) + phase) * safeAmplitude);

                        Transformation oldT = display.getTransformation();
                        Transformation newT = new Transformation(
                            new org.joml.Vector3f(baseTranslation.x + offset, baseTranslation.y, baseTranslation.z),
                            oldT.getLeftRotation(),
                            oldT.getScale(),
                            oldT.getRightRotation()
                        );
                        display.setTransformation(newT);
                    }
                }.runTaskTimer(CursorMenuPlugin.plugin, 0L, period);
                tasks.add(task.getTaskId());
                break;
            }

            case "__old-left-right": {
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

                        Transformation oldT = display.getTransformation();
                        Transformation newT = new Transformation(
                            new org.joml.Vector3f(
                                (float) (x - display.getLocation().getX()),
                                0,
                                0
                            ),
                            oldT.getLeftRotation(),
                            oldT.getScale(),
                            oldT.getRightRotation()
                        );
                        display.setTransformation(newT);
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

        // 清除实体和打字机效果
        List<ActiveTextDisplay> displays = activeDisplays.remove(playerId);
        if (displays != null) {
            displays.forEach(display -> {
                if (display.display.isValid()) {
                    typewriterEffect.cancelEffect(display.display);
                    display.display.remove();
                }
            });
        }
    }

    public void cleanup() {
        activeDisplays.keySet().forEach(this::clearPlayerDisplays);
        typewriterEffect.cleanupAll();
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
        boolean animEnabled = section.getBoolean("animation.enabled", !"none".equalsIgnoreCase(animType));
        double animSpeed = section.getDouble("animation.speed", 1.0);
        double animAmplitude = section.getDouble("animation.amplitude", 0.25);
        double animPhaseOffset = section.getDouble("animation.phase-offset", 0.0);
        double spacing = section.getDouble("animation.spacing", 0.5);
        String alignment = section.getString("alignment", "center");
        int duration = section.getInt("duration", 30) * 20;

        // 打字机效果配置
        boolean typewriterEnabled = section.getBoolean("typewriter.enabled", false);
        int typewriterSpeed = Math.max(1, section.getInt("typewriter.speed", 2));
        int typewriterStartDelay = Math.max(0, section.getInt("typewriter.start-delay", 0));
        String typewriterCursorChar = section.getString("typewriter.cursor-char", "|");

        clearPlayerDisplays(player.getUniqueId());

        createTextDisplays(
                player,
                contents,
                scale,
                x, y, z,
                tiltX, tiltY, tiltZ,
                animEnabled, animType, animSpeed, animAmplitude, animPhaseOffset, spacing,
                alignment,
                duration,
                typewriterEnabled, typewriterSpeed, typewriterStartDelay, typewriterCursorChar
        );

        return true;
    }

    public List<String> getAllTextIds() {
        return config.contains("text-displays")
                ? new ArrayList<>(config.getConfigurationSection("text-displays").getKeys(false))
                : new ArrayList<>();
    }
}
