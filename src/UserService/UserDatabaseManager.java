package UserService;

import UserService.UserService.UserData;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import java.sql.*;

public class UserDatabaseManager {
    private static final String DB_URL  = System.getenv("DB_URL")  != null ? System.getenv("DB_URL")  : "jdbc:sqlite:users.db";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "";
    private static final String DB_PASS = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "";

    private static final HikariDataSource pool;

    static {
        HikariConfig config = new HikariConfig();
        if (DB_URL.startsWith("jdbc:sqlite")) {
            SQLiteConfig sqConfig = new SQLiteConfig();
            sqConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
            sqConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
            sqConfig.setBusyTimeout(5000);
            sqConfig.setCacheSize(-65536);
            SQLiteDataSource ds = new SQLiteDataSource(sqConfig);
            ds.setUrl(DB_URL);
            config.setDataSource(ds);
        } else {
            config.setJdbcUrl(DB_URL);
            if (!DB_USER.isEmpty()) {
                config.setUsername(DB_USER);
                config.setPassword(DB_PASS);
            }
        }
        if (DB_URL.startsWith("jdbc:sqlite")) {
            config.setMaximumPoolSize(20);
            config.setMinimumIdle(5);
        } else {
            config.setMaximumPoolSize(50);
            config.setMinimumIdle(10);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        pool = new HikariDataSource(config);
    }

    public static Connection connect() throws SQLException {
        return pool.getConnection();
    }

    // --- Initialization ---
    public static void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                     "id INTEGER PRIMARY KEY, " +
                     "username TEXT NOT NULL UNIQUE, " +
                     "email TEXT, " +
                     "password TEXT)";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Database initialized (" + DB_URL.split(":")[1] + ").");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- CRUD OPERATIONS ---

    public static void createUser(int id, String username, String email, String password) throws SQLException {
        String sql = "INSERT INTO users(id, username, email, password) VALUES(?,?,?,?)";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, password);
            pstmt.executeUpdate();
        }
    }

    public static void updateUser(int id, String username, String email, String password) throws SQLException {
        String sql = "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, password);
            pstmt.setInt(4, id);
            pstmt.executeUpdate();
        }
    }

    public static void deleteUser(int id) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public static UserData getUser(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                UserData user = new UserData();
                user.id = rs.getInt("id");
                user.username = rs.getString("username");
                user.email = rs.getString("email");
                user.password = rs.getString("password"); // In production, never return passwords!
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // User not found
    }

    public static void resetDatabase() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS users");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset User DB", e);
        }

        initialize(); // recreate schema
    }

    // --- USER DATA CLASS ---
    static class UserData {
        public int id;
        public String username;
        public String email;
        public String password;
    }
}