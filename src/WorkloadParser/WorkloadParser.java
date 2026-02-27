package WorkloadParser;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorkloadParser
 *
 * Workload format (matches your template):
 *  USER create <id> <username> <email> <password>
 *  USER get <id>
 *  USER update <id> username:<username> email:<email> password:<password>
 *  USER delete <id> <username> <email> <password>
 *
 *  PRODUCT create <id> <name> <description> <price> <quantity>
 *  PRODUCT info <id>
 *  PRODUCT update <id> name:<name> description:<description> price:<price> quantity:<quantity>
 *  PRODUCT delete <id> <name> <price> <quantity>
 *
 *  ORDER place <product_id> <user_id> <quantity>
 *
 * Notes:
 * - Lines may contain comments after '#': ignored. Seen in your samples. :contentReference[oaicite:0]{index=0}
 * - PRODUCT create in some samples omits description (4 args after id); we support both.
 *   (Samples: PRODUCT create 2 productname-2398 3.99 9) :contentReference[oaicite:1]{index=1}
 * - ORDER place argument order is product_id then user_id then quantity. :contentReference[oaicite:2]{index=2}
 *
 * Usage:
 *  java WorkloadParser.WorkloadParser <config.json> <workload.txt>
 *  or java WorkloadParser.WorkloadParser <workload.txt>   (defaults config.json)
 */
public class WorkloadParser {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws Exception {
        final String configPath;
        final String workloadPath;

        if (args.length == 1) {
            configPath = "config.json";
            workloadPath = args[0];
        } else if (args.length == 2) {
            configPath = args[0];
            workloadPath = args[1];
        } else {
            System.err.println("Usage: java WorkloadParser.WorkloadParser <workload.txt>");
            System.err.println("   or: java WorkloadParser.WorkloadParser <config.json> <workload.txt>");
            System.exit(1);
            return;
        }

        String orderBase = loadOrderServiceBaseUrl(configPath);

        runWorkload(orderBase, workloadPath);
    }

    /**
     * Reads OrderService location from config.json.
     * Your earlier code used OrderService.port; some configs only include port (no ip).
     * We support:
     *   "OrderService": { "ip": "...", "port": 8082 }
     * or "OrderService": { "port": 8082 }
     */
    private static String loadOrderServiceBaseUrl(String configPath) throws IOException {
        String json = Files.readString(Paths.get(configPath), StandardCharsets.UTF_8);
        JsonObject cfg = JsonParser.parseString(json).getAsJsonObject();

        JsonObject os = cfg.getAsJsonObject("OrderService");
        if (os == null) throw new IllegalArgumentException("config missing OrderService");

        String ip = os.has("ip") ? os.get("ip").getAsString() : "127.0.0.1";
        int port = os.get("port").getAsInt();

        return "http://" + ip + ":" + port;
    }

    private static void runWorkload(String orderBase, String workloadPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(workloadPath))) {
            String line;
            int lineNo = 0;

            while ((line = br.readLine()) != null) {
                lineNo++;

                // strip comments and trim
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                line = line.trim();

                if (line.isEmpty()) continue;

                try {
                    processLine(orderBase, line);
                } catch (Exception e) {
                    // Keep going; workloads include expected failures. :contentReference[oaicite:3]{index=3}
                    System.err.println("[WorkloadParser] line " + lineNo + " FAILED: " + line);
                    System.err.println("  -> " + e.getMessage());
                }
            }
        }
    }

    private static void processLine(String orderBase, String line) throws IOException {
        String[] t = line.split("\\s+");
        if (t.length < 2) return;

        String kind = t[0].toUpperCase();
        String cmd = t[1].toLowerCase();

        switch (kind) {
            case "USER":
                handleUser(orderBase, cmd, t);
                break;
            case "PRODUCT":
                handleProduct(orderBase, cmd, t);
                break;
            case "ORDER":
                handleOrder(orderBase, cmd, t);
                break;
            default:
                // ignore
        }
    }

    // ===================== USER =====================

    private static void handleUser(String orderBase, String cmd, String[] t) throws IOException {
        if ("get".equals(cmd) || "info".equals(cmd)) {
            requireLen(t, 3);
            int id = parseInt(t[2], "id");
            httpGet(orderBase + "/user/" + id);
            return;
        }

        // create/update/delete -> POST /user
        requireLen(t, 3);
        int id = parseInt(t[2], "id");

        JsonObject body = new JsonObject();
        body.addProperty("command", cmd);
        body.addProperty("id", id);

        if ("create".equals(cmd) || "delete".equals(cmd)) {
            requireLen(t, 6);
            body.addProperty("username", t[3]);
            body.addProperty("email", t[4]);
            body.addProperty("password", t[5]);
            httpPostJson(orderBase + "/user", body);
            return;
        }

        if ("update".equals(cmd)) {
            // USER update <id> username:... email:... password:... :contentReference[oaicite:4]{index=4}
            Map<String, String> kv = parseKeyValueTokens(t, 3);
            if (kv.containsKey("username")) body.addProperty("username", kv.get("username"));
            if (kv.containsKey("email")) body.addProperty("email", kv.get("email"));
            if (kv.containsKey("password")) body.addProperty("password", kv.get("password"));
            httpPostJson(orderBase + "/user", body);
            return;
        }

        // Unknown command: send anyway (service should reject)
        httpPostJson(orderBase + "/user", body);
    }

    // ===================== PRODUCT =====================

    private static void handleProduct(String orderBase, String cmd, String[] t) throws IOException {
        if ("info".equals(cmd) || "get".equals(cmd)) {
            requireLen(t, 3);
            int id = parseInt(t[2], "id");
            httpGet(orderBase + "/product/" + id);
            return;
        }

        requireLen(t, 3);
        int id = parseInt(t[2], "id");

        JsonObject body = new JsonObject();
        body.addProperty("command", cmd);
        body.addProperty("id", id);

        if ("create".equals(cmd)) {
            // Template says: PRODUCT create <id> <name> <description> <price> <quantity> :contentReference[oaicite:5]{index=5}
            // Samples also have: PRODUCT create <id> <name> <price> <quantity> (no description) :contentReference[oaicite:6]{index=6}
            if (t.length == 7) {
                // with description
                String name = t[3];
                String description = t[4];
                double price = parseDouble(t[5], "price");
                int qty = parseInt(t[6], "quantity");

                putProductNameCompat(body, name);
                body.addProperty("description", description);
                body.addProperty("price", price);
                body.addProperty("quantity", qty);
            } else if (t.length == 6) {
                // no description provided (sample workloads)
                String name = t[3];
                double price = parseDouble(t[4], "price");
                int qty = parseInt(t[5], "quantity");

                putProductNameCompat(body, name);
                // Synthesize a non-empty description so your ProductService validation passes
                body.addProperty("description", "desc-" + name);
                body.addProperty("price", price);
                body.addProperty("quantity", qty);
            } else {
                throw new IllegalArgumentException("PRODUCT create expects 6 or 7 tokens");
            }

            httpPostJson(orderBase + "/product", body);
            return;
        }

        if ("update".equals(cmd)) {
            // PRODUCT update <id> name:... description:... price:... quantity:... :contentReference[oaicite:7]{index=7}
            Map<String, String> kv = parseKeyValueTokens(t, 3);

            if (kv.containsKey("name")) putProductNameCompat(body, kv.get("name"));
            if (kv.containsKey("productname")) putProductNameCompat(body, kv.get("productname"));

            if (kv.containsKey("description")) body.addProperty("description", kv.get("description"));
            if (kv.containsKey("price")) body.addProperty("price", parseDouble(kv.get("price"), "price"));
            if (kv.containsKey("quantity")) body.addProperty("quantity", parseInt(kv.get("quantity"), "quantity"));

            httpPostJson(orderBase + "/product", body);
            return;
        }

        if ("delete".equals(cmd)) {
            // PRODUCT delete <id> <name> <price> <quantity> :contentReference[oaicite:8]{index=8}
            requireLen(t, 6);
            String name = t[3];
            double price = parseDouble(t[4], "price");
            int qty = parseInt(t[5], "quantity");

            putProductNameCompat(body, name);
            body.addProperty("price", price);
            body.addProperty("quantity", qty);

            httpPostJson(orderBase + "/product", body);
            return;
        }

        // Unknown command
        httpPostJson(orderBase + "/product", body);
    }

    private static void putProductNameCompat(JsonObject body, String name) {
        // Many provided JSON specs use "productname"; your implementation may use "name".
        // To be safe, send both.
        body.addProperty("productname", name);
        body.addProperty("name", name);
    }

    // ===================== ORDER =====================

    private static void handleOrder(String orderBase, String cmd, String[] t) throws IOException {
        if ("place".equals(cmd)) {
            // ORDER place <product_id> <user_id> <quantity> :contentReference[oaicite:9]{index=9}
            requireLen(t, 5); 

            int productId = parseInt(t[2], "product_id");
            int userId = parseInt(t[3], "user_id");
            int qty = parseInt(t[4], "quantity");

            JsonObject body = new JsonObject();
            body.addProperty("command", "place order");
            body.addProperty("product_id", productId);
            body.addProperty("user_id", userId);
            body.addProperty("quantity", qty);

            httpPostJson(orderBase + "/order", body);
        } else if ("get".equals(cmd)) {
            // ORDER get <user_id>
            requireLen(t, 3);
            int user_id = parseInt(t[2], "user_id");
            httpGet(orderBase + "/user/purchased/" + user_id);
        }
        else if (!"place".equals(cmd)) {
            // unknown order command, ignore/send anyway
            JsonObject body = new JsonObject();
            body.addProperty("command", cmd);
            httpPostJson(orderBase + "/order", body);
            return;
        }
    }

    // ===================== token parsing =====================

    /**
     * Parse tokens like: username:abc email:x@y.com password:pw
     * Starting from index start.
     */
    private static Map<String, String> parseKeyValueTokens(String[] t, int start) {
        Map<String, String> kv = new LinkedHashMap<>();
        for (int i = start; i < t.length; i++) {
            String token = t[i];
            int colon = token.indexOf(':');
            if (colon <= 0) continue;
            String k = token.substring(0, colon).toLowerCase();
            String v = token.substring(colon + 1);
            kv.put(k, v);
        }
        return kv;
    }

    private static void requireLen(String[] t, int n) {
        if (t.length < n) throw new IllegalArgumentException("Not enough tokens");
    }

    private static int parseInt(String s, String field) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid int for " + field + ": " + s);
        }
    }

    private static double parseDouble(String s, String field) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid number for " + field + ": " + s);
        }
    }

    // ===================== HTTP helpers =====================

    private static void httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(8000);

        int code = conn.getResponseCode();
        drain(conn);
        if (code >= 400) {
            System.err.println("[WorkloadParser] GET " + url + " -> " + code);
        }
        conn.disconnect();
    }

    private static void httpPostJson(String url, JsonObject body) throws IOException {
        byte[] payload = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setFixedLengthStreamingMode(payload.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        drain(conn);

        // Note: workloads include expected failures; we only log non-200 for visibility.
        if (code >= 400) {
            System.err.println("[WorkloadParser] POST " + url + " -> " + code + " body=" + body);
        }

        conn.disconnect();
    }

    private static void drain(HttpURLConnection conn) {
        try {
            var in = (conn.getResponseCode() >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) return;
            byte[] buf = new byte[4096];
            while (in.read(buf) != -1) {}
            in.close();
        } catch (Exception ignored) {}
    }
}
