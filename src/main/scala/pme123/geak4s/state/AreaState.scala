package pme123.geak4s.state

import com.raquo.laminar.api.L.*
import pme123.geak4s.domain.area.*
import pme123.geak4s.domain.uwert.ComponentType
import pme123.geak4s.domain.GeakProject
import pme123.geak4s.views.WordFormView
import scala.scalajs.js

/**
 * State management for area calculations
 * Persists area calculation data in the project model
 */
object AreaState:

  /** Building envelope area calculations for the current project */
  val areaCalculations: Var[Option[BuildingEnvelopeArea]] = Var(None)

  /** Initialize state from project */
  def loadFromProject(project: GeakProject): Unit =
    areaCalculations.set(project.areaCalculations)

  /** Get current area calculations to save to project */
  def getAreaCalculations: Option[BuildingEnvelopeArea] =
    areaCalculations.now()

  /** Initialize empty area calculations */
  def initializeEmpty(): Unit =
    areaCalculations.set(Some(BuildingEnvelopeArea.empty))

  /** Update area calculation for a specific component type */
  def updateAreaCalculation(componentType: ComponentType, entries: List[AreaEntry]): Unit =
    areaCalculations.update : maybeArea =>
      val area = maybeArea.getOrElse(BuildingEnvelopeArea.empty)
      val calculation = AreaCalculation(componentType, entries)
      Some(area.update(calculation))

  /**
    * Upsert EBF rows from polygon data using polygon label as `description` key.
    * - missing description: append new entry
    * - existing description: replace its area values
    */
  def syncEbfPolygons(polygons: Seq[(String, Double)]): Unit =
    val normalized = polygons
      .map((label, area) => (label.trim, if area.isNaN || area < 0 then 0.0 else area))
      .filter(_._1.nonEmpty)

    val existingEntries = areaCalculations.now()
      .flatMap(_.get(ComponentType.EBF))
      .map(_.entries)
      .getOrElse(List.empty)

    // Rebuild from polygon sync: only keep entries that exist in the current polygon list,
    // preserving manually-set fields (quantity, orientation) for matching labels.
    val currentLabels = normalized.map(_._1).toSet
    val syncedEntries = normalized.zipWithIndex.map { case ((label, polygonArea), idx) =>
      existingEntries.find(_.description == label) match
        case Some(current) =>
          current.copy(
            area = polygonArea,
            totalArea = polygonArea * current.quantity,
            areaNew = polygonArea,
            totalAreaNew = polygonArea * current.quantityNew
          )
        case None =>
          AreaEntry.empty((idx + 1).toString).copy(
            description = label,
            area = polygonArea,
            quantity = 1,
            totalArea = polygonArea,
            areaNew = polygonArea,
            quantityNew = 1,
            totalAreaNew = polygonArea
          )
    }.toList

    val renumbered = syncedEntries.zipWithIndex.map { case (entry, index) =>
      entry.copy(nr = (index + 1).toString)
    }

    updateAreaCalculation(ComponentType.EBF, renumbered)

    val totalEbf = renumbered.map(_.totalArea).sum
    val totalEbfStr = f"$totalEbf%.0f"
    WordFormView.formVar.update(_.copy(ebf = totalEbfStr))

  /** Get entries for a specific component type */
  def getEntries(componentType: ComponentType): Signal[List[AreaEntry]] =
    areaCalculations.signal.map { maybeArea =>
      maybeArea
        .flatMap(_.get(componentType))
        .map(_.entries)
        .getOrElse(List.empty)
    }

  /** Clear all area calculations */
  def clear(): Unit =
    areaCalculations.set(None)

  /** Save area calculations to project */
  def saveToProject(project: GeakProject): GeakProject =
    project.copy(areaCalculations = areaCalculations.now())

end AreaState

