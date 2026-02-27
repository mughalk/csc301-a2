package OrderService;

import com.google.gson.JsonObject;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class OrderDatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:orders.db";

    // --- Singleton Connection ---
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // --- Initialization ---
    public static void initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS user_purchases (" +
                     "user_id INTEGER NOT NULL, " +
                     "product_id INTEGER NOT NULL, " +
                     "quantity INTEGER NOT NULL, " +
                     "PRIMARY KEY (user_id, product_id))";
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Orders database initialized (SQLite).");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- CRUD OPERATIONS ---

    /**
     * Record a purchase. If the user already purchased this product,
     * increment the quantity. Otherwise, insert a new row.
     */
    public static void addOrUpdatePurchase(int userId, int productId, int quantity) throws SQLException {
        String sql = "INSERT INTO user_purchases (user_id, product_id, quantity) " +
                     "VALUES (?, ?, ?) " +
                     "ON CONFLICT(user_id, product_id) " +
                     "DO UPDATE SET quantity = quantity + ?";
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, productId);
            pstmt.setInt(3, quantity);
            pstmt.setInt(4, quantity);
            pstmt.executeUpdate();
        }
    }

    /**
     * Get all purchases for a specific user.
     * Returns a Map of productId -> quantity
     */
    public static Map<Integer, Integer> getPurchasesForUser(int userId) {
        String sql = "SELECT product_id, quantity FROM user_purchases WHERE user_id = ?";
        Map<Integer, Integer> purchases = new HashMap<>();
        
        try (Connection conn = connect(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                int productId = rs.getInt("product_id");
                int quantity = rs.getInt("quantity");
                purchases.put(productId, quantity);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return purchases;
    }
}
