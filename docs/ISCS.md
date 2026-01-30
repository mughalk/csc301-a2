# ISCS (Internal Service Communication) Javadoc

## Overview
The `ISCS` service acts as an internal service router and load balancer for inter-service communication. It accepts requests from the OrderService and routes them to appropriate backend services (UserService, ProductService) using round-robin load balancing. It's designed with future scalability in mind to support multiple service instances.

## Package
```
ISCS
```

## Main Class: ISCS

### Description
Provides an HTTP routing server that forwards requests between microservices. The ISCS service loads configuration from a JSON file, registers available service endpoints, and routes incoming requests using round-robin load balancing.

### Static Fields

#### private static final Map<String, List<String>> serviceRegistry
Registry mapping service names to lists of available endpoints.

**Structure:**
```java
Map<String, List<String>> where:
  - Key: Service name (e.g., "UserService", "ProductService", "OrderService")
  - Value: List of addresses (e.g., ["127.0.0.1:8081", "127.0.0.1:8082"])
```

**Purpose:** Stores multiple endpoints per service for future A2 scalability (multiple instances per service).

#### private static final Map<String, AtomicInteger> rrCounters
Round-robin counters for load balancing across service instances.

**Structure:**
```java
Map<String, AtomicInteger> where:
  - Key: Service name
  - Value: Atomic counter tracking which instance to send request to next
```

**Purpose:** Ensures requests are distributed evenly across available instances of each service.

### Methods

#### public static void main(String[] args) throws IOException
Initializes and starts the ISCS HTTP routing server.

**Parameters:**
- `args` - Command-line arguments. Must contain exactly one argument: path to configuration JSON file.

**Behavior:**
1. Validates that exactly one config file argument is provided
2. Loads configuration from specified file
3. Extracts ISCS server configuration (IP and port)
4. Creates HTTP server listening on configured port
5. Registers global request handler for all paths (`/`)
6. Uses cached thread pool for concurrent request handling
7. Starts server and prints startup message
8. Prints confirmation that server is ready to route requests

**Configuration File Format:**
```json
{
  "UserService": {
    "ip": "127.0.0.1",
    "port": 8081
  },
  "ProductService": {
    "ip": "127.0.0.1",
    "port": 8082
  },
  "OrderService": {
    "ip": "127.0.0.1",
    "port": 8080
  },
  "InterServiceCommunication": {
    "ip": "127.0.0.1",
    "port": 8000
  }
}
```

**Error Handling:**
- Prints error message and exits with code 1 if config file argument is missing
- Throws RuntimeException if "InterServiceCommunication" config section is missing

#### private static void loadConfiguration(String filePath) throws IOException
Loads and parses the configuration JSON file, registering all services.

**Parameters:**
- `filePath` - Path to configuration JSON file

**Behavior:**
1. Reads entire file as UTF-8
2. Parses JSON into JsonObject
3. Registers UserService
4. Registers ProductService
5. Registers OrderService
6. Registers InterServiceCommunication (ISCS itself)
7. Prints registration confirmations for each service

**Throws:**
- `IOException` - If file cannot be read

#### private static void registerService(JsonObject config, String serviceName)
Registers a single service endpoint from configuration.

**Parameters:**
- `config` - Root configuration JsonObject
- `serviceName` - Name of service to register (e.g., "UserService")

**Behavior:**
1. Checks if service exists in configuration
2. Extracts IP address and port from service configuration
3. Creates address string in format "ip:port"
4. Adds address to service registry (creates list if needed)
5. Initializes round-robin counter to 0 for this service
6. Prints registration message

**Graceful Handling:**
- Silently skips registration if service not found in configuration (returns early)

### Inner Class: RouterHandler
Implements `HttpHandler` to process all incoming HTTP requests and route them to appropriate services.

#### public void handle(HttpExchange exchange) throws IOException
Main request handler that routes incoming requests to backend services.

**Parameters:**
- `exchange` - HTTP exchange object containing request and response information

**Request Processing Flow:**

**1. Path Analysis**
- Extracts request path (e.g., "/user/1001", "/product/101")
- Determines target service based on path prefix:
  - `/user/*` → UserService
  - `/product/*` → ProductService
  - `/order/*` → OrderService
  - Other paths → 404 Unknown Route

**2. Load Balancing**
- Calls `getNextNode()` to get next service instance using round-robin
- Returns 503 Service Not Available if no instances registered

**3. Request Construction**
- Builds target URL: `http://ip:port + path + query_string`
- Preserves query parameters if present

**4. HTTP Forwarding**
- Opens connection to target service
- Sets request method (GET, POST, etc.)
- Disables automatic redirects
- Enables output for request body
- Forwards relevant HTTP headers (excludes Host and Content-Length)
- Writes request body if applicable

**5. Response Handling**
- Reads response code from target service
- Reads response body (from output stream for success, error stream for errors)
- Sets Content-Type header to application/json
- Sends response back to client with same status code and body

**6. Error Handling**
- Catches any exceptions and sends 500 Internal Server Error with error message
- Ensures connection is closed in finally block

**Logging:**
- Prints routing information: `[ISCS] Routing: METHOD path -> targetUrl`

### Helper Methods

#### private static String getNextNode(String serviceName)
Selects next service instance using round-robin load balancing.

**Parameters:**
- `serviceName` - Name of service to get instance for

**Returns:**
- Address string in format "ip:port" of the selected instance, or null if no instances available

**Behavior:**
1. Retrieves list of instances for service from registry
2. Returns null if service not registered or has no instances
3. Gets current counter value and increments it
4. Calculates index using modulo (wraps around to 0 after reaching end)
5. Handles potential negative overflow (converts to absolute value)
6. Returns instance at calculated index

**Load Balancing:**
- Distributes requests evenly across available instances
- Counter increments atomically and independently per service
- Example with 3 instances:
  - Request 1 → Instance 0 (counter 0 % 3 = 0)
  - Request 2 → Instance 1 (counter 1 % 3 = 1)
  - Request 3 → Instance 2 (counter 2 % 3 = 2)
  - Request 4 → Instance 0 (counter 3 % 3 = 0)

#### private static void copyStream(InputStream input, OutputStream output) throws IOException
Copies data from input stream to output stream.

**Parameters:**
- `input` - Source input stream
- `output` - Destination output stream

**Behavior:**
- Reads in 4096 byte chunks
- Continues until EOF
- Writes all bytes to output stream

**Purpose:** Used for forwarding request and response bodies through the router.

#### private static void sendError(HttpExchange exchange, int code, String message) throws IOException
Sends error response in JSON format.

**Parameters:**
- `exchange` - HTTP exchange for response
- `code` - HTTP status code
- `message` - Error message text

**Response Format:**
```json
{
  "error": "error message"
}
```

**Headers:**
- Content-Type: application/json

## Request Routing Flow

```
OrderService Client
        |
        | HTTP Request (e.g., GET /user/1001)
        ↓
    ISCS Router
        |
        | Determines target: UserService
        | Selects instance via round-robin
        |
        ↓
    UserService (127.0.0.1:8081)
        |
        | Processes request
        | Returns response
        |
        ↓
    ISCS Router
        |
        | Forwards response back
        |
        ↓
OrderService Client
```

## Error Handling

### HTTP Status Codes from ISCS
| Code | Meaning | Cause |
|------|---------|-------|
| 200-299 | Success | Backend service returned successful response |
| 300-399 | Redirect | Backend service returned redirect |
| 400-499 | Client Error | Backend service returned client error |
| 500-599 | Server Error | Backend service returned server error |
| 404 | Not Found | Unknown route prefix (not /user, /product, /order) |
| 503 | Service Unavailable | Service not registered or has no instances |
| 500 | Internal Error | Exception during forwarding (connection error, etc.) |

### Error Response Formats

**404 Unknown Route:**
```json
{
  "error": "Unknown Route: /invalid"
}
```

**503 Service Not Available:**
```json
{
  "error": "Service Not Available: UnknownService"
}
```

**500 Forwarding Error:**
```json
{
  "error": "ISCS Forwarding Error: Connection refused"
}
```

## Dependencies
- `com.sun.net.httpserver.*` - Built-in Java HTTP server
- `com.google.gson.*` - JSON serialization/deserialization
- `java.net.HttpURLConnection` - HTTP client for forwarding
- `java.util.concurrent.Executors` - Thread pool creation
- `java.util.concurrent.atomic.AtomicInteger` - Thread-safe counters

## Thread Safety
- Uses `ConcurrentHashMap` for service registry (thread-safe)
- Uses `AtomicInteger` for round-robin counters (atomic operations)
- HTTP server uses cached thread pool to handle multiple concurrent requests
- Each request is processed independently with no shared mutable state

## Configuration

### Required Configuration Fields
```json
{
  "UserService": {
    "ip": <string>,       // IP address of UserService
    "port": <integer>     // Port of UserService
  },
  "ProductService": {
    "ip": <string>,       // IP address of ProductService
    "port": <integer>     // Port of ProductService
  },
  "OrderService": {
    "ip": <string>,       // IP address of OrderService
    "port": <integer>     // Port of OrderService
  },
  "InterServiceCommunication": {
    "ip": <string>,       // IP address for ISCS to listen on
    "port": <integer>     // Port for ISCS to listen on
  }
}
```

## Starting the Service

```bash
javac -cp lib/* src/ISCS/*.java -d compiled/ISCS/
java -cp lib/*:compiled/ISCS/ ISCS.ISCS config.json
```

### Output Example
```
>>> Starting ISCS (Internal Router)...
   + Registered Node for UserService: 127.0.0.1:8081
   + Registered Node for ProductService: 127.0.0.1:8082
   + Registered Node for OrderService: 127.0.0.1:8080
   + Registered Node for InterServiceCommunication: 127.0.0.1:8000
>>> ISCS Listening on port 8000
>>> Ready to route requests from Order Service.
```

## Request Examples

### Route to UserService
```
GET /user/1001
HTTP/1.1 200 OK
{
  "id": 1001,
  "username": "alice",
  "email": "alice@example.com",
  ...
}
```

### Route to ProductService
```
GET /product/101
HTTP/1.1 200 OK
{
  "id": 101,
  "name": "Widget",
  "price": 29.99,
  ...
}
```

### Route to OrderService
```
GET /order/550e8400-e29b-41d4-a716-446655440000
HTTP/1.1 200 OK
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": 1001,
  "product_id": 101,
  ...
}
```

### Route with Query Parameters
```
GET /user/1001?format=json
→ Forwards to UserService as: /user/1001?format=json
```

## Design Patterns

### Circuit Breaker (Future Enhancement)
The current design is prepared for circuit breaker pattern implementation - if a service becomes unavailable, it can be removed from the registry.

### Load Balancing Strategy
- **Current**: Round-robin distribution
- **Future**: Can extend with weighted round-robin, least-connections, etc.

### Service Registry
- **Current**: Static configuration from JSON file
- **Future**: Can be extended to dynamic registration/discovery (e.g., Consul, Eureka)

### Horizontal Scalability
The service architecture supports multiple instances:
```json
{
  "UserService": ["127.0.0.1:8081", "127.0.0.1:8082", "127.0.0.1:8083"]
}
```

## Notes
- ISCS forwards all HTTP methods (GET, POST, PUT, DELETE, etc.)
- ISCS preserves request and response headers (except Host and Content-Length)
- ISCS is transparent to clients - they see responses as if communicating directly with backend
- The service is stateless - it can be restarted without affecting client operations
- Order of service registration does not affect routing
- Service instances are selected in the order they appear in the configuration lists
