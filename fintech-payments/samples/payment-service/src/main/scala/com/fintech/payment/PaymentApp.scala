package com.fintech.payment

import com.fintech.payment.domain.{PaymentMethod, PaymentRequest}
import com.fintech.payment.config.{BatchConfig, DatabaseConfig}
import com.fintech.payment.repository.{PaymentRepository, PaymentRepositoryLive}
import com.fintech.payment.service.{FraudDetectionServiceLive, PaymentService, PaymentServiceLive}
import zio.{Chunk, Clock, Console, ZIO, ZIOAppDefault, ZLayer}
import javax.sql.DataSource
import java.util.UUID

object PaymentApp extends ZIOAppDefault:

  // Generate test payment data
  def generatePaymentRequests(count: Int): Chunk[PaymentRequest] =
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
  def batchProcessingProgram: ZIO[PaymentService & PaymentRepository, Throwable, Unit] =
    for
      _ <- Console.printLine("=" * 80)
      _ <- Console.printLine("🚀 Payment Service - High-Performance Batch Processing")
      _ <- Console.printLine("=" * 80)
      _ <- Console.printLine("")

      // Generate 1,000,000 payment requests
      paymentCount = 1_000_000
      _ <- Console.printLine(s"📝 Generating $paymentCount payment requests...")
      requests = generatePaymentRequests(paymentCount)
      _ <- Console.printLine(s"✅ Generated ${requests.size} payment requests")
      _ <- Console.printLine("")

      // Process batch
      batchId = s"batch-${UUID.randomUUID()}"
      _ <- Console.printLine(s"⚡ Starting batch processing: $batchId")
      _ <- Console.printLine(s"   Parallelism: 16 streams")
      _ <- Console.printLine(s"   Chunk size: 1000")
      _ <- Console.printLine(s"   Batch insert size: 500")
      _ <- Console.printLine("")

      startTime <- Clock.instant
      stats <- PaymentService.processBatch(requests, batchId)
      endTime <- Clock.instant

      // Display results
      _ <- Console.printLine("")
      _ <- Console.printLine("=" * 80)
      _ <- Console.printLine("📊 BATCH PROCESSING RESULTS")
      _ <- Console.printLine("=" * 80)
      _ <- Console.printLine(s"Batch ID:              ${stats.batchId}")
      _ <- Console.printLine(s"Tenant ID:             ${stats.tenantId}")
      _ <- Console.printLine(s"Total Payments:        ${stats.totalPayments}")
      _ <- Console.printLine(s"Successful:            ${stats.successfulPayments}")
      _ <- Console.printLine(s"Failed:                ${stats.failedPayments}")
      _ <- Console.printLine(s"Total Amount:          BRL ${stats.totalAmount}")
      _ <- Console.printLine(s"Duration:              ${stats.durationMs.getOrElse(0L)}ms (${stats.durationMs.getOrElse(0L).toDouble / 1000.0}s)")
      _ <- Console.printLine(s"Throughput:            ${stats.throughputPerSecond.map(_.setScale(2, BigDecimal.RoundingMode.HALF_UP)).getOrElse(0)} payments/second")
      _ <- Console.printLine("=" * 80)
      _ <- Console.printLine("")

      // Verify data in database
      _ <- Console.printLine("🔍 Verifying data in database...")
      totalCount <- PaymentRepository.countPayments()
      _ <- Console.printLine(s"✅ Total payments in database: $totalCount")
      _ <- Console.printLine("")

      _ <- Console.printLine("✅ Batch processing completed successfully!")
      _ <- Console.printLine("")
    yield ()

  // DataSource layer using HikariCP
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

  // Complete application layer composition
  val appLayer: ZLayer[Any, Throwable, PaymentService & PaymentRepository] =
    ZLayer.make[PaymentService & PaymentRepository](
      DatabaseConfig.layer,
      BatchConfig.layer,
      dataSourceLayer,
      PaymentRepositoryLive.layer,
      FraudDetectionServiceLive.layer,
      PaymentServiceLive.layer
    )

  override def run: ZIO[Any, Throwable, Unit] =
    batchProcessingProgram
      .provide(appLayer)
      .catchAll { error =>
        Console.printLineError(s"❌ Error: ${error.getMessage}") *>
          Console.printLineError(s"   Cause: ${error.getCause}") *>
          ZIO.fail(error)
      }
