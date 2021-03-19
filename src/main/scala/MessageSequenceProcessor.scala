package beam.protocolvis

import MessageReader._

import fs2._

import scala.util.matching.Regex

/**
 * @author Dmitry Openkov
 */
object MessageSequenceProcessor {

  def processMessages[F[_]](messages: Stream[F, RowData]): Stream[F, PumlEntry] = {
    messages.through(fixTransitionEvent[F])
      .map {
        case Event(sender, receiver, payload, _) =>
          Interaction(userFriendlyActorName(sender), userFriendlyActorName(receiver), userFriendlyPayload(payload))
        case Message(sender, receiver, payload) =>
          Interaction(userFriendlyActorName(sender), userFriendlyActorName(receiver), userFriendlyPayload(payload))
        case Transition(sender, receiver, _, state) =>
          Note(userFriendlyActorName(receiver), state)
      }
  }

  private val PayloadRegex: Regex = """(\w+)\(([^()]+).*""".r
  private def userFriendlyPayload(payload: String): String = {
    payload match {
      case PayloadRegex("TriggerWithId", internal) => internal
      case PayloadRegex(external, _) => external
      case _ => payload
    }
  }

  private val PersonIdRegex: Regex = """\d+(-\d+){1,}""".r
  private def userFriendlyActorName(actor: Actor) = {
    val isParentPopulation = actor.parent == "population"
    val isParentHousehold = actor.parent.startsWith("population/")
    val looksLikeId = PersonIdRegex.matches(actor.name)
    (isParentPopulation, isParentHousehold, looksLikeId) match {
      case (true, _, true) => "Household"
      case (false, true, true) => "Person"
      case (false, true, false) => s"HouseholdFleetManager:${actor.name}"
      case _ => actor.name
    }
  }

  def fixTransitionEvent[F[_]]: Pipe[F, RowData, RowData] = {
    def go(s: Stream[F, RowData], accum: IndexedSeq[RowData]): Pull[F, RowData, Unit] = {
      s.pull.uncons1.flatMap {
        case Some((row: Event, tl)) if accum.nonEmpty =>
          val cycle = row +: accum
          Pull.output(Chunk.seq(cycle)) >> go(tl, IndexedSeq.empty)
        case Some((row, tl)) if accum.nonEmpty =>
          go(tl, accum :+ row)
        case Some((row: Transition, tl)) =>
          go(tl, IndexedSeq(row))
        case Some((row, tl)) =>
          Pull.output1(row) >> go(tl, accum)
        case None =>
          Pull.output(Chunk.seq(accum)) >> Pull.done
      }
    }

    in => go(in, IndexedSeq.empty).stream
  }

  sealed trait PumlEntry

  case class Note(over: String, value: String) extends PumlEntry

  case class Interaction(from: String, to: String, payload: String) extends PumlEntry

}
