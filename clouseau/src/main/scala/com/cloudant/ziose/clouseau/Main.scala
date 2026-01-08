/*
sbt 'clouseau/runMain com.cloudant.ziose.clouseau.Main'
 */
package com.cloudant.ziose.clouseau

import com.cloudant.ziose.core.{ActorFactory, AddressableActor, EngineWorker, Node}
import com.cloudant.ziose.otp.{OTPLayers, OTPNodeConfig}
import com.cloudant.ziose.scalang.{ScalangMeterRegistry, ScalangPrometheusMeterRegistry}
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import zio.http.{Headers, Method, Response, Routes, Server, handler}
import zio.{&, LogLevel, RIO, Scope, System, Task, ZIO, ZIOAppArgs, ZIOAppDefault}

object Main extends ZIOAppDefault {
  def getNodeIdx: Task[Int] = {
    for {
      prop <- System.property("node")
      lastChar = prop.getOrElse("1").last
      index = {
        if (('1' to '3').contains(lastChar)) {
          lastChar - '1'
        } else {
          0
        }
      }
    } yield index
  }

  private def startSupervisor(
    node: ClouseauNode,
    config: WorkerConfiguration
  ): RIO[EngineWorker & Node & ActorFactory, AddressableActor[_, _]] = {
    val clouseauCfg: ClouseauConfiguration = config.clouseau.get
    val nodeCfg: OTPNodeConfig             = config.node
    ClouseauSupervisor.start(node, Configuration(clouseauCfg, nodeCfg, capacity(config)))
  }

  private def main(
    workerCfg: WorkerConfiguration,
    metricsRegistry: ScalangMeterRegistry,
    metricsPrometheusRegistry: ScalangPrometheusMeterRegistry,
    loggerCfg: LogConfiguration
  ): RIO[Scope & EngineWorker & Node & ActorFactory, Unit] = {
    for {
      runtime  <- ZIO.runtime[EngineWorker & Node & ActorFactory]
      otp_node <- ZIO.service[Node]
      remote_node = s"node${workerCfg.node.name.last}@${workerCfg.node.domain}"
      _      <- otp_node.monitorRemoteNode(remote_node)
      worker <- ZIO.service[EngineWorker]
      logLevel = loggerCfg.level.getOrElse(LogLevel.Debug)
      node <- ZIO.succeed(new ClouseauNode()(runtime, worker, metricsRegistry, metricsPrometheusRegistry, logLevel))
      supervisor <- startSupervisor(node, workerCfg)
      _          <- ZIO.addFinalizer(worker.shutdown *> supervisor.shutdown *> otp_node.shutdown)
      _          <- supervisor.awaitShutdown
    } yield ()
  }

  private val workerId: Int = 1
  private val engineId: Int = 1

  private def capacity(workerCfg: WorkerConfiguration) = {
    workerCfg.capacity.getOrElse(CapacityConfiguration())
  }

  def app(
    entryPoint: String,
    workerCfg: WorkerConfiguration,
    metricsRegistry: ScalangMeterRegistry,
    metricsPrometheusRegistry: ScalangPrometheusMeterRegistry,
    loggerCfg: LogConfiguration
  ): Task[Unit] = {
    val node = workerCfg.node
    val name = s"${node.name}@${node.domain}"
    for {
      _ <- ZIO.logInfo(s"Clouseau running as ${name} from ${entryPoint}")
      _ <- ZIO
        .scoped(main(workerCfg, metricsRegistry, metricsPrometheusRegistry, loggerCfg))
        .provide(OTPLayers.nodeLayers(engineId, workerId, node))
    } yield ()
  }

  private lazy val micrometerPrometheusRouter = {
    Method.GET / "metrics" -> handler(
      ZIO.serviceWith[PrometheusMeterRegistry](m => noCors(Response.text(m.scrape())))
    )
  }

  private def noCors(r: Response): Response = {
    r.updateHeaders(_.combine(Headers(("Access-Control-Allow-Origin", "*"))))
  }

  private lazy val httpServer = (
    Server
      .serve(Routes(micrometerPrometheusRouter))
      *> ZIO.never
  ).forkDaemon

  override def run = (
    for {
      appCfg  <- ZIO.service[AppCfg]
      nodeIdx <- getNodeIdx
      workerCfg                 = appCfg.config(nodeIdx)
      loggerCfg                 = appCfg.logger
      metricsRegistry           = ClouseauMetrics.makeRegistry
      metricsLayer              = ClouseauMetrics.makeLayer(metricsRegistry)
      metricsPrometheusRegistry = ClouseauPrometheusMetrics.makeRegistry
      metricsPrometheusLayer    = ClouseauPrometheusMetrics.makeLayer(metricsPrometheusRegistry)
      _ <- ZIO
        .scoped(
          for {
            f <- httpServer
            _ <- zio.Console.printLine(s"f: $f")
            _ <- zio.Console.printLine(f.getClass)
            _ <- app("Main", workerCfg, metricsRegistry, metricsPrometheusRegistry, loggerCfg)
            _ <- f.join
          } yield ()
        )
        .provide(
          LoggerFactory.loggerDefault(loggerCfg),
          metricsLayer,
          metricsPrometheusLayer,
          Server.default
        )
    } yield ()
  ).provideSome[ZIOAppArgs](AppCfg.layer)
}
