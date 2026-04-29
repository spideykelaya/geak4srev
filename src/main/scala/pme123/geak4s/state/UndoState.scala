package pme123.geak4s.state

import com.raquo.laminar.api.L.*
import pme123.geak4s.domain.GeakProject

/**
 * Undo history for project state changes.
 * Stores snapshots of GeakProject before each updateProject call.
 * Triggered by Ctrl+Z / Cmd+Z in the UI.
 */
object UndoState:

  private val MaxHistory = 50

  private val history: Var[List[GeakProject]] = Var(List.empty)

  /** Push the current project state onto the undo stack before a change is applied. */
  def push(project: GeakProject): Unit =
    history.update(stack => (project :: stack).take(MaxHistory))

  /** Pop the most recent snapshot from the stack. Returns None if history is empty. */
  def pop(): Option[GeakProject] =
    history.now() match
      case Nil => None
      case head :: tail =>
        history.set(tail)
        Some(head)

  /** True when there is at least one state to undo to. */
  val canUndo: Signal[Boolean] = history.signal.map(_.nonEmpty)

  /** Number of undo steps available. */
  val size: Signal[Int] = history.signal.map(_.length)

  /** Clear history (called on project load / new project). */
  def clear(): Unit = history.set(List.empty)
