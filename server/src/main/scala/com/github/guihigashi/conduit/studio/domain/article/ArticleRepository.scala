package com.github.guihigashi.conduit.studio.domain.article

import java.util.UUID
import zio.*

trait ArticleRepository:
  def save(article: Article): Task[Article]
  def update(article: Article): Task[Article]
  def findById(id: UUID): Task[Option[Article]]
  def findAll(status: Option[ArticleStatus] = None, authorId: Option[UUID] = None): Task[List[Article]]
  def claimNextForGeneration: Task[Option[Article]]
  def claimNextForPublishing: Task[Option[Article]]
  def delete(id: UUID): Task[Unit]
