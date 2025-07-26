package com.example.ui;

import static com.example.ui.CursorMenuPlugin.itemDisplayManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.example.ui.CursorMenuPlugin.plugin;
import static com.example.ui.CursorMenuPlugin.sectionManager;

public class Commands implements CommandExecutor, TabExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        switch (args.length) {

            case 0:
                sender.sendMessage(ChatColor.RED + "[CursorMenu] 未知参数");
                return true;

            case 1:
                switch (args[0].toLowerCase()) {
                    case "run":
                        sender.sendMessage(ChatColor.RED + "[CursorMenu] 输入对应的菜单选项");
                        return true;

                    case "stop":
                        if(sender instanceof Player) {
                            if (sender.hasPermission("cursormenu.stop")) {
                                plugin.stopCursor((Player) sender,true);
                            } else {
                                sender.sendMessage(ChatColor.RED + "[CursorMenu] 你没有权限使用该命令");
                                return true;
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "[CursorMenu] 仅玩家可用");
                        }
                        return true;

                    case "reload":
                        if (sender.hasPermission("cursormenu.reload")) {
                            plugin.reloadPluginConfig();
                            sender.sendMessage(ChatColor.GREEN + "[CursorMenu] 插件重载...");
                        } else {
                            sender.sendMessage(ChatColor.RED + "[CursorMenu] 你没有权限使用该命令");
                        }
                        return true;

                    case "itemsstop":
                        if(sender instanceof Player) {
                            if (sender.hasPermission("cursormenu.itemsstop")) {
                                itemDisplayManager.hideItem((Player) sender);
                                sender.sendMessage(ChatColor.GREEN + "[CursorMenu] 已关闭物品显示");
                            } else {
                                sender.sendMessage(ChatColor.RED + "[CursorMenu] 你没有权限使用该命令");
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "[CursorMenu] 仅玩家可用");
                        }
                        return true;

                    default:
                        sender.sendMessage(ChatColor.RED + "[CursorMenu] 未知参数");
                        return true;
                }

            case 2:
                switch (args[0].toLowerCase()) {
                    case "run":
                        if (sender.hasPermission("cursormenu.start")) {
                            if(sectionManager.has(args[1])){
                                plugin.setupCursor((Player) sender,args[1]);
                            } else {
                                sender.sendMessage(ChatColor.RED + "[CursorMenu] 无该菜单选项");
                            }
                            return true;
                        } else {
                            sender.sendMessage(ChatColor.RED + "[CursorMenu] 你没有权限使用该命令");
                            return true;
                        }

                    case "items":
                        if (sender instanceof Player) {
                            if (sender.hasPermission("cursormenu.items")) {
                                String id = args[1];
                                if (!itemDisplayManager.showItem((Player) sender, id)) {
                                    sender.sendMessage(ChatColor.RED + "[CursorMenu] 物品ID不存在: " + id);
                                }
                            } else {
                                sender.sendMessage(ChatColor.RED + "[CursorMenu] 你没有权限使用该命令");
                            }
                        } else {
                            sender.sendMessage(ChatColor.RED + "[CursorMenu] 仅玩家可用");
                        }
                        return true;

                    default:
                        sender.sendMessage(ChatColor.RED + "[CursorMenu] 未知参数");
                        return true;
                }

            default:
                sender.sendMessage(ChatColor.RED + "[CursorMenu] 未知参数");
                return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        switch (args.length) {

            case 1:

                if (commandSender.hasPermission("cursormenu.reload")) list.add("reload");
                if (commandSender.hasPermission("cursormenu.start")) list.add("run");
                if (commandSender.hasPermission("cursormenu.stop")) list.add("stop");
                if (commandSender.hasPermission("cursormenu.items")) list.add("items");
                if (commandSender.hasPermission("cursormenu.itemsstop")) list.add("itemsstop");

                return StringUtil.copyPartialMatches(args[0].toLowerCase(), list, new ArrayList<String>());

            case 2:
                switch (args[0].toLowerCase()) {
                    case "run":
                        if (commandSender.hasPermission("cursormenu.start")){
                            list.addAll(sectionManager.keySet());
                            return StringUtil.copyPartialMatches(args[1].toLowerCase(), list, new ArrayList<String>());
                        }
                    case "items":
                        if (commandSender.hasPermission("cursormenu.items")){
                            list.addAll(itemDisplayManager.getAllItemIds());
                            return StringUtil.copyPartialMatches(args[1], list, new ArrayList<String>());
                        }

                    default:
                        return Collections.emptyList();
                }

            default:
                return Collections.emptyList();
        }
    }
}
