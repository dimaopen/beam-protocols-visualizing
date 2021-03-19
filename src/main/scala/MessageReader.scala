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
    io.file.readAll(path, blocker, 1024)
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
        Message(extractActor(row, "sender"), extractActor(row, "receiver"), row("payload").get)
      case "event" =>
        Event(extractActor(row, "sender"), extractActor(row, "receiver"), row("payload").get,
          row("state").get)
      case "transition" =>
        Transition(extractActor(row, "sender"), extractActor(row, "receiver"), row("payload").get,
          row("state").get)
    }
  }


  sealed abstract class RowData {
    def sender: Actor

    def receiver: Actor

  }

  case class Event(
                    sender: Actor,
                    receiver: Actor,
                    payload: String,
                    data: String,
                  ) extends RowData

  case class Message(
                      sender: Actor,
                      receiver: Actor,
                      payload: String,
                    ) extends RowData

  case class Transition(
                         sender: Actor,
                         receiver: Actor,
                         prevState: String,
                         state: String,
                       ) extends RowData

  case class Actor(parent: String, name: String)
}
