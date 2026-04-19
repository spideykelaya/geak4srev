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
    if componentType == ComponentType.EBF then
      val totalEbf    = entries.map(_.totalArea).sum
      val rounded = math.round(totalEbf).toDouble
      if rounded > 0 then
        // Sync to project model (Schritt 7 fields)
        AppState.updateProject { p =>
          val updatedBuildingData = p.project.buildingData.copy(
            energyReferenceArea = Some(rounded)
          )
          val updatedUsages =
            if p.buildingUsages.nonEmpty then
              p.buildingUsages.updated(0, p.buildingUsages(0).copy(area = rounded))
            else p.buildingUsages
          p.copy(
            project        = p.project.copy(buildingData = updatedBuildingData),
            buildingUsages = updatedUsages
          )
        }
        if syncEbfToWordForm then
          val totalEbfStr = f"$totalEbf%.0f"
          WordFormView.formVar.update(_.copy(ebf = totalEbfStr))

  /**
    * Sync polygon data to their respective ComponentType sections.
    * Groups polygons by label prefix, routes each group to the matching ComponentType.
    * Preserves manually-set fields (quantity, orientation) for matching labels.
    */
  def syncPolygons(polygons: Seq[(String, String, Double, Option[Double], Option[Double])]): Unit =
    val normalized = polygons
      .map((label, areaType, area, ovhDist, sdDist) => (label.trim, areaType.trim, if area.isNaN || area < 0 then 0.0 else area, ovhDist, sdDist))
      .filter(_._1.nonEmpty)

    // Route each polygon to a ComponentType using areaType if present, else fall back to label inference.
    // Polygons that don't match any known type default to EBF so they are never silently dropped.
    val byType: Map[ComponentType, Seq[(String, Double, Option[Double], Option[Double])]] =
      normalized
        .groupBy { case (label, areaType, _, _, _) =>
          val resolved =
            if areaType.nonEmpty then ComponentType.fromPolygonLabel(areaType)
            else ComponentType.fromPolygonLabel(label)
          resolved.getOrElse(ComponentType.EBF)
        }
        .map { case (ct, entries) => ct -> entries.map((label, _, area, ovhDist, sdDist) => (label, area, ovhDist, sdDist)) }

    // For types with no polygons in this sync: clear polygon-derived entries but keep manual ones.
    val presentTypes = byType.keySet
    areaCalculations.now().foreach { area =>
      area.calculations
        .filter(c => !presentTypes.contains(c.componentType))
        .foreach { c =>
          val manualOnly = c.entries.filter(_.isManual)
          if manualOnly.length != c.entries.length then
            updateAreaCalculation(c.componentType, manualOnly)
        }
    }

    byType.foreach { case (compType, typePolygons) =>
      val existingEntries = areaCalculations.now()
        .flatMap(_.get(compType))
        .map(_.entries)
        .getOrElse(List.empty)

      val syncedEntries = typePolygons.map { case (rawLabel, polygonArea, ovhDist, sdDist) =>
        existingEntries.find(e => e.kuerzel == rawLabel && !e.isManual) match
          case Some(current) =>
            current.copy(
              area = polygonArea,
              totalArea = polygonArea * current.quantity,
              areaNew = polygonArea,
              totalAreaNew = polygonArea * current.quantityNew,
              overhangDist   = ovhDist.getOrElse(current.overhangDist),
              sideShadingDist = sdDist.getOrElse(current.sideShadingDist)
            )
          case None =>
            AreaEntry.empty(rawLabel).copy(
              area = polygonArea,
              quantity = 1,
              totalArea = polygonArea,
              areaNew = polygonArea,
              quantityNew = 1,
              totalAreaNew = polygonArea,
              overhangDist    = ovhDist.getOrElse(0.0),
              sideShadingDist = sdDist.getOrElse(0.0)
            )
      }.toList

      // Manual entries for this type are preserved alongside polygon-derived ones
      val manualEntries = existingEntries.filter(_.isManual)
      updateAreaCalculation(compType, syncedEntries ++ manualEntries)
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

