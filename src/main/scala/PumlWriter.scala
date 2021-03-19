package beam.protocolvis

import cats.effect.{Blocker, ContextShift, Sync}
import fs2._
import MessageSequenceProcessor._

import java.nio.file.{Path, StandardOpenOption}

/**
 * @author Dmitry Openkov
 */
object PumlWriter {
  def writeData[F[_] : Sync : ContextShift](data: Stream[F, MessageSequenceProcessor.PumlEntry],
                                            path: Path, blocker: Blocker): Stream[F, Unit] = {
    val stringValues = Stream("@startuml") ++ data.map(toPumlRow) ++ Stream("@enduml")
    stringValues
      .intersperse("\n")
      .through(text.utf8Encode)
      .through(io.file.writeAll[F](path, blocker, List(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))
  }


  def toPumlRow(pumlEntry: PumlEntry): String = pumlEntry match {
    case Note(over, value) => s"""rnote over "$over": $value"""
    case Interaction(from, to, payload) => s""""$from" -> "$to": $payload"""
  }
}
