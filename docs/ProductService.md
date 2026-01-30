# ProductService Javadoc

## Overview
The `ProductService` is an HTTP-based microservice responsible for managing product inventory. It provides endpoints for creating, retrieving, updating, and deleting products with comprehensive validation of product data.

## Package
```
ProductService
```

## Main Class: ProductService

### Description
Provides an HTTP server for product management with JSON-based request/response protocol. The service listens on a configurable IP and port (default: 127.0.0.1:8082) and manages product data through a SQLite database.

### Constants
- **Default Port**: 8082
- **Default IP**: 127.0.0.1

### Methods

#### public static void main(String[] args) throws IOException
Initializes and starts the ProductService HTTP server.

**Parameters:**
- `args` - Command-line arguments. If provided, `args[0]` should be a path to a JSON configuration file containing ProductService settings (port, ip).

**Behavior:**
1. Initializes the database via `ProductDatabaseManager.initialize()`
2. Loads configuration from file if provided (JSON format with ProductService section)
3. Creates an HTTP server with a thread pool of 20 threads
4. Registers the `/product` endpoint handler
5. Starts the server and prints startup message

**Configuration File Format:**
```json
{
  "ProductService": {
    "port": 8082,
    "ip": "127.0.0.1"
  }
}
```

### Inner Class: ProductHandler
Implements `HttpHandler` to process HTTP requests on the `/product` endpoint.

#### public void handle(HttpExchange exchange) throws IOException
Main entry point for HTTP requests. Routes requests to appropriate handlers based on HTTP method.

**Parameters:**
- `exchange` - The HTTP exchange object containing request and response information

**HTTP Methods Supported:**
- **POST** - Handles product commands (create, update, delete)
- **GET** - Retrieves a product by ID

#### private void handlePost(HttpExchange exchange) throws IOException
Processes POST requests for product management operations.

**Request Body Format (JSON):**
```json
{
  "command": "create|update|delete",
  "id": 101,
  "name": "Widget",
  "description": "A useful widget",
  "price": 29.99,
  "quantity": 100
}
```

**Commands:**

**1. Create Command**
- **Required Fields**: `id`, `command`, `name`, `description`, `price`, `quantity`
- **Field Validation**:
  - `id` - Must be a positive integer (> 0)
  - `command` - Must be "create"
  - `name` - Must not be blank
  - `description` - Must not be blank
  - `price` - Must be non-negative double (>= 0.0)
  - `quantity` - Must be non-negative integer (>= 0)
- **Response**: 200 OK - Returns product JSON object
- **Error Codes**:
  - 400 - Missing/invalid required fields or validation failure
  - 409 - Duplicate product ID

**Response Format:**
```json
{
  "id": 101,
  "name": "Widget",
  "description": "A useful widget",
  "price": 29.99,
  "quantity": 100
}
```

**2. Update Command**
- **Required Fields**: `id`, `command`
- **Optional Fields**: `name`, `description`, `price`, `quantity`
- **Field Validation**:
  - `id` - Must be a positive integer
  - `name` - If provided, must not be blank
  - `description` - If provided, must not be blank
  - `price` - If provided, must be non-negative double
  - `quantity` - If provided, must be non-negative integer
- **Behavior**: 
  - Only updates fields that are provided
  - At least one updateable field must be provided
  - Database is checked before update (lazy update check)
- **Response**: 200 OK - Returns updated product JSON object
- **Error Codes**:
  - 400 - Invalid field values or no updateable fields provided
  - 404 - Product not found
  - 500 - Database error

**3. Delete Command**
- **Required Fields**: `id`, `command`, `name`, `price`, `quantity`
- **Note**: `description` is intentionally NOT required for deletion
- **Field Validation**:
  - `id` - Must be a positive integer
  - `name` - Must not be blank
  - `price` - Must be non-negative double
  - `quantity` - Must be non-negative integer
- **Behavior**: All provided fields must match the stored product data exactly for deletion to proceed
- **Response**: 200 OK - Returns status JSON
```json
{
  "status": "deleted"
}
```
- **Error Codes**:
  - 400 - Missing required fields or validation failure
  - 401 - Field mismatch (product exists but data doesn't match)
  - 404 - Product not found
  - 500 - Database error

#### private void handleGet(HttpExchange exchange) throws IOException
Processes GET requests to retrieve a product by ID.

**Request Format:**
```
GET /product/{id}
```

**Parameters:**
- `id` - Product ID (must be a valid integer)

**Response**: 200 OK - Returns product object
```json
{
  "id": 101,
  "name": "Widget",
  "description": "A useful widget",
  "price": 29.99,
  "quantity": 100
}
```

**Error Codes:**
- 400 - Missing product ID or invalid ID format
- 404 - Product not found
- 500 - Database error

### Inner Class: Product
Data model representing a product.

**Fields:**
```java
int id              // Unique product identifier
String name         // Product name
String description  // Product description
double price        // Product price
int quantity        // Available quantity in stock
```

**Constructor:**
```java
Product(int id, String name, String description, double price, int quantity)
```
- Converts null strings to empty strings
- Stores all numeric values as provided

**Methods:**

#### String toJson()
Generates JSON representation of the product.

**Returns:**
```json
{
  "id": 101,
  "name": "Widget",
  "description": "A useful widget",
  "price": 29.99,
  "quantity": 100
}
```

**Escaping:** The method properly escapes backslashes and quotes in name and description fields.

#### private static String escape(String s)
Escapes special characters in strings for JSON serialization.

**Parameters:**
- `s` - String to escape

**Returns:**
- Escaped string with backslashes and quotes properly escaped, or empty string if input is null

### Validation Helper Methods

#### private static boolean isBlank(String s)
Checks if string is null or contains only whitespace.

**Parameters:**
- `s` - String to check

**Returns:**
- `true` if null or blank, `false` otherwise

#### private static void requireNonNull(HttpExchange ex, Object value, String field) throws IOException
Validates that a value is not null. Sends 400 response if validation fails.

**Parameters:**
- `ex` - HTTP exchange for sending error response
- `value` - Value to validate
- `field` - Field name for error message

**Throws:** `IllegalArgumentException` if validation fails (after sending response)

#### private static void requireNonBlank(HttpExchange ex, String value, String field) throws IOException
Validates that a string is not null or blank. Sends 400 response if validation fails.

**Parameters:**
- `ex` - HTTP exchange
- `value` - String to validate
- `field` - Field name for error message

**Throws:** `IllegalArgumentException` if validation fails

#### private static void requirePositiveInt(HttpExchange ex, Integer value, String field) throws IOException
Validates that an integer is positive (> 0). Sends 400 response if validation fails.

**Parameters:**
- `ex` - HTTP exchange
- `value` - Integer to validate
- `field` - Field name for error message

**Throws:** `IllegalArgumentException` if validation fails

#### private static void requireNonNegativeInt(HttpExchange ex, Integer value, String field) throws IOException
Validates that an integer is non-negative (>= 0). Sends 400 response if validation fails.

**Parameters:**
- `ex` - HTTP exchange
- `value` - Integer to validate
- `field` - Field name for error message

**Throws:** `IllegalArgumentException` if validation fails

#### private static void requireNonNegativeDouble(HttpExchange ex, Double value, String field) throws IOException
Validates that a double is non-negative (>= 0.0) and not NaN or Infinite. Sends 400 response if validation fails.

**Parameters:**
- `ex` - HTTP exchange
- `value` - Double to validate
- `field` - Field name for error message

**Throws:** `IllegalArgumentException` if validation fails

#### private static void validateOptionalNonBlank(HttpExchange ex, String value, String field) throws IOException
Validates optional string field - only validates if provided (not null).

**Parameters:**
- `ex` - HTTP exchange
- `value` - String to validate
- `field` - Field name for error message

**Behavior:** If value is not null but is blank, sends 400 response

#### private static void validateOptionalNonNegativeInt(HttpExchange ex, Integer value, String field) throws IOException
Validates optional integer field - only validates if provided (not null).

**Parameters:**
- `ex` - HTTP exchange
- `value` - Integer to validate
- `field` - Field name for error message

#### private static void validateOptionalNonNegativeDouble(HttpExchange ex, Double value, String field) throws IOException
Validates optional double field - only validates if provided (not null).

**Parameters:**
- `ex` - HTTP exchange
- `value` - Double to validate
- `field` - Field name for error message

### JSON Parsing Helper Methods

#### private static String readBody(HttpExchange exchange) throws IOException
Reads the complete request body.

**Parameters:**
- `exchange` - HTTP exchange object

**Returns:**
- Request body as UTF-8 string

#### private static void sendJson(HttpExchange exchange, int status, String response) throws IOException
Sends a JSON response with appropriate headers.

**Parameters:**
- `exchange` - HTTP exchange object
- `status` - HTTP status code
- `response` - JSON response body

#### private static JsonObject parseJsonObject(HttpExchange ex, String body) throws IOException
Parses a string as JSON object.

**Parameters:**
- `ex` - HTTP exchange (for error response)
- `body` - JSON string to parse

**Returns:**
- Parsed JsonObject

**Throws:** `IllegalArgumentException` with error response if JSON is invalid

#### private static String jString(JsonObject o, String key)
Safely extracts string value from JSON object.

**Parameters:**
- `o` - JSON object
- `key` - Field key

**Returns:**
- String value, or null if missing, null, or not a string

#### private static Integer jIntStrict(JsonObject o, String key)
Safely extracts integer value from JSON object with strict type checking.

**Parameters:**
- `o` - JSON object
- `key` - Field key

**Returns:**
- Integer value, or null if:
  - Field is missing or null
  - Field is not an integer (e.g., 1.5, "5", "abc")
  - Value exceeds Integer bounds

#### private static Double jDouble(JsonObject o, String key)
Safely extracts double value from JSON object.

**Parameters:**
- `o` - JSON object
- `key` - Field key

**Returns:**
- Double value, or null if missing, null, or not a number

#### private static String jName(JsonObject o)
Extracts product name from JSON object with backward-compatible fallback.

**Parameters:**
- `o` - JSON object

**Returns:**
- Name value or null

## Error Handling

### HTTP Status Codes
| Code | Meaning | Typical Cause |
|------|---------|---------------|
| 200 | OK | Successful operation |
| 400 | Bad Request | Invalid JSON, missing required fields, invalid format, validation failure |
| 401 | Unauthorized | Delete field mismatch |
| 404 | Not Found | Product does not exist |
| 405 | Method Not Allowed | HTTP method other than POST/GET |
| 409 | Conflict | Duplicate product ID |
| 500 | Internal Server Error | Database error |

### Error Response Format
```json
{
  "error": "Detailed error message"
}
```

## Dependencies
- `com.sun.net.httpserver.*` - Built-in Java HTTP server
- `com.google.gson.*` - JSON serialization/deserialization
- `java.math.BigDecimal` - Precise decimal validation
- `ProductDatabaseManager` - Database persistence layer

## Thread Safety
- HTTP server uses a fixed thread pool of 20 threads
- Database operations are handled by `ProductDatabaseManager` (thread-safe implementation assumed)

## Usage Examples

### Create a Product
```
POST /product
Content-Type: application/json

{
  "command": "create",
  "id": 101,
  "name": "Widget Pro",
  "description": "Professional grade widget",
  "price": 49.99,
  "quantity": 50
}
```

### Get a Product
```
GET /product/101
```

### Update Product Price
```
POST /product
Content-Type: application/json

{
  "command": "update",
  "id": 101,
  "price": 44.99
}
```

### Delete a Product
```
POST /product
Content-Type: application/json

{
  "command": "delete",
  "id": 101,
  "name": "Widget Pro",
  "price": 44.99,
  "quantity": 50
}
```

## Starting the Service

### Without Configuration File
```bash
javac -cp lib/* src/ProductService/*.java -d compiled/ProductService/
java -cp lib/*:compiled/ProductService/ ProductService.ProductService
```

Starts on default 127.0.0.1:8082

### With Configuration File
```bash
java -cp lib/*:compiled/ProductService/ ProductService.ProductService config.json
```

## Notes
- Product prices are stored as double precision floating point numbers
- Product names and descriptions support escaped JSON special characters
- The DELETE command intentionally does not require a description match
- Strict integer validation ensures quantity and ID are always whole numbers
- Price validation ensures no NaN or Infinite values are accepted
