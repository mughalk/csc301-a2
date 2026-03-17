package ProductService;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import java.sql.*;

public class ProductDatabaseManager {

    private static final String DB_URL  = System.getenv("DB_URL")  != null ? System.getenv("DB_URL")  : "jdbc:sqlite:products.db";
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

    // Match UserService naming
    public static void initialize() {
        try (Connection c = openConn();
             Statement st = c.createStatement()) {

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS products (" +
                            "id INTEGER PRIMARY KEY," +
                            "name TEXT NOT NULL," +
                            "description TEXT NOT NULL," +
                            "price DOUBLE PRECISION NOT NULL," +
                            "quantity INTEGER NOT NULL" +
                            ")"
            );

            // If table existed from an older version, ensure description exists
            try {
                st.executeUpdate("ALTER TABLE products ADD COLUMN description TEXT NOT NULL DEFAULT 'N/A'");
            } catch (SQLException ignored) {
                // duplicate column, ok
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DB: " + e.getMessage(), e);
        }
    }

    private static Connection openConn() throws SQLException {
        return pool.getConnection();
    }

    public static boolean productExists(int id) throws SQLException {
        try (Connection c = openConn();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM products WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static ProductService.Product getProduct(int id) throws SQLException {
        try (Connection c = openConn();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, name, description, price, quantity FROM products WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ProductService.Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"),
                        rs.getInt("quantity")
                );
            }
        }
    }

    // returns false if id already exists
    public static boolean createProduct(ProductService.Product p) throws SQLException {
        try (Connection c = openConn()) {
            if (productExists(p.id)) return false;

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO products(id, name, description, price, quantity) VALUES(?,?,?,?,?)")) {
                ps.setInt(1, p.id);
                ps.setString(2, p.name);
                ps.setString(3, p.description);
                ps.setDouble(4, p.price);
                ps.setInt(5, p.quantity);
                ps.executeUpdate();
                return true;
            }
        }
    }

    // returns false if id not found
    public static boolean updateProduct(ProductService.Product p) throws SQLException {
        try (Connection c = openConn();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE products SET name=?, description=?, price=?, quantity=? WHERE id=?")) {
            ps.setString(1, p.name);
            ps.setString(2, p.description);
            ps.setDouble(3, p.price);
            ps.setInt(4, p.quantity);
            ps.setInt(5, p.id);

            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }

    public enum DeleteResult { NOT_FOUND, MISMATCH, DELETED }

    public static DeleteResult deleteProduct(int id, String name, double price, int quantity) throws SQLException {
        try (Connection c = openConn()) {
            if (!productExists(id)) return DeleteResult.NOT_FOUND;

            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM products WHERE id=? AND name=? AND price=? AND quantity=?")) {
                ps.setInt(1, id);
                ps.setString(2, name);
                ps.setDouble(3, price);
                ps.setInt(4, quantity);

                int affected = ps.executeUpdate();
                return (affected > 0) ? DeleteResult.DELETED : DeleteResult.MISMATCH;
            }
        }
    }
    public static void resetDatabase() {
        try (Connection conn = openConn();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate("DROP TABLE IF EXISTS products");

        } catch (SQLException e) {
            throw new RuntimeException("Failed to reset Product DB", e);
        }

        initialize();
    }
}
