package beam.protocolvis

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import fs2._

import java.nio.file.{NoSuchFileException, Paths}

/**
 * @author Dmitry Openkov
 */
object VisualizingApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    parseArgs(args) match {
      case Left(error) => IO.delay {
        println(s"Wrong args: $error")
        ExitCode.Error
      }
      case Right(argMap) =>
        Blocker[IO].use { blocker =>
          val inputFile = argMap("input")
          val csvStream: Stream[IO, MessageReader.RowData] = MessageReader.readData[IO](Paths.get(inputFile), blocker)
          val puml: Stream[IO, MessageSequenceProcessor.PumlEntry] = MessageSequenceProcessor.processMessages(csvStream)
          PumlWriter.writeData[IO](puml, Paths.get(argMap("output")), blocker).compile.drain.as(ExitCode.Success)
            .handleErrorWith {
              case _: NoSuchFileException => IO.delay(println(s"File not found: $inputFile")).as(ExitCode.Error)
              case x: Throwable => IO.delay(println(s"Error: ${x.getMessage}")).as(ExitCode.Error)
            }
        }

    }
  }

  def parseArgs(args: List[String]): Either[String, Map[String, String]] = {
    val pairs = args
      .sliding(2, 2)
      .toList
      .collect {
        case List("--input", filePath: String) => ("input", filePath)
        case List("--output", filePath: String) => ("output", filePath)
        case List("--person", personId: String) => ("personId", personId)
        case arg@_ => ("error", arg.mkString(" "))
      }
    pairs.find { case (name, _) => name == "error" } match {
      case Some((_, wrongArgs)) => wrongArgs.asLeft
      case None =>
        val map = pairs.toMap
        val notFound = IndexedSeq("input", "output", "personId").filterNot(map.contains)
        if (notFound.nonEmpty) s"Mandatory values: ${notFound.mkString(", ")}".asLeft
        else map.asRight
    }
  }
}
