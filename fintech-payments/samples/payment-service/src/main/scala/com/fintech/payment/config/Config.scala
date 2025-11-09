package com.fintech.payment.config

import zio.{Duration, ZIO, ZLayer}
import scala.concurrent.duration.DurationInt
import javax.sql.DataSource

// Database configuration with validation
case class DatabaseConfig(
  url: String,
  user: String,
  password: String,
  maxPoolSize: Int,
  connectionTimeout: Duration
):
  require(url.nonEmpty && url.startsWith("jdbc:"), "Invalid JDBC URL - must start with 'jdbc:'")
  require(user.nonEmpty, "Database user cannot be empty")
  require(maxPoolSize > 0, "maxPoolSize must be positive")
  require(connectionTimeout.toMillis > 0, "connectionTimeout must be positive")

object DatabaseConfig:
  val databaseLayer: ZLayer[Any, Nothing, DatabaseConfig] = ZLayer.succeed(
    DatabaseConfig(
      url = "jdbc:postgresql://localhost:5432/payment_db",
      user = "payment_user",
      password = "payment_pass",
      maxPoolSize = 50, // High connection pool for batch processing
      connectionTimeout = Duration.fromScala(30.seconds)
    )
  )

  val dataSourceLayer: ZLayer[DatabaseConfig, Throwable, DataSource] =
    ZLayer.fromZIO {
      for
        config <- ZIO.service[DatabaseConfig]
        dataSource <- ZIO.attempt {
          val ds = new com.zaxxer.hikari.HikariDataSource()
          ds.setJdbcUrl(config.url)
          ds.setUsername(config.user)
          ds.setPassword(config.password)
          ds.setMaximumPoolSize(config.maxPoolSize)
          ds.setConnectionTimeout(config.connectionTimeout.toMillis)
          ds.setAutoCommit(true)
          ds
        }
      yield dataSource
    }

// Batch processing configuration with validation
case class BatchConfig(
  parallelism: Int,
  chunkSize: Int,
  batchInsertSize: Int,
  retryAttempts: Int,
  retryDelay: Duration
):
  require(parallelism > 0, "parallelism must be positive")
  require(chunkSize > 0, "chunkSize must be positive")
  require(batchInsertSize > 0, "batchInsertSize must be positive")
  require(retryAttempts >= 0, "retryAttempts must be non-negative")
  require(retryDelay.toMillis >= 0, "retryDelay must be non-negative")

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

