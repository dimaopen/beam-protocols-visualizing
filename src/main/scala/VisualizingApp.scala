package beam.protocolvis

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import fs2._
import scopt.{OEffect, OParser}

import java.io.File
import java.nio.file.{NoSuchFileException, Path, Paths}

/**
 * @author Dmitry Openkov
 */
object VisualizingApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    parseArgs(args) match {
      case Left(errors) => IO.delay {
        OParser.runEffects(errors)
        ExitCode.Error
      }
      case Right(cliOptions) =>
        Blocker[IO].use { blocker =>
          val inputFile = cliOptions.input
          val csvStream: Stream[IO, MessageReader.RowData] = MessageReader.readData[IO](inputFile, blocker)
          val puml: Stream[IO, MessageSequenceProcessor.PumlEntry] = MessageSequenceProcessor.processMessages(csvStream)
          PumlWriter.writeData[IO](puml, cliOptions.output, blocker).compile.drain.as(ExitCode.Success)
            .handleErrorWith {
              case _: NoSuchFileException => IO.delay(println(s"File not found: $inputFile")).as(ExitCode.Error)
              case x: Throwable => IO.delay(println(s"Error: ${x.getMessage}")).as(ExitCode.Error)
            }
        }

    }
  }

  def parseArgs(args: List[String]): Either[List[OEffect], CliOptions] = {
    import scopt.OParser
    val builder = OParser.builder[CliOptions]
    val parser1 = {
      import builder._
      OParser.sequence(
        programName("beam-protocols"),
        opt[File]('i', "input")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(input = x.toPath))
          .text("csv file with BEAM message sequence"),
        opt[File]('o', "output")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(output = x.toPath))
          .text("path where to save the generated puml file"),
      )
    }
    OParser.runParser(parser1, args, CliOptions()) match {
      case (Some(cliOptions), _) => cliOptions.asRight
      case (None, effects) => effects.asLeft
    }
  }

  case class CliOptions(input: Path = Paths.get("."), output: Path = Paths.get("."))
}
