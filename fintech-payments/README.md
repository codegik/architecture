# 🧬 Architecture overview

# 🏛️ Structure

## 1 🎯 Problem Statement and Context [DONE]

A fintech is developing a payment platform that will allow customers to:

* Register accounts and user profiles (customers and merchants)
* Perform payments between users
* Query and generate financial statements and reports
* Integrate with external payment gateways (e.g., banks, credit cards)
* Send notifications (email, push)
* Handle massive operations (e.g., processing thousands of daily payments)

The system must be multi-tenant, support rapid growth, and ensure high availability, auditability, and security of sensitive data.


### 1.1 Problem space [DONE]

The fintech payment platform faces several critical challenges that must be addressed in the architecture design:

#### 1.1.1 Transaction Volume and Performance
- The platform must handle massive daily transaction volumes. 
- Payment processing cannot be delayed, as users expect immediate confirmation. 
- The system must support peak loads during business hours and special events (e.g., Black Friday, salary payment days) without degradation.

#### 1.1.2 Multi-Tenancy Complexity
- Supporting multiple tenants introduces complexity in data isolation, security boundaries, and resource allocation. 
- The architecture must ensure complete data isolation between tenants while maintaining operational efficiency and avoiding resource waste.

#### 1.1.3 Financial Data Security and Compliance
- Payment platforms handle highly sensitive data including account numbers, transaction histories, user data. 
- The system must comply with strict regulatory requirements such as LGPD.
- Ensure end-to-end encryption, secure key management, and audit trails.

#### 1.1.4 Integration Challenges
- The platform must integrate with external systems including banks and credit card networks. 
- Each integration has different protocols, authentication mechanisms, response times, and reliability characteristics. 
- The architecture must handle asynchronous responses, timeout scenarios and possible failures.

#### 1.1.5 Consistency and Auditability
- Financial systems require atomic transactions. 
- Every operation must be auditable with complete traceability of who did what, when, and why. 
- The system needs to support regulatory audits, dispute resolution, and fraud investigation.

#### 1.1.6 Availability and Disaster Recovery
- Payment platforms are critical systems where downtime directly impacts revenue and customer trust. 
- The architecture must achieve 99.9% or higher availability through redundancy, automatic failover, and disaster recovery capabilities. 
- This includes multi-region deployment, database replication.

#### 1.1.7 Real-Time Notifications and Communication
- Users expect immediate notifications for payment confirmations, security alerts. 
- The system must support notification by push and email. 


## 2. 🎯 Goals [DONE]

- Scalability: Design a scalable architecture.
- Security: Ensure security best practices.
- High Availability: Achieve 99.9% uptime.
- Auditability: Design for audit trails.
- Multi-Tenancy: Isolate tenant data.

## 3. 🎯 Non-Goals

- Cryptocurrency or Blockchain Integration.
- International Multi-Currency Support.
- Lending or Credit Services.
- Investment Portfolio Management.
- Physical Card Issuance.
- Legacy System Backward Compatibility.
- White-Label.


## 📐 4. Principles

- Cloud-Native: Leverage cloud services for scalability, availability, and managed infrastructure.
- Security First: Prioritize security in every design decision.
- API-First: Design APIs before implementation.
- Observability: Build in logging, monitoring, and tracing from day one.
- Resilience: Design for failure and automatic recovery.
- Auditability: Maintain immutable audit logs.
- Performance: Optimize for critical paths without compromising consistency.


# 🏗️ 5. Overall Diagrams

## 🗂️ 5.1 Overall architecture


## 🗂️ 5.2 Deployment


## 🗂️ 5.3 Use Cases


## 🧭 6. Trade-offs

### Major Decisions

### Tradeoffs

1. Open-source.
* ✅ PROS:
    * Benefits from community support and contributions.
* 🚫 CONS:
    * Supporting and maintaining in house.

3. WebSockets vs HTTP
* ✅ PROS:
    * WebSockets provide lower latency and faster communication due to persistent connections.
    * Full-duplex communication allows for real-time updates.
* 🚫 CONS:
    * Requires additional setup and management compared to traditional HTTP requests.
* Benchmark comparison

  | Requests | Websocket total duration | HTTP total duration | Winner                |
  |----------|--------------------------|---------------------|-----------------------|
  | 1K       | 110 ms                   | 204 ms              | Websocket ~92% faster |
  | 10K      | 12123 ms                 | 19757 ms            | Websocket ~93% faster |
  | 100K     | 120914 ms                | 219011 ms           | Websocket ~94% faster |
  Source: https://github.com/codegik/websocket-benchmark


## 🌏 7. For each key major component


## 💾 8. Migrations
There is no migration needed for this project as it is being built from scratch.

## 🧪 9. Testing strategy

- Before creating new tests, we should first ensure that the existing tests are running and passing.
    - Increase the coverage of existing integration/contract tests to 80% or more.
    - We should not start the migration without having a good coverage of the existing contracts.
    - It will reduce the chances of breaking existing functionality during the migration.
    - The testes must run in developer environments and CI/CD pipeline.

- Frontend Tests
    - Svelte component rendering tests with focus on performance metrics.
    - Client-side state management tests.
    - WebSocket client implementation tests.

- Contract tests
    - Test API contracts between decomposed microservices (Product, User, Review, Order, etc.).
    - Verify WebSocket message formats and protocols.
    - Validate data synchronization contracts between PostgreSQL and OpenSearch.

- Integration tests
    - Try to cover most of the scenarios, e.g. Uploading file, deleting file, searching file, updating metadata, etc.
    - Test WebSocket real-time communication flows.
    - Run in isolated environments before production deployment.

- Infra tests
    - Verify PGsync data synchronization between Aurora and OpenSearch.
    - Test CloudFront edge caching effectiveness.
    - Validate Global Accelerator routing behavior.

- Performance tests
    - Use Gatling to simulate the user behavior and check the system's performance.
    - Test search latency using OpenSearch under various query patterns.
    - Measure database query performance under load
    - Measure UI rendering time across device types
    - Benchmark WebSocket vs HTTP performance in real usage scenarios
    - Track CDN cache hit/miss ratios
    - Execute in staging environment with production-like conditions

- Chaos tests
    - Simulate AWS region failures to test Global Accelerator failover
    - Test WebSocket reconnection strategies during network disruptions
    - Inject latency between services to identify performance bottlenecks
    - Verify system behavior during PGsync failures
    - Execute in isolated production environment during low-traffic periods

## 👀 10. Observability strategy

Observability-based testing in production (also called "testing in production" or "production testing") uses monitoring, logging, and tracing data to validate system behavior after deployment.

There will be an event notifier that is going to log all operations during the migration.

There will be a dashboard to expose the migration progress, metrics and performance.

There will be alerts to notify the team about any issue during the migration.

Here are the key approaches:

- **Synthetic Monitoring**: Collect features metrics (latency, counters, etc) continuously to validate critical user journeys.

- **Real User Monitoring**: Track actual user interactions and performance metrics.
    - Svelte component render times
    - WebSocket connection success rates
    - Search result relevance and speed
    - Page load times across different regions
-
- **Error Rate Monitoring**: Set up alerts for anomalies in.
    - WebSocket connection failures
    - OpenSearch query timeouts
    - Aurora PostgreSQL connection pool exhaustion
    - CloudFront 5xx errors

- **Business Metrics Validation**: Monitor business KPIs to detect regressions.
    - Product listing success rate
    - Search-to-purchase conversion
    - User session duration
    - Revenue per visitor

## 👌 11. Technology Stack

### Frontend
- **Framework**: Svelte (migrating from React 16)
- **Build Tool**: Vite
- **State Management**: Universal Store (compatible with both React and Svelte)
- **Real-time Communication**: WebSocket API

### Backend
- **Primary Language**: Java (Latest Stable Version)
- **Framework**: Spring Boot
- **Build Tool**: Maven
- **Migration Assistant**: GitHub Copilot

### Database & Search
- **Primary Database**: Aurora PostgreSQL
- **Search Engine**: OpenSearch
- **Data Synchronization**: PGsync

### Observability & Monitoring
- **Metrics Collection**: CloudWatch
- **Logging**: CloudWatch Logs



## 👥 12. References

* Architecture Anti-Patterns: https://architecture-antipatterns.tech/
* EIP https://www.enterpriseintegrationpatterns.com/
* SOA Patterns https://patterns.arcitura.com/soa-patterns
* API Patterns https://microservice-api-patterns.org/
* Anti-Patterns https://sourcemaking.com/antipatterns/software-development-antipatterns
* Refactoring Patterns https://sourcemaking.com/refactoring/refactorings
* Database Refactoring Patterns https://databaserefactoring.com/
* Data Modelling Redis https://redis.com/blog/nosql-data-modeling/
* Cloud Patterns https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/introduction.html
* 12 Factors App https://12factor.net/
* Relational DB Patterns https://www.geeksforgeeks.org/design-patterns-for-relational-databases/
* Rendering Patterns https://www.patterns.dev/vanilla/rendering-patterns/
* REST API Design https://blog.stoplight.io/api-design-patterns-for-rest-web-services
