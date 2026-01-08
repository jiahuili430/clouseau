package com.cloudant.ziose.scalang

import com.cloudant.ziose.macros.CheckEnv
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.config.NamingConvention
import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}
import io.prometheus.metrics.model.registry.PrometheusRegistry

import java.util.concurrent.TimeUnit

class ScalangPrometheusMeterRegistry(
  registry: PrometheusRegistry,
  durationUnit: TimeUnit,
  rateUnit: TimeUnit
) extends PrometheusMeterRegistry(
      PrometheusConfig.DEFAULT,
      registry,
      Clock.SYSTEM
    ) {
  def getDurationUnit = durationUnit
  def getRateUnit     = rateUnit
  def rateUnitName(unit: TimeUnit) = {
    unit match {
      case TimeUnit.DAYS         => "events/day"
      case TimeUnit.HOURS        => "events/hour"
      case TimeUnit.MICROSECONDS => "events/microsecond"
      case TimeUnit.MILLISECONDS => "events/millisecond"
      case TimeUnit.MINUTES      => "events/minute"
      case TimeUnit.NANOSECONDS  => "events/nanosecond"
      case TimeUnit.SECONDS      => "events/second"
    }
  }

  @CheckEnv(System.getProperty("env"))
  def toStringMacro: List[String] = List(
    s"${getClass.getSimpleName}",
    s"registry=$registry",
    s"durationUnit=$durationUnit",
    s"rateUnit=$rateUnit"
  )
}

object ScalangPrometheusMeterRegistry {
  def make(
    durationUnit: TimeUnit,
    rateUnit: TimeUnit
  ): ScalangPrometheusMeterRegistry = {
    val registry = {
      new ScalangPrometheusMeterRegistry(
        PrometheusRegistry.defaultRegistry,
        durationUnit,
        rateUnit
      )
    }
    registry.config().namingConvention(NamingConvention.dot)
    registry
  }
}
