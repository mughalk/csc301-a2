package UserService;

import UserService.UserService.UserData;

import java.sql.*;

public class UserDatabaseManager {
    private static final String DB_URL  = System.getenv("DB_URL")  != null ? System.getenv("DB_URL")  : "jdbc:sqlite:users.db";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "";
    private static final String DB_PASS = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "";

    // --- Singleton Connection ---
    public static Connection connect() throws SQLException {
        if (!DB_USER.isEmpty()) {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }
        return DriverManager.getConnection(DB_URL);
    }

    // --- Initialization ---
    public static void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS users (" +
                     "id INTEGER PRIMARY KEY, " +
                     "username TEXT NOT NULL UNIQUE, " +
                     "email TEXT, " +
                     "password TEXT)";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            if (DB_URL.startsWith("jdbc:sqlite")) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA cache_size=-65536");
                stmt.execute("PRAGMA busy_timeout=5000");
            }
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