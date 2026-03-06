package com.cmenu.ui.layout;

import com.cmenu.ui.CursorMenuPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.cmenu.ui.CursorMenuPlugin.*;

public class MenuLayout {

    public final boolean nextMenuEnabled;
    public final String nextMenuKey;

    public final float tiltX, tiltY, tiltZ;

    public double x;

    public double y;

    public double z;

    public String name;

    public List<String> command;

    public boolean stop;

    public String key;

    public boolean teleportBool;

    public Location teleportLoc;

    public boolean stopCommandBool;

    public List<String> stopCommands;

    private boolean isClick;

    public boolean teleportOriginal;
    public final String permission;
    private long commandDelay;
    private boolean useCommandDelay;
    
    // PAPI条件相关字段
    private String conditionVariable;
    private String conditionOperator;
    private String conditionValue;
    
    // 随机命令相关字段
    private List<String> randomCommands;
    private List<Integer> randomChances;
    private boolean useRandomCommands;

    public MenuLayout(String key,String name, List<String> command, boolean stop, double x, double y, double z,boolean teleportBool,boolean tBack,Location teleportLoc, boolean stopCommandBool, List<String> stopCommands, float tiltX, float tiltY, float tiltZ, String permission, boolean nextMenuEnabled, String nextMenuKey) {
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
        this.conditionVariable = null;
        this.conditionOperator = null;
        this.conditionValue = null;
        this.useRandomCommands = false;
    }

    public void loadConfig(ConfigurationSection config) {
        this.useCommandDelay = config.contains("command-delay");
        this.commandDelay     = config.getLong("command-delay", 20);
        
        // 加载PAPI条件
        if (config.contains("condition.variable") && config.contains("condition.operator") && config.contains("condition.value")) {
            this.conditionVariable = config.getString("condition.variable");
            this.conditionOperator = config.getString("condition.operator");
            this.conditionValue = config.getString("condition.value");
        }
        
        // 加载随机命令配置
        if (config.contains("random-commands") && config.contains("random-chances")) {
            this.randomCommands = config.getStringList("random-commands");
            this.randomChances = config.getIntegerList("random-chances");
            this.useRandomCommands = this.randomCommands.size() == this.randomChances.size() && !this.randomCommands.isEmpty();
        }
    }

    public void runCommand(Player player) {
        if (!permission.isEmpty() && !player.hasPermission(permission) && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "[CursorMenu] 你没有权限执行该按钮");
            return;
        }
        
        // 检查PAPI条件
        if (conditionVariable != null && conditionOperator != null && conditionValue != null) {
            if (!checkCondition(player)) {
                player.sendMessage(ChatColor.RED + "[CursorMenu] 条件不满足，无法执行该操作");
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

        //plugin.stopCursor(player, false);

        CursorMenuPlugin.plugin.selectedLayouts.put(player, this);
        if (this.isClick) return;
        this.isClick = true;

        foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
            this.isClick = false;
        }, null, 1L);
        
        // 如果配置了随机命令，则使用随机命令逻辑，否则使用默认命令逻辑
        if (useRandomCommands) {
            executeRandomCommands(player);
        } else {
            // 修改命令执行循环，添加延迟 {new}
            for (String cmd : command) {  // {new}
                // 处理命令占位符 {new}
                String processedCmd = cmd.replaceAll("%player%", player.getName());  // {new}
                if (hasPAPI) {  // {new}
                    processedCmd = PlaceholderAPI.setPlaceholders(player, processedCmd);  // {new}
                }  // {new}

                // 使用延迟调度器执行命令 {new}
                // 使用延迟调度器执行命令 {new}
                String finalProcessedCmd = processedCmd;
                if (useCommandDelay) {
                    foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                        // 检查玩家是否仍在菜单中再执行命令
                        if (plugin.playerCursors.containsKey(player)) {
                            dispatchCommand(player, finalProcessedCmd);
                        }
                    }, null, commandDelay);
                } else {
                    dispatchCommand(player, finalProcessedCmd);
                }
            }  // {new}
        }

        // ✅ 跳转菜单逻辑（移出 for 循环）
        if (nextMenuEnabled && !nextMenuKey.isEmpty()) {
            foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                plugin.setupCursor(player, nextMenuKey);
            }, null, 5L);
            return;
        }

        // ✅ 如果没有跳转，才执行 stop
        if (stop) {
            foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                plugin.stopCursor(player, false);
                this.teleport(player);
                this.runStopCommand(player);
            }, null, 2L);
        }


        foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
            this.isClick = false;
        }, null, 15L);

        System.out.println("玩家 " + player.getName() + " 目前状态: " + player.isInsideVehicle());
    }

    // 执行随机命令的方法
    private void executeRandomCommands(Player player) {
        Random random = new Random();
        int totalChance = 0;
        
        // 计算总概率
        for (int chance : randomChances) {
            totalChance += chance;
        }
        
        // 如果总概率为0，则不执行任何命令
        if (totalChance <= 0) {
            return;
        }
        
        // 生成随机数
        int randomValue = random.nextInt(totalChance) + 1;
        int currentChance = 0;
        
        // 确定执行哪个命令
        for (int i = 0; i < randomCommands.size(); i++) {
            currentChance += randomChances.get(i);
            if (randomValue <= currentChance) {
                String cmd = randomCommands.get(i);
                // 处理命令占位符
                String processedCmd = cmd.replaceAll("%player%", player.getName());
                if (hasPAPI) {
                    processedCmd = PlaceholderAPI.setPlaceholders(player, processedCmd);
                }
                
                // 使用延迟调度器执行命令
                String finalProcessedCmd = processedCmd;
                if (useCommandDelay) {
                    foliaLib.scheduling().entitySpecificScheduler(player).runDelayed(task -> {
                        // 检查玩家是否仍在菜单中再执行命令
                        if (plugin.playerCursors.containsKey(player)) {
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

    private boolean checkCondition(Player player) {
        if (!hasPAPI) return true;
        
        // 获取PAPI变量的值
        String variableValue = PlaceholderAPI.setPlaceholders(player, conditionVariable);
        
        try {
            // 尝试将两个值都解析为数字进行数值比较
            double varValue = Double.parseDouble(variableValue);
            double condValue = Double.parseDouble(conditionValue);
            
            switch (conditionOperator) {
                case ">":
                    return varValue > condValue;
                case ">=":
                    return varValue >= condValue;
                case "<":
                    return varValue < condValue;
                case "<=":
                    return varValue <= condValue;
                case "==":
                case "=":
                    return varValue == condValue;
                case "!=":
                    return varValue != condValue;
            }
        } catch (NumberFormatException e) {
            // 如果不能解析为数字，则进行字符串比较
            switch (conditionOperator) {
                case "==":
                case "=":
                    return variableValue.equals(conditionValue);
                case "!=":
                    return !variableValue.equals(conditionValue);
                // 字符串不支持大小比较
            }
        }
        
        return true; // 如果操作符不支持，则默认通过
    }

    private void teleport(Player player) {
        if (this.teleportBool) {
            Location loc;
            if (this.teleportOriginal) {
                loc = playerLocations.remove(player);
            } else {
                loc = teleportLoc.clone();
            }

            // ✅ 强制使用 exit-camera 的 yaw 和 pitch
            loc.setYaw(CursorMenuPlugin.exitYaw);
            loc.setPitch(CursorMenuPlugin.exitPitch);

            player.teleport(loc);
        } else {
            // ✅ 如果没有传送，仍然应用 exit-camera 的 yaw/pitch
            Location loc = player.getLocation();
            loc.setYaw(CursorMenuPlugin.exitYaw);
            loc.setPitch(CursorMenuPlugin.exitPitch);
            player.teleport(loc);
        }
    }

    private void runStopCommand(Player player) {
        if(this.stopCommandBool) {
            for(String cmd : stopCommands) {
                cmd = cmd.replaceAll("%player%", player.getName());
                if(hasPAPI){
                    cmd = PlaceholderAPI.setPlaceholders(player, cmd);
                }
                if(cmd.toLowerCase().startsWith("[console]")) {
                    cmd = cmd.replaceAll("\\[console\\]", "").trim();
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } else if(cmd.toLowerCase().startsWith("[op]")) {
                    cmd = cmd.replaceAll("\\[op\\]", "").trim();
                    if(player.isOp()){
                        player.performCommand(cmd);
                    } else {
                        try {
                            player.setOp(true);
                            player.performCommand(cmd);
                        } finally {
                            player.setOp(false);
                        }
                    }
                } else {
                    if(cmd.toLowerCase().startsWith("[player]")) {
                        cmd = cmd.replaceAll("\\[player\\]", "").trim();
                    }
                    player.performCommand(cmd);
                }
            }
        }
    }

    private void dispatchCommand(Player player, String cmd) {
        if (cmd.toLowerCase().startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replaceAll("(?i)\\[console\\]", "").trim());
        } else if (cmd.toLowerCase().startsWith("[op]")) {
            String realCmd = cmd.replaceAll("(?i)\\[op\\]", "").trim();
            if (player.isOp()) {
                player.performCommand(realCmd);
            } else {
                player.setOp(true);
                try { player.performCommand(realCmd); }
                finally { player.setOp(false); }
            }
        } else if (cmd.toLowerCase().startsWith("[server]")) {
            // 添加对Velocity服务器的server命令支持
            String serverName = cmd.replaceAll("(?i)\\[server\\]", "").trim();
            connectToServer(player, serverName);
        } else {
            String realCmd = cmd.replaceAll("(?i)\\[player\\]", "").trim();
            player.performCommand(realCmd);
        }
    }

    /**
     * 连接到Velocity代理的其他服务器
     * @param player 玩家
     * @param serverName 服务器名称
     */
    private void connectToServer(Player player, String serverName) {
        try {
            // 创建BungeeCord/Velocity连接数据包
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            
            // 发送插件消息
            player.sendPluginMessage(CursorMenuPlugin.plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED + "[CursorMenu] 无法连接到服务器: " + serverName);
            e.printStackTrace();
        }
    }
}