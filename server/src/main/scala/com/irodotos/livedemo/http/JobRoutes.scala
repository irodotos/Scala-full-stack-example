package com.irodotos.livedemo.http

import cats.effect.*
import cats.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.dsl.Http4sDsl
import com.irodotos.livedemo.core.*
import com.irodotos.livedemo.domain.job.Job
import org.http4s.server.Router
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*

class JobRoutes[F[_]: Concurrent] private (jobs: Jobs[F]) extends Http4sDsl[F] {
  private val prefix = "/jobs"

  // post /jobs/create { Job }
  private val createJobRoute: HttpRoutes[F] = HttpRoutes.of[F] {
    case req@POST -> Root / "create" =>
      for {
        job <- req.as[Job]
        id <- jobs.create(job)
        resp <- Created(id)
      } yield resp
  }

  // get /jobs
  private val getAllRoute: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root =>
    jobs.all.flatMap(jobs => Ok(jobs))
  }

  val routes: HttpRoutes[F] = Router(
    prefix -> (createJobRoute <+> getAllRoute)
  )
}

object JobRoutes {
  def resource[F[_]: Concurrent](jobs: Jobs[F]): Resource[F, JobRoutes[F]] =
    Resource.pure(new JobRoutes[F](jobs))
}