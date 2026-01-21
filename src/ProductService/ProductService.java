import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.concurrent.Executors;

public class ProductService {

    private static final int PORT = 8081;

    // Creates products.db in the working directory you run from
    private static final String DB_URL = "jdbc:sqlite:products.db";

    // ---------- Model ----------
    static class Product {
        int id;
        String productname;
        double price;
        int quantity;

        Product(int id, String productname, double price, int quantity) {
            this.id = id;
            this.productname = productname;
            this.price = price;
            this.quantity = quantity;
        }

        String toJson() {
            return "{\n" +
                    "    \"id\": " + id + ",\n" +
                    "    \"productname\": \"" + escape(productname) + "\",\n" +
                    "    \"price\": " + price + " ,\n" +
                    "    \"quantity\": " + quantity + "\n" +
                    "}";
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    // ---------- Main ----------
    public static void main(String[] args) throws IOException {
        initDb();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));
        server.createContext("/product", new ProductHandler());
        server.start();

        System.out.println("ProductService started on port " + PORT);
        System.out.println("SQLite DB: products.db (auto-created if missing)");
    }

    // ---------- DB init / connection ----------
    private static void initDb() {
        try (Connection c = DriverManager.getConnection(DB_URL);
             Statement st = c.createStatement()) {

            // Recommended for concurrent reads/writes:
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA foreign_keys=ON;");

            st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS products (" +
                            "id INTEGER PRIMARY KEY," +
                            "productname TEXT NOT NULL," +
                            "price REAL NOT NULL," +
                            "quantity INTEGER NOT NULL" +
                            ")"
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize DB: " + e.getMessage(), e);
        }
    }

    private static Connection openConn() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    // ---------- HTTP Handler ----------
    static class ProductHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();

                if ("POST".equalsIgnoreCase(method)) {
                    handlePost(exchange);
                } else if ("GET".equalsIgnoreCase(method)) {
                    handleGet(exchange);
                } else {
                    exchange.sendResponseHeaders(405, 0);
                    exchange.close();
                }
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }

        // -------- POST /product --------
        private void handlePost(HttpExchange exchange) throws IOException, SQLException {
            String body = readBody(exchange);

            String command = getJsonString(body, "command");
            Integer id = getJsonInt(body, "id");

            if (command == null || id == null) {
                sendJson(exchange, 400, "{\"error\":\"Missing required field(s): command, id\"}");
                return;
            }

            command = command.trim().toLowerCase();

            switch (command) {
                case "create":
                    handleCreate(exchange, body, id);
                    break;
                case "update":
                    handleUpdate(exchange, body, id);
                    break;
                case "delete":
                    handleDelete(exchange, body, id);
                    break;
                default:
                    sendJson(exchange, 400, "{\"error\":\"Invalid command\"}");
            }
        }

        private void handleCreate(HttpExchange exchange, String body, int id) throws IOException, SQLException {
            String productname = getJsonString(body, "productname");
            Double price = getJsonDouble(body, "price");
            Integer quantity = getJsonInt(body, "quantity");

            if (productname == null || price == null || quantity == null) {
                sendJson(exchange, 400, "{\"error\":\"Missing required field(s) for create\"}");
                return;
            }

            try (Connection c = openConn()) {
                if (dbExistsById(c, id)) {
                    sendJson(exchange, 409, "{\"error\":\"Product id already exists\"}");
                    return;
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO products(id, productname, price, quantity) VALUES(?,?,?,?)")) {
                    ps.setInt(1, id);
                    ps.setString(2, productname);
                    ps.setDouble(3, price);
                    ps.setInt(4, quantity);
                    ps.executeUpdate();
                }
            }

            sendJson(exchange, 200, new Product(id, productname, price, quantity).toJson());
        }

        private void handleUpdate(HttpExchange exchange, String body, int id) throws IOException, SQLException {
            String productname = getJsonString(body, "productname");
            Double price = getJsonDouble(body, "price");
            Integer quantity = getJsonInt(body, "quantity");

            if (productname == null && price == null && quantity == null) {
                sendJson(exchange, 400, "{\"error\":\"No updatable fields provided\"}");
                return;
            }

            Product updated;
            try (Connection c = openConn()) {
                Product existing = dbGetById(c, id);
                if (existing == null) {
                    sendJson(exchange, 404, "{\"error\":\"Product not found\"}");
                    return;
                }

                if (productname != null) existing.productname = productname;
                if (price != null) existing.price = price;
                if (quantity != null) existing.quantity = quantity;

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET productname=?, price=?, quantity=? WHERE id=?")) {
                    ps.setString(1, existing.productname);
                    ps.setDouble(2, existing.price);
                    ps.setInt(3, existing.quantity);
                    ps.setInt(4, id);
                    ps.executeUpdate();
                }

                updated = existing;
            }

            sendJson(exchange, 200, updated.toJson());
        }

        private void handleDelete(HttpExchange exchange, String body, int id) throws IOException, SQLException {
            String productname = getJsonString(body, "productname");
            Double price = getJsonDouble(body, "price");
            Integer quantity = getJsonInt(body, "quantity");

            if (productname == null || price == null || quantity == null) {
                sendJson(exchange, 400, "{\"error\":\"Missing required field(s) for delete\"}");
                return;
            }

            try (Connection c = openConn()) {
                if (!dbExistsById(c, id)) {
                    sendJson(exchange, 404, "{\"error\":\"Product not found\"}");
                    return;
                }

                int affected;
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM products WHERE id=? AND productname=? AND price=? AND quantity=?")) {
                    ps.setInt(1, id);
                    ps.setString(2, productname);
                    ps.setDouble(3, price);
                    ps.setInt(4, quantity);
                    affected = ps.executeUpdate();
                }

                if (affected == 0) {
                    sendJson(exchange, 401, "{\"error\":\"Delete failed: fields do not match\"}");
                    return;
                }
            }

            sendJson(exchange, 200, "{\"status\":\"deleted\"}");
        }

        // -------- GET /product/<id> --------
        private void handleGet(HttpExchange exchange) throws IOException, SQLException {
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/product") || path.equals("/product/")) {
                sendJson(exchange, 400, "{\"error\":\"Missing product id\"}");
                return;
            }

            String prefix = "/product/";
            if (!path.startsWith(prefix)) {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
                return;
            }

            String idStr = path.substring(prefix.length());
            int slash = idStr.indexOf('/');
            if (slash >= 0) idStr = idStr.substring(0, slash);

            int id;
            try {
                id = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, "{\"error\":\"Invalid product id\"}");
                return;
            }

            Product p;
            try (Connection c = openConn()) {
                p = dbGetById(c, id);
            }

            if (p == null) {
                sendJson(exchange, 404, "{\"error\":\"Product not found\"}");
                return;
            }

            sendJson(exchange, 200, p.toJson());
        }

        private String readBody(HttpExchange exchange) throws IOException {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        }
    }

    // ---------- DB helpers ----------
    private static boolean dbExistsById(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM products WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Product dbGetById(Connection c, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, productname, price, quantity FROM products WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Product(
                        rs.getInt("id"),
                        rs.getString("productname"),
                        rs.getDouble("price"),
                        rs.getInt("quantity")
                );
            }
        }
    }

    // ---------- HTTP response helper ----------
    private static void sendJson(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---------- Tiny JSON helpers ----------
    private static String getJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int k = json.indexOf(pattern);
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;

        int firstQuote = json.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int secondQuote = json.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) return null;

        return json.substring(firstQuote + 1, secondQuote);
    }

    private static Integer getJsonInt(String json, String key) {
        String raw = getJsonNumberRaw(json, key);
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double getJsonDouble(String json, String key) {
        String raw = getJsonNumberRaw(json, key);
        if (raw == null) return null;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getJsonNumberRaw(String json, String key) {
        String pattern = "\"" + key + "\"";
        int k = json.indexOf(pattern);
        if (k < 0) return null;
        int colon = json.indexOf(':', k);
        if (colon < 0) return null;

        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

        int j = i;
        while (j < json.length() && ("-0123456789.".indexOf(json.charAt(j)) >= 0)) j++;

        if (j == i) return null;
        return json.substring(i, j);
    }
}
