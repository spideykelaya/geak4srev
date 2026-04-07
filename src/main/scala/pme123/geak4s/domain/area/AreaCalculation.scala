package pme123.geak4s.domain.area

import pme123.geak4s.domain.uwert.ComponentType

/** Single area entry (one row in the table) */
case class AreaEntry(
    kuerzel: String,       // Kürzel / Label (z.B. "EBF1", "DA1") – sync mit EBF-Rechner
    orientation: String,   // Ausrichtung (N, S, O, W, horizontal, etc.)
    description: String,   // Beschrieb (freies Textfeld, später U-Wert-Kopplung)
    length: Double,        // Länge / Umfang [m]
    width: Double,         // Breite / Höhe [m]
    area: Double,          // Fläche [m2]
    quantity: Int,         // Anzahl [Stk.]
    totalArea: Double,     // Fläche Total [m2]
    // SOLL values (new state)
    areaNew: Double,       // Fläche Neu [m2]
    quantityNew: Int,      // Anzahl Neu [Stk.]
    totalAreaNew: Double,  // Fläche Total Neu [m2]
    descriptionNew: String // Beschrieb Neu
):
  /** Calculate total area from individual values */
  def calculateTotalArea: Double = area * quantity

  /** Calculate new total area from new values */
  def calculateTotalAreaNew: Double = areaNew * quantityNew

end AreaEntry

object AreaEntry:
  /** Create a new empty entry */
  def empty(kuerzel: String = ""): AreaEntry = AreaEntry(
    kuerzel = kuerzel,
    orientation = "",
    description = "",
    length = 0.0,
    width = 0.0,
    area = 0.0,
    quantity = 1,
    totalArea = 0.0,
    areaNew = 0.0,
    quantityNew = 1,
    totalAreaNew = 0.0,
    descriptionNew = ""
  )

  /** Create entry with auto-calculated totals */
  def apply(
      kuerzel: String,
      orientation: String,
      description: String,
      length: Double,
      width: Double,
      area: Double,
      quantity: Int,
      areaNew: Double,
      quantityNew: Int,
      descriptionNew: String
  ): AreaEntry =
    val totalArea    = area * quantity
    val totalAreaNew = areaNew * quantityNew
    AreaEntry(
      kuerzel,
      orientation,
      description,
      length,
      width,
      area,
      quantity,
      totalArea,
      areaNew,
      quantityNew,
      totalAreaNew,
      descriptionNew
    )
  end apply

end AreaEntry

/** Area calculation for a specific category */
case class AreaCalculation(
    componentType: ComponentType,
    entries: List[AreaEntry]
):
  /** Total area IST (sum of all totalArea) */
  def totalAreaIst: Double = entries.map(_.totalArea).sum

  /** Total area SOLL (sum of all totalAreaNew) */
  def totalAreaSoll: Double = entries.map(_.totalAreaNew).sum

  /** Total quantity IST */
  def totalQuantityIst: Int = entries.map(_.quantity).sum

  /** Total quantity SOLL */
  def totalQuantitySoll: Int = entries.map(_.quantityNew).sum

end AreaCalculation

object AreaCalculation:
  /** Create empty calculation for a category */
  def empty(componentType: ComponentType): AreaCalculation =
    AreaCalculation(componentType, List.empty)

end AreaCalculation

/** Complete building envelope area summary */
case class BuildingEnvelopeArea(
    calculations: Seq[AreaCalculation]
):
  /** Get calculation for a specific component type */
  def get(componentType: ComponentType): Option[AreaCalculation] =
    calculations.find(_.componentType == componentType)

  /** Update calculation for a specific component type */
  def update(calculation: AreaCalculation): BuildingEnvelopeArea =
    copy(calculations = (calculations.filterNot(_.componentType == calculation.componentType) :+ calculation))

end BuildingEnvelopeArea

object BuildingEnvelopeArea:
  lazy val empty: BuildingEnvelopeArea = BuildingEnvelopeArea(
    calculations = Seq.empty
  )
end BuildingEnvelopeArea
