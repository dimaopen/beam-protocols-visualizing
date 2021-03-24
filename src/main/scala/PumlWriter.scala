package beam.protocolvis

import cats.effect.{Blocker, ContextShift, Sync}
import fs2._

import java.nio.file.{Path, StandardOpenOption}

/**
 * @author Dmitry Openkov
 */
object PumlWriter {
  def writeData[F[_] : Sync : ContextShift, T](data: Stream[F, T],
                                            path: Path, blocker: Blocker)(serializer: Function[T, String]): Stream[F, Unit] = {
    val stringValues = Stream("@startuml") ++ data.map(serializer) ++ Stream("@enduml")
    stringValues
      .intersperse("\n")
      .through(text.utf8Encode)
      .through(io.file.writeAll[F](path, blocker, List(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))
  }
}
