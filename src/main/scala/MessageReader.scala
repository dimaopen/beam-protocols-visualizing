package beam.protocolvis

import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import fs2._
import fs2.data.csv._

import java.nio.file.{Files, Path}

/**
 * @author Dmitry Openkov
 */
object MessageReader {

  def readData[F[_] : Sync](path: Path, blocker: Blocker)(implicit cs: ContextShift[F]): F[Stream[F, RowData]] = {
    val chunkSize = 32 * 1024

    def readSingleFile(path: Path): F[Stream[F, RowData]] = {
      import java.nio.file.FileSystems
      val fileData = io.file.readAll(path, blocker, chunkSize)
      for {
        matcher <- Sync[F].delay(FileSystems.getDefault.getPathMatcher("glob:*.gz"))
        isGzip = matcher.matches(path.getFileName)
        data = if (isGzip) fileData
          .through(compression.gunzip(chunkSize))
          .flatMap(_.content)
        else fileData
      } yield data
        .through(text.utf8Decode)
        .flatMap(Stream.emits(_))
        .through(rows[F]())
        .through(headers[F, String])
        .map(rowToRowData)

    }

    def findFiles(dir: Path): F[Seq[Path]] = {
      val FileNum = """actor_messages_(\d+)""".r.unanchored
      import scala.jdk.CollectionConverters._
      for {
        paths <- Sync[F].delay(Files.newDirectoryStream(dir, "*.actor_messages_*.csv.gz").iterator().asScala.toSeq)
        pathWithNum = paths
          .map(path => path -> path.getFileName.toString)
          .collect {
            case (path, FileNum(num)) => path -> num.toInt
          }
        sorted = pathWithNum.sortBy { case (_, num) => num }
      } yield sorted.map { case (path, _) => path }
    }

    def readFromDir(path: Path): F[Stream[F, RowData]] =
      for {
        files <- findFiles(path)
        fileStreamSeq <- files.traverse(readSingleFile)
      } yield Stream.emits(fileStreamSeq).flatten


    for {
      isDirectory <- Sync[F].delay(Files.isDirectory(path))
      data <- if (isDirectory) readFromDir(path) else readSingleFile(path)
    } yield data

  }

  private def rowToRowData(row: CsvRow[String]): RowData = {
    def extractActor(record: CsvRow[String], position: String) = {
      val parent = record(s"${position}_parent").get
      val name = record(s"${position}_name").get
      Actor(parent, name)
    }

    row("type").get match {
      case "message" =>
        Message(extractActor(row, "sender"), extractActor(row, "receiver"), row("payload").get,
          row("triggerId").get.toLong)
      case "event" =>
        Event(extractActor(row, "sender"), extractActor(row, "receiver"), row("payload").get,
          row("state").get, row("triggerId").get.toLong)
      case "transition" =>
        Transition(extractActor(row, "sender"), extractActor(row, "receiver"), row("payload").get,
          row("state").get, row("triggerId").get.toLong)
    }
  }


  sealed abstract class RowData {
    def sender: Actor

    def receiver: Actor

    def triggerId: Long
  }

  case class Event(
                    sender: Actor,
                    receiver: Actor,
                    payload: String,
                    data: String,
                    triggerId: Long,
                  ) extends RowData

  case class Message(
                      sender: Actor,
                      receiver: Actor,
                      payload: String,
                      triggerId: Long,
                    ) extends RowData

  case class Transition(
                         sender: Actor,
                         receiver: Actor,
                         prevState: String,
                         state: String,
                         triggerId: Long,
                       ) extends RowData

  case class Actor(parent: String, name: String)

}
