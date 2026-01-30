# OrderService Javadoc

## Overview
The `OrderService` is an HTTP-based microservice that acts as an API gateway for placing and retrieving orders. It proxies requests to User and Product services through an Internal Service Communication (ISCS) router and manages order state.

## Package
```
OrderService
```

## Main Class: OrderService

### Description
Provides an HTTP server that handles order operations (creation and retrieval) with request proxying to dependent services. The service loads configuration from a JSON file and communicates with User and Product services through ISCS.

### Static Fields

#### private static final Gson GSON
JSON serializer/deserializer instance used throughout the service.

#### private static int port
HTTP port on which this service listens (loaded from configuration).

#### private static String iscsBase
Base URL for the Internal Service Communication router (loaded from configuration).
Format: `http://ip:port`

#### private static final Map<String, JsonObject> orders
In-memory map storing placed orders by order ID. Uses `ConcurrentHashMap` for thread-safe concurrent access.

### Methods

#### public static void main(String[] args) throws Exception
Initializes and starts the OrderService HTTP server.

**Parameters:**
- `args` - Command-line arguments. Must contain exactly one argument: path to configuration JSON file.

**Behavior:**
1. Validates that exactly one config file argument is provided
2. Loads configuration from the specified file
3. Creates HTTP server listening on configured port
4. Registers handlers for `/user`, `/product` (proxy), and `/order` (order operations)
5. Uses cached thread pool for concurrent request handling
6. Prints startup message with listening port

**Configuration File Format:**
```json
{
  "OrderService": {
    "port": 8080
  },
  "InterServiceCommunication": {
    "ip": "127.0.0.1",
    "port": 8000
  }
}
```

**Error Handling:**
- Prints usage message and exits with code 1 if arguments are invalid

#### private static void loadConfig(String path) throws IOException
Loads and parses the configuration JSON file.

**Parameters:**
- `path` - File path to configuration JSON

**Behavior:**
1. Reads entire file as UTF-8
2. Parses JSON into JsonObject
3. Extracts OrderService port configuration
4. Extracts InterServiceCommunication IP and port
5. Constructs iscsBase URL for routing

**Throws:**
- `IOException` - If file cannot be read
- `JsonParseException` - If JSON is malformed (uncaught, causes failure)
- `NullPointerException` - If required config sections are missing

### Inner Class: ProxyHandler
Implements `HttpHandler` to proxy `/user` and `/product` requests through ISCS.

#### public void handle(HttpExchange ex) throws IOException
Handles proxied requests to User and Product services.

**Parameters:**
- `ex` - HTTP exchange object

**Behavior:**
1. Extracts HTTP method, request path, and query parameters
2. Constructs target URL with ISCS base, path, and query string
3. Reads request body
4. Forwards request using `forward()` helper method
5. Sends response back to client with same status code and body

**Request Flow:**
```
Client -> OrderService -> ISCS -> UserService/ProductService
```

### Inner Class: OrderHandler
Implements `HttpHandler` to process order operations.

#### public void handle(HttpExchange ex) throws IOException
Routes HTTP requests to appropriate order handler based on method and path.

**Parameters:**
- `ex` - HTTP exchange object

**Behavior:**
- GET requests: delegates to `handleGet()`
- POST requests to `/order`: delegates to `handlePost()`
- All others: sends 404 error

#### private void handleGet(HttpExchange ex, String path) throws IOException
Retrieves a previously placed order by ID.

**Request Format:**
```
GET /order/{orderId}
```

**Path Format:**
- Path must be in format `/order/{orderId}` where orderId is a string UUID
- Must have exactly 3 segments when split by "/"

**Response (Success)**: 200 OK
```json
{
  "id": "uuid-string",
  "user_id": 1001,
  "product_id": 101,
  "quantity": 5
}
```

**Error Codes:**
- 404 - Order not found (invalid path format or order not in memory store)

#### private void handlePost(HttpExchange ex) throws IOException
Places a new order with validation and inventory management.

**Request Body Format (JSON):**
```json
{
  "command": "place order",
  "user_id": 1001,
  "product_id": 101,
  "quantity": 5
}
```

**Processing Steps:**

1. **Parse Request JSON**
   - Validates JSON is well-formed
   - Returns 400 if malformed or empty body

2. **Command Validation**
   - Requires "command" field to equal "place order" (case-insensitive)
   - Returns 400 if missing or incorrect

3. **Required Fields Validation**
   - Requires fields: `user_id`, `product_id`, `quantity`
   - All must be integers
   - Returns 400 if missing or non-integer type

4. **Quantity Validation**
   - Quantity must be positive (> 0)
   - Returns 400 if quantity <= 0

5. **User Validation**
   - Makes GET request to ISCS: `/user/{user_id}`
   - Returns 404 if user not found

6. **Product Validation**
   - Makes GET request to ISCS: `/product/{product_id}`
   - Returns 404 if product not found
   - Extracts product quantity field

7. **Inventory Check**
   - Verifies requested quantity does not exceed available stock
   - Returns 400 with "Exceeded quantity limit" if stock insufficient

8. **Inventory Update**
   - Posts update command to ISCS `/product` endpoint
   - Decrements product quantity by ordered amount
   - Returns 400 if update fails

9. **Order Creation**
   - Generates UUID for order ID
   - Stores order in in-memory map
   - Creates response JSON

**Response (Success)**: 200 OK
```json
{
  "product_id": 101,
  "user_id": 1001,
  "quantity": 5,
  "status": "Success"
}
```

**Error Codes:**
| Code | Scenario |
|------|----------|
| 400 | Malformed JSON, missing command, invalid command, missing fields, non-integer types, invalid quantity, insufficient stock |
| 404 | User not found, product not found |

### Inner Class: HttpResult
Simple data class for HTTP response results.

**Fields:**
```java
int code        // HTTP status code
byte[] body     // Response body bytes
```

**Constructor:**
```java
HttpResult(int c, byte[] b)
```

### Helper Methods

#### private static HttpResult forward(String method, String urlStr, byte[] body, HttpExchange ex) throws IOException
Forwards an HTTP request to a target service and returns the response.

**Parameters:**
- `method` - HTTP method (GET, POST, etc.)
- `urlStr` - Target URL
- `body` - Request body bytes (null for GET requests)
- `ex` - Original exchange (for header information)

**Returns:**
- HttpResult containing response code and body

**Behavior:**
1. Opens HTTP connection to target URL
2. Sets request method
3. Disables automatic redirects
4. Enables output for request body
5. Forwards request headers (skips Host and Content-Length)
6. Writes request body if provided
7. Reads response code
8. Reads response body from InputStream or ErrorStream as appropriate
9. Closes connection

**Error Handling:**
- Returns HttpResult with appropriate error code if connection fails

#### private static JsonObject parseJson(HttpExchange ex) throws IOException
Parses request body as JSON.

**Parameters:**
- `ex` - HTTP exchange with request body

**Returns:**
- Parsed JsonObject

**Throws:**
- `RuntimeException` - If body is empty or JSON is invalid

#### private static int getInt(JsonObject o, String k)
Extracts integer field from JSON object.

**Parameters:**
- `o` - JSON object
- `k` - Field key

**Returns:**
- Integer value

**Throws:**
- `RuntimeException` - If field is missing

#### private static byte[] readAll(InputStream is) throws IOException
Reads entire input stream to byte array.

**Parameters:**
- `is` - Input stream to read

**Returns:**
- Complete stream contents as byte array

#### private static void sendJson(HttpExchange ex, int code, JsonObject obj) throws IOException
Sends JSON response with specified status code.

**Parameters:**
- `ex` - HTTP exchange
- `code` - HTTP status code
- `obj` - JsonObject to serialize

#### private static void sendRaw(HttpExchange ex, int code, byte[] body) throws IOException
Sends raw response bytes with specified status code.

**Parameters:**
- `ex` - HTTP exchange
- `code` - HTTP status code
- `body` - Response body bytes

#### private static void sendError(HttpExchange ex, int code, String msg) throws IOException
Sends error response in JSON format.

**Parameters:**
- `ex` - HTTP exchange
- `code` - HTTP status code
- `msg` - Error message

**Response Format:**
```json
{
  "error": "error message"
}
```

#### private static void respondStatus(HttpExchange ex, int code, String status) throws IOException
Sends status response in JSON format.

**Parameters:**
- `ex` - HTTP exchange
- `code` - HTTP status code
- `status` - Status message

**Response Format:**
```json
{
  "status": "status message"
}
```

#### private static void respondSuccess(HttpExchange ex, int userId, int productId, int qty) throws IOException
Sends successful order response.

**Parameters:**
- `ex` - HTTP exchange
- `userId` - User ID from order
- `productId` - Product ID from order
- `qty` - Quantity ordered

**Response Format:**
```json
{
  "product_id": 101,
  "user_id": 1001,
  "quantity": 5,
  "status": "Success"
}
```

## Order Processing Flow

```
1. Client sends POST /order with order details
2. OrderService validates command and fields
3. OrderService queries ISCS for user existence
4. OrderService queries ISCS for product and stock
5. OrderService validates quantity against stock
6. OrderService updates product quantity via ISCS
7. OrderService generates UUID and stores order in memory
8. OrderService returns success response with order details
```

## Error Handling

### HTTP Status Codes
| Code | Meaning | Typical Cause |
|------|---------|---------------|
| 200 | OK | Order placed successfully |
| 400 | Bad Request | Invalid JSON, missing fields, validation failure, exceeded stock |
| 404 | Not Found | User not found, product not found, order not found |

### Order Validation Sequence
1. JSON syntax validation
2. Command field validation
3. Required fields presence check
4. Type validation (integers)
5. Value range validation (quantity > 0)
6. User existence check
7. Product existence check
8. Inventory sufficiency check
9. Inventory update operation

## Dependencies
- `com.google.gson.*` - JSON serialization/deserialization
- `java.net.HttpURLConnection` - HTTP communication
- `java.util.UUID` - Unique order ID generation
- `java.util.concurrent.ConcurrentHashMap` - Thread-safe order storage
- `java.util.concurrent.Executors` - Thread pool creation

## Thread Safety
- Uses `ConcurrentHashMap` for order storage to support concurrent order placement
- HTTP server uses cached thread pool for handling multiple concurrent requests
- Connection forwarding is thread-safe (no shared state between requests)

## Configuration

### Required Configuration Fields
```json
{
  "OrderService": {
    "port": <integer>     // Port to listen on
  },
  "InterServiceCommunication": {
    "ip": <string>,       // ISCS router IP
    "port": <integer>     // ISCS router port
  }
}
```

## Starting the Service

```bash
javac -cp lib/* src/OrderService/*.java -d compiled/OrderService/
java -cp lib/*:compiled/OrderService/ OrderService.OrderService config.json
```

## Usage Example

### Place an Order
```
POST /order
Content-Type: application/json

{
  "command": "place order",
  "user_id": 1001,
  "product_id": 101,
  "quantity": 5
}
```

### Retrieve an Order
```
GET /order/550e8400-e29b-41d4-a716-446655440000
```

### Get User Details (via OrderService proxy)
```
GET /user/1001
```

### Get Product Details (via OrderService proxy)
```
GET /product/101
```

## Notes
- Orders are stored in-memory only; they are lost when the service restarts
- The service requires ISCS to be running and properly configured for order placement to work
- Product quantity is decremented atomically as part of the order placement process
- No order cancellation or modification is supported in this implementation
- Order IDs are UUIDs and are not sequential
