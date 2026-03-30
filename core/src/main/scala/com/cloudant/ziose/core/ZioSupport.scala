package com.cloudant.ziose.core

import zio.{Exit, IO, Runtime, Unsafe, ZIO}

trait ZioSupport {
  implicit final class ZioOps[E, A](self: IO[E, A]) {
    def unsafeRun: Exit[E, A] = {
      Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(self))
    }

    def unsafeRunGetOrThrow: A = {
      Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(self).getOrThrowFiberFailure())
    }
  }

  implicit final class ZioOpsREA[R, E, A](self: ZIO[R, E, A]) {
    def unsafeRunWithCustomRuntime(runtime: Runtime[R]): Exit[E, A] = {
      Unsafe.unsafe(implicit u => runtime.unsafe.run(self))
    }

    def unsafeRunWithCustomRuntimeGetOrThrow(runtime: Runtime[R]): A = {
      Unsafe.unsafe(implicit u => runtime.unsafe.run(self).getOrThrowFiberFailure())
    }
  }
}
