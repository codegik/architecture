package com.fintech.payment.config

import zio.{Duration, ZLayer}
import scala.concurrent.duration.DurationInt

// Database configuration
case class DatabaseConfig(
  url: String,
  user: String,
  password: String,
  maxPoolSize: Int,
  connectionTimeout: Duration
)

object DatabaseConfig:
  val layer: ZLayer[Any, Nothing, DatabaseConfig] = ZLayer.succeed(
    DatabaseConfig(
      url = "jdbc:postgresql://localhost:5432/payment_db",
      user = "payment_user",
      password = "payment_pass",
      maxPoolSize = 50, // High connection pool for batch processing
      connectionTimeout = Duration.fromScala(30.seconds)
    )
  )

// Batch processing configuration
case class BatchConfig(
  parallelism: Int,
  chunkSize: Int,
  batchInsertSize: Int,
  retryAttempts: Int,
  retryDelay: Duration
)

object BatchConfig:
  val layer: ZLayer[Any, Nothing, BatchConfig] = ZLayer.succeed(
    BatchConfig(
      parallelism = 16, // Number of parallel streams for processing
      chunkSize = 1000, // Process payments in chunks of 1000
      batchInsertSize = 500, // Insert 500 payments per batch to database
      retryAttempts = 3,
      retryDelay = Duration.fromScala(1.seconds)
    )
  )

// Application configuration
case class AppConfig(
  name: String,
  version: String,
  httpPort: Int
)

object AppConfig:
  val layer: ZLayer[Any, Nothing, AppConfig] = ZLayer.succeed(
    AppConfig(
      name = "payment-service",
      version = "1.0.0",
      httpPort = 8080
    )
  )
