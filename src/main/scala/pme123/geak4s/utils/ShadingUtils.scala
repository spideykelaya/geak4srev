package pme123.geak4s.utils

/**
 * Verschattungsfaktor Fs nach SIA 380/1:2016, Ziffer 3.5.4.13.
 *
 *   Fs = Fs1 · Fs2 · Fs3,l · Fs3,r
 *
 *   Fs1 = Horizontverschattungsfaktor    – Winkel α [°]  (AreaEntry.horizont)
 *   Fs2 = Überstandsverschattungsfaktor  – Winkel β = arctan(a/d)  [°]
 *   Fs3 = Seitenverschattungsfaktor      – Winkel γ = arctan(b/e)  [°]
 *
 * Tabellenwerte aus SIA 380/1:2016 (Klimastation Zürich), drei Orientierungsspalten:
 *   Süd | Ost/West | Nord
 * Zwischenrichtungen (SO, SW, NO, NW) werden linear zwischen Hauptrichtungen interpoliert.
 * Bei unbekannter Ausrichtung wird Süd verwendet (konservativster Fall).
 */
object ShadingUtils:

  // Orientierungsgewichte (südAnteil, ostwestAnteil, nordAnteil)
  private def weights(orientation: String): (Double, Double, Double) =
    orientation.trim.toUpperCase match
      case "S"        => (1.0, 0.0, 0.0)
      case "SO" | "SW" => (0.5, 0.5, 0.0)
      case "O"  | "W"  => (0.0, 1.0, 0.0)
      case "NO" | "NW" => (0.0, 0.5, 0.5)
      case "N"        => (0.0, 0.0, 1.0)
      case _          => (1.0, 0.0, 0.0)  // Süd als konservativer Fallback

  // Spalten: (Winkel [°], Süd, Ost/West, Nord)
  // Fs1: Horizontverschattung – SIA 380/1:2016 Abb. 29
  private val fs1Table: Array[(Double, Double, Double, Double)] = Array(
    ( 0.0, 1.00, 1.00, 1.00),
    (10.0, 0.96, 0.94, 1.00),
    (20.0, 0.82, 0.81, 0.97),
    (30.0, 0.59, 0.68, 0.94),
    (40.0, 0.45, 0.60, 0.90),
    (50.0, 0.36, 0.50, 0.86),
    (60.0, 0.27, 0.40, 0.82),
    (70.0, 0.19, 0.30, 0.78),
  )

  // Fs2: Überstandsverschattung – SIA 380/1:2016 Abb. 36
  private val fs2Table: Array[(Double, Double, Double, Double)] = Array(
    ( 0.0, 1.00, 1.00, 1.00),
    (15.0, 0.95, 0.95, 0.96),
    (30.0, 0.91, 0.89, 0.91),
    (45.0, 0.75, 0.77, 0.80),
    (60.0, 0.52, 0.59, 0.66),
    (75.0, 0.26, 0.34, 0.48),
  )

  // Fs3: Seitenverschattung (pro Seite) – SIA 380/1:2016 Abb. 37
  // Nord-Spalte ist immer 1.00: Seitenblenden für Nordfenster irrelevant
  private val fs3Table: Array[(Double, Double, Double, Double)] = Array(
    ( 0.0, 1.00, 1.00, 1.00),
    (15.0, 0.97, 0.96, 1.00),
    (30.0, 0.94, 0.92, 1.00),
    (45.0, 0.84, 0.84, 1.00),
    (60.0, 0.72, 0.75, 1.00),
    (75.0, 0.57, 0.65, 1.00),
  )

  // Lineare Interpolation im Tabellenfeld; Winkel wird auf Tabellenbereich geklemmt.
  private def lookup(
    table: Array[(Double, Double, Double, Double)],
    angleDeg: Double,
    wS: Double, wOW: Double, wN: Double
  ): Double =
    val angle = angleDeg.max(0.0).min(table.last._1)
    val i = table.lastIndexWhere(_._1 <= angle)
    val (fsS, fsOW, fsN) =
      if i < 0 then
        val (_, s, ow, n) = table(0); (s, ow, n)
      else if i >= table.length - 1 then
        val (_, s, ow, n) = table.last; (s, ow, n)
      else
        val (a0, s0, ow0, n0) = table(i)
        val (a1, s1, ow1, n1) = table(i + 1)
        val t = (angle - a0) / (a1 - a0)
        (s0 + t * (s1 - s0), ow0 + t * (ow1 - ow0), n0 + t * (n1 - n0))
    fsS * wS + fsOW * wOW + fsN * wN

  /**
   * Berechnet Fs = Fs1 × Fs2 × Fs3,l × Fs3,r ∈ [0, 1] nach SIA 380/1:2016 Ziff. 3.5.4.13.
   * Gibt None zurück wenn kein Verschattungsparameter vorhanden ist.
   */
  def shadingFactor(
    orientation: String,
    overhangDepth: Double,    // a: Überstandtiefe [m]
    overhangDist: Double,     // d: Vertikalabstand Überhang→Fenster [m]
    sideShadingDepth: Double, // b: Tiefe Seitenblende [m]
    sideShadingDist: Double,  // e: Horizontalabstand Blende→Fensterrand [m]
    beidseitig: Boolean,
    horizont: Double          // α: allgemeiner Horizontwinkel [°]
  ): Option[Double] =
    val hasOverhang    = overhangDepth > 0 && overhangDist > 0
    val hasHorizont    = horizont > 0
    val hasSideShading = sideShadingDepth > 0 && sideShadingDist > 0
    if !hasOverhang && !hasHorizont && !hasSideShading then return None

    val (wS, wOW, wN) = weights(orientation)

    // Fs1: Horizontverschattung (Winkel α direkt in °)
    val fs1 =
      if hasHorizont then lookup(fs1Table, horizont, wS, wOW, wN)
      else 1.0

    // Fs2: Überstandsverschattung (Winkel β = arctan(a/d) in °)
    val fs2 =
      if hasOverhang then
        val angleDeg = math.toDegrees(math.atan(overhangDepth / overhangDist))
        lookup(fs2Table, angleDeg, wS, wOW, wN)
      else 1.0

    // Fs3: Seitenverschattung (Winkel γ = arctan(b/e) in °)
    // beidseitig: Fs3,l · Fs3,r  (gleicher Winkel, gleiche Seite → quadriert)
    val fs3 =
      if hasSideShading then
        val angleDeg = math.toDegrees(math.atan(sideShadingDepth / sideShadingDist))
        val fS3 = lookup(fs3Table, angleDeg, wS, wOW, wN)
        if beidseitig then fS3 * fS3 else fS3
      else 1.0

    // Gesamtverschattungsfaktor: Fs = Fs1 × Fs2 × Fs3,l × Fs3,r
    Some(math.max(0.0, math.min(1.0, fs1 * fs2 * fs3)))
