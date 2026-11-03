package com.github.guihigashi.conduit.studio.infrastructure.ai

import zio.*
import com.github.guihigashi.conduit.studio.domain.article.{AiArticleGenerator, GeneratedArticle, WritingTone}
import com.github.guihigashi.conduit.studio.domain.tag.Tag

final case class IntelligentArticleGenerator() extends AiArticleGenerator:

  override def generateArticle(
      topic: String,
      authorTags: Set[Tag],
      writingTone: WritingTone,
      requestedTags: Set[String],
      targetWordCount: Option[Int] = None
  ): Task[GeneratedArticle] =
    val effectiveTags   = if requestedTags.nonEmpty then requestedTags else authorTags.map(_.value)
    val tagsList        = (effectiveTags + "architecture").toList
    val wordCountTarget = targetWordCount.getOrElse(600)

    val intro =
      s"""# $topic
         |
         |## Introduction
         |Modern software engineering requires continuous evolution in how we approach $topic. In this article, written from a $writingTone perspective, we explore core design principles, practical architectural patterns, and production trade-offs. As systems grow in complexity and scale, adopting disciplined boundaries and resilient communication protocols becomes essential for long-term maintainability.
         |""".stripMargin

    val coreConcepts =
      s"""
         |## Core Concepts & Architectural Blueprint
         |When designing systems focused on ${effectiveTags.mkString(", ")}, type safety, modular boundaries, and operational simplicity must remain paramount. Clear separation between domain models and infrastructure adapters ensures that business rules stay independent of external frameworks.
         |
         |```scala
         |// Architectural blueprint for $topic
         |trait Service[F[_]]:
         |  def execute(command: Command): F[Either[DomainError, Event]]
         |
         |final case class Engine(config: EngineConfig) extends Service[Task]:
         |  def execute(command: Command): Task[Either[DomainError, Event]] =
         |    ZIO.succeed(Right(Event.Processed(command.id)))
         |```
         |""".stripMargin

    val implementationDetails =
      s"""
         |## Implementation Deep Dive
         |Real-world deployments require handling edge cases, resource cleanup, and structured concurrency. Implementing declarative pipeline stages with built-in backpressure guarantees that throughput spikes do not exhaust connection pools or memory buffers.
         |
         |```scala
         |// Resilient pipeline execution
         |def processStream(source: ZStream[Any, Throwable, Message]): ZStream[Any, Throwable, Result] =
         |  source
         |    .mapZIOPar(4)(msg => handleMessage(msg).retry(Schedule.exponential(100.millis)))
         |    .tap(res => ZIO.logInfo(s"Processed $${res.id}"))
         |```
         |
         |By isolating failure domains at the boundary, errors can be classified into retryable transient faults and terminal validation errors without leaking internal stack traces.
         |""".stripMargin

    val resilienceAndPerformance =
      s"""
         |## Resilience & Production Observability
         |Operating $topic in production necessitates high-fidelity telemetry, distributed tracing, and metrics collection. Emphasizing structured logging and health checks gives operations teams immediate visibility into bottlenecks.
         |
         |- **Circuit Breakers**: Prevent cascading outages across downstream dependencies.
         |- **Dead Letter Queues**: Capture anomalous payloads for safe asynchronous inspection.
         |- **Resource Scoping**: Enforce deterministic finalization of sockets and file descriptors.
         |""".stripMargin

    val advancedPatterns =
      s"""
         |## Advanced Patterns & Trade-offs
         |Balancing latency against consistency is an unavoidable architectural tension. While eventual consistency simplifies distributed state across multi-region clusters, synchronous consensus models provide strict linearizability at the cost of coordination overhead.
         |
         |Evaluating these trade-offs early in system evolution prevents costly retroactive schema migrations and database partitioning refactors. Teams must document architectural decision records (ADRs) highlighting the specific trade-offs accepted.
         |""".stripMargin

    val conclusion =
      s"""
         |## Practical Takeaways
         |- Prioritize composability, functional purity, and resilience.
         |- Keep state transitions explicit and validated at domain boundaries.
         |- Embrace event-driven notification channels and automated recovery strategies.
         |
         |## Conclusion
         |Applying these principles to $topic ensures sustainable developer velocity, high reliability, and predictable latency under scale.
         |""".stripMargin

    val body =
      if wordCountTarget <= 250 then
        intro + coreConcepts + conclusion
      else if wordCountTarget <= 550 then
        intro + coreConcepts + implementationDetails + conclusion
      else if wordCountTarget <= 850 then
        intro + coreConcepts + implementationDetails + resilienceAndPerformance + conclusion
      else
        intro + coreConcepts + implementationDetails + resilienceAndPerformance + advancedPatterns + conclusion

    ZIO.succeed(
      GeneratedArticle(
        title = s"Architecting $topic: Principles and Practice",
        description = s"A comprehensive guide to $topic exploring design trade-offs with a $writingTone perspective.",
        body = body,
        tags = tagsList
      )
    )

object IntelligentArticleGenerator:
  val layer: ULayer[AiArticleGenerator] =
    ZLayer.succeed(IntelligentArticleGenerator())
