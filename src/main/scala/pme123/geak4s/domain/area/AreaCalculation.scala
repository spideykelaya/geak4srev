package pme123.geak4s.domain.area

import pme123.geak4s.domain.uwert.ComponentType

/** Single area entry (one row in the table) */
case class AreaEntry(
    kuerzel: String,           // Kürzel / Label (z.B. "EBF1", "DA1") – sync mit EBF-Rechner
    orientation: String,       // Ausrichtung (N, S, O, W, horizontal, etc.)
    description: String,       // Beschrieb (freies Textfeld, später U-Wert-Kopplung)
    length: Double,            // Länge / Umfang [m]
    width: Double,             // Breite / Höhe [m]
    area: Double,              // Fläche [m2]
    quantity: Int,             // Anzahl [Stk.]
    totalArea: Double,         // Fläche Total [m2]
    // SOLL values (new state)
    areaNew: Double,           // Fläche Neu [m2]
    quantityNew: Int,          // Anzahl Neu [Stk.]
    totalAreaNew: Double,      // Fläche Total Neu [m2]
    descriptionNew: String,    // Beschrieb Neu
    isManual: Boolean = false,  // true = manually added, not from polygon sync
    // Fenster shading (only used for ComponentType.Window)
    overhang: Double = 0.0,
    overhangDist: Double = 0.0,
    sideShading: Double = 0.0,
    sideShadingDist: Double = 0.0,
    horizont: Double = 0.0,      // allgemeiner Horizontwinkel [°] nach SIA 380/1
    // Linked U-Wert calculation
    uwertId: Option[String] = None,
    uValue: Option[Double] = None,
    bValue: Option[Double] = None,      // b-factor from linked calculation
    gValue: Option[Double] = None,      // only for Window
    glassRatio: Option[Double] = None,  // only for Window
    beidseitig: Boolean = false,        // only for Window
    // GEAK Plus economic columns (0 = use component-type default)
    werterhalt: Double = 0.0,           // CHF like-for-like replacement cost
    investition: Double = 0.0,          // CHF improvement measure cost
    nutzungsdauer: Int = 0,             // component lifespan in years
    rateKey: String = "",               // selected rate preset ("pvc","holz","einfach","aufwaendig") or ""
    installedIn: Option[String] = None  // for Window/Door: label of the wall/roof the element is installed in
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
