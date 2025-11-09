package com.fintech.payment

import com.fintech.payment.service.BatchProcessService
import zio.{ZIO, ZIOAppDefault}
import zio.Console.printLineError

object PaymentApp extends ZIOAppDefault:

  override def run: ZIO[Any, Throwable, Unit] =
    BatchProcessService.run(paymentCount = 1_000_000)
      .provide(BatchProcessService.layer)
      .catchAll { error =>
        printLineError(s"❌ Error: ${error.getMessage}") *>
          printLineError(s"   Cause: ${error.getCause}") *>
          ZIO.fail(error)
      }
