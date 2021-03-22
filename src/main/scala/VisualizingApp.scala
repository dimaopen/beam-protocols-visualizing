package beam.protocolvis

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import fs2._
import scopt.{OEffect, OParser}

import java.io.File
import java.nio.file.{Files, NoSuchFileException, Path, Paths}

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
        for {
          confirm <- confirmOverwrite(cliOptions.output, cliOptions.forceOverwriting)
          exitCode <- if (confirm) doJob(cliOptions.input, cliOptions.output) else IO.delay {
            println("Exiting...")
            ExitCode.Error
          }
        } yield exitCode


    }
  }

  private def doJob(inputFile: Path, output: Path): IO[ExitCode] = {
    Blocker[IO].use { blocker =>
      val csvStream: Stream[IO, MessageReader.RowData] = MessageReader.readData[IO](inputFile, blocker)
      val puml: Stream[IO, MessageSequenceProcessor.PumlEntry] = MessageSequenceProcessor.processMessages(csvStream)
      PumlWriter.writeData[IO](puml, output, blocker).compile.drain.as(ExitCode.Success)
        .handleErrorWith {
          case _: NoSuchFileException => IO.delay(println(s"File not found: $inputFile")).as(ExitCode.Error)
          case x: Throwable => IO.delay(println(s"Error: ${x.getMessage}")).as(ExitCode.Error)
        }
    }
  }

  private def confirmOverwrite(path: Path, force: Boolean): IO[Boolean] = {
    for {
      exists <- if (force) IO.pure(false) else IO.delay(Files.exists(path))
      overwrite <- if (exists) askUserYesNoQuestion("File exits. Overwrite? (Y/n)", default = true) else IO.pure(true)
    } yield overwrite
  }

  private def askUserYesNoQuestion(question: String, default: Boolean): IO[Boolean] = {
    for {
      _ <- IO.delay(println(question))
      value <- IO.delay(scala.io.StdIn.readLine())
      answer = if (value.trim.isEmpty) default.some else parseYesNoString(value)
      result <- answer match {
        case Some(value) => IO.pure(value)
        case None => askUserYesNoQuestion(question, default)
      }
    } yield result

  }

  private def parseYesNoString(str: String): Option[Boolean] = {
    str.trim.toLowerCase match {
      case "y" => true.some
      case "yes" => true.some
      case "n" => false.some
      case "no" => false.some
      case _ => None
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
        opt[Unit]('f', "force")
          .optional()
          .action((_, c) => c.copy(forceOverwriting = true))
          .text("overwrite output file"),
      )
    }
    OParser.runParser(parser1, args, CliOptions()) match {
      case (Some(cliOptions), _) => cliOptions.asRight
      case (None, effects) => effects.asLeft
    }
  }

  case class CliOptions(input: Path = Paths.get("."), output: Path = Paths.get("."), forceOverwriting: Boolean = false)

}
