package com.cloudant.ziose.clouseau

import _root_.com.cloudant.ziose.scalang
import _root_.com.cloudant.ziose.core
import core.{Actor, ActorBuilder, AddressableActor, EngineWorker, Node, ProcessContext}
import scalang.Service
import scalang.Adapter
import scalang.SNode
import zio.Exit.Failure
import zio.Exit.Success
import com.cloudant.ziose.scalang.ScalangMeterRegistry
import com.cloudant.ziose.macros.CheckEnv
import zio.{&, Exit, LogLevel, Runtime, Tag}

class ClouseauNode(implicit
  override val runtime: Runtime[core.EngineWorker & core.Node],
  worker: core.EngineWorker,
  metricsRegistry: ScalangMeterRegistry,
  logLevel: LogLevel
) extends SNode(metricsRegistry, logLevel)(runtime)
    with ZioSupport {
  /*
   * Each service would need to implement a constructor in the following form
   *
   * ```scala
   * import com.cloudant.ziose.scalang.{Node => SNode}
   *
   * object ClouseauSupervisor extends ActorConstructor[ClouseauSupervisor] {
   *   def make(node: SNode, service_ctx: ServiceContext[ConfigurationArgs]) = {
   *     def maker[PContext <: ProcessContext](process_context: PContext): ClouseauSupervisor =
   *       ClouseauSupervisor(service_ctx)(Adapter(process_context, node))
   *
   *     ActorBuilder()
   *       .withCapacity(16)
   *       .withName("ClouseauSupervisor")
   *       .withMaker(maker)
   *       .build(this)
   *     }
   *
   *   def start(node: SNode, config: Configuration) = {
   *     val ctx = new ServiceContext[ConfigurationArgs] {val args = ConfigurationArgs(config)}
   *     node.spawnServiceZIO[ClouseauSupervisor, ConfigurationArgs](make(node, ctx))
   *   }
   * }
   * ```
   */

  val workerId = worker.id

  override def spawn(fun: scalang.Process => Unit): scalang.Pid = {
    val result: AddressableActor[_ <: Actor, _ <: ProcessContext] = (
      for {
        addressable <- worker.spawn(SimpleProcess.make(this, fun))
      } yield addressable
    ).unsafeRunWithCustomRuntimeGetOrThrow(runtime)

    result.self
  }

  override def spawnService[TS <: Service[A] with Actor: Tag, A <: Product](
    builder: ActorBuilder.Sealed[TS]
  )(implicit adapter: Adapter[_, _]): core.Result[core.Node.Error, AddressableActor[TS, ProcessContext]] = {
    val result: Exit[Node.Error, AddressableActor[TS, _ <: ProcessContext]] = {
      spawnServiceZIO[TS, A](builder)
        .unsafeRunWithCustomRuntime(runtime)
    } // TODO: kill the caller

    result match {
      case Failure(cause) if cause.isFailure     => core.Failure(cause.failureOption.get)
      case Failure(cause) if cause.isDie         => core.Failure(core.Node.Error.Unknown(cause.dieOption.get))
      case Failure(cause) if cause.isInterrupted => core.Failure(core.Node.Error.Interrupt(cause.interruptOption.get))
      case Failure(cause: core.Node.Error)       => core.Failure(cause)
      case Failure(cause) => core.Failure(core.Node.Error.Unknown(new Throwable(cause.prettyPrint)))
      case Success(actor) => core.Success(actor.asInstanceOf[AddressableActor[TS, ProcessContext]])
    }
  }

  override def spawnService[TS <: Service[A] with Actor: Tag, A <: Product](
    builder: ActorBuilder.Sealed[TS],
    reentrant: Boolean
  )(implicit adapter: Adapter[_, _]): core.Result[core.Node.Error, AddressableActor[TS, ProcessContext]] = {
    // TODO Handle reentrant argument
    val result: Exit[Node.Error, AddressableActor[TS, _ <: ProcessContext]] = {
      spawnServiceZIO[TS, A](builder)
        .unsafeRunWithCustomRuntime(runtime)
    } // TODO: kill the caller

    result match {
      case Failure(cause) if cause.isFailure     => core.Failure(cause.failureOption.get)
      case Failure(cause) if cause.isDie         => core.Failure(core.Node.Error.Unknown(cause.dieOption.get))
      case Failure(cause) if cause.isInterrupted => core.Failure(core.Node.Error.Interrupt(cause.interruptOption.get))
      case Failure(cause: core.Node.Error)       => core.Failure(cause)
      case Failure(cause) => core.Failure(core.Node.Error.Unknown(new Throwable(cause.prettyPrint)))
      case Success(actor) => core.Success(actor.asInstanceOf[AddressableActor[TS, ProcessContext]])
    }
  }

  override def spawnServiceZIO[TS <: Service[A] with Actor: Tag, A <: Product](builder: ActorBuilder.Sealed[TS]) = {
    for {
      addressable <- worker.spawn[TS](builder)
    } yield addressable
  }

  override def spawnServiceZIO[TS <: Service[A] with Actor: Tag, A <: Product](
    builder: ActorBuilder.Sealed[TS],
    reentrant: Boolean
  ) = {
    // TODO Handle reentrant argument
    for {
      addressable <- worker.spawn[TS](builder)
    } yield addressable
  }

  @CheckEnv(System.getProperty("env"))
  def toStringMacro: List[String] = List(
    s"${getClass.getSimpleName}",
    s"runtime=$runtime",
    s"worker=$worker",
    s"metricsRegistry=$metricsRegistry"
  )
}
