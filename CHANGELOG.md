# CustomScreenMenu 更新日志 / Changelog

## [1.4.4] - 2026-04-06

### ✨ 新增功能 / New Features

#### 打字机效果 / Typewriter Effect
**文字逐字显示动画** - 为文本元素添加打字机效果，让文字像打字一样逐字显示。

- 可配置显示速度（每个字符显示间隔）
- 可配置开始延迟（延迟多少tick后开始显示）
- 可配置光标字符（显示在文字末尾的闪烁光标）
- 支持热重载，修改配置后 `/cmenu reload` 即可生效

**Text Character-by-Character Animation** - Add typewriter effect to text elements, displaying text character by character like typing.

- Configurable display speed (interval between each character)
- Configurable start delay (ticks before display starts)
- Configurable cursor character (blinking cursor at the end of text)
- Hot reload support, changes take effect after `/cmenu reload`

#### 悬停音效 / Hover Sound Effect
**鼠标悬停音效** - 当玩家鼠标悬停在文本元素上时播放音效。

- 可配置音效名称（支持所有Minecraft音效）
- 可配置音量和音调
- 可配置冷却时间（防止音效过于频繁播放）
- 内置音效回退机制（配置音效无效时自动尝试备用音效）

**Mouse Hover Sound Effect** - Play sound when player hovers mouse over text elements.

- Configurable sound name (supports all Minecraft sounds)
- Configurable volume and pitch
- Configurable cooldown (prevent sound from playing too frequently)
- Built-in sound fallback mechanism (automatically try backup sounds when configured sound is invalid)

### 🐛 问题修复 / Bug Fixes

**修复 join-run 命令占位符不替换问题** - 修复了 `config.yml` 中 `join-run.commands` 配置的命令不替换 `%player%` 等占位符的问题。

- 现在无论是否安装 PlaceholderAPI，内置占位符都会正确替换
- 支持的内置占位符：`%player%`, `%player_name%`, `%player_uuid%`, `%player_displayname%`, `%player_x%`, `%player_y%`, `%player_z%`, `%player_world%`, `%player_health%` 等

**Fixed join-run Command Placeholder Issue** - Fixed issue where placeholders like `%player%` were not replaced in `join-run.commands` configuration.

- Built-in placeholders are now correctly replaced regardless of PlaceholderAPI installation
- Supported built-in placeholders: `%player%`, `%player_name%`, `%player_uuid%`, `%player_displayname%`, `%player_x%`, `%player_y%`, `%player_z%`, `%player_world%`, `%player_health%`, etc.

### 🔧 代码优化 / Code Optimizations

**线程安全优化** - 将 `HoverEnlargeManager` 中的 `HashMap` 替换为 `ConcurrentHashMap`，提高多线程环境下的安全性。

**内存优化** - 优化了 `CursorMenuPlugin.getPlayerTextDisplays()` 方法，使用 `Collections.emptyList()` 替代创建新的 `ArrayList`。

**重复清理修复** - 修复了 `TypewriterEffect` 中可能出现的重复清理问题，添加了 `completed` 标志防止重复操作。

**Thread Safety Optimization** - Replaced `HashMap` with `ConcurrentHashMap` in `HoverEnlargeManager` for better safety in multi-threaded environments.

**Memory Optimization** - Optimized `CursorMenuPlugin.getPlayerTextDisplays()` method to use `Collections.emptyList()` instead of creating new `ArrayList`.

**Duplicate Cleanup Fix** - Fixed potential duplicate cleanup issue in `TypewriterEffect`, added `completed` flag to prevent duplicate operations.

### 📝 配置示例 / Configuration Example

```yaml
# 菜单配置示例 (menu/example.yml)
layouts:
  welcome_text:
    type: text
    text: "&a欢迎来到服务器！"
    # 打字机效果配置
    typewriter-enabled: true
    typewriter-speed: 2          # 每2tick显示一个字符
    typewriter-start-delay: 10   # 延迟10tick后开始
    typewriter-cursor-char: "|"  # 光标字符
    # 悬停音效配置
    hover-sound: "block.note.pling"
    hover-sound-volume: 1.0
    hover-sound-pitch: 1.0
    hover-sound-cooldown: 20     # 20tick冷却时间
```

### 📦 文件变更 / File Changes

| 文件 / File | 变更内容 / Changes |
|-------------|-------------------|
| `TypewriterEffect.java` | 新增打字机效果管理器 |
| `HoverSoundManager.java` | 新增悬停音效管理器 |
| `MenuLayout.java` | 添加打字机和悬停音效配置字段 |
| `TextDisplayManager.java` | 集成打字机效果 |
| `HoverEnlargeManager.java` | 集成悬停音效、线程安全优化 |
| `CursorMenuPlugin.java` | 添加TextDisplay查找方法、修复join-run占位符 |
| `CommandUtils.java` | 添加内置占位符替换方法 |
| `lang.yml` | 添加打字机和悬停音效配置帮助信息 |

---

## [1.4.3] - 2026-03-20

### 🔧 1.21.11 数据包兼容性修复 / 1.21.11 Packet Compatibility Fix

**Paper 1.21.11 兼容性完整修复** - 解决了在Paper 1.21.11版本中set_entity_data数据包异常导致玩家被踢出的问题。

- 所有Transformation操作改为创建新对象而非修改现有对象
- 修复了double到float类型转换的编译错误
- 添加了缺失的Transformation导入
- 完整兼容Paper 1.21.11版本

**Complete Paper 1.21.11 Compatibility Fix** - Resolved set_entity_data packet exception causing player kicks in Paper 1.21.11.

- All Transformation operations now create new objects instead of modifying existing ones
- Fixed double to float type conversion compilation errors
- Added missing Transformation imports
- Fully compatible with Paper 1.21.11

---

## [1.4.2] - 2026-03-19

### 🐛 实体数据包兼容性问题修复 / Entity Packet Compatibility Fix

**修复1.21.x版本实体数据包异常** - 修复了因TextDisplay/ItemDisplay实体Transformation数据设置错误导致的客户端崩溃问题。

- 修复了TextDisplay旋转动画中EulerAngle设置不正确的问题
- 修复了ItemDisplay旋转逻辑中rotateAxis方法调用错误的问题
- 修复了MenuLayout中TextDisplay倾斜角度设置未生效的问题
- 正确使用JOML的AxisAngle4f和Quaternionf替代旧方法

**Fixed Entity Packet Issue for 1.21.x** - Fixed client crash caused by incorrect TextDisplay/ItemDisplay entity Transformation data.

- Fixed incorrect EulerAngle setting in TextDisplay rotation animation
- Fixed incorrect rotateAxis method call in ItemDisplay rotation logic
- Fixed TextDisplay tilt angle setting not being applied in MenuLayout
- Properly use JOML's AxisAngle4f and Quaternionf instead of deprecated methods

### 🔒 安全性更新 / Security Updates

**密码哈希算法升级** - 从不安全的SHA-256升级到BCrypt加密算法。

- 新注册用户自动使用BCrypt加密
- 旧用户首次登录成功后自动升级密码格式
- BCrypt轮次设置为12，提供良好的安全性和性能平衡

**Password Hashing Algorithm Upgrade** - Upgraded from insecure SHA-256 to BCrypt encryption algorithm.

- New users automatically use BCrypt encryption
- Legacy passwords auto-upgrade after first successful login
- BCrypt rounds set to 12 for good security and performance balance

**临时OP操作安全性增强** - 改进了`[op]`命令执行的安全机制，确保OP状态正确恢复。

**Temporary OP Operation Security Enhancement** - Improved security mechanism for `[op]` command execution, ensuring OP status is correctly restored.

### 🧵 并发安全修复 / Concurrency Safety Fixes

**修复非线程安全集合** - 将所有HashMap/HashSet替换为线程安全的ConcurrentHashMap，解决了多线程环境下可能出现的并发问题。

**Fixed Non-Thread-Safe Collections** - Replaced all HashMap/HashSet with thread-safe ConcurrentHashMap, resolving potential concurrency issues in multi-threaded environments.

### 📝 消息配置化 / Message Configuration

**全面配置化消息** - 所有提示信息现在都可以在`lang.yml`中自定义配置。

- 支持热加载，修改配置后使用`/cursormenu reload`即可生效
- 新增50+条可配置消息
- 配置分类：登录提示、注册提示、权限提示、命令提示、玩家管理提示、菜单提示、系统提示、NPC提示、WASD导航提示

**Full Message Configuration** - All prompt messages can now be customized in `lang.yml`.

- Hot reload support, changes take effect after `/cursormenu reload`
- Added 50+ configurable messages
- Configuration categories: login, register, permission, command, player management, menu, system, NPC, WASD navigation

### 📊 统计系统 / Statistics System

**集成bStats统计** - 插件现在使用bStats收集匿名使用统计数据。

- 自动收集服务器数量、玩家数量等基础数据
- 数据完全匿名，不收集任何个人信息
- 可在`plugins/bStats/config.yml`中禁用

**Integrated bStats Statistics** - The plugin now uses bStats to collect anonymous usage statistics.

- Automatically collects basic data like server count, player count
- Data is completely anonymous, no personal information collected
- Can be disabled in `plugins/bStats/config.yml`

### 🧹 代码质量改进 / Code Quality Improvements

**新增命令执行工具类** - 统一了命令执行逻辑，支持`[console]`、`[op]`、`[player]`、`[server]`、`[message]`、`[actionbar]`等命令前缀。

**Added Command Execution Utility Class** - Unified command execution logic, supporting `[console]`, `[op]`, `[player]`, `[server]`, `[message]`, `[actionbar]` command prefixes.

**代码清理** - 清理了重复的导入语句，简化了代码结构。

**Code Cleanup** - Cleaned up duplicate import statements, simplified code structure.

### 📦 文件变更 / File Changes

| 文件 / File | 变更内容 / Changes |
|-------------|-------------------|
| `pom.xml` | 添加BCrypt依赖、bStats依赖，版本更新至1.4.2 |
| `plugin.yml` | 版本更新至1.4.2 |
| `lang.yml` | 新增50+条可配置消息 |
| `CommandUtils.java` | 新增命令执行工具类 |
| `Commands.java` | 使用配置化消息 |
| `CursorMenuPlugin.java` | 使用配置化消息、线程安全集合、集成bStats、修复EulerAngle |
| `MenuLayout.java` | 使用配置化消息 |
| `TextDisplayManager.java` | 修复旋转动画EulerAngle设置 |
| `ItemDisplayManager.java` | 修复旋转逻辑rotateAxis调用 |
| `UserDataManager.java` | 升级密码哈希算法 |
| `DatabaseManager.java` | 新增获取密码哈希方法 |

### ⚠️ 注意事项 / Important Notes

- BCrypt计算比SHA-256慢，这是正常的安全特性
- 旧密码在首次成功登录后自动升级为BCrypt格式
- 无需手动迁移数据库
- bStats统计数据完全匿名，可在配置中禁用

- BCrypt computation is slower than SHA-256, this is a normal security feature
- Legacy passwords auto-upgrade to BCrypt format after first successful login
- No manual database migration required
- bStats statistics are completely anonymous and can be disabled in configuration

---

## [1.4.1] - 2026-03-10

📌 更新日志 – v1.4.1

🔒 安全性更新

**密码哈希算法升级** - 从不安全的SHA-256升级到BCrypt加密算法。

新注册用户自动使用BCrypt加密，旧用户首次登录成功后自动升级密码格式。

**临时OP操作安全性增强** - 改进了`[op]`命令执行的安全机制，确保OP状态正确恢复。

🧵 并发安全修复

**修复非线程安全集合** - 将所有HashMap/HashSet替换为线程安全的ConcurrentHashMap，解决了多线程环境下可能出现的并发问题。

📝 消息配置化

**全面配置化消息** - 所有提示信息现在都可以在`lang.yml`中自定义配置。

支持热加载，修改配置后使用`/cursormenu reload`即可生效，无需重启服务器。

新增配置分类：登录提示、注册提示、权限提示、命令提示、玩家管理提示、菜单提示、系统提示、NPC提示、WASD导航提示。

🧹 代码质量改进

**新增命令执行工具类** - 统一了命令执行逻辑，支持`[console]`、`[op]`、`[player]`、`[server]`等命令前缀。

**代码清理** - 清理了重复的导入语句，简化了代码结构。

📦 文件变更

新增 `CommandUtils.java` 命令执行工具类。

更新 `lang.yml` 新增50+条可配置消息。

⚠️ 注意事项

BCrypt计算比SHA-256慢，这是正常的安全特性，无需担心性能问题。

---

## [1.4.0] - 2026-03-01

### 🆕 新增功能 / New Features

#### NPC镜像系统 / NPC Mirror System
- **新增NPC镜像模块** - 在3D菜单中创建玩家镜像NPC
  - 自动同步玩家装备（头盔、胸甲、护腿、靴子、主手、副手）
  - 支持SkinsRestorer皮肤同步
  - 可配置NPC位置偏移和朝向
  - 支持菜单关联配置，可指定哪些菜单启用NPC
  - NPC名称支持自定义格式和PlaceholderAPI变量
  - 玩家可单独禁用自己的NPC创建

- **Added NPC Mirror Module** - Create player mirror NPCs in 3D menus
  - Auto-sync player equipment (helmet, chestplate, leggings, boots, main hand, off hand)
  - Support SkinsRestorer skin synchronization
  - Configurable NPC position offset and orientation
  - Menu association configuration to specify which menus enable NPCs
  - NPC name supports custom format and PlaceholderAPI variables
  - Players can individually disable their own NPC creation

#### WASD导航系统 / WASD Navigation System
- **新增WASD导航模块** - 使用键盘导航菜单
  - W/S 键上下选择菜单项
  - A/D 键左右选择菜单项
  - 空格键确认执行命令
  - 与原有光标控制系统完全兼容，可同时使用
  - 支持菜单关联配置
  - 选择时自动放大选中项并播放音效
  - 选择冷却机制防止快速连续选择

- **Added WASD Navigation Module** - Navigate menus using keyboard
  - W/S keys for up/down selection
  - A/D keys for left/right selection
  - Space key to confirm and execute commands
  - Fully compatible with existing cursor control system, can be used simultaneously
  - Menu association configuration support
  - Auto-enlarge selected item and play sound effect on selection
  - Selection cooldown mechanism to prevent rapid consecutive selections

#### PlaceholderAPI变量 / PlaceholderAPI Variables
- **新增WASD导航PAPI变量**
  - `%cmp_wasd_menu%` - 当前WASD菜单名称
  - `%cmp_wasd_enabled%` - 是否启用WASD导航
  - `%cmp_wasd_index%` - 当前选中索引
  - `%cmp_wasd_x%` - 当前X坐标
  - `%cmp_wasd_y%` - 当前Y坐标
  - `%cmp_wasd_z%` - 当前Z坐标
  - `%cmp_wasd_location%` - 完整位置信息（格式：菜单名,x,y,z）
  - `%cmp_<菜单名>_xyz%` - 指定菜单的坐标

- **Added WASD Navigation PAPI Variables**
  - `%cmp_wasd_menu%` - Current WASD menu name
  - `%cmp_wasd_enabled%` - Whether WASD navigation is enabled
  - `%cmp_wasd_index%` - Current selected index
  - `%cmp_wasd_x%` - Current X coordinate
  - `%cmp_wasd_y%` - Current Y coordinate
  - `%cmp_wasd_z%` - Current Z coordinate
  - `%cmp_wasd_location%` - Full location info (format: menu_name,x,y,z)
  - `%cmp_<menu_name>_xyz%` - Coordinates for specified menu

### ⚙️ 配置文件 / Configuration Files

#### 新增配置文件 / New Configuration Files
- `npc_config.yml` - NPC镜像系统配置
  - 启用/禁用NPC功能
  - NPC位置偏移配置
  - 菜单关联设置（白名单/黑名单模式）
  - 名称显示配置（支持PAPI变量）
  - 装备和皮肤同步开关

- `wasd_config.yml` - WASD导航系统配置
  - 启用/禁用WASD导航
  - 导航阈值配置
  - 选择冷却时间
  - 音效配置
  - 菜单关联设置

- `npc_config.yml` - NPC mirror system configuration
  - Enable/disable NPC feature
  - NPC position offset configuration
  - Menu association settings (whitelist/blacklist mode)
  - Name display configuration (supports PAPI variables)
  - Equipment and skin sync toggles

- `wasd_config.yml` - WASD navigation system configuration
  - Enable/disable WASD navigation
  - Navigation threshold configuration
  - Selection cooldown time
  - Sound effect configuration
  - Menu association settings

#### 菜单配置扩展 / Menu Configuration Extensions
- 新增 `wasd-enabled` 选项 - 单独控制每个菜单的WASD导航开关
- Added `wasd-enabled` option - Individually control WASD navigation for each menu

### 🔧 命令 / Commands

#### NPC命令 / NPC Commands
- `/cursormenu npc toggle` - 切换NPC创建状态
- `/cursormenu npc enable` - 启用NPC创建
- `/cursormenu npc disable` - 禁用NPC创建
- `/cursormenu npc status` - 查看NPC状态
- `/cursormenu npc reload` - 重载NPC配置
- `/cursormenu npc rotate <角度>` - 旋转NPC

### 🔌 依赖 / Dependencies

#### 新增软依赖 / New Soft Dependencies
- **FancyNpcs** (可选) - NPC镜像功能必需
- **SkinsRestorer** (可选) - NPC皮肤同步支持

- **FancyNpcs** (optional) - Required for NPC mirror feature
- **SkinsRestorer** (optional) - NPC skin synchronization support

### 📝 权限 / Permissions

#### 新增权限 / New Permissions
- `cursormenu.npc.toggle` - 切换NPC创建状态
- `cursormenu.npc.status` - 查看NPC状态
- `cursormenu.npc.reload` - 重载NPC配置
- `cursormenu.npc.rotate` - 旋转NPC

### 🔄 兼容性 / Compatibility

- ✅ NPC镜像系统和WASD导航系统均为模块化设计
- ✅ 不修改原有CMP核心功能
- ✅ 所有新功能可独立启用/禁用
- ✅ 与原有光标控制系统完全兼容

- ✅ NPC mirror system and WASD navigation system are modularly designed
- ✅ No modifications to original CMP core functionality
- ✅ All new features can be independently enabled/disabled
- ✅ Fully compatible with existing cursor control system

### 📦 文件结构 / File Structure

```
com.cmenu.ui/
├── npc/                          # NPC镜像模块
│   ├── NPCModule.java
│   ├── NPCMirrorManager.java
│   ├── NPCMirrorHook.java
│   ├── NPCConfig.java
│   └── NPCCommandHandler.java
└── wasd/                         # WASD导航模块
    ├── WASDModule.java
    ├── WASDNavigationManager.java
    ├── WASDNavigationHook.java
    ├── WASDConfig.java
    ├── WASDSession.java
    └── WASDExpansion.java
```

---

## 升级说明 / Upgrade Notes

### 从 1.3.x 升级到 1.4.0 / Upgrading from 1.3.x to 1.4.0

1. **备份配置文件** - 升级前请备份现有配置
2. **删除旧版本jar** - 删除 `CustomScreenMenu-1.3.x.jar`
3. **放入新版本jar** - 将 `CustomScreenMenu-1.4.0.jar` 放入 plugins 目录
4. **首次启动** - 会自动生成 `npc_config.yml` 和 `wasd_config.yml`
5. **安装依赖插件**（可选）- 安装 FancyNpcs 和 SkinsRestorer 以启用完整功能

1. **Backup configuration files** - Please backup existing configurations before upgrading
2. **Delete old jar** - Delete `CustomScreenMenu-1.3.x.jar`
3. **Place new jar** - Put `CustomScreenMenu-1.4.0.jar` in plugins directory
4. **First startup** - Will auto-generate `npc_config.yml` and `wasd_config.yml`
5. **Install dependency plugins** (optional) - Install FancyNpcs and SkinsRestorer for full functionality

---


