package pme123.geak4s.domain.uwert

/** 
 * Domain model for U-Wert (thermal transmittance) calculations
 * Stores both IST (current) and SOLL (target) calculations for a building component
 */
case class UWertCalculation(
  id: String,
  label: String = "",                            // User-defined name (e.g. "Aussenwand renoviert")
  componentLabel: String,                        // Selected building component label
  componentType: ComponentType,
  bWertName: Option[String],
  istCalculation: UWertTableData,
  sollCalculation: UWertTableData
)

object UWertCalculation:
  def empty(id: String): UWertCalculation = UWertCalculation(
    id = id,
    label = "",
    componentLabel = "",
    componentType = ComponentType.ExteriorWall,
    bWertName = None,
    istCalculation = UWertTableData.empty,
    sollCalculation = UWertTableData.empty
  )

/** 
 * Data for a single U-Wert calculation table (IST or SOLL)
 */
case class UWertTableData(
  materials: List[MaterialLayer],                // Material layers (rows 1-9)
  bFactor: Double                                // b-Factor for the calculation
):
  /** Calculate total R-value (sum of all layer R-values) */
  def rTotal: Double = materials.map(_.rValue).sum
  
  /** Calculate U-value without b-factor */
  def uValueWithoutB: Double = if rTotal != 0 then 1.0 / rTotal else 0.0
  
  /** Calculate U-value with b-factor */
  def uValue: Double = if rTotal != 0 then bFactor / rTotal else 0.0

object UWertTableData:
  /** Create empty table data */
  def empty: UWertTableData = UWertTableData(
    materials = List.empty,
    bFactor = 1.0
  )
  
  /** Initialize table data from a building component */
  def fromComponent(component: BuildingComponent): UWertTableData = UWertTableData(
    materials = List(
      // Row 1: Heat transfer from inside
      MaterialLayer(
        nr = 1,
        description = component.heatTransferFromInside.label,
        thickness = component.heatTransferFromInside.thicknessInM,
        lambda = component.heatTransferFromInside.thermalConductivity,
        isEditable = false
      ),
      // Row 9: Heat transfer to outside (last row)
      MaterialLayer(
        nr = 9,
        description = component.heatTransferToOutside.label,
        thickness = component.heatTransferToOutside.thicknessInM,
        lambda = component.heatTransferToOutside.thermalConductivity,
        isEditable = false
      )
    ),
    bFactor = 1.0
  )

/** 
 * A single material layer in the U-Wert calculation
 */
case class MaterialLayer(
  nr: Int,                                       // Row number (1-9)
  description: String,                           // Material description/name
  thickness: Double,                             // d in m
  lambda: Double,                                // λ (thermal conductivity)
  isEditable: Boolean = true                     // Whether this row can be edited
):
  /** Calculate R-value (d/λ) */
  def rValue: Double = if lambda != 0 then thickness / lambda else 0.0

object MaterialLayer:
  /** Create an empty editable material layer */
  def empty(nr: Int): MaterialLayer = MaterialLayer(
    nr = nr,
    description = "",
    thickness = 0.0,
    lambda = 0.0,
    isEditable = true
  )

