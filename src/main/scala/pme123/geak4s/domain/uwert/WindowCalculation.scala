package pme123.geak4s.domain.uwert

case class WindowCalculation(
  id: String,
  label: String       = "",
  uValue: Double      = 0.0,
  gValue: Double      = 0.0,
  glassRatio: Double  = 0.0,   // as decimal, e.g. 0.70
  overhang: Double    = 0.0,   // Überhang [m]
  overhangDist: Double    = 0.0,   // Abstand Überhang [m]
  sideShading: Double     = 0.0,   // Seitenblende [m]
  sideShadingDist: Double = 0.0    // Abstand Seitenblende [m]
)

object WindowCalculation:
  def empty(id: String): WindowCalculation = WindowCalculation(id = id)

  val uValuePresets: Seq[Double]     = Seq(0.5, 0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.3, 1.5, 1.7, 2.0, 2.6, 3.0, 5.0)
  val gValuePresets: Seq[Double]     = Seq(0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70)
  val glassRatioPresets: Seq[Double] = Seq(0.40, 0.45, 0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80)
