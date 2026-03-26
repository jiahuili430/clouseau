package com.cloudant.ziose.clouseau

import com.cloudant.ziose.scalang.Adapter
import zio.{Exit, Fiber, IO, Runtime, UIO, Unsafe, ZIO}

trait ZioSupport {
  implicit final class ZioOps[E, A](self: IO[E, A]) {
    def unsafeRunWithForkDaemon(implicit adapter: Adapter[_, _]): UIO[Fiber.Runtime[E, A]] = {
      Unsafe.unsafe(implicit u => adapter.runtime.unsafe.run(self).forkDaemon)
    }
  }

  implicit final class ZioOpsREA[R, E, A](self: ZIO[R, E, A]) {
    def unsafeRunWithCustomRuntime(runtime: Runtime[R]): Exit[E, A] = {
      Unsafe.unsafe(implicit u => runtime.unsafe.run(self))
    }

    def unsafeRunWithCustomRuntimeGetOrThrow(runtime: Runtime[R]): A = {
      Unsafe.unsafe(implicit u => runtime.unsafe.run(self).getOrThrowFiberFailure)
    }
  }
}
