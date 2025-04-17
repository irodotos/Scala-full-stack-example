package com.irodotos.livedemo.core

import com.irodotos.livedemo.domain.job.*
import java.util.UUID

import cats.effect.*
import cats.syntax.all.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.transactor.Transactor
import java.{util => ju}
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor

trait Jobs[F[_]] { // "algebra"
  def create(job: Job): F[UUID]
  def all: F[List[Job]]
}

class JobsLive[F[_]: Concurrent] private (transactor: Transactor[F]) extends Jobs[F] {
  override def all: F[List[Job]] =
    sql"""
      SELECT
        company,
        title,
        description,
        externalUrl,
        salaryLo,
        salaryHi,
        currency,
        remote,
        location,
        country
      FROM jobs
    """
      .query[Job]
      .stream
      .transact(transactor)
      .compile
      .toList

  override def create(job: Job): F[ju.UUID] =
    sql"""
      INSERT INTO jobs(
        company,
        title,
        description,
        externalUrl,
        salaryLo,
        salaryHi,
        currency,
        remote,
        location,
        country
      ) VALUES (
        ${job.company},
        ${job.title},
        ${job.description},
        ${job.externalUrl},
        ${job.salaryLo},
        ${job.salaryHi},
        ${job.currency},
        ${job.remote},
        ${job.location},
        ${job.country}
      )
    """.update
      .withUniqueGeneratedKeys[UUID]("id")
      .transact(transactor)
}

object JobsLive {
  def make[F[_]: Concurrent](postgres: Transactor[F]): F[JobsLive[F]] =
    new JobsLive[F](postgres).pure[F]

  def resource[F[_]: Concurrent](postgres: Transactor[F]): Resource[F, JobsLive[F]] =
    Resource.pure(new JobsLive[F](postgres))
}

object JobsPlayground extends IOApp.Simple {

  private def makePostgres: Resource[IO, HikariTransactor[IO]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](16)
    transactor <- HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5444/",
      "docker",
      "docker",
      ec
    )
  } yield transactor

  private def program(postgres: Transactor[IO]): IO[Unit] =
    for {
      jobs <- JobsLive.make[IO](postgres)
      _    <- jobs.create(Job.dummy)
      list <- jobs.all
      _    <- IO.println(list)
    } yield ()

  override def run: IO[Unit] =
    makePostgres.use(program)
}