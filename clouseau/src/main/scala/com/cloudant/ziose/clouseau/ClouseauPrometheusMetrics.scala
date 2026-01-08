package com.cloudant.ziose.clouseau

import com.cloudant.ziose.scalang.ScalangPrometheusMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import zio.metrics.connectors.micrometer.MicrometerConfig
import zio.ZLayer

import java.util.concurrent.TimeUnit

object ClouseauPrometheusMetrics {
  val rateUnit     = TimeUnit.SECONDS
  val durationUnit = TimeUnit.MILLISECONDS

  def makeRegistry: ScalangPrometheusMeterRegistry = {
    ScalangPrometheusMeterRegistry
      .make(durationUnit, rateUnit)
  }

  def makeLayer(metricsPrometheusRegistry: PrometheusMeterRegistry) = {
    ZLayer.succeed(MicrometerConfig.default) ++
      ZLayer.succeed(metricsPrometheusRegistry)
  }
}
