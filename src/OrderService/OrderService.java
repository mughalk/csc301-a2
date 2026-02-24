package OrderService;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class OrderService {

    private static final Gson GSON = new Gson();

    private static int port;
    private static String iscsBase;

    // In-memory order store (allowed unless spec says otherwise)
    private static final Map<String, JsonObject> orders = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java OrderService <config.json>");
            System.exit(1);
        }

        // Initialize database before server starts
        OrderDatabaseManager.initialize();

        loadConfig(args[0]);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/user/purchased", new UserPurchasesHandler());
        server.createContext("/user", new ProxyHandler());
        server.createContext("/product", new ProxyHandler());
        server.createContext("/order", new OrderHandler());

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("OrderService running on port " + port);
    }

    private static void loadConfig(String path) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        JsonObject cfg = JsonParser.parseString(json).getAsJsonObject();

        JsonObject os = cfg.getAsJsonObject("OrderService");
        port = os.get("port").getAsInt();

        JsonObject iscs = cfg.getAsJsonObject("InterServiceCommunication");
        iscsBase = "http://" + iscs.get("ip").getAsString() + ":" + iscs.get("port").getAsInt();
    }

    /* ===================== PROXY HANDLER ===================== */

    static class ProxyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            String query = ex.getRequestURI().getQuery();

            String target = iscsBase + path + (query != null ? "?" + query : "");
            byte[] body = readAll(ex.getRequestBody());

            HttpResult res = forward(method, target, body, ex);
            sendRaw(ex, res.code, res.body);
        }
    }

    /* ===================== ORDER HANDLER ===================== */

    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("GET".equals(method)) {
                handleGet(ex, path);
                return;
            }

            if ("POST".equals(method) && "/order".equals(path)) {
                handlePost(ex);
                return;
            }

            sendError(ex, 404, "Invalid endpoint");
        }

        private void handleGet(HttpExchange ex, String path) throws IOException {
            String[] parts = path.split("/");
            if (parts.length != 3) {
                sendError(ex, 404, "Order id missing");
                return;
            }

            JsonObject order = orders.get(parts[2]);
            if (order == null) {
                sendError(ex, 404, "Order not found");
                return;
            }

            sendJson(ex, 200, order);
        }

        private void handlePost(HttpExchange ex) throws IOException {
            JsonObject req;
            try {
                req = parseJson(ex);
            } catch (Exception e) {
                // Malformed JSON / empty body
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            // Command check (treat wrong/missing command as invalid request for the tests)
            try {
                if (!req.has("command") ||
                        !"place order".equalsIgnoreCase(req.get("command").getAsString())) {
                    respondStatus(ex, 400, "Invalid Request");
                    return;
                }
            } catch (Exception e) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            // Required fields + type checks (avoid throwing -> RemoteDisconnected)
            if (!req.has("user_id") || !req.has("product_id") || !req.has("quantity")) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            int userId, productId, qty;
            try {
                userId = req.get("user_id").getAsInt();
                productId = req.get("product_id").getAsInt();
                qty = req.get("quantity").getAsInt();
            } catch (Exception e) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            if (qty <= 0) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            // 1) Check user exists (via ISCS)
            HttpResult userRes = forward("GET", iscsBase + "/user/" + userId, null, ex);
            if (userRes.code != 200) {
                // Test expects 404 for invalid user id
                respondStatus(ex, 404, "Invalid Request");
                return;
            }

            // 2) Get product (via ISCS)
            HttpResult prodRes = forward("GET", iscsBase + "/product/" + productId, null, ex);
            if (prodRes.code != 200) {
                // Test expects 404 for invalid product id
                respondStatus(ex, 404, "Invalid Request");
                return;
            }

            JsonObject product;
            try {
                product = JsonParser
                        .parseString(new String(prodRes.body, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (Exception e) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            int stock;
            try {
                stock = product.get("quantity").getAsInt();
            } catch (Exception e) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            // Special case: exceeded stock
            if (qty > stock) {
                respondStatus(ex, 400, "Exceeded quantity limit"); // (409 also acceptable per your spec)
                return;
            }

            // 3) Update product quantity
            JsonObject update = new JsonObject();
            update.addProperty("command", "update");
            update.addProperty("id", productId);
            update.addProperty("quantity", stock - qty);

            HttpResult updRes = forward(
                    "POST",
                    iscsBase + "/product",
                    GSON.toJson(update).getBytes(StandardCharsets.UTF_8),
                    ex
            );

            if (updRes.code != 200) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            // 4) Record purchase in database
            try {
                OrderDatabaseManager.addOrUpdatePurchase(userId, productId, qty);
            } catch (Exception e) {
                respondStatus(ex, 400, "Invalid Request");
                return;
            }

            // 5) Store order internally (optional; not required by the create tests)
            String orderId = UUID.randomUUID().toString();
            JsonObject order = new JsonObject();
            order.addProperty("id", orderId);
            order.addProperty("user_id", userId);
            order.addProperty("product_id", productId);
            order.addProperty("quantity", qty);
            orders.put(orderId, order);

            // 6) Success response must include these keys (per your test output)
            respondSuccess(ex, userId, productId, qty);
        }

    }

    /* ===================== USER PURCHASES HANDLER ===================== */

    static class UserPurchasesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if (!"GET".equals(method)) {
                sendError(ex, 405, "Method not allowed");
                return;
            }

            // Parse /user/purchased/{userId}
            String[] parts = path.split("/");
            if (parts.length != 4 || !"purchased".equals(parts[2])) {
                sendError(ex, 404, "Invalid endpoint");
                return;
            }

            int userId;
            try {
                userId = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                sendError(ex, 404, "Invalid user id");
                return;
            }

            // 1) Verify user exists via UserService (via ISCS)
            // might need to test
            HttpResult userRes = forward("GET", iscsBase + "/user/" + userId, null, ex);
            if (userRes.code != 200) {
                sendError(ex, 404, "User not found");
                return;
            }

            // 2) Get user's purchases from database
            Map<Integer, Integer> purchases = OrderDatabaseManager.getPurchasesForUser(userId);

            // 3) Build response JSON
            JsonObject response = new JsonObject();
            for (Map.Entry<Integer, Integer> entry : purchases.entrySet()) {
                response.addProperty(String.valueOf(entry.getKey()), entry.getValue());
            }

            // 4) Return response (200 with JSON, empty or populated)
            sendJson(ex, 200, response);
        }
    }

    /* ===================== HELPERS ===================== */

    private static void respondStatus(HttpExchange ex, int code, String status) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("status", status);
        byte[] b = GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static void respondSuccess(HttpExchange ex, int userId, int productId, int qty) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("product_id", productId);
        o.addProperty("user_id", userId);
        o.addProperty("quantity", qty);
        o.addProperty("status", "Success");

        byte[] b = GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    static class HttpResult {
        int code;
        byte[] body;
        HttpResult(int c, byte[] b) { code = c; body = b; }
    }

    private static HttpResult forward(String method, String urlStr, byte[] body, HttpExchange ex)
            throws IOException {

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");

        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct != null) conn.setRequestProperty("Content-Type", ct);

        if ("POST".equals(method)) {
            conn.setDoOutput(true);
            if (body != null) conn.getOutputStream().write(body);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
        byte[] resp = (is == null) ? new byte[0] : readAll(is);

        return new HttpResult(code, resp);
    }

    private static JsonObject parseJson(HttpExchange ex) throws IOException {
        byte[] b = readAll(ex.getRequestBody());
        if (b.length == 0) throw new RuntimeException("Empty body");
        return JsonParser.parseString(new String(b, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static int getInt(JsonObject o, String k) {
        if (!o.has(k)) throw new RuntimeException("Missing field: " + k);
        return o.get(k).getAsInt();
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void sendJson(HttpExchange ex, int code, JsonObject obj) throws IOException {
        byte[] b = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private static void sendRaw(HttpExchange ex, int code, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }

    private static void sendError(HttpExchange ex, int code, String msg) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("error", msg);
        sendJson(ex, code, o);
    }
}

