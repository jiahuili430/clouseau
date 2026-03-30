package com.cloudant.ziose.clouseau

import com.cloudant.ziose.scalang.Adapter
import zio.{Fiber, IO, UIO, Unsafe}

trait ZioSupport {
  implicit final class ZioOps[E, A](self: IO[E, A]) {
    def unsafeRunWithForkDaemon(implicit adapter: Adapter[_, _]): UIO[Fiber.Runtime[E, A]] = {
      Unsafe.unsafe(implicit u => adapter.runtime.unsafe.run(self).forkDaemon)
    }
  }
}
