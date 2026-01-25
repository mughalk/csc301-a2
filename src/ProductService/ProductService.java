import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.concurrent.Executors;

public class ProductService {

    private static final int PORT = 8081;

    // ---------- Model ----------
    public static class Product {
        int id;
        String productname;
        String description; // required; cannot be blank
        double price;
        int quantity;

        Product(int id, String productname, String description, double price, int quantity) {
            this.id = id;
            this.productname = productname;
            this.description = (description == null) ? "" : description;
            this.price = price;
            this.quantity = quantity;
        }

        String toJson() {
            return "{\n" +
                    "    \"id\": " + id + ",\n" +
                    "    \"productname\": \"" + escape(productname) + "\",\n" +
                    "    \"description\": \"" + escape(description) + "\",\n" +
                    "    \"price\": " + price + " ,\n" +
                    "    \"quantity\": " + quantity + "\n" +
                    "}";
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    // ---- validation helpers ----
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void requireNonNull(HttpExchange ex, Object value, String field) throws IOException {
        if (value == null) {
            sendJson(ex, 400, "{\"error\":\"Missing or invalid required field: " + field + "\"}");
            throw new IllegalArgumentException("Missing/invalid " + field);
        }
    }

    private static void requireNonBlank(HttpExchange ex, String value, String field) throws IOException {
        if (isBlank(value)) {
            sendJson(ex, 400, "{\"error\":\"Field cannot be empty: " + field + "\"}");
            throw new IllegalArgumentException("Empty " + field);
        }
    }

    private static void requirePositiveInt(HttpExchange ex, Integer value, String field) throws IOException {
        requireNonNull(ex, value, field);
        if (value <= 0) {
            sendJson(ex, 400, "{\"error\":\"Field must be > 0: " + field + "\"}");
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private static void requireNonNegativeInt(HttpExchange ex, Integer value, String field) throws IOException {
        requireNonNull(ex, value, field);
        if (value < 0) {
            sendJson(ex, 400, "{\"error\":\"Field must be >= 0: " + field + "\"}");
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private static void requireNonNegativeDouble(HttpExchange ex, Double value, String field) throws IOException {
        requireNonNull(ex, value, field);
        if (value.isNaN() || value.isInfinite() || value < 0.0) {
            sendJson(ex, 400, "{\"error\":\"Field must be >= 0: " + field + "\"}");
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private static void validateOptionalNonBlank(HttpExchange ex, String description, String field) throws IOException {
        if (isBlank(description) || description.strip() == "" ) {
            sendJson(ex, 400, "{\"error\":\"Field must not be blank: " + field + "\"}");
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    // For PATCH-like update: validate only if provided
    private static void validateOptionalNonNegativeInt(HttpExchange ex, Integer value, String field) throws IOException {
        if (value != null && value < 0) {
            sendJson(ex, 400, "{\"error\":\"Field must be >= 0: " + field + "\"}");
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private static void validateOptionalNonNegativeDouble(HttpExchange ex, Double value, String field) throws IOException {
        if (value != null && (value.isNaN() || value.isInfinite() || value < 0.0)) {
            sendJson(ex, 400, "{\"error\":\"Field must be >= 0: " + field + "\"}");
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    // ---------- Main ----------
    public static void main(String[] args) throws IOException {
        DatabaseManager.initDb();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newFixedThreadPool(20));
        server.createContext("/product", new ProductHandler());
        server.start();

        System.out.println("ProductService started on port " + PORT);
        System.out.println("SQLite DB: products.db");
    }

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
            try {
                String body = readBody(exchange);
                JsonObject json = parseJsonObject(exchange, body);

                String command = jString(json, "command");
                Integer id = jIntStrict(json, "id");  // MUST be integer

                requireNonBlank(exchange, command, "command");
                requirePositiveInt(exchange, id, "id");

                command = command.trim().toLowerCase();

                switch (command) {
                    case "create": handleCreate(exchange, json, id); break;
                    case "update": handleUpdate(exchange, json, id); break;
                    case "delete": handleDelete(exchange, json, id); break;
                    default: sendJson(exchange, 400, "{\"error\":\"Invalid command\"}");
                }
            } catch (IllegalArgumentException ignored) {
                // validation already responded with 400
            }
        }

        private void handleCreate(HttpExchange exchange, JsonObject json, int id) throws IOException, SQLException {
            String productname = jString(json, "productname");
            String description = jString(json, "description"); // REQUIRED; cannot be blank
            Double price = jDouble(json, "price");
            Integer quantity = jIntStrict(json, "quantity");   // MUST be integer

            try {
                requireNonBlank(exchange, productname, "productname");
                requireNonBlank(exchange, description, "description");
                requireNonNegativeDouble(exchange, price, "price");
                requireNonNegativeInt(exchange, quantity, "quantity");
            } catch (IllegalArgumentException ignored) {
                return;
            }

            try (Connection c = DatabaseManager.openConn()) {
                if (DatabaseManager.dbExistsById(c, id)) {
                    sendJson(exchange, 409, "{\"error\":\"Product id already exists\"}");
                    return;
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO products(id, productname, description, price, quantity) VALUES(?,?,?,?,?)")) {
                    ps.setInt(1, id);
                    ps.setString(2, productname);
                    ps.setString(3, description);
                    ps.setDouble(4, price);
                    ps.setInt(5, quantity);
                    ps.executeUpdate();
                }
            }

            sendJson(exchange, 200, new Product(id, productname, description, price, quantity).toJson());
        }

        private void handleUpdate(HttpExchange exchange, JsonObject json, int id) throws IOException, SQLException {
            String productname = jString(json, "productname");     // optional
            String description = jString(json, "description");     // optional; if provided cannot be blank
            Double price = jDouble(json, "price");                 // optional
            Integer quantity = jIntStrict(json, "quantity");       // optional; if present MUST be integer

            try {
                if (productname == null && description == null && price == null && quantity == null) {
                    sendJson(exchange, 400, "{\"error\":\"No updatable fields provided\"}");
                    return;
                }

                // productname can be updated but cannot be blank if provided
                if (productname != null && isBlank(productname)) {
                    sendJson(exchange, 400, "{\"error\":\"Field cannot be empty: productname\"}");
                    return;
                }

                validateOptionalNonBlank(exchange, description, "description");
                validateOptionalNonNegativeDouble(exchange, price, "price");
                validateOptionalNonNegativeInt(exchange, quantity, "quantity");
            } catch (IllegalArgumentException ignored) {
                return;
            }

            Product updated;
            try (Connection c = DatabaseManager.openConn()) {
                Product existing = DatabaseManager.dbGetById(c, id);
                if (existing == null) {
                    sendJson(exchange, 404, "{\"error\":\"Product not found\"}");
                    return;
                }

                if (productname != null) existing.productname = productname;
                if (description != null) existing.description = description; // can be ""
                if (price != null) existing.price = price;
                if (quantity != null) existing.quantity = quantity;

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE products SET productname=?, description=?, price=?, quantity=? WHERE id=?")) {
                    ps.setString(1, existing.productname);
                    ps.setString(2, existing.description == null ? "" : existing.description);
                    ps.setDouble(3, existing.price);
                    ps.setInt(4, existing.quantity);
                    ps.setInt(5, id);
                    ps.executeUpdate();
                }

                updated = existing;
            }

            sendJson(exchange, 200, updated.toJson());
        }

        private void handleDelete(HttpExchange exchange, JsonObject json, int id) throws IOException, SQLException {
            // NOTE: description is intentionally NOT required for deletion
            String productname = jString(json, "productname");
            Double price = jDouble(json, "price");
            Integer quantity = jIntStrict(json, "quantity"); // MUST be integer

            try {
                requireNonBlank(exchange, productname, "productname");
                requireNonNegativeDouble(exchange, price, "price");
                requireNonNegativeInt(exchange, quantity, "quantity");
            } catch (IllegalArgumentException ignored) {
                return;
            }

            try (Connection c = DatabaseManager.openConn()) {
                if (!DatabaseManager.dbExistsById(c, id)) {
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
            try (Connection c = DatabaseManager.openConn()) {
                p = DatabaseManager.dbGetById(c, id);
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

    // ---------- HTTP response helper ----------
    private static void sendJson(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // Parse once per request body
    private static JsonObject parseJsonObject(HttpExchange ex, String body) throws IOException {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            sendJson(ex, 400, "{\"error\":\"Invalid JSON\"}");
            throw new IllegalArgumentException("invalid json");
        }
    }

    // Safe getters: return null if missing or explicit null
    private static String jString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        try { return e.getAsString(); } catch (Exception ignore) { return null; }
    }

    /**
     * Strict integer parsing:
     * - returns null if missing/null
     * - returns null if present but not an integer (e.g., 1.2, "5", "abc")
     */
    private static Integer jIntStrict(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;

        // Must be a JSON number (not a string like "2")
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;

        try {
            // Use BigDecimal so we can reject fractional values safely.
            BigDecimal bd = e.getAsBigDecimal();
            bd = bd.stripTrailingZeros();
            if (bd.scale() > 0) return null; // fractional
            // Also reject values outside int range
            if (bd.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0) return null;
            if (bd.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) return null;
            return bd.intValueExact();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Double jDouble(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        try { return e.getAsDouble(); } catch (Exception ignore) { return null; }
    }
}
