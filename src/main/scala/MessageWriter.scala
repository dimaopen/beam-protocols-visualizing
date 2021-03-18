package beam.protocolvis

import cats.effect.{Resource, Sync}
import cats.implicits._

import java.io.OutputStream
import java.nio.file.{FileSystems, Path}

/**
 * @author Dmitry Openkov
 */
object MessageWriter {
  def openFileForWriting[F[_] : Sync](path: Path): Resource[F, OutputStream] =
    Resource.make {
      Sync[F].delay(FileSystems.getDefault.provider().newOutputStream(path))
    } { stream =>
      Sync[F].delay(stream.close()).handleErrorWith(_ => Sync[F].unit)
    }

}
