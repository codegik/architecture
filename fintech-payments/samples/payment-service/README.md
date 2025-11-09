# Payment Service - High-Performance Batch Processing

A high-performance payment processing service built with **Java 25**, **Scala 3** and **ZIO** framework, demonstrating the architecture patterns from the fintech payment platform design.

## Features

- Process 1,000,000 payments efficiently  
- ZIO Streams with configurable parallelism  
- Real-time ML-based fraud scoring (simulated)  

## Technology Stack

- **Java 25**: Latest LTS version for performance and security
- **Scala 3.4.3**: Modern Scala with improved syntax
- **SBT 1.9.7**: Build tool for Scala projects
- **ZIO 2.0.19**: Functional effect system for async/concurrent programming
- **ZIO Streams**: High-performance streaming for batch processing
- **Quill**: Type-safe database queries with compile-time verification
- **PostgreSQL 15**: Relational database with ACID guarantees
- **HikariCP**: High-performance JDBC connection pooling
- **Docker Compose**: Container orchestration for PostgreSQL

## 1. Start PostgreSQL

```bash
cd samples/payment-service
docker compose up -d
```

Wait for PostgreSQL to initialize (~10 seconds):

## 2. Compile the Project

```bash
sbt compile
```

## 3. Run the Batch Processing

```bash
sbt run
```

This will process 1,000,000 payment requests in batches, applying fraud detection and storing results in PostgreSQL.

Here is a real sample output running on my local machine:

```
================================================================================
🚀 Payment Service - High-Performance Batch Processing
================================================================================

📝 Generating 1000000 payment requests...
✅ Generated 1000000 payment requests

⚡ Starting batch processing: batch-70528f6e-4294-4500-b776-27441243e70f
   Parallelism: 16 streams
   Chunk size: 1000
   Batch insert size: 500

timestamp=2025-11-09T18:08:26.351600Z level=INFO thread=#zio-fiber-2109486616 message="Starting batch processing: batch-70528f6e-4294-4500-b776-27441243e70f with 1000000 payments" location=com.fintech.payment.service.PaymentServiceLive.processBatch file=PaymentService.scala line=39
timestamp=2025-11-09T18:10:36.085715Z level=INFO thread=#zio-fiber-2109486616 message="Batch completed: batch-70528f6e-4294-4500-b776-27441243e70f | Duration: 129571ms | Throughput: 7717.78 payments/sec | Total in DB: 2000000" location=com.fintech.payment.service.PaymentServiceLive.processBatch file=PaymentService.scala line=89

================================================================================
📊 BATCH PROCESSING RESULTS
================================================================================
Batch ID:              batch-70528f6e-4294-4500-b776-27441243e70f
Tenant ID:             tenant_2
Total Payments:        1000000
Successful:            1000000
Failed:                0
Total Amount:          BRL 5003829100.41
Duration:              129571ms (129.571s)
Throughput:            7717.78 payments/second
================================================================================
```

## Performance Tuning

### Parallelism Configuration

Edit `src/main/scala/com/fintech/payment/config/Config.scala`:

```scala
BatchConfig(
  parallelism = 16,        // Number of parallel streams (adjust based on CPU cores)
  chunkSize = 1000,        // Payments per processing chunk
  batchInsertSize = 500,   // Database batch insert size
  retryAttempts = 3,
  retryDelay = 1.second
)
```

**Recommendations**:
- **parallelism**: Set to 2x CPU cores (e.g., 16 for 8-core machine)
- **chunkSize**: 500-2000 for optimal memory usage
- **batchInsertSize**: 100-1000 depending on network latency

### Database Connection Pool

Edit `DatabaseConfig`:

```scala
DatabaseConfig(
  url = "jdbc:postgresql://localhost:5432/payment_db",
  user = "payment_user",
  password = "payment_pass",
  maxPoolSize = 50,         // Adjust based on workload
  connectionTimeout = 30.seconds
)
```

## Key Implementation Details

### ZIO Streams Pipeline

The batch processing uses ZIO Streams for maximum performance:

```scala
ZStream
  .fromIterable(requests)                    // Create stream from requests
  .mapZIOParUnordered(parallelism) { ... }  // Process in parallel
  .grouped(batchInsertSize)                  // Group for batch inserts
  .mapZIO { chunk => repository.insertBatch(chunk) }
  .runCollect                                // Execute stream
```

**Benefits**:
- Non-blocking parallel processing
- Backpressure handling
- Memory-efficient chunking
- Automatic error recovery

### Fraud Detection

Simulated fraud detection with risk scoring:

```scala
// Risk score calculation (0-100)
baseScore + velocityScore + amountScore
// Decision logic
score < 50  => ALLOW      (proceed with payment)
score 50-70 => CHALLENGE  (require MFA)
score > 70  => BLOCK      (reject payment)
```

### Idempotency

Each payment has a unique idempotency key to prevent duplicates:

```scala
idempotencyKey = s"${tenantId}-${senderAccountId}-${receiverAccountId}-${amount}-${nanoTime}"

// Database unique constraint ensures no duplicates
CREATE UNIQUE INDEX idx_payments_unique_idempotency 
ON payments(tenant_id, idempotency_key);
```


## References

- [ZIO Documentation](https://zio.dev/)
- [Quill Documentation](https://getquill.io/)
- [Fintech Payment Platform Architecture](../../README.md)

