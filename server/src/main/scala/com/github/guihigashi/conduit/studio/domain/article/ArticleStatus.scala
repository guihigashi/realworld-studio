package com.github.guihigashi.conduit.studio.domain.article

import zio.schema.*

enum ArticleStatus derives Schema:
  case QueuedGeneration
  case Generating
  case PendingApproval
  case Approved
  case Publishing
  case Published
  case Failed

  def canTransitionTo(next: ArticleStatus): Boolean = (this, next) match
    case (QueuedGeneration, Generating)      => true
    case (Generating, PendingApproval)       => true
    case (Generating, Failed)                => true
    case (PendingApproval, Approved)         => true
    case (PendingApproval, QueuedGeneration) => true
    case (Approved, Publishing)              => true
    case (Approved, QueuedGeneration)        => true
    case (Publishing, Published)             => true
    case (Publishing, Failed)                => true
    case (Failed, QueuedGeneration)          => true
    case _                                   => false

object ArticleStatus:
  def fromString(s: String): Option[ArticleStatus] =
    val normalized = s.replaceAll("_", "").toLowerCase.trim
    values.find(_.toString.toLowerCase == normalized)
