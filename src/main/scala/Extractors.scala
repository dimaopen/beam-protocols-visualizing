package beam.protocolvis

import MessageReader.RowData

import cats.effect.Sync
import fs2.{Chunk, Stream}

/**
 * @author Dmitry Openkov
 */
object Extractors {

  def byPerson[F[_] : Sync](personId: String): Function[Stream[F, RowData], F[Stream[F, RowData]]] = {
    stream =>
      Sync[F].pure(
        stream.scanChunks(Set.empty[Long]) { case (ids, chunk) =>
          val (newIds, seq) = chunk.foldLeft(ids -> IndexedSeq.empty[RowData]) { case ((ids, seq), row) =>
            if (isSenderOrReceiver(personId, row)) (if (row.triggerId >= 0) ids + row.triggerId else ids, seq :+ row)
            else if (ids.contains(row.triggerId)) (ids + row.triggerId, seq :+ row)
            else (ids, seq)
          }
          (newIds, Chunk.indexedSeq(seq))
        }
      )
  }


  private def isSenderOrReceiver[F[_] : Sync](personId: String, row: RowData) = {
    row.sender.name == personId || row.receiver.name == personId
  }

  def messageExtractor[F[_] : Sync](extractorType: ExtractorType): Function[Stream[F, RowData], F[Stream[F, RowData]]] =
    extractorType match {
      case AllMessages => Sync[F].pure(_)
      case ByPerson(id) => byPerson(id)
    }

  sealed trait ExtractorType

  object AllMessages extends ExtractorType

  case class ByPerson(id: String) extends ExtractorType

}
