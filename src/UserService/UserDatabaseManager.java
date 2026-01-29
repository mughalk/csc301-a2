package UserService;

import UserService.UserService.UserData;

import java.sql.*;

public class UserDatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:users.db";

    // --- Singleton Connection ---
    public static Connection connect() throws SQLException {
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
            stmt.execute(sql);
            System.out.println("Database initialized (SQLite).");
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

    // --- USER DATA CLASS ---
    static class UserData {
        public int id;
        public String username;
        public String email;
        public String password;
    }
}