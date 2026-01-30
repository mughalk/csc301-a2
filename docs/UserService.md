# UserService Javadoc

## Overview
The `UserService` is an HTTP-based microservice responsible for managing user accounts. It provides endpoints for creating, retrieving, updating, and deleting user accounts with secure password hashing.

## Package
```
UserService
```

## Main Class: UserService

### Description
Provides an HTTP server for user account management with JSON-based request/response protocol. The service listens on a configurable IP and port (default: 127.0.0.1:8081) and manages user data through a SQLite database.

### Constants
- **Default Port**: 8081
- **Default IP**: 127.0.0.1

### Methods

#### public static void main(String[] args) throws IOException
Initializes and starts the UserService HTTP server.

**Parameters:**
- `args` - Command-line arguments. If provided, `args[0]` should be a path to a JSON configuration file containing UserService settings (port, ip).

**Behavior:**
1. Initializes the database via `UserDatabaseManager.initialize()`
2. Loads configuration from file if provided (JSON format with UserService section)
3. Creates an HTTP server with a thread pool of 20 threads
4. Registers the `/user` endpoint handler
5. Starts the server and prints startup message

**Configuration File Format:**
```json
{
  "UserService": {
    "port": 8081,
    "ip": "127.0.0.1"
  }
}
```

### Inner Class: UserHandler
Implements `HttpHandler` to process HTTP requests on the `/user` endpoint.

#### public void handle(HttpExchange exchange) throws IOException
Main entry point for HTTP requests. Routes requests to appropriate handlers based on HTTP method.

**Parameters:**
- `exchange` - The HTTP exchange object containing request and response information

**HTTP Methods Supported:**
- **POST** - Handles user commands (create, update, delete)
- **GET** - Retrieves a user by ID

#### private void handlePost(HttpExchange exchange) throws IOException
Processes POST requests for user management operations.

**Request Body Format (JSON):**
```json
{
  "command": "create|update|delete",
  "id": 1001,
  "username": "john_doe",
  "email": "john@example.com",
  "password": "plaintext_password"
}
```

**Commands:**

**1. Create Command**
- **Required Fields**: `id`, `username`, `password`, `email`
- **Validation**:
  - `id` must be non-zero
  - `username` must not be empty
  - `password` must not be empty
  - `email` must be a valid email format
- **Response**: 200 OK - Returns user object without password hash
- **Error Codes**:
  - 400 - Missing/invalid required fields or invalid email format
  - 409 - Duplicate user ID

**2. Update Command**
- **Required Fields**: `id`
- **Optional Fields**: `username`, `email`, `password`
- **Validation**:
  - If provided, `email` must be valid
  - If provided, `username` must not be empty
  - If provided, `password` must not be empty
- **Behavior**: Only updates fields that are provided; others are preserved from database
- **Response**: 200 OK - Returns merged user object
- **Error Codes**:
  - 400 - Invalid field values
  - 404 - User not found

**3. Delete Command**
- **Required Fields**: `id`, `username`, `email`, `password`
- **Validation**: All provided fields must match the stored user data exactly
- **Response**: 200 OK if successful, empty JSON object
- **Error Codes**:
  - 400 - Missing required fields
  - 404 - User not found
  - 401 - Field mismatch (verification failed)

#### private void handleGet(HttpExchange exchange) throws IOException
Processes GET requests to retrieve a user by ID.

**Request Format:**
```
GET /user/{id}
```

**Parameters:**
- `id` - User ID (must be a valid integer)

**Response**: 200 OK - Returns user object with hashed password
```json
{
  "id": 1001,
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SHA256_HASH"
}
```

**Error Codes:**
- 400 - Invalid user ID format (non-numeric)
- 404 - User not found

### Helper Methods

#### private static UserData convertToUserData(DatabaseManager.UserData dbUser)
Converts database user object to API user object.

**Parameters:**
- `dbUser` - User object from database

**Returns:**
- UserData object suitable for JSON serialization, or null if input is null

#### private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException
Sends an HTTP response with specified status code and body.

**Parameters:**
- `exchange` - HTTP exchange object
- `statusCode` - HTTP status code
- `response` - Response body (typically JSON)

#### private static String getRequestBody(HttpExchange exchange) throws IOException
Reads the complete request body from the HTTP exchange.

**Parameters:**
- `exchange` - HTTP exchange object

**Returns:**
- Request body as a String (UTF-8 encoded)

#### private static Map<String, String> parseQuery(String query)
Parses URL query strings into a key-value map.

**Parameters:**
- `query` - Query string (format: "key1=value1&key2=value2")

**Returns:**
- Map of query parameters

**Example:**
```
parseQuery("id=1&name=bob") -> {id: "1", name: "bob"}
```

#### public static String hashPassword(String password)
Hashes a plaintext password using SHA-256 algorithm.

**Parameters:**
- `password` - Plaintext password to hash

**Returns:**
- Hexadecimal representation of the SHA-256 hash (uppercase)

**Note:** The hashed password is what is stored in the database. Plaintext passwords are never stored.

#### public static boolean isValidEmail(String emailStr)
Validates email address format using regex pattern.

**Parameters:**
- `emailStr` - Email address to validate

**Returns:**
- `true` if email matches valid pattern, `false` otherwise

**Valid Email Pattern:**
- Must contain alphanumeric characters, dots, underscores, hyphens, or plus signs before @
- Must contain domain name and at least 2-letter TLD after @
- Example: user.name+tag@example.co.uk

### Inner Class: UserData
Data transfer object for user information.

**Fields:**
- `int id` - Unique user identifier
- `String username` - Username (unique)
- `String email` - Email address (must be valid format)
- `String password` - Password (hashed using SHA-256 in database)
- `String command` - Command to execute (create, update, delete) - not stored in database

## Error Handling

### HTTP Status Codes
| Code | Meaning | Typical Cause |
|------|---------|---------------|
| 200 | OK | Successful operation |
| 400 | Bad Request | Invalid JSON, missing required fields, invalid format |
| 401 | Unauthorized | Delete authentication failed (field mismatch) |
| 404 | Not Found | User does not exist |
| 405 | Method Not Allowed | HTTP method other than POST/GET |
| 409 | Conflict | Duplicate user ID |
| 500 | Internal Server Error | Database error or unexpected exception |

### Database Exceptions
- **SQLException**: Database errors are caught and returned as 500 Internal Server Error (or 409 if constraint violation)
- **JsonSyntaxException**: Invalid JSON in request body returns 400 Bad Request
- **General Exception**: Unexpected errors return 500 Internal Server Error

## Dependencies
- `com.sun.net.httpserver.*` - Built-in Java HTTP server
- `com.google.gson.*` - JSON serialization/deserialization
- `java.security.MessageDigest` - SHA-256 hashing
- `java.util.regex.*` - Email validation
- `UserDatabaseManager` - Database persistence layer

## Thread Safety
- HTTP server uses a fixed thread pool of 20 threads
- Database operations are handled by `UserDatabaseManager` (thread-safe implementation assumed)

## Usage Example

### Create a User
```
POST /user
Content-Type: application/json

{
  "command": "create",
  "id": 1001,
  "username": "alice",
  "email": "alice@example.com",
  "password": "mypassword123"
}
```

### Get a User
```
GET /user/1001
```

### Update a User's Email
```
POST /user
Content-Type: application/json

{
  "command": "update",
  "id": 1001,
  "email": "newemail@example.com"
}
```

### Delete a User
```
POST /user
Content-Type: application/json

{
  "command": "delete",
  "id": 1001,
  "username": "alice",
  "email": "alice@example.com",
  "password": "mypassword123"
}
```

## Starting the Service

### Without Configuration File
```bash
javac -cp lib/* src/UserService/*.java -d compiled/UserService/
java -cp lib/*:compiled/UserService/ UserService.UserService
```

Starts on default 127.0.0.1:8081

### With Configuration File
```bash
java -cp lib/*:compiled/UserService/ UserService.UserService config.json
```

Starts on the port/IP specified in config.json

## Notes
- Passwords are hashed with SHA-256 before storage
- Password hashes are returned in responses but never used for authentication (only for deletion verification)
- Email validation uses a regex pattern; not all RFC-compliant emails may be accepted
- The service expects `UserDatabaseManager` to be available and initialized
