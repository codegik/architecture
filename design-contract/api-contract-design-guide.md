# API Contract Design: A Deep Dive

## Core Principles

### 1. Stability Over Flexibility
API contracts are promises to consumers. Breaking changes destroy trust and create operational chaos.

**Key Rule**: Once published, a contract should be treated as immutable for that version.

### 2. Explicit Over Implicit
Every field, every status code, every behavior should be explicitly defined. Never rely on implementation details or "obvious" behavior.

**Bad**:
```json
{
  "date": "2025-10-13"
}
```

**Good**:
```json
{
  "effectiveDate": "2025-10-13T00:00:00Z"
}
```

### 3. Consumer-Centric Design
Design contracts based on what consumers need, not what your database schema looks like.

### 4. Self-Describing
Contracts should be understandable without reading implementation code or extensive documentation.

### 5. Principle of Least Surprise
Follow industry standards and conventions. Don't reinvent patterns unless absolutely necessary.

## Contract-First vs Code-First

### Contract-First Approach

Design the API contract before writing any code.

**Process**:
1. Define business requirements
2. Create OpenAPI/Swagger spec or Proto files
3. Review with stakeholders
4. Generate client SDKs or client data schema
5. Implement business logic

**Advantages**:
- Forces clear thinking about API design
- Enables parallel development (frontend/backend)
- Contract becomes source of truth
- Early validation with consumers

**Disadvantages**:
- Requires discipline to maintain spec
- Can feel slower at start because we need to understand the requirements deeply

### Code-First Approach

Write code first, generate contracts from annotations or code inspection.

**Advantages**:
- Faster initial development
- Contract always matches implementation
- Less tooling complexity

**Disadvantages**:
- Implementation details leak into contract
- Harder to review API design separately
- Refactoring changes the contract
- No contract validation before implementation

**When to use**: Internal tools, prototypes, POCs

### Recommendation
Use **Contract-First** for any API that will have multiple consumers or needs to be stable.

## Versioning Strategies

### Why Version?
APIs evolve. Versioning allows evolution without breaking existing consumers.

### Versioning Approaches

#### 1. URI Versioning
```
GET /api/v1/users/123
GET /api/v2/users/123
```

**Pros**:
- Clear and visible
- Easy to route in load balancers
- Simple to understand
- Can host multiple versions simultaneously

**Cons**:
- Version proliferation in URLs

**Best for**: Public APIs, major version changes

#### 2. Header Versioning
```
GET /api/users/123
Accept: application/vnd.myapp.v1+json

GET /api/users/123
Accept: application/vnd.myapp.v2+json
```

**Pros**:
- Clean URLs
- Can negotiate version

**Cons**:
- Less visible
- Harder to test (can't just click link)
- Requires custom header handling

**Best for**: Internal APIs, sophisticated consumers

#### 3. Query Parameter Versioning
```
GET /api/users/123?version=1
GET /api/users/123?version=2
```

**Pros**:
- Easy to implement
- Visible and testable

**Cons**:
- Clutters query params
- Can conflict with filtering params

**Best for**: Nothing (avoid if possible)

## Request Design

### HTTP Methods and Semantics

#### GET
- Retrieve resources
- Must be idempotent and safe
- No request body
- Cacheable

```
GET /api/v1/users/123
GET /api/v1/users?status=active&limit=50
```

#### POST
- Create new resources
- Not idempotent
- Has request body
- Not cacheable by default

```
POST /api/v1/users
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com"
}
```

#### PUT
- Replace entire resource
- Idempotent
- Client provides full resource representation

```
PUT /api/v1/users/123
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com",
  "status": "active"
}
```

#### PATCH
- Partial update
- Should be idempotent
- Client provides only fields to change

```
PATCH /api/v1/users/123
Content-Type: application/json

{
  "email": "newemail@example.com"
}
```

#### DELETE
- Remove resource
- Idempotent
- May or may not have request body

```
DELETE /api/v1/users/123
```

### Query Parameters

#### Filtering
```
GET /api/v1/users?status=active
GET /api/v1/users?status=active&role=admin
```

#### Sorting
```
GET /api/v1/users?sort=lastName
GET /api/v1/users?sort=lastName:asc
```

#### Pagination
```
GET /api/v1/users?limit=50&offset=100
```

#### Field Selection
```
GET /api/v1/users?fields=id,firstName,lastName
```

#### Search
```
GET /api/v1/users?q=john
```

### Request Body Design

#### Keep It Flat When Possible
```json
{
  "firstName": "John",
  "lastName": "Doe",
  "email": "john@example.com"
}
```

#### Avoid Deep Nesting
```json
{
  "user": {
    "profile": {
      "personal": {
        "name": {
          "first": "John"
        }
      }
    }
  }
}
```

## Response Design

### HTTP Status Codes

#### Success Codes (2xx)

**200 OK**: Standard success response
```
GET /api/v1/users/123
200 OK
```

**201 Created**: Resource created successfully
```
POST /api/v1/users
201 Created
Location: /api/v1/users/123
```

**202 Accepted**: Request accepted for async processing
```
POST /api/v1/reports
202 Accepted
Location: /api/v1/reports/status/abc123
```

#### Client Error Codes (4xx)

**400 Bad Request**: Invalid request syntax or validation error
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      {
        "field": "email",
        "message": "Invalid email format"
      }
    ]
  }
}
```

**401 Unauthorized**: Authentication required or failed
```json
{
  "error": {
    "code": "AUTHENTICATION_REQUIRED",
    "message": "Valid authentication token required"
  }
}
```

**403 Forbidden**: Authenticated but not authorized
```json
{
  "error": {
    "code": "INSUFFICIENT_PERMISSIONS",
    "message": "User does not have permission to perform this action"
  }
}
```

**404 Not Found**: Resource doesn't exist
```json
{
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "User with id 123 not found"
  }
}
```

**422 Unprocessable Entity**: Business logic validation failed
```json
{
  "error": {
    "code": "INSUFFICIENT_BALANCE",
    "message": "Account balance too low for this transaction"
  }
}
```

#### Server Error Codes (5xx)

Try to avid returning 500 errors. Use 4xx when possible.

Never returns the stack trace of the internal error.

**500 Internal Server Error**: Unexpected server error
```json
{
  "error": {
    "code": "INTERNAL_ERROR",
    "message": "An unexpected error occurred",
    "requestId": "abc123-def456"
  }
}
```

## Evolution and Backward Compatibility

### Safe Changes (Non-Breaking)

1. Adding optional request fields
2. Adding response fields
3. Adding new endpoints
4. Making required fields optional
5. Adding new enum values (with care)
6. Making validation less strict

### Breaking Changes

1. Removing or renaming fields
2. Changing field types
3. Making optional fields required
4. Changing URLs or HTTP methods
5. Removing endpoints
6. Changing behavior
7. Making validation stricter

### Deprecation Strategy

1. **Announce**: Document deprecation date and alternative
2. **Warn**: Add deprecation headers
3. **Support**: Maintain deprecated version during transition
4. **Remove**: Only after sunset period

```
Deprecation: version="v1", date="2025-07-01", link="/docs/migration/v2"
Sunset: Wed, 01 Jul 2025 00:00:00 GMT
```


## Security Considerations

### Authentication

**Bearer Token**:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Basic Auth** (avoid at all):
```
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
```

### Input Validation

1. **Whitelist, not blacklist**: Define what's allowed
2. **Validate types**: Ensure string is string, int is int
3. **Validate ranges**: Check min/max values
4. **Validate formats**: Email, phone, URL patterns
5. **Validate lengths**: Prevent DOS with huge inputs
6. **Sanitize output**: Prevent XSS in responses

### Sensitive Data

Never expose in responses:
- Passwords (even hashed)
- Full credit card numbers
- Social security numbers
- API keys/secrets
- Internal IDs that reveal business info

**Bad**:
```json
{
  "creditCard": "4532-1234-5678-9010",
  "ssn": "123-45-6789"
}
```

**Good**:
```json
{
  "creditCardLast4": "9010",
  "creditCardBrand": "visa",
  "ssnLast4": "6789"
}
```

### HTTPS Only

All production APIs must use HTTPS. Include in spec:

## Performance Patterns

### Bulk Operations

**Bad** (N API calls):
```
POST /api/v1/users/123/tags
POST /api/v1/users/124/tags
POST /api/v1/users/125/tags
```

**Good** (1 API call):
```
POST /api/v1/users/tags/bulk
{
  "operations": [
    {"userId": "123", "tags": ["premium"]},
    {"userId": "124", "tags": ["verified"]},
    {"userId": "125", "tags": ["active"]}
  ]
}
```

## Best Practices Summary

1. **Start with Contract-First design** for important APIs
2. **Version from day one** using URI versioning (v1, v2)
3. **Use semantic HTTP methods** correctly (GET, POST, PUT, PATCH, DELETE)
4. **Return proper status codes** (200, 201, 400, 404, 500, etc.)
5. **Use nouns for resources**, not verbs
6. **Keep URLs shallow** (max 2-3 levels)
7. **Always use ISO-8601 for timestamps** in UTC
8. **Use strings for money**, include currency
9. **Document nullable fields** explicitly
10. **Include pagination metadata** for collections
11. **Use structured error responses** with codes and details
12. **Support idempotency** for non-safe operations
13. **Never break backward compatibility** within a major version
14. **Deprecate before removing** with clear sunset dates
15. **Always use HTTPS** in production
16. **Validate all inputs** strictly
17. **Never expose sensitive data** in responses
18. **Write comprehensive OpenAPI specs**
19. **Implement contract tests** between consumers and providers
20. **Use HTTP caching** appropriately
21. **Provide bulk operations** for efficiency
22. **Allow field selection** to reduce payload size
23. **Use correlation IDs** for request tracing
24. **Document everything** including edge cases
25. **Design for monitoring** (include request IDs, timing info)
26. **Plan for errors** (retries, timeouts, circuit breakers)
27. **Keep it simple** unless complexity is justified

