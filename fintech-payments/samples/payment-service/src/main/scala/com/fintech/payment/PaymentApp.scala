package com.fintech.payment

import com.fintech.payment.domain.{PaymentMethod, PaymentRequest}
import com.fintech.payment.config.{BatchConfig, DatabaseConfig}
import com.fintech.payment.repository.{PaymentRepository, PaymentRepositoryLive}
import com.fintech.payment.service.{FraudDetectionServiceLive, PaymentService, PaymentServiceLive}
import zio.{Chunk, Clock, ZIO, ZIOAppDefault, ZLayer}
import zio.Console.{printLine, printLineError}
import javax.sql.DataSource
import java.util.UUID

object PaymentApp extends ZIOAppDefault:

  // Generate test payment data
  private def generatePaymentRequests(count: Int): Chunk[PaymentRequest] =
    Chunk.fromIterable(
      (1 to count).map { i =>
        val tenantId = s"tenant_${(i % 10) + 1}" // 10 different tenants
        PaymentRequest(
          tenantId = tenantId,
          senderAccountId = s"acc_sender_${i % 1000}",
          receiverAccountId = s"acc_receiver_${(i + 500) % 1000}",
          amount = BigDecimal(scala.util.Random.nextDouble() * 10000).setScale(2, BigDecimal.RoundingMode.HALF_UP),
          paymentMethod = PaymentMethod.values(i % PaymentMethod.values.length)
        )
      }
    )

  // Main batch processing program
  private def batchProcessingProgram(paymentCount: Int): ZIO[PaymentService & PaymentRepository, Throwable, Unit] =
    for
      _ <- printLine("=" * 80)
      _ <- printLine("🚀 Payment Service - High-Performance Batch Processing")
      _ <- printLine("=" * 80)
      _ <- printLine("")

      // Generate payment requests
      _ <- printLine(s"📝 Generating $paymentCount payment requests...")
      requests = generatePaymentRequests(paymentCount)
      _ <- printLine(s"✅ Generated ${requests.size} payment requests")
      _ <- printLine("")

      // Process batch
      batchId = s"batch-${UUID.randomUUID()}"
      _ <- printLine(s"⚡ Starting batch processing: $batchId")
      _ <- printLine(s"   Parallelism: 16 streams")
      _ <- printLine(s"   Chunk size: 1000")
      _ <- printLine(s"   Batch insert size: 500")
      _ <- printLine("")

      startTime <- Clock.instant
      stats <- PaymentService.processBatch(requests, batchId)
      endTime <- Clock.instant

      // Display results
      _ <- printLine("")
      _ <- printLine("=" * 80)
      _ <- printLine("📊 BATCH PROCESSING RESULTS")
      _ <- printLine("=" * 80)
      _ <- printLine(s"Batch ID:              ${stats.batchId}")
      _ <- printLine(s"Tenant ID:             ${stats.tenantId}")
      _ <- printLine(s"Total Payments:        ${stats.totalPayments}")
      _ <- printLine(s"Successful:            ${stats.successfulPayments}")
      _ <- printLine(s"Failed:                ${stats.failedPayments}")
      _ <- printLine(s"Total Amount:          BRL ${stats.totalAmount}")
      _ <- printLine(s"Duration:              ${stats.durationMs.getOrElse(0L)}ms (${stats.durationMs.getOrElse(0L).toDouble / 1000.0}s)")
      _ <- printLine(s"Throughput:            ${stats.throughputPerSecond.map(_.setScale(2, BigDecimal.RoundingMode.HALF_UP)).getOrElse(0)} payments/second")
      _ <- printLine("=" * 80)
      _ <- printLine("")

      // Verify data in database
      _ <- printLine("🔍 Verifying data in database...")
      totalCount <- PaymentRepository.countPayments()
      _ <- printLine(s"✅ Total payments in database: $totalCount")
      _ <- printLine("")

      _ <- printLine("✅ Batch processing completed successfully!")
      _ <- printLine("")
    yield ()

  // DataSource layer using HikariCP
  private val dataSourceLayer: ZLayer[DatabaseConfig, Throwable, DataSource] =
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

  // Complete application layer composition
  private val appLayer: ZLayer[Any, Throwable, PaymentService & PaymentRepository] =
    ZLayer.make[PaymentService & PaymentRepository](
      DatabaseConfig.layer,
      BatchConfig.layer,
      dataSourceLayer,
      PaymentRepositoryLive.layer,
      FraudDetectionServiceLive.layer,
      PaymentServiceLive.layer
    )

  override def run: ZIO[Any, Throwable, Unit] =
    batchProcessingProgram(paymentCount = 1_000_000)
      .provide(appLayer)
      .catchAll { error =>
        printLineError(s"❌ Error: ${error.getMessage}") *>
          printLineError(s"   Cause: ${error.getCause}") *>
          ZIO.fail(error)
      }
