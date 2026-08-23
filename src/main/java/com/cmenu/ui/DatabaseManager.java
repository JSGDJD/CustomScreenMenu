package com.cmenu.ui;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private Connection connection;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private synchronized void initializeDatabase() {
        try {
            // 确保数据文件夹存在
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            // 连接到SQLite数据库（如果不存在会自动创建）
            String url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "users.db").getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // 创建用户表（增强版，包含IP绑定字段）
            try (Statement stmt = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "username TEXT NOT NULL UNIQUE," +
                        "password_hash TEXT NOT NULL," +
                        "registered BOOLEAN NOT NULL DEFAULT 0," +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                        "register_ip TEXT NOT NULL DEFAULT ''," +
                        "last_login_ip TEXT," +
                        "login_attempts INTEGER DEFAULT 0," +
                        "last_login_time TIMESTAMP" +
                        ")";
                stmt.execute(sql);
            }

            // 检查是否需要添加新列（旧数据库升级）
            try (Statement stmt = connection.createStatement()) {
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(users)");
                boolean hasRegisterIp = false;
                boolean hasLastLoginIp = false;

                while (rs.next()) {
                    String columnName = rs.getString("name");
                    if ("register_ip".equals(columnName)) hasRegisterIp = true;
                    if ("last_login_ip".equals(columnName)) hasLastLoginIp = true;
                }

                // 添加 register_ip 列
                if (!hasRegisterIp) {
                    stmt.execute("ALTER TABLE users ADD COLUMN register_ip TEXT NOT NULL DEFAULT ''");
                    plugin.getLogger().info("已添加 register_ip 列到用户表");
                }

                // 添加 last_login_ip 列
                if (!hasLastLoginIp) {
                    stmt.execute("ALTER TABLE users ADD COLUMN last_login_ip TEXT");
                    plugin.getLogger().info("已添加 last_login_ip 列到用户表");
                }

                // 添加登录尝试次数和最后登录时间列
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN login_attempts INTEGER DEFAULT 0");
                } catch (SQLException ignored) {}
                try {
                    stmt.execute("ALTER TABLE users ADD COLUMN last_login_time TIMESTAMP");
                } catch (SQLException ignored) {}
            }

            plugin.getLogger().info("用户数据库初始化成功（增强版 - IP绑定）");
        } catch (SQLException e) {
            plugin.getLogger().severe("初始化数据库时出错: " + e.getMessage());
        }
    }

    // 获取数据库连接，如果连接已关闭则重新建立连接
    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            // 重新建立数据库连接
            String url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "users.db").getAbsolutePath();
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("重新建立数据库连接成功");
        }
        return connection;
    }

    public synchronized boolean isUserRegistered(String username) {
        String sql = "SELECT registered FROM users WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getBoolean("registered");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("查询用户注册状态时出错: " + e.getMessage());
        }

        return false;
    }

    public synchronized boolean isUsernameExists(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("检查用户名是否存在时出错: " + e.getMessage());
        }

        return false;
    }

    public synchronized boolean isIpAlreadyRegistered(String registerIp) {
        String sql = "SELECT id FROM users WHERE register_ip = ? AND registered = 1";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, registerIp);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("检查IP注册状态时出错: " + e.getMessage());
        }

        return false;
    }

    public synchronized boolean registerUser(String username, String passwordHash, String registerIp) {
        String sql = "INSERT INTO users(username, password_hash, registered, register_ip) VALUES(?, ?, ?, ?)";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setBoolean(3, true);
            pstmt.setString(4, registerIp);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("注册用户时出错: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean authenticateUser(String username, String passwordHash) {
        String sql = "SELECT password_hash FROM users WHERE username = ? AND registered = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setBoolean(2, true);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                return storedHash.equals(passwordHash);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("验证用户时出错: " + e.getMessage());
        }

        return false;
    }

    public synchronized String getStoredPasswordHash(String username) {
        String sql = "SELECT password_hash FROM users WHERE username = ? AND registered = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setBoolean(2, true);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("password_hash");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取用户密码哈希时出错: " + e.getMessage());
        }

        return null;
    }

    public synchronized UserInfo getUserInfo(String username) {
        String sql = "SELECT username, register_ip, last_login_ip, login_attempts, last_login_time FROM users WHERE username = ? AND registered = 1";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new UserInfo(
                    rs.getString("username"),
                    rs.getString("register_ip"),
                    rs.getString("last_login_ip"),
                    rs.getInt("login_attempts"),
                    rs.getTimestamp("last_login_time")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取用户信息时出错: " + e.getMessage());
        }

        return null;
    }

    public synchronized UserInfo getUserInfoByIp(String ip) {
        String sql = "SELECT username, register_ip, last_login_ip, login_attempts, last_login_time FROM users WHERE register_ip = ? AND registered = 1 LIMIT 1";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, ip);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new UserInfo(
                    rs.getString("username"),
                    rs.getString("register_ip"),
                    rs.getString("last_login_ip"),
                    rs.getInt("login_attempts"),
                    rs.getTimestamp("last_login_time")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("根据IP获取用户信息时出错: " + e.getMessage());
        }

        return null;
    }

    public synchronized boolean updateLoginInfo(String username, String currentIp, boolean success) {
        String sql = "UPDATE users SET last_login_ip = ?, login_attempts = ?, last_login_time = CURRENT_TIMESTAMP WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, currentIp);
            pstmt.setInt(2, success ? 0 : -1);
            pstmt.setString(3, username);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("更新登录信息时出错: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean updateLoginAttempts(String username, int attempts) {
        String sql = "UPDATE users SET login_attempts = ? WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, attempts);
            pstmt.setString(2, username);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("更新登录尝试次数时出错: " + e.getMessage());
            return false;
        }
    }

    public synchronized int getLoginAttempts(String username) {
        String sql = "SELECT login_attempts FROM users WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("login_attempts");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取登录尝试次数时出错: " + e.getMessage());
        }

        return 0;
    }

    public synchronized boolean updateUserPassword(String username, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setString(2, username);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("更新用户密码时出错: " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, username);

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("删除用户时出错: " + e.getMessage());
            return false;
        }
    }

    public synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
            connection = null;
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接时出错: " + e.getMessage());
        }
    }

    public static class UserInfo {
        private final String username;
        private final String registerIp;
        private final String lastLoginIp;
        private final int loginAttempts;
        private final java.sql.Timestamp lastLoginTime;

        public UserInfo(String username, String registerIp, String lastLoginIp, int loginAttempts, java.sql.Timestamp lastLoginTime) {
            this.username = username;
            this.registerIp = registerIp;
            this.lastLoginIp = lastLoginIp;
            this.loginAttempts = loginAttempts;
            this.lastLoginTime = lastLoginTime;
        }

        public String getUsername() { return username; }
        public String getRegisterIp() { return registerIp; }
        public String getLastLoginIp() { return lastLoginIp; }
        public int getLoginAttempts() { return loginAttempts; }
        public java.sql.Timestamp getLastLoginTime() { return lastLoginTime; }

        public boolean isSameIp(String currentIp) {
            return registerIp != null && !registerIp.isEmpty() && registerIp.equals(currentIp);
        }
    }
}
