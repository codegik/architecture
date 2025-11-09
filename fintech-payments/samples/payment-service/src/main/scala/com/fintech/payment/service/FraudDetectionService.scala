package com.fintech.payment.service

import com.fintech.payment.domain.{PaymentRequest, FraudCheckResult, FraudDecision}
import zio.{UIO, URIO, ZIO, ZLayer}

// Fraud Detection Service - simulates fraud scoring based on architecture
trait FraudDetectionService:
  def checkFraud(payment: PaymentRequest): UIO[FraudCheckResult]

object FraudDetectionService:
  def checkFraud(payment: PaymentRequest): URIO[FraudDetectionService, FraudCheckResult] =
    ZIO.serviceWithZIO[FraudDetectionService](_.checkFraud(payment))

// Simple fraud detection implementation (simulated)
case class FraudDetectionServiceLive() extends FraudDetectionService:

  override def checkFraud(payment: PaymentRequest): UIO[FraudCheckResult] =
    for
      // Simulate fraud scoring based on amount and velocity
      baseScore <- ZIO.succeed(calculateBaseScore(payment))
      velocityScore <- checkVelocity(payment)
      amountScore <- checkAmountPattern(payment)

      totalScore = (baseScore + velocityScore + amountScore).min(100)
      decision = determineDecision(totalScore)
      reasons = generateReasons(totalScore, payment)
    yield FraudCheckResult(totalScore, decision, reasons)

  private def calculateBaseScore(payment: PaymentRequest): Int =
    // Base score calculation
    val amountFactor = (payment.amount / 10000).toInt.min(30)
    val random = scala.util.Random.nextInt(20)
    amountFactor + random

  private def checkVelocity(payment: PaymentRequest): UIO[Int] =
    // Simulate velocity check (in real implementation, query recent transactions)
    ZIO.succeed(scala.util.Random.nextInt(25))

  private def checkAmountPattern(payment: PaymentRequest): UIO[Int] =
    // Check for unusual amounts
    ZIO.succeed {
      if payment.amount > 50000 then 30
      else if payment.amount > 10000 then 15
      else 5
    }

  private def determineDecision(score: Int): FraudDecision =
    score match
      case s if s < 50 => FraudDecision.ALLOW
      case s if s <= 70 => FraudDecision.CHALLENGE
      case _ => FraudDecision.BLOCK

  private def generateReasons(score: Int, payment: PaymentRequest): List[String] =
    val reasons = scala.collection.mutable.ListBuffer[String]()

    if payment.amount > 50000 then
      reasons += "High transaction amount"

    if score > 70 then
      reasons += "Risk score exceeds block threshold"
    else if score > 50 then
      reasons += "Risk score requires MFA challenge"

    reasons.toList

object FraudDetectionServiceLive:
  val layer: ZLayer[Any, Nothing, FraudDetectionService] =
    ZLayer.succeed(FraudDetectionServiceLive())
