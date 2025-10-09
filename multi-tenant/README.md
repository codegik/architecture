# Single Tenant vs Multi Tenant Applications

# Multi-Tenant

Multiple independent organizations (Tenant A, B, C, etc.) can have completely isolated data, services or infrastructure.

It depends on the use case where you can identify the level of isolation required.

We can have levels of Multi-Tenancy.

## 1. Infrastructure Isolation
Is the highest level of multi-tenancy isolation where each tenant gets completely separate infrastructure resources.

**Complete Resource Separation**

- Each tenant has dedicated servers, databases, application instances, and storage.
- No shared compute, memory, or storage resources between tenants.
- Each tenant essentially runs on their own isolated environment.

**Network Isolation**

- Separate VPCs, subnets, and security groups per tenant.
- Independent load balancers and networking components.
- Isolated DNS and routing configurations.

**Data Isolation**

- Completely separate database instances per tenants.
- No risk of data leakage between tenants.
- Each tenant can have different database configurations, versions, or types.

**Security**

- Maximum security isolation - breach in one tenant doesn't affect others.
- Independent security policies and access controls.

## 2. Shared Infrastructure, Separate Databases

This is a middle-ground approach where tenants share compute resources but maintain complete data isolation through separate database instances.

**Shared Application Layer**

- Multiple tenants share the same application servers, web servers, and compute instances.
- Apps deployed across shared infrastructure.
- Shared load balancers, networking components, and application runtime.

**Network Sharing**

- Shared VPC, subnets, and networking infrastructure.
- Common security groups with tenant-aware application logic.
- Shared DNS and routing configurations with tenant identification.

**Database Isolation**

- Each tenant has a dedicated database instance or cluster.
- Complete data separation with no risk of cross-tenant data access.
- Independent database configurations, versions, and performance tuning.
- Separate backup, recovery, and maintenance schedules per tenant.

**Security Benefits**

- Data isolation prevents tenant data leakage.
- Application-level tenant validation and access control.


## 3. Shared Database, Separate Schemas

This approach maximizes resource sharing while maintaining logical data separation through database schema isolation.

**Shared Application Layer**

- Multiple tenants share the same application servers, web servers, and compute instances.
- Apps deployed across shared infrastructure.
- Shared load balancers, networking components, and application runtime.

**Network Sharing**

- Shared VPC, subnets, and networking infrastructure.
- Common security groups with tenant-aware application logic.
- Shared DNS and routing configurations with tenant identification.

**Shared Single Database Instance**

- All tenants share the same database server and instance.
- Common database engine, memory, and storage resources.
- Shared connection pooling and database management overhead.
- Cost-effective database licensing and infrastructure.

**Schema-Level Isolation**

- Each tenant gets a dedicated database schema.
- Tables, views, and stored procedures are isolated per tenant.
- Schema naming conventions (e.g., tenant_a.users, tenant_b.users).

**Application-Level Tenant Routing**

- Application logic determines which schema to query based on tenant context.
- Connection strings or query prefixes route to appropriate schema.
- Tenant identification from JWT tokens or session context.
- Dynamic schema switching within the application.

**Security Considerations**

- Database-level access controls prevent cross-tenant data access.
- Application-layer validation ensures tenant isolation.
- Shared database means potential blast radius if compromised.
- Requires careful privilege management and query validation.


## Routing Mechanism

Independently of the isolation level, you need a way to identify the tenant and route requests to the appropriate tenant's resources.

To redirect tenants to their dedicated infrastructure in an Infrastructure Isolation model, you need a routing mechanism at the entry point:

**DNS-Based Routing** 

Use DNS to direct requests to the appropriate tenant's infrastructure based on subdomains or domain names.
```
# Route 53 Configuration
- tenant-a.yourapp.com -> ALB-A (10.1.0.0/16)
- tenant-b.yourapp.com -> ALB-B (10.2.0.0/16)
- tenant-c.yourapp.com -> ALB-C (10.3.0.0/16)
```

**Path-Based Routing**

Use a single domain with path-based routing to direct requests to the appropriate tenant's infrastructure.
```
# API Gateway Configuration
- yourapp.com/tenant-a/* -> ALB-A
- yourapp.com/tenant-b/* -> ALB-B
- yourapp.com/tenant-c/* -> ALB-C
```

**Header-Based Routing**

Use custom headers to identify the tenant and route requests accordingly.
```
# CloudFront or API Gateway Configuration
- X-Tenant-ID: tenant-a -> Origin-A
- X-Tenant-ID: tenant-b -> Origin-B
- X-Tenant-ID: tenant-c -> Origin-C
```

**Authentication and Authorization Based Routing**

Implement single authentication service for all tenants (e.g., OAuth, JWT). Token contains all necessary routing information.
```
# ALB Listener Rules (configured per tenant)
Rules:
  - Condition: Host header = "tenant-a.yourapp.com"
    Action: Forward to tenant-a-target-group
  - Condition: Host header = "tenant-b.yourapp.com"  
    Action: Forward to tenant-b-target-group
```


## Multi-Tenant Trade Offs Comparison

| Aspect                     | Infrastructure Isolation                   | Shared Infrastructure, Separate Databases     | Shared Database, Separate Schemas         |
|----------------------------|--------------------------------------------|-----------------------------------------------|-------------------------------------------|
| **Cost**                   | Highest - Dedicated resources per tenant   | Medium - Shared compute, separate DB costs    | Lowest - Maximum resource sharing         |
| **Security**               | Maximum - Complete isolation               | Moderate - Data isolated, shared compute risk | Lowest - Single DB compromise affects all |
| **Compliance**             | Excellent - Meets strictest requirements   | Good - Suitable for most standards            | Limited - May not meet strict regulations |
| **Operational Complexity** | High - Multiple environments to manage     | Medium - Mixed management approach            | Low - Single environment                  |
| **Performance**            | Predictable - No tenant interference       | Mixed - Shared compute, isolated DB           | Shared - Noisy neighbor issues            |
| **Scaling**                | Independent per tenant                     | Mixed - App scales together, DB individually  | Unified - All tenants scale together      |
| **Data Isolation**         | Complete physical separation               | Complete database separation                  | Logical schema separation                 |
| **Blast Radius**           | Single tenant only                         | Application layer affects all, data isolated  | All tenants affected                      |
| **Maintenance**            | Complex - Per tenant operations            | Balanced - Shared app, separate DB ops        | Simple - Single operation set             |
| **Best For**               | Enterprise, compliance-critical            | Mid-market, data privacy needs                | Startups, cost-sensitive                  |
| **Tenant Impact**          | Zero cross-tenant impact                   | Compute interference possible                 | High cross-tenant impact risk             |
| **Customization**          | High - Per tenant configs                  | Medium - Shared app, custom DB                | Low - Shared everything                   |
