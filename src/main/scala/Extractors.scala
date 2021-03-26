package beam.protocolvis

import MessageReader.RowData

import cats.effect.Sync
import cats.implicits._
import fs2.Stream

/**
 * @author Dmitry Openkov
 */
object Extractors {
  def byPerson[F[_] : Sync](personId: String): Function[Stream[F, RowData], F[Stream[F, RowData]]] = {
    stream =>
      val triggerIds = stream.collect {
        case row if row.triggerId >= 0 & isSenderOrReceiver(personId, row) => row.triggerId
      }.compile.to(Set)


      for {
        ids <- triggerIds
      } yield stream.filter { row =>
        ids.contains(row.triggerId) || isSenderOrReceiver(personId, row)
      }
  }


  private def isSenderOrReceiver[F[_] : Sync](personId: String, row: RowData) = {
    row.sender.name == personId || row.receiver.name == personId
  }

  def messageExtractor[F[_] : Sync](extractorType: ExtractorType): Function[Stream[F, RowData], F[Stream[F, RowData]]] =
    extractorType match {
      case AllMessages => Sync[F].pure(_)
      case ByPerson(id) =>byPerson(id)
    }

  sealed trait ExtractorType

  object AllMessages extends ExtractorType

  case class ByPerson(id: String) extends ExtractorType

}
