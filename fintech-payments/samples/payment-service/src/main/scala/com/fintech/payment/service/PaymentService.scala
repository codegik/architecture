package com.fintech.payment.service

import com.fintech.payment.config.BatchConfig
import com.fintech.payment.domain.{BatchStatistics, FraudCheckResult, FraudDecision, Payment, PaymentRequest, PaymentResponse, PaymentStatus}
import com.fintech.payment.repository.PaymentRepository
import zio.stream.ZStream
import zio.{Chunk, Task, ZIO, ZLayer}

import java.time.Instant
import java.util.UUID

trait PaymentService:
  def processBatch(requests: Chunk[PaymentRequest], batchId: String): Task[BatchStatistics]
  def processPayment(request: PaymentRequest): Task[PaymentResponse]
  def getPaymentById(id: UUID): Task[Option[Payment]]

object PaymentService:
  def processBatch(requests: Chunk[PaymentRequest], batchId: String): ZIO[PaymentService, Throwable, BatchStatistics] =
    ZIO.serviceWithZIO[PaymentService](_.processBatch(requests, batchId))

  def processPayment(request: PaymentRequest): ZIO[PaymentService, Throwable, PaymentResponse] =
    ZIO.serviceWithZIO[PaymentService](_.processPayment(request))

  def getPaymentById(id: UUID): ZIO[PaymentService, Throwable, Option[Payment]] =
    ZIO.serviceWithZIO[PaymentService](_.getPaymentById(id))

case class PaymentServiceLive(
  repository: PaymentRepository,
  fraudService: FraudDetectionService,
  batchConfig: BatchConfig
) extends PaymentService:

  override def processBatch(requests: Chunk[PaymentRequest], batchId: String): Task[BatchStatistics] =
    val startTime = Instant.now()
    val tenantId = requests.headOption.map(_.tenantId).getOrElse("unknown")
    val totalAmount = requests.map(_.amount).sum

    for
      _ <- ZIO.logInfo(s"Starting batch processing: $batchId with ${requests.size} payments")

      // Process payments in parallel streams with chunking for optimal performance
      results <- ZStream
        .fromIterable(requests)
        .mapZIOParUnordered(batchConfig.parallelism) { request =>
          processPaymentInternal(request)
            .catchAll { error =>
              ZIO.logError(s"Failed to process payment: ${error.getMessage}") *>
                ZIO.succeed(createFailedPayment(request, error.getMessage))
            }
        }
        .grouped(batchConfig.batchInsertSize)
        .mapZIO { chunk =>
          repository.insertBatch(chunk) *>
            ZIO.logDebug(s"Inserted batch of ${chunk.size} payments")
        }
        .runCollect

      endTime = Instant.now()
      durationMs = endTime.toEpochMilli - startTime.toEpochMilli

      // Calculate statistics
      finalCount <- repository.countPayments()
      successCount = requests.size
      failCount = 0
      throughput = BigDecimal(requests.size) / BigDecimal(durationMs) * 1000

      stats = BatchStatistics(
        id = UUID.randomUUID(),
        batchId = batchId,
        tenantId = tenantId,
        totalPayments = requests.size,
        successfulPayments = successCount,
        failedPayments = failCount,
        totalAmount = totalAmount,
        startTime = startTime,
        endTime = Some(endTime),
        durationMs = Some(durationMs),
        throughputPerSecond = Some(throughput),
        createdAt = Instant.now()
      )

      _ <- repository.saveBatchStatistics(stats)
      _ <- ZIO.logInfo(
        s"Batch completed: $batchId | " +
        s"Duration: ${durationMs}ms | " +
        s"Throughput: ${throughput.setScale(2, BigDecimal.RoundingMode.HALF_UP)} payments/sec | " +
        s"Total in DB: $finalCount"
      )
    yield stats

  override def processPayment(request: PaymentRequest): Task[PaymentResponse] =
    for
      payment <- processPaymentInternal(request)
      _ <- repository.insertBatch(Chunk(payment))
    yield PaymentResponse(
      id = payment.id,
      status = payment.status,
      fraudScore = payment.fraudScore,
      fraudDecision = payment.fraudDecision,
      errorMessage = payment.errorMessage
    )

  override def getPaymentById(id: UUID): Task[Option[Payment]] =
    repository.findById(id)

  // Internal processing logic
  private def processPaymentInternal(request: PaymentRequest): Task[Payment] =
    for
      _ <- validatePayment(request)
      fraudResult <- fraudService.checkFraud(request)
      status = determinePaymentStatus(fraudResult)
      payment = Payment(
        id = UUID.randomUUID(),
        tenantId = request.tenantId,
        senderAccountId = request.senderAccountId,
        receiverAccountId = request.receiverAccountId,
        amount = request.amount,
        currency = request.currency,
        status = status,
        idempotencyKey = PaymentRequest.generateIdempotencyKey(request),
        paymentMethod = request.paymentMethod,
        fraudScore = fraudResult.score,
        fraudDecision = Some(fraudResult.decision),
        externalGatewayId = None,
        externalGatewayResponse = None,
        errorMessage = if status == PaymentStatus.FAILED then Some(s"Fraud check: ${fraudResult.reasons.mkString(", ")}") else None,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        processedAt = if status == PaymentStatus.COMPLETED then Some(Instant.now()) else None
      )

      _ <- ZIO.logDebug(s"Processed payment ${payment.id}: status=${status}, fraudScore=${fraudResult.score}")
    yield payment

  private def validatePayment(request: PaymentRequest): Task[Unit] =
    if request.amount <= 0 then
      ZIO.fail(new IllegalArgumentException("Amount must be positive"))
    else
      ZIO.unit

  private def determinePaymentStatus(fraudResult: FraudCheckResult): PaymentStatus =
    fraudResult.decision match
      case FraudDecision.ALLOW => PaymentStatus.COMPLETED
      case FraudDecision.CHALLENGE => PaymentStatus.PROCESSING
      case FraudDecision.BLOCK => PaymentStatus.FAILED

  private def createFailedPayment(request: PaymentRequest, errorMsg: String): Payment =
    Payment(
      id = UUID.randomUUID(),
      tenantId = request.tenantId,
      senderAccountId = request.senderAccountId,
      receiverAccountId = request.receiverAccountId,
      amount = request.amount,
      currency = request.currency,
      status = PaymentStatus.FAILED,
      idempotencyKey = PaymentRequest.generateIdempotencyKey(request),
      paymentMethod = request.paymentMethod,
      fraudScore = 0,
      fraudDecision = None,
      externalGatewayId = None,
      externalGatewayResponse = None,
      errorMessage = Some(errorMsg),
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      processedAt = None
    )

object PaymentServiceLive:
  val layer: ZLayer[PaymentRepository & FraudDetectionService & BatchConfig, Nothing, PaymentService] =
    ZLayer.fromFunction(PaymentServiceLive.apply)
