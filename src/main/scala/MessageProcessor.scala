package beam.protocolvis

import cats.effect.{Blocker, ContextShift, Sync}
import fs2.Stream

import java.nio.file.Path

/**
 * @author Dmitry Openkov
 */
trait MessageProcessor {
  def convertToPuml[F[_] : Sync : ContextShift](messages: Stream[F, MessageReader.RowData], output: Path, blocker: Blocker):
  Stream[F, Unit]
}
