package OrderService;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class OrderDatabaseManager {
    private static final String DB_URL  = System.getenv("DB_URL")  != null ? System.getenv("DB_URL")  : "jdbc:sqlite:orders.db";
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

    public static void resetDatabase() {
        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS user_purchases");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset Order DB", e);
        }

        initialize();
    }
}
