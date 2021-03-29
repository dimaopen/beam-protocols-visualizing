package beam.protocolvis

import cats.effect.{Blocker, ContextShift, Sync}
import fs2._
import fs2.data.csv._

import java.nio.file.Path

/**
 * @author Dmitry Openkov
 */
object MessageReader {

  def readData[F[_] : Sync](path: Path, blocker: Blocker)(implicit cs: ContextShift[F]): Stream[F, RowData] = {
    val chunkSize = 32 * 1024

    def readFile(path: Path): Stream[F, Byte] = {
      import java.nio.file.FileSystems
      val matcher = FileSystems.getDefault.getPathMatcher("glob:*.gz")

      val fileData = io.file.readAll(path, blocker, chunkSize)

      if (matcher.matches(path))
        fileData
          .through(compression.gunzip(chunkSize))
          .flatMap(_.content)
      else
        fileData
    }

    readFile(path)
      .through(text.utf8Decode)
      .flatMap(Stream.emits(_))
      .through(rows[F]())
      .through(headers[F, String])
      .map(rowToRowData)
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
