package com.fintech.payment.repository

import com.fintech.payment.domain.*
import io.getquill.*
import zio.*

import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

trait PaymentRepository:
  def insertBatch(payments: Chunk[Payment]): Task[Long]
  def findById(id: UUID): Task[Option[Payment]]
  def findByTenantAndStatus(tenantId: String, status: PaymentStatus): Task[List[Payment]]
  def updateStatus(id: UUID, status: PaymentStatus, processedAt: Instant): Task[Long]
  def saveBatchStatistics(stats: BatchStatistics): Task[Long]
  def countPayments(): Task[Long]

object PaymentRepository:
  def insertBatch(payments: Chunk[Payment]): ZIO[PaymentRepository, Throwable, Long] =
    ZIO.serviceWithZIO[PaymentRepository](_.insertBatch(payments))

  def findById(id: UUID): ZIO[PaymentRepository, Throwable, Option[Payment]] =
    ZIO.serviceWithZIO[PaymentRepository](_.findById(id))

  def findByTenantAndStatus(tenantId: String, status: PaymentStatus): ZIO[PaymentRepository, Throwable, List[Payment]] =
    ZIO.serviceWithZIO[PaymentRepository](_.findByTenantAndStatus(tenantId, status))

  def updateStatus(id: UUID, status: PaymentStatus, processedAt: Instant): ZIO[PaymentRepository, Throwable, Long] =
    ZIO.serviceWithZIO[PaymentRepository](_.updateStatus(id, status, processedAt))

  def saveBatchStatistics(stats: BatchStatistics): ZIO[PaymentRepository, Throwable, Long] =
    ZIO.serviceWithZIO[PaymentRepository](_.saveBatchStatistics(stats))

  def countPayments(): ZIO[PaymentRepository, Throwable, Long] =
    ZIO.serviceWithZIO[PaymentRepository](_.countPayments())

// Quill-based implementation for PostgreSQL
case class PaymentRepositoryLive(dataSource: DataSource) extends PaymentRepository:
  import io.getquill.SnakeCase

  val ctx = new PostgresZioJdbcContext(SnakeCase)
  import ctx.*

  // Custom encoders/decoders for enums
  given MappedEncoding[PaymentStatus, String] = MappedEncoding[PaymentStatus, String](_.toString)
  given MappedEncoding[String, PaymentStatus] = MappedEncoding[String, PaymentStatus](PaymentStatus.valueOf)

  given MappedEncoding[FraudDecision, String] = MappedEncoding[FraudDecision, String](_.toString)
  given MappedEncoding[String, FraudDecision] = MappedEncoding[String, FraudDecision](FraudDecision.valueOf)

  given MappedEncoding[PaymentMethod, String] = MappedEncoding[PaymentMethod, String](_.toString)
  given MappedEncoding[String, PaymentMethod] = MappedEncoding[String, PaymentMethod](PaymentMethod.valueOf)

  // Quill schema mapping
  private inline def paymentSchema = quote {
    querySchema[Payment]("payments")
  }

  private inline def batchStatsSchema = quote {
    querySchema[BatchStatistics]("batch_statistics")
  }

  override def insertBatch(payments: Chunk[Payment]): Task[Long] =
    if payments.isEmpty then ZIO.succeed(0L)
    else
      ctx.run(
        liftQuery(payments.toList).foreach(p => paymentSchema.insertValue(p))
      ).provideEnvironment(ZEnvironment(dataSource))
       .map(_.sum) // Sum the list of affected rows

  override def findById(id: UUID): Task[Option[Payment]] =
    ctx.run(
      paymentSchema.filter(_.id == lift(id))
    ).map(_.headOption)
     .provideEnvironment(ZEnvironment(dataSource))

  override def findByTenantAndStatus(tenantId: String, status: PaymentStatus): Task[List[Payment]] =
    ctx.run(
      paymentSchema
        .filter(p => p.tenantId == lift(tenantId) && p.status == lift(status))
    ).provideEnvironment(ZEnvironment(dataSource))

  override def updateStatus(id: UUID, status: PaymentStatus, processedAt: Instant): Task[Long] =
    ctx.run(
      quote {
        paymentSchema
          .filter(_.id == lift(id))
          .update(
            _.status -> lift(status),
            _.processedAt -> Some(lift(processedAt))
          )
      }
    ).provideEnvironment(ZEnvironment(dataSource))

  override def saveBatchStatistics(stats: BatchStatistics): Task[Long] =
    ctx.run(
      batchStatsSchema.insertValue(lift(stats))
    ).provideEnvironment(ZEnvironment(dataSource))

  override def countPayments(): Task[Long] =
    ctx.run(
      paymentSchema.size
    ).provideEnvironment(ZEnvironment(dataSource))

object PaymentRepositoryLive:
  val layer: ZLayer[DataSource, Nothing, PaymentRepository] =
    ZLayer.fromFunction(PaymentRepositoryLive.apply)

