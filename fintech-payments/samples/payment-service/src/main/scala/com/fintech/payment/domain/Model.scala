package com.fintech.payment.domain

import zio.json.{DeriveJsonCodec, JsonCodec}
import java.time.Instant
import java.util.UUID

// Payment status enum
enum PaymentStatus:
  case PENDING, PROCESSING, COMPLETED, FAILED

object PaymentStatus:
  given JsonCodec[PaymentStatus] = JsonCodec.string.transform(
    str => PaymentStatus.valueOf(str),
    status => status.toString
  )

// Fraud decision enum
enum FraudDecision:
  case ALLOW, CHALLENGE, BLOCK

object FraudDecision:
  given JsonCodec[FraudDecision] = JsonCodec.string.transform(
    str => FraudDecision.valueOf(str),
    decision => decision.toString
  )

// Payment method enum
enum PaymentMethod:
  case BANK_TRANSFER, CREDIT_CARD, PIX

object PaymentMethod:
  given JsonCodec[PaymentMethod] = JsonCodec.string.transform(
    str => PaymentMethod.valueOf(str),
    method => method.toString
  )

// Payment domain model
case class Payment(
  id: UUID,
  tenantId: String,
  senderAccountId: String,
  receiverAccountId: String,
  amount: BigDecimal,
  currency: String,
  status: PaymentStatus,
  idempotencyKey: String,
  paymentMethod: PaymentMethod,
  fraudScore: Int,
  fraudDecision: Option[FraudDecision],
  externalGatewayId: Option[String],
  externalGatewayResponse: Option[String],
  errorMessage: Option[String],
  createdAt: Instant,
  updatedAt: Instant,
  processedAt: Option[Instant]
)

object Payment:
  given JsonCodec[Payment] = DeriveJsonCodec.gen[Payment]

// Payment request for batch processing
case class PaymentRequest(
  tenantId: String,
  senderAccountId: String,
  receiverAccountId: String,
  amount: BigDecimal,
  currency: String = "BRL",
  paymentMethod: PaymentMethod = PaymentMethod.PIX
)

object PaymentRequest:
  given JsonCodec[PaymentRequest] = DeriveJsonCodec.gen[PaymentRequest]

  // Generate idempotency key from request attributes
  def generateIdempotencyKey(req: PaymentRequest): String =
    s"${req.tenantId}-${req.senderAccountId}-${req.receiverAccountId}-${req.amount}-${System.nanoTime()}"

// Payment response
case class PaymentResponse(
  id: UUID,
  status: PaymentStatus,
  fraudScore: Int,
  fraudDecision: Option[FraudDecision],
  errorMessage: Option[String]
)

object PaymentResponse:
  given JsonCodec[PaymentResponse] = DeriveJsonCodec.gen[PaymentResponse]

// Batch statistics
case class BatchStatistics(
  id: UUID,
  batchId: String,
  tenantId: String,
  totalPayments: Int,
  successfulPayments: Int,
  failedPayments: Int,
  totalAmount: BigDecimal,
  startTime: Instant,
  endTime: Option[Instant],
  durationMs: Option[Long],
  throughputPerSecond: Option[BigDecimal],
  createdAt: Instant
)

object BatchStatistics:
  given JsonCodec[BatchStatistics] = DeriveJsonCodec.gen[BatchStatistics]

// Payment validation errors
enum PaymentValidationError:
  case InvalidAmount(amount: BigDecimal)
  case InvalidAccountId(accountId: String)
  case DuplicateIdempotencyKey(key: String)
  case InsufficientBalance(accountId: String, balance: BigDecimal, required: BigDecimal)
  case AccountNotFound(accountId: String)
  case TenantNotFound(tenantId: String)

// Fraud check result
case class FraudCheckResult(
  score: Int,
  decision: FraudDecision,
  reasons: List[String]
)

object FraudCheckResult:
  given JsonCodec[FraudCheckResult] = DeriveJsonCodec.gen[FraudCheckResult]
