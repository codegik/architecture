package com.fintech.payment.service

import com.fintech.payment.config.{BatchConfig, DatabaseConfig}
import com.fintech.payment.repository.{PaymentRepository, PaymentRepositoryLive}
import com.fintech.payment.service.{FraudDetectionServiceLive, PaymentService, PaymentServiceLive}
import zio.Console.printLine
import zio.{ZIO, ZLayer}

import java.util.UUID

object BatchProcessService:

  def run(paymentCount: Int): ZIO[PaymentService & PaymentRepository, Throwable, Unit] =
    for
      _ <- printLine("=" * 80)
      _ <- printLine("🚀 Payment Service - High-Performance Batch Processing")
      _ <- printLine("=" * 80)
      _ <- printLine("")
      _ <- printLine(s"📝 Generating $paymentCount payment requests...")
      requests = PaymentService.generatePaymentRequests(paymentCount)
      _ <- printLine(s"✅ Generated ${requests.size} payment requests")
      _ <- printLine("")
      batchId = s"batch-${UUID.randomUUID()}"
      _ <- printLine(s"⚡ Starting batch processing: $batchId")
      _ <- printLine(s"   Parallelism: 16 streams")
      _ <- printLine(s"   Chunk size: 1000")
      _ <- printLine(s"   Batch insert size: 500")
      _ <- printLine("")
      stats <- PaymentService.processBatch(requests, batchId)
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
      _ <- printLine("🔍 Verifying data in database...")
      totalCount <- PaymentRepository.countPayments()
      _ <- printLine(s"✅ Total payments in database: $totalCount")
      _ <- printLine("")
      _ <- printLine("✅ Batch processing completed successfully!")
      _ <- printLine("")
    yield ()

  val layer: ZLayer[Any, Throwable, PaymentService & PaymentRepository] =
    ZLayer.make[PaymentService & PaymentRepository](
      DatabaseConfig.databaseLayer,
      BatchConfig.layer,
      DatabaseConfig.dataSourceLayer,
      PaymentRepositoryLive.layer,
      FraudDetectionServiceLive.layer,
      PaymentServiceLive.layer
    )

