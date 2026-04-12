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

  /** Update area calculation for a specific component type.
   *  syncEbfToWordForm: set to true only when called from Step 6 (manual table edits). */
  def updateAreaCalculation(
      componentType: ComponentType,
      entries: List[AreaEntry],
      syncEbfToWordForm: Boolean = false
  ): Unit =
    areaCalculations.update : maybeArea =>
      val area = maybeArea.getOrElse(BuildingEnvelopeArea.empty)
      val calculation = AreaCalculation(componentType, entries)
      Some(area.update(calculation))
    if syncEbfToWordForm && componentType == ComponentType.EBF then
      val totalEbf    = entries.map(_.totalArea).sum
      val totalEbfStr = f"$totalEbf%.0f"
      WordFormView.formVar.update(_.copy(ebf = totalEbfStr))

  /**
    * Sync polygon data to their respective ComponentType sections.
    * Groups polygons by label prefix, routes each group to the matching ComponentType.
    * Preserves manually-set fields (quantity, orientation) for matching labels.
    */
  def syncPolygons(polygons: Seq[(String, String, Double)]): Unit =
    val normalized = polygons
      .map((label, areaType, area) => (label.trim, areaType.trim, if area.isNaN || area < 0 then 0.0 else area))
      .filter(_._1.nonEmpty)

    // Route each polygon to a ComponentType using areaType if present, else fall back to label inference
    val byType: Map[ComponentType, Seq[(String, Double)]] =
      normalized
        .groupBy { case (label, areaType, _) =>
          if areaType.nonEmpty then ComponentType.fromPolygonLabel(areaType)
          else ComponentType.fromPolygonLabel(label)
        }
        .collect { case (Some(ct), entries) => ct -> entries.map((label, _, area) => (label, area)) }

    byType.foreach { case (compType, typePolygons) =>
      val existingEntries = areaCalculations.now()
        .flatMap(_.get(compType))
        .map(_.entries)
        .getOrElse(List.empty)

      val syncedLabels = typePolygons.map(_._1).toSet

      val syncedEntries = typePolygons.map { case (rawLabel, polygonArea) =>
        existingEntries.find(_.kuerzel == rawLabel) match
          case Some(current) =>
            current.copy(
              area = polygonArea,
              totalArea = polygonArea * current.quantity,
              areaNew = polygonArea,
              totalAreaNew = polygonArea * current.quantityNew
            )
          case None =>
            AreaEntry.empty(rawLabel).copy(
              area = polygonArea,
              quantity = 1,
              totalArea = polygonArea,
              areaNew = polygonArea,
              quantityNew = 1,
              totalAreaNew = polygonArea
            )
      }.toList

      // Preserve existing entries whose label is NOT in the current polygon sync
      // (e.g. manually added rows, or entries from a previous plan that was not synced)
      val unmatched = existingEntries.filterNot(e => syncedLabels.contains(e.kuerzel))
      val renumbered = syncedEntries ++ unmatched

      updateAreaCalculation(compType, renumbered)
    }

  /** Rename a kuerzel across all component types (triggered when EBF polygon is renamed) */
  def renameDescription(oldLabel: String, newLabel: String): Unit =
    areaCalculations.update { maybeArea =>
      maybeArea.map { area =>
        val updated = area.calculations.map { calc =>
          val renamedEntries = calc.entries.map { entry =>
            if entry.kuerzel == oldLabel then entry.copy(kuerzel = newLabel) else entry
          }
          calc.copy(entries = renamedEntries)
        }
        area.copy(calculations = updated)
      }
    }

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

