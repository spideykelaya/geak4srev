package pme123.geak4s.state

import com.raquo.laminar.api.L.*
import pme123.geak4s.domain.uwert.*
import pme123.geak4s.domain.GeakProject
import scala.scalajs.js

/**
 * State management for U-Wert calculations
 * Persists calculation data in the project model
 */
object UWertState:

  /** All U-Wert calculations for the current project */
  val calculations: Var[List[UWertCalculation]] = Var(List.empty)

  /** Counter for generating unique IDs */
  private var idCounter = 0

  /** Initialize state from project */
  def loadFromProject(project: GeakProject): Unit =
    calculations.set(project.uwertCalculations)
    // Reset counter to avoid ID conflicts
    idCounter = project.uwertCalculations.length

  /** Get current calculations to save to project */
  def getCalculations: List[UWertCalculation] =
    calculations.now()

  /** Add a new empty calculation */
  def addCalculation(): String =
    idCounter += 1
    val id = s"uwert-calc-$idCounter"
    val newCalc = UWertCalculation.empty(id)
    calculations.update(_ :+ newCalc)
    id

  /** Remove a calculation by ID */
  def removeCalculation(id: String): Unit =
    calculations.update(_.filterNot(_.id == id))

  /** Update a calculation */
  def updateCalculation(id: String, update: UWertCalculation => UWertCalculation): Unit =
    calculations.update { calcs =>
      calcs.map { calc =>
        if calc.id == id then update(calc) else calc
      }
    }

  /** Update component selection */
  def updateComponent(id: String, component: BuildingComponent, bWertName: Option[String]): Unit =
    val alwaysOne = Set(ComponentType.PitchedRoof, ComponentType.FlatRoof)
    updateCalculation(id, calc =>
      val base = UWertTableData.fromComponent(component)
      val tableData = if alwaysOne.contains(component.compType) then base else base
      calc.copy(
        componentLabel = component.label,
        componentType = component.compType,
        bWertName = if alwaysOne.contains(component.compType) then None else bWertName,
        istCalculation = tableData.copy(bFactor = if alwaysOne.contains(component.compType) then 1.0 else base.bFactor),
        sollCalculation = tableData.copy(bFactor = if alwaysOne.contains(component.compType) then 1.0 else base.bFactor)
      )
    )

  /** Update b-factor for both IST and SOLL */
  def updateBFactor(id: String, bFactor: Double): Unit =
    updateCalculation(id, calc =>
      calc.copy(
        istCalculation = calc.istCalculation.copy(bFactor = bFactor),
        sollCalculation = calc.sollCalculation.copy(bFactor = bFactor)
      )
    )

  /** Update IST table materials and sync to SOLL */
  def updateIstMaterials(id: String, materials: List[MaterialLayer]): Unit =
    updateCalculation(id, calc =>
      calc.copy(
        istCalculation = calc.istCalculation.copy(materials = materials),
        sollCalculation = calc.sollCalculation.copy(materials = materials)
      )
    )

  /** Update SOLL table materials */
  def updateSollMaterials(id: String, materials: List[MaterialLayer]): Unit =
    updateCalculation(id, calc =>
      calc.copy(
        sollCalculation = calc.sollCalculation.copy(materials = materials)
      )
    )

  /** Update IST b-factor */
  def updateIstBFactor(id: String, bFactor: Double): Unit =
    updateCalculation(id, calc =>
      calc.copy(
        istCalculation = calc.istCalculation.copy(bFactor = bFactor)
      )
    )

  /** Update SOLL b-factor */
  def updateSollBFactor(id: String, bFactor: Double): Unit =
    updateCalculation(id, calc =>
      calc.copy(
        sollCalculation = calc.sollCalculation.copy(bFactor = bFactor)
      )
    )

  /** Add a new material layer to IST table and sync the new layer to SOLL */
  def addIstMaterialLayer(id: String): Unit =
    updateCalculation(id, calc =>
      val currentMaterials = calc.istCalculation.materials
      val usedNumbers = currentMaterials.map(_.nr).toSet
      val nextNr = (2 to 8).find(!usedNumbers.contains(_)).getOrElse(2)
      val newLayer = MaterialLayer.empty(nextNr)
      val updatedMaterials = (currentMaterials.filterNot(_.nr == 9) :+ newLayer :+ currentMaterials.find(_.nr == 9).get).sortBy(_.nr)
      // Add the same empty layer to SOLL
      val sollMaterials = calc.sollCalculation.materials
      val updatedSoll = (sollMaterials.filterNot(_.nr == 9) :+ newLayer :+ sollMaterials.find(_.nr == 9).get).sortBy(_.nr)
      calc.copy(
        istCalculation = calc.istCalculation.copy(materials = updatedMaterials),
        sollCalculation = calc.sollCalculation.copy(materials = updatedSoll)
      )
    )

  /** Add a new material layer to SOLL table */
  def addSollMaterialLayer(id: String): Unit =
    updateCalculation(id, calc =>
      val currentMaterials = calc.sollCalculation.materials
      // Find the next available number (between 2 and 8)
      val usedNumbers = currentMaterials.map(_.nr).toSet
      val nextNr = (2 to 8).find(!usedNumbers.contains(_)).getOrElse(2)

      // Insert the new layer before the last row (row 9)
      val newLayer = MaterialLayer.empty(nextNr)
      val updatedMaterials = (currentMaterials.filterNot(_.nr == 9) :+ newLayer :+ currentMaterials.find(_.nr == 9).get).sortBy(_.nr)

      calc.copy(
        sollCalculation = calc.sollCalculation.copy(materials = updatedMaterials)
      )
    )

  /** Move an editable layer up or down within IST or SOLL table */
  def moveMaterialLayer(id: String, tableType: String, layerNr: Int, direction: Int): Unit =
    updateCalculation(id, calc =>
      val materials = if tableType == "IST" then calc.istCalculation.materials else calc.sollCalculation.materials
      val editables = materials.filter(_.isEditable).sortBy(_.nr)
      val idx = editables.indexWhere(_.nr == layerNr)
      val swapIdx = idx + direction
      if idx < 0 || swapIdx < 0 || swapIdx >= editables.size then calc
      else
        val a = editables(idx)
        val b = editables(swapIdx)
        val swapped = materials.map { m =>
          if m.nr == a.nr then m.copy(nr = b.nr)
          else if m.nr == b.nr then m.copy(nr = a.nr)
          else m
        }.sortBy(_.nr)
        if tableType == "IST" then calc.copy(istCalculation = calc.istCalculation.copy(materials = swapped))
        else calc.copy(sollCalculation = calc.sollCalculation.copy(materials = swapped))
    )

  /** Remove a material layer from IST table and sync removal to SOLL */
  def removeIstMaterialLayer(id: String, layerNr: Int): Unit =
    updateCalculation(id, calc =>
      calc.copy(
        istCalculation = calc.istCalculation.copy(
          materials = calc.istCalculation.materials.filterNot(m => m.nr == layerNr && m.isEditable)
        ),
        sollCalculation = calc.sollCalculation.copy(
          materials = calc.sollCalculation.materials.filterNot(m => m.nr == layerNr && m.isEditable)
        )
      )
    )

  /** Remove a material layer from SOLL table */
  def removeSollMaterialLayer(id: String, layerNr: Int): Unit =
    updateCalculation(id, calc =>
      calc.copy(
        sollCalculation = calc.sollCalculation.copy(
          materials = calc.sollCalculation.materials.filterNot(m => m.nr == layerNr && m.isEditable)
        )
      )
    )

  /** Get a specific calculation by ID */
  def getCalculation(id: String): Signal[Option[UWertCalculation]] =
    calculations.signal.map(_.find(_.id == id))

  /** Clear all calculations */
  def clear(): Unit =
    calculations.set(List.empty)

  /** Save calculations to project */
  def saveToProject(project: GeakProject): GeakProject =
    project.copy(uwertCalculations = calculations.now())

end UWertState

