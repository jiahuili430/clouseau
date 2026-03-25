package com.cloudant.ziose.clouseau

import com.cloudant.ziose.scalang.{JmxObjectNameComponents, ScalangMeterRegistry}
import com.codahale.metrics.{Metric, MetricFilter}
import io.micrometer.core.instrument.MeterRegistry
import zio.metrics.connectors.micrometer.{MicrometerConfig, micrometerLayer}
import zio.{ULayer, ZLayer}

import java.util.concurrent.TimeUnit

object ClouseauMetrics {
  private val rateUnit     = TimeUnit.SECONDS
  private val durationUnit = TimeUnit.MILLISECONDS

  private class ClouseauMetricsFilter extends MetricFilter {
    def matches(name: String, metric: Metric): Boolean = {
      /*
       * We use histogram under the hood to implement timer,
       * so we want to prevent reporting of them.
       */
      val blackList = List(".*InitService\\|spawned\\.timer\\.histogram.+".r)
      !blackList.exists(_.findFirstIn(name).isDefined)
    }
  }

  def makeRegistry: ScalangMeterRegistry = ScalangMeterRegistry.make(
    "com.cloudant.clouseau",
    new ClouseauMetricsFilter,
    clouseauObjectNameTransformer,
    durationUnit,
    rateUnit
  )

  private def clouseauObjectNameTransformer: JmxObjectNameComponents => JmxObjectNameComponents = {
    (components: JmxObjectNameComponents) =>
      {
        val domain = {
          if (components.packageName == "com.cloudant.ziose.clouseau") {
            components.domain
          } else {
            components.packageName
          }
        }
        JmxObjectNameComponents(domain, components.packageName, components.service, components.name)
      }
  }

  def makeLayer(metricsRegistry: MeterRegistry): ULayer[Unit] = {
    ZLayer.succeed(MicrometerConfig.default) ++
      ZLayer.succeed(metricsRegistry) >>>
      micrometerLayer
  }
}
