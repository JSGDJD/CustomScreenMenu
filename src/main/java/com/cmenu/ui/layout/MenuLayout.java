package com.cmenu.ui.layout;

import com.cmenu.ui.CursorMenuPlugin;
import com.cmenu.ui.util.CommandUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

import static com.cmenu.ui.CursorMenuPlugin.foliaLib;
import static com.cmenu.ui.CursorMenuPlugin.hasPAPI;

public class MenuLayout {
    public final String key;
    public String name;
    private final List<String> command;
    private final boolean stop;
    public final double x, y, z;
    private boolean isClick = false;
    private final boolean teleportBool;
    private final boolean teleportOriginal;
    private final Location teleportLoc;
    private final boolean stopCommandBool;
    private final List<String> stopCommands;
    public final float tiltX, tiltY, tiltZ;
    private final String permission;
    private final boolean nextMenuEnabled;
    private final String nextMenuKey;
    private long commandDelay = 20;
    private boolean useCommandDelay = false;
    private final String linkCommand;

    // PAPI条件相关字段
    private String conditionVariable;
    private String conditionOperator;
    private String conditionValue;

    // 随机命令相关字段
    private List<String> randomCommands;
    private List<Integer> randomChances;
    private boolean useRandomCommands;

    // 文本悬停放大相关字段
    private boolean hoverEnlargeEnabled;
    private double hoverEnlargeScale;
    
    // 文本大小相关字段
    private double textSize = 1.0;

    // 打字机效果相关字段
    private boolean typewriterEnabled = false;
    private int typewriterSpeed = 2;
    private int typewriterStartDelay = 0;
    private String typewriterCursorChar = "|";

    // 悬停音效相关字段
    private String hoverSound = null;
    private float hoverSoundVolume = 1.0f;
    private float hoverSoundPitch = 1.0f;
    private long hoverSoundCooldown = 20L;

    private boolean floatingAnimationEnabled = false;
    private String floatingAnimationType = "none";
    private double floatingAnimationSpeed = 1.0;
    private double floatingAnimationAmplitude = 0.25;

    public MenuLayout(String key,String name, List<String> command, boolean stop, double x, double y, double z,boolean teleportBool,boolean tBack,Location teleportLoc, boolean stopCommandBool, List<String> stopCommands, float tiltX, float tiltY, float tiltZ, String permission, boolean nextMenuEnabled, String nextMenuKey) {
        this(key, name, command, stop, x, y, z, teleportBool, tBack, teleportLoc, stopCommandBool, stopCommands, tiltX, tiltY, tiltZ, permission, nextMenuEnabled, nextMenuKey, "");
    }

    public MenuLayout(String key, String name, List<String> command, boolean stop, double x, double y, double z,
                      boolean teleportBool, boolean tBack, Location teleportLoc, boolean stopCommandBool,
                      List<String> stopCommands, float tiltX, float tiltY, float tiltZ, String permission,
                      boolean nextMenuEnabled, String nextMenuKey, String linkCommand) {
        this.key = key;
        this.name = name;
        this.command = command;
        this.stop = stop;
        this.x = x;
        this.y = y;
        this.z = z;
        this.isClick = false;
        this.teleportBool = teleportBool;
        this.teleportLoc = teleportLoc;
        this.stopCommandBool = stopCommandBool;
        this.stopCommands = stopCommands;
        this.teleportOriginal = tBack;
        this.tiltX = tiltX;
        this.tiltY = tiltY;
        this.tiltZ = tiltZ;
        this.permission = permission;
        this.nextMenuEnabled = nextMenuEnabled;
        this.nextMenuKey = nextMenuKey;
        this.commandDelay = 20;
        this.linkCommand = linkCommand;
    }

    public void setCommandDelay(long delay) {
        this.commandDelay = delay;
        this.useCommandDelay = delay > 0;
    }

    public boolean isHoverEnlargeEnabled() { return hoverEnlargeEnabled; }
    public double getHoverEnlargeScale() { return hoverEnlargeScale; }
    public double getTextSize() { return textSize; }
    public boolean isTypewriterEnabled() { return typewriterEnabled; }
    public int getTypewriterSpeed() { return typewriterSpeed; }
    public int getTypewriterStartDelay() { return typewriterStartDelay; }
    public String getTypewriterCursorChar() { return typewriterCursorChar; }
    public String getHoverSound() { return hoverSound; }
    public float getHoverSoundVolume() { return hoverSoundVolume; }
    public float getHoverSoundPitch() { return hoverSoundPitch; }
    public long getHoverSoundCooldown() { return hoverSoundCooldown; }
    public boolean isFloatingAnimationEnabled() { return floatingAnimationEnabled; }
    public String getFloatingAnimationType() { return floatingAnimationType; }
    public double getFloatingAnimationSpeed() { return floatingAnimationSpeed; }
    public double getFloatingAnimationAmplitude() { return floatingAnimationAmplitude; }

    public List<String> getCommands() { return command; }
    
    public void loadConfig(ConfigurationSection config) {
        if (config.contains("command-delay")) {
            setCommandDelay(config.getLong("command-delay", 20));
        }

        if (config.contains("condition.variable") && config.contains("condition.operator") && config.contains("condition.value")) {
            this.conditionVariable = config.getString("condition.variable");
            this.conditionOperator = config.getString("condition.operator");
            this.conditionValue = config.getString("condition.value");
        }

        if (config.contains("random-commands") && config.contains("random-chances")) {
            this.randomCommands = config.getStringList("random-commands");
            this.randomChances = config.getIntegerList("random-chances");
            this.useRandomCommands = this.randomCommands.size() == this.randomChances.size() && !this.randomCommands.isEmpty();
        }

        if (config.contains("hover-enlarge.enabled")) {
            this.hoverEnlargeEnabled = config.getBoolean("hover-enlarge.enabled");
            this.hoverEnlargeScale = config.getDouble("hover-enlarge.scale", 1.2);
        }
        
        if (config.contains("text-size")) {
            this.textSize = config.getDouble("text-size", 1.0);
        }

        if (config.contains("typewriter")) {
            ConfigurationSection typewriterConfig = config.getConfigurationSection("typewriter");
            if (typewriterConfig != null) {
                this.typewriterEnabled = typewriterConfig.getBoolean("enabled", false);
                this.typewriterSpeed = Math.max(1, typewriterConfig.getInt("speed", 2));
                this.typewriterStartDelay = Math.max(0, typewriterConfig.getInt("start-delay", 0));
                this.typewriterCursorChar = typewriterConfig.getString("cursor-char", "|");
            }
        }

        if (config.contains("hover-sound")) {
            ConfigurationSection soundConfig = config.getConfigurationSection("hover-sound");
            if (soundConfig != null) {
                String soundName = soundConfig.getString("name");
                if (soundName != null && !soundName.isEmpty()) {
                    this.hoverSound = soundName;
                    this.hoverSoundVolume = (float) soundConfig.getDouble("volume", 1.0);
                    this.hoverSoundPitch = (float) soundConfig.getDouble("pitch", 1.0);
                    this.hoverSoundCooldown = Math.max(5L, soundConfig.getLong("cooldown", 20));
                }
            }
        }

        if (config.contains("floating-animation")) {
            ConfigurationSection animationConfig = config.getConfigurationSection("floating-animation");
            if (animationConfig != null) {
                this.floatingAnimationEnabled = animationConfig.getBoolean("enabled", false);
                this.floatingAnimationType = animationConfig.getString("type", "up-down");
                this.floatingAnimationSpeed = Math.max(0.01, animationConfig.getDouble("speed", 1.0));
                this.floatingAnimationAmplitude = Math.max(0.0, animationConfig.getDouble("amplitude", 0.25));
            }
        }
    }

    public void runCommand(Player player) {
        if (!permission.isEmpty() && !player.hasPermission(permission) && !player.isOp()) {
            player.sendMessage(CursorMenuPlugin.plugin.getLangMessage("permission.no_permission_button", "&c[CursorMenu] 你没有权限执行该按钮"));
            return;
        }

        if (CursorMenuPlugin.plugin.requiresLogin(name, key) && !CursorMenuPlugin.plugin.isPlayerLoggedIn(player)) {
            CursorMenuPlugin.plugin.sendActionBarMessage(player, CursorMenuPlugin.plugin.getLangMessage("menu.require_login", "&c[CursorMenu] 请先登录后再执行此操作"));
            return;
        }

        if (CursorMenuPlugin.plugin.requiresNotRegistered(name, key) && CursorMenuPlugin.plugin.getUserDataManager().isUserRegistered(player.getName())) {
            CursorMenuPlugin.plugin.sendActionBarMessage(player, CursorMenuPlugin.plugin.getLangMessage("menu.already_registered", "&c[CursorMenu] 您已注册，请直接登录"));
            foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                CursorMenuPlugin.plugin.setupCursor(player, "登录菜单");
            }, null, 5L);
            return;
        }

        if (conditionVariable != null && conditionOperator != null && conditionValue != null) {
            if (!checkCondition(player)) {
                player.sendMessage(CursorMenuPlugin.plugin.getLangMessage("menu.condition_not_met", "&c[CursorMenu] 条件不满足，无法执行该操作"));
                return;
            }
        }

        if (stop && player.isInsideVehicle()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                vehicle.removePassenger(player);
                vehicle.remove();
            }
        }

        CursorMenuPlugin.plugin.selectedLayouts.put(player, this);
        if (this.isClick) return;
        this.isClick = true;

        foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
            this.isClick = false;
        }, null, 1L);

        if (CursorMenuPlugin.plugin.getCurrentPlayerMenu(player) != null &&
                (CursorMenuPlugin.plugin.getCurrentPlayerMenu(player).equals("login_menu") ||
                        CursorMenuPlugin.plugin.getCurrentPlayerMenu(player).equals("register_menu") ||
                        CursorMenuPlugin.plugin.getCurrentPlayerMenu(player).equals("login")) &&
                !(nextMenuEnabled && !nextMenuKey.isEmpty())) {

            switch (this.key) {
                case "cancel_button":
                    CursorMenuPlugin.plugin.stopCursor(player, true);
                    break;
            }
        }

        if (linkCommand != null && !linkCommand.isEmpty()) {
            sendClickableLink(player, linkCommand);
        } else {
            if (useRandomCommands) {
                executeRandomCommands(player);
            } else {
                for (String cmd : command) {
                    String processedCmd = cmd;
                    if (hasPAPI) {
                        processedCmd = PlaceholderAPI.setPlaceholders(player, processedCmd);
                    }
                    String finalProcessedCmd = processedCmd;
                    if (useCommandDelay) {
                        foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                            if (CursorMenuPlugin.plugin.playerCursors.containsKey(player)) {
                                dispatchCommand(player, finalProcessedCmd);
                            }
                        }, null, commandDelay);
                    } else {
                        dispatchCommand(player, finalProcessedCmd);
                    }
                }
            }
        }

        if (nextMenuEnabled && !nextMenuKey.isEmpty()) {
            foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                CursorMenuPlugin.plugin.setupCursor(player, nextMenuKey);
            }, null, 5L);
            return;
        }

        if (stop) {
            foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                boolean teleportBack = teleportBool && teleportOriginal;
                CursorMenuPlugin.plugin.stopCursor(player, teleportBack);
                this.teleport(player);
                this.runStopCommand(player);
            }, null, 2L);
        }

        foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
            this.isClick = false;
        }, null, 15L);
    }

    private void sendClickableLink(Player player, String linkCommand) {
        if (linkCommand.startsWith("[link]")) {
            String linkPart = linkCommand.substring(6);
            String[] parts = linkPart.split("\\|", 2);
            
            String url = parts[0];
            String displayText = parts.length > 1 ? parts[1] : url;
            
            TextComponent linkComponent = new TextComponent(
                ChatColor.translateAlternateColorCodes('&', displayText)
            );
            linkComponent.setClickEvent(new ClickEvent(
                ClickEvent.Action.OPEN_URL, url
            ));
            
            player.spigot().sendMessage(ChatMessageType.CHAT, linkComponent);
        } else {
            String linkMessage = ChatColor.translateAlternateColorCodes('&', linkCommand);
            player.spigot().sendMessage(ChatMessageType.CHAT, TextComponent.fromLegacyText(linkMessage));
        }
    }

    private void executeRandomCommands(Player player) {
        Random random = new Random();
        int totalChance = 0;

        for (int chance : randomChances) {
            totalChance += chance;
        }

        if (totalChance <= 0) {
            return;
        }

        int randomValue = random.nextInt(totalChance) + 1;
        int currentChance = 0;

        for (int i = 0; i < randomCommands.size(); i++) {
            currentChance += randomChances.get(i);
            if (randomValue <= currentChance) {
                String cmd = randomCommands.get(i);
                String processedCmd = cmd;
                if (hasPAPI) {
                    processedCmd = PlaceholderAPI.setPlaceholders(player, processedCmd);
                }

                String finalProcessedCmd = processedCmd;
                if (useCommandDelay) {
                    foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                        if (CursorMenuPlugin.plugin.playerCursors.containsKey(player)) {
                            dispatchCommand(player, finalProcessedCmd);
                        }
                    }, null, commandDelay);
                } else {
                    dispatchCommand(player, finalProcessedCmd);
                }
                break;
            }
        }
    }

    private void dispatchCommand(Player player, String cmd) {
        CommandUtils.executeCommand(player, cmd);
    }

    private void connectToServer(Player player, String serverName) {
        CommandUtils.connectToServer(player, serverName);
    }

    private void teleport(Player player) {
        if (!teleportBool) return;

        Location loc;
        if (teleportOriginal) {
            loc = CursorMenuPlugin.plugin.playerLocations.get(player);
        } else {
            loc = teleportLoc.clone();
        }

        if (loc != null && loc.getWorld() != null) {
            loc.setYaw(CursorMenuPlugin.exitYaw);
            loc.setPitch(CursorMenuPlugin.exitPitch);

            World targetWorld = loc.getWorld();
            if (targetWorld != null) {
                player.teleport(loc);
            } else {
                Location safeLoc = player.getLocation().clone();
                safeLoc.setYaw(CursorMenuPlugin.exitYaw);
                safeLoc.setPitch(CursorMenuPlugin.exitPitch);
                player.teleport(safeLoc);
            }
        } else {
            Location loc2 = player.getLocation();
            loc2.setYaw(CursorMenuPlugin.exitYaw);
            loc2.setPitch(CursorMenuPlugin.exitPitch);
            player.teleport(loc2);
        }
    }

    private void runStopCommand(Player player) {
        if(this.stopCommandBool) {
            for(String cmd : stopCommands) {
                CommandUtils.executeCommand(player, cmd);
            }
        }
    }

    private boolean checkCondition(Player player) {
        if (!hasPAPI) return true;

        String variableValue = PlaceholderAPI.setPlaceholders(player, conditionVariable);

        try {
            double varValue = Double.parseDouble(variableValue);
            double condValue = Double.parseDouble(conditionValue);

            switch (conditionOperator) {
                case ">": return varValue > condValue;
                case ">=": return varValue >= condValue;
                case "<": return varValue < condValue;
                case "<=": return varValue <= condValue;
                case "==": return varValue == condValue;
                case "!=": return varValue != condValue;
                default: return false;
            }
        } catch (NumberFormatException e) {
            switch (conditionOperator) {
                case "==": return variableValue.equals(conditionValue);
                case "!=": return !variableValue.equals(conditionValue);
                default: return false;
            }
        }
    }

    private boolean requiresLogin() {
        return false;
    }

    private boolean requiresNotRegistered() {
        return false;
    }
}
