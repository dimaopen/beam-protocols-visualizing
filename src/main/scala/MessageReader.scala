package beam.protocolvis

import cats.effect.{Resource, Sync}
import cats.syntax.all._
import com.univocity.parsers.common.record.Record
import com.univocity.parsers.csv.{CsvParser, CsvParserSettings}

import java.io.InputStream
import java.nio.file.{FileSystems, Path}

/**
 * @author Dmitry Openkov
 */
object MessageReader {

  def readData[F[_] : Sync](path: Path): F[IndexedSeq[RowData]] = {
    openFileForReading(path).use { inputStream =>
      readCsv(inputStream)
    }
  }

  def readCsv[F[_] : Sync](inputStream: InputStream): F[IndexedSeq[RowData]] = {
    import scala.jdk.CollectionConverters._
    for {
      parser <- createParser
      result <- Sync[F].delay(parser.parseAllRecords(inputStream).asScala.toIndexedSeq)
    } yield result.map(recordToRowData)
  }

  private def recordToRowData(record: Record): RowData = {
    def extractActor(record: Record, position: String) = {
      val parent = record.getString(s"${position}_parent")
      val name = record.getString(s"${position}_name")
      Actor(parent, name)
    }

    record.getString("type") match {
      case "message" =>
        Message(extractActor(record, "sender"), extractActor(record, "receiver"), record.getString("payload"))
      case "event" =>
        Event(extractActor(record, "sender"), extractActor(record, "receiver"), record.getString("payload"),
          record.getString("state"))
      case "transition" =>
        Event(extractActor(record, "sender"), extractActor(record, "receiver"), record.getString("payload"),
          record.getString("state"))
    }
  }

  private def createParser[F[_] : Sync]: F[CsvParser] = {
    Sync[F].delay {
      val settings = new CsvParserSettings
      settings.setHeaderExtractionEnabled(true)
      settings.getFormat.setLineSeparator("\n")
      new CsvParser(settings)
    }
  }

  def openFileForReading[F[_] : Sync](path: Path): Resource[F, InputStream] =
    Resource.make {
      Sync[F].delay(FileSystems.getDefault.provider().newInputStream(path)) // build
    } { inStream =>
      Sync[F].delay(inStream.close()).handleErrorWith(_ => Sync[F].unit)
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
