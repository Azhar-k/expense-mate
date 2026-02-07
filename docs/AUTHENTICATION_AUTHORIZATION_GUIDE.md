# Authentication and Authorization Implementation Guide

This document explains how authentication and authorization work in this Spring Boot application using JWT tokens and role-based access control (RBAC). This guide can be used as a prompt for an AI code editor to implement similar functionality in another application.

## Overview

The application implements a two-phase security mechanism:
1. **Authentication Phase**: Validates JWT tokens from the `Authorization` header
2. **Authorization Phase**: Enforces role-based access control using the `@RequireRole` annotation

Both phases are implemented as Spring MVC interceptors that run before controller methods are invoked.

## Architecture Components

### 1. JWT Token Validator (`JwtTokenValidator`)

**Purpose**: Validates JWT tokens and extracts user information and roles.

**Key Features**:
- Validates token signature using HMAC SHA algorithm with a secret key from `jwt.secret` property
- Extracts username from the `subject` claim
- Extracts roles from multiple possible claim names: `roles`, `role`, or `authorities`
- Handles token expiration
- Returns structured validation results via `TokenValidationResult`

**Implementation Details**:
- Uses `io.jsonwebtoken` library (JJWT)
- Secret key is read from `application.properties` as `jwt.secret`
- Supports extracting roles as arrays, lists, or single string values
- Returns a builder pattern result object with validation status, username, roles, expiration status, and error messages

**Key Methods**:
- `validateTokenWithDetails(String token)`: Main validation method that returns `TokenValidationResult`
- `extractAllClaims(String token)`: Parses and validates JWT token using secret key
- `extractRoles(Claims claims)`: Extracts roles from various claim formats

### 2. Authentication Interceptor (`JwtAuthenticationInterceptor`)

**Purpose**: Intercepts all API requests to validate JWT tokens and extract user context.

**Execution Flow**:
1. Checks if the request path is public (Swagger, health checks, static resources) - if yes, allows access
2. Extracts `Authorization` header from request
3. Validates that header starts with `Bearer ` prefix
4. Extracts the token from the header
5. Validates the token using `JwtTokenValidator`
6. Checks if token is expired or invalid - throws `UnauthorizedException` if so
7. Extracts username from token's subject claim
8. Stores username and roles in request attributes (`username` and `roles`) for later use
9. Allows request to proceed if all validations pass

**Public Paths** (no authentication required):
- `/swagger-ui/**`
- `/api-docs/**`
- `/v3/api-docs/**`
- `/swagger-ui.html`
- `/webjars/**`
- `/favicon.ico`
- `/health/**`
- `/`

**Request Attributes Set**:
- `username`: String containing the authenticated user's username
- `roles`: List<String> containing the user's roles

**Error Handling**:
- Missing Authorization header → `UnauthorizedException("Missing Authorization header")`
- Invalid Bearer format → `UnauthorizedException("Authorization header must start with Bearer")`
- Empty token → `UnauthorizedException("Bearer token is missing")`
- Expired token → `UnauthorizedException("Token has expired")`
- Invalid token → `UnauthorizedException("Invalid token: [error message]")`
- Missing subject claim → `UnauthorizedException("Token is missing the required subject claim")`

### 3. Role-Based Access Interceptor (`RoleBasedAccessInterceptor`)

**Purpose**: Enforces role-based access control based on `@RequireRole` annotations on controllers and methods.

**Execution Flow**:
1. Checks if handler is a `HandlerMethod` (only processes controller methods, not static resources)
2. Looks for `@RequireRole` annotation on the method first, then on the controller class
3. If no annotation found → allows all authenticated users
4. If annotation found with empty roles array → allows all authenticated users
5. Retrieves current user's roles from request attributes via `SecurityContextUtil`
6. Checks if user has at least one of the required roles (OR logic)
7. Throws `UnauthorizedException` if user doesn't have required roles

**Role Matching Logic**:
- Case-insensitive comparison
- Removes `ROLE_` prefix if present (handles both `admin` and `ROLE_ADMIN` formats)
- User needs to have at least ONE of the specified roles (OR logic, not AND)

**Example**:
- `@RequireRole("admin")` → user must have `admin` role
- `@RequireRole({"admin", "manager"})` → user must have `admin` OR `manager` role
- No annotation → all authenticated users allowed

### 4. RequireRole Annotation (`@RequireRole`)

**Purpose**: Declarative annotation to specify required roles for endpoints.

**Usage**:
- Can be applied to controller classes (applies to all methods) or individual methods
- Method-level annotation overrides class-level annotation
- If not specified, all authenticated users are allowed
- Supports multiple roles (OR logic)

**Annotation Definition**:
```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    String[] value() default {};
}
```

**Example Usage**:
```java
@RestController
@RequestMapping("/api/audit-logs")
@RequireRole("admin")  // Class-level: all methods require admin
public class AuditController {
    
    @GetMapping
    public ResponseEntity<?> getAll() { ... }  // Requires admin
    
    @GetMapping("/public")
    @RequireRole({})  // Method-level override: allows all authenticated users
    public ResponseEntity<?> getPublic() { ... }
}
```

### 5. Security Context Utility (`SecurityContextUtil`)

**Purpose**: Provides convenient access to authenticated user information from request attributes.

**Key Methods**:
- `getCurrentUsername()`: Returns the current authenticated user's username
- `getCurrentUserRoles()`: Returns the list of roles for the current user
- `hasRole(String role)`: Checks if the current user has a specific role
- `isAdmin()`: Checks if the current user is an admin (handles various admin role formats)

**Implementation**:
- Uses Spring's `RequestContextHolder` to access the current `HttpServletRequest`
- Reads from request attributes set by `JwtAuthenticationInterceptor`
- Returns `null` if not in a request context (e.g., background threads)

### 6. Token Validation Result (`TokenValidationResult`)

**Purpose**: Structured result object for JWT token validation.

**Properties**:
- `valid`: Boolean indicating if token is valid
- `username`: String containing username from token's subject claim
- `expired`: Boolean indicating if token is expired
- `error`: String containing error message if validation failed
- `roles`: List<String> containing user roles extracted from token

**Builder Pattern**: Uses builder pattern for construction.

### 7. Web MVC Configuration (`WebMvcConfig`)

**Purpose**: Registers the authentication and authorization interceptors.

**Configuration**:
- Registers `JwtAuthenticationInterceptor` first (runs before authorization)
- Registers `RoleBasedAccessInterceptor` second (runs after authentication)
- Both interceptors apply to `/api/**` paths
- Both exclude Swagger/OpenAPI endpoints and health checks

**Interceptor Order**:
1. `JwtAuthenticationInterceptor` (authentication)
2. `RoleBasedAccessInterceptor` (authorization)

## Request Flow

```
1. HTTP Request arrives
   ↓
2. JwtAuthenticationInterceptor.preHandle()
   - Check if public path → allow if yes
   - Extract Authorization header
   - Validate Bearer token format
   - Validate JWT token signature and expiration
   - Extract username and roles
   - Store in request attributes
   ↓
3. RoleBasedAccessInterceptor.preHandle()
   - Check for @RequireRole annotation
   - If no annotation → allow
   - If annotation exists → check user roles
   - Allow if user has required role(s)
   ↓
4. Controller method executes
   - Can use SecurityContextUtil to get current user info
   ↓
5. Response returned
```

## Implementation Requirements

To implement this authentication and authorization system in another application, you need:

### Dependencies (Maven/Gradle)
```xml
<!-- JWT Library -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
</dependency>
```

### Configuration Properties
```properties
# application.properties
jwt.secret=your-secret-key-here-must-be-at-least-256-bits-for-HS256
```

### Required Components

1. **JwtTokenValidator** - Service that validates JWT tokens
2. **JwtAuthenticationInterceptor** - Interceptor for token validation
3. **RoleBasedAccessInterceptor** - Interceptor for role-based access control
4. **RequireRole** - Annotation for declaring required roles
5. **TokenValidationResult** - Result object for token validation
6. **SecurityContextUtil** - Utility for accessing user context
7. **WebMvcConfig** - Configuration to register interceptors
8. **UnauthorizedException** - Custom exception for authentication/authorization failures

### Exception Handling

The application should have a global exception handler that catches `UnauthorizedException` and returns appropriate HTTP 401 responses:

```java
@ExceptionHandler(UnauthorizedException.class)
public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse(ex.getMessage()));
}
```

## Example: AuditController Usage

The `AuditController` demonstrates the authorization system:

```java
@RestController
@RequestMapping("/api/audit-logs")
@RequireRole("admin")  // All endpoints in this controller require admin role
public class AuditController {
    
    // All methods automatically require admin role due to class-level annotation
    
    @GetMapping
    public ResponseEntity<PageResponse<AuditLogDTO>> getAllAuditLogs(...) {
        // Can access current user via SecurityContextUtil
        String username = SecurityContextUtil.getCurrentUsername();
        logger.info("Fetching audit logs by admin: {}", username);
        // ... implementation
    }
}
```

## Key Design Decisions

1. **Two-Phase Security**: Authentication and authorization are separated into two interceptors for clarity and maintainability.

2. **Request Attributes**: User information is stored in request attributes rather than Spring Security context, making it lightweight and easy to access.

3. **Annotation-Based Authorization**: Using `@RequireRole` annotation provides declarative, readable security configuration.

4. **Flexible Role Matching**: Role comparison is case-insensitive and handles various role name formats (`admin`, `ADMIN`, `ROLE_ADMIN`).

5. **OR Logic for Multiple Roles**: When multiple roles are specified, user needs only one (OR logic), which is more permissive and user-friendly.

6. **Public Path Exclusion**: Swagger/OpenAPI and health check endpoints are excluded from authentication for development and monitoring purposes.

7. **Comprehensive Error Messages**: Detailed error messages help with debugging and provide clear feedback to API consumers.

## Security Considerations

1. **Secret Key**: The JWT secret key should be:
   - At least 256 bits (32 characters) for HS256 algorithm
   - Stored securely (environment variables, secrets manager)
   - Never committed to version control

2. **Token Expiration**: Tokens should have reasonable expiration times to limit exposure if compromised.

3. **HTTPS**: Always use HTTPS in production to protect tokens in transit.

4. **Role Validation**: The system validates roles from the token itself. Ensure your token issuer includes correct roles.

5. **Public Endpoints**: Be careful when adding paths to the public path exclusion list.

## Testing Considerations

When testing:
- Mock `JwtTokenValidator` to return test tokens
- Test both authenticated and unauthenticated scenarios
- Test role-based access with different role combinations
- Test expired and invalid tokens
- Test public path exclusions

## Migration Notes

When implementing this in another application:
1. Ensure JWT tokens are issued with `subject` claim containing username
2. Ensure JWT tokens include roles in `roles`, `role`, or `authorities` claim
3. Configure the secret key to match the token issuer
4. Adjust public path exclusions based on your application's needs
5. Update error handling to match your application's error response format
6. Consider adding additional security features like token refresh, rate limiting, etc.
