package pme123.geak4s.domain.uwert

/** Building component type classification */
enum ComponentType:
  case EBF                    // Energiebezugsfläche
  case BasementFloor          // Kellerboden
  case BasementWallToOutside  // Kellerwand
  case BasementWallToEarth    // Kellerwand
  case BasementWallToUnheated // Kellerwand
  case BasementCeiling        // Keller-Decke
  case ExteriorWall           // Aussenwand
  case FloorToOutside         // Boden
  case AtticFloor             // Estrichboden
  case PitchedRoof            // Steildach
  case FlatRoof               // Flachdach
  case ShutterBoxCover
  case Window                 // Fenster
  case Door                   // Tür

  def label: String = this match
  case EBF                    => "EBF - Energiebezugsfläche"
  case PitchedRoof            => "Dach gegen Aussenluft"
  case AtticFloor             => "Decke gegen unbeheizt"
  case ExteriorWall           => "Wand gegen Aussenluft"
  case BasementWallToEarth    => "Wand gegen Erdreich"
  case BasementWallToUnheated => "Wand gegen unbeheizt"
  case Window                 => "Fenster"
  case Door                   => "Tür"
  case BasementFloor          => "Boden gegen Erdreich"
  case BasementCeiling        => "Boden gegen unbeheizt"
  case FloorToOutside         => "Boden gegen aussen"
  // legacy — no longer shown in UI
  case BasementWallToOutside  => "Kellerwand gg. Aussen"
  case FlatRoof               => "Flachdach"
  case ShutterBoxCover        => "Storenabdeckung mit Aerogel"

  /** Short label used as polygon prefix in the Flächen-Rechner */
  def polygonLabel: String = this match
    case EBF    => "EBF"
    case Window => "Fenster"
    case Door   => "Tür"
    case _      => label

  /**
   * Get background color for this component type
   * Returns a light pastel color for visual distinction
   */
  def color: String = this match
    case EBF                    => "#fed7aa" // orange
    case PitchedRoof            => "#ddd6fe" // violet
    case AtticFloor             => "#ddd6fe" // violet
    case ExteriorWall           => "#fde68a" // gelb
    case BasementWallToEarth    => "#a7f3d0" // grün
    case BasementWallToUnheated => "#bfdbfe" // blau
    case Window                 => "#fbcfe8" // pink
    case Door                   => "#fbcfe8" // pink
    case BasementFloor          => "#a7f3d0" // grün
    case BasementCeiling        => "#bfdbfe" // blau
    case FloorToOutside         => "#fde68a" // gelb
    case BasementWallToOutside  => "#f3e5f5" // legacy
    case FlatRoof               => "#ddd6fe" // legacy
    case ShutterBoxCover        => "#fff3e0" // legacy

  /** Base polygon stroke color matching sidebar.js AREA_TYPE_COLORS */
  def polygonColor: String = this match
    case EBF                    => "#fb923c"
    case PitchedRoof            => "#a78bfa"
    case AtticFloor             => "#a78bfa"
    case ExteriorWall           => "#fbbf24"
    case BasementWallToEarth    => "#34d399"
    case BasementWallToUnheated => "#60a5fa"
    case Window                 => "#f472b6"
    case Door                   => "#f472b6"
    case BasementFloor          => "#34d399"
    case BasementCeiling        => "#60a5fa"
    case FloorToOutside         => "#fbbf24"
    case _                      => "#8888aa"

end ComponentType

object ComponentType:
  /** Fixed display order — only these types are shown in the UI. */
  val orderedVisibleTypes: Seq[ComponentType] = Seq(
    EBF,
    PitchedRoof,
    AtticFloor,
    ExteriorWall,
    BasementWallToEarth,
    BasementWallToUnheated,
    Window,
    Door,
    BasementFloor,
    BasementCeiling,
    FloorToOutside
  )

  /** Types for which a U-Wert calculation makes sense (excludes EBF, Fenster, Tür). */
  val uWertTypes: Seq[ComponentType] =
    orderedVisibleTypes.filterNot(Set(EBF, Window, Door).contains)

  /** Match a polygon label (possibly with trailing number) back to its ComponentType. */
  def fromPolygonLabel(rawLabel: String): Option[ComponentType] =
    val base = rawLabel.trim.replaceAll(""" \d+$""", "").trim
    ComponentType.values.find(_.polygonLabel == base)

case class BuildingComponent(
    compType: ComponentType,
    heatTransferFromInside: HeatTransfer,
    heatTransferToOutside: HeatTransfer,
    materials: Seq[HeatTransfer] = Seq.empty
):
  lazy val label: String = compType.label
end BuildingComponent

case class HeatTransfer(
    label: String,
    thicknessInM: Double,
    // Thermal conductivity lambda in W/(m·K)
    thermalConductivity: Double
)

// buildingComponents in the fixed display order (uWertTypes only — no EBF, Window, Door)
lazy val buildingComponents: Seq[BuildingComponent] =
  Seq(
    BuildingComponent(ComponentType.PitchedRoof,            transferFromInside, transferToOutsideVentilated),
    BuildingComponent(ComponentType.AtticFloor,             transferFromInside, transferToOutsideUnheated),
    BuildingComponent(ComponentType.ExteriorWall,           transferFromInside, transferToOutside),
    BuildingComponent(ComponentType.BasementWallToEarth,    transferFromInside, transferToGround),
    BuildingComponent(ComponentType.BasementWallToUnheated, transferFromInside, transferToOutsideUnheated),
    BuildingComponent(ComponentType.BasementFloor,          transferFromInside, transferToGround),
    BuildingComponent(ComponentType.BasementCeiling,        transferFromInside, transferToOutsideUnheated),
    BuildingComponent(ComponentType.FloorToOutside,         transferFromInside, transferToOutside)
  )

// Heat transfer definitions
lazy val transferFromInside          = HeatTransfer("Wärmeübergang Innen", 1, 8)
lazy val transferToOutside           = HeatTransfer("Wärmeübergang gegen aussen", 1, 25)
lazy val transferToOutsideUnheated   = HeatTransfer("Wärmeübergang gegen Unbeheizt", 1, 8)
lazy val transferToGround            =
  HeatTransfer("Wärmeübergang gegen Erdreich", 1, 0) // No heat transfer coefficient for ground
lazy val transferToOutsideVentilated = HeatTransfer("äusserer Übergang bei Hinterlüftung", 1, 12.5)
