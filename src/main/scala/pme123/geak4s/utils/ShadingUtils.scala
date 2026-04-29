package pme123.geak4s.utils

/**
 * Verschattungsfaktor Fs nach SIA 380/1:2016, Gleichung B.11 / Tabelle B.11.
 *
 *   Fs = max(0,  1  −  f_hor · tan(α_hor)
 *                    −  f_finL · tan(α_fin)      [linke Blende]
 *                    −  f_finR · tan(α_fin) )     [rechte Blende, nur beidseitig]
 *
 * Horizontaler Schattenwinkel (Überhang + Horizont werden addiert):
 *   tan(α_hor) = (a / d)  +  tan(γ_H)
 *     a   = Überstandtiefe Überhang [m]           (AreaEntry.overhang)
 *     d   = Vertikalabstand Unterkante Überhang – Fenster [m]  (AreaEntry.overhangDist)
 *     γ_H = allgemeiner Horizontwinkel [°]         (AreaEntry.horizont)
 *
 * Vertikaler Schattenwinkel (Seitenblenden):
 *   tan(α_fin) = b / e
 *     b = Tiefe der Seitenblende [m]              (AreaEntry.sideShading)
 *     e = Horizontalabstand Blende – Fensterrand [m] (AreaEntry.sideShadingDist)
 *
 * Koeffizienten aus Tabelle B.11 SIA 380/1:2016 (Klimastation Zürich).
 * Bei unbekannter/leerer Ausrichtung wird Süd verwendet (konservativster Fall).
 */
object ShadingUtils:

  private case class Coeff(fHor: Double, fFinL: Double, fFinR: Double)

  /** Tabelle B.11 SIA 380/1:2016 – Klimastation Zürich */
  private val table: Map[String, Coeff] = Map(
    "S"  -> Coeff(0.65, 0.40, 0.40),
    "SO" -> Coeff(0.52, 0.54, 0.36),
    "SW" -> Coeff(0.52, 0.36, 0.54),
    "O"  -> Coeff(0.34, 0.56, 0.18),
    "W"  -> Coeff(0.34, 0.18, 0.56),
    "NO" -> Coeff(0.21, 0.41, 0.10),
    "NW" -> Coeff(0.21, 0.10, 0.41),
    "N"  -> Coeff(0.00, 0.00, 0.00),
  )

  private val fallback = Coeff(0.65, 0.40, 0.40)  // Süd als Fallback

  /**
   * Berechnet Fs ∈ [0, 1] nach SIA 380/1:2016 Gl. B.11.
   * Gibt None zurück wenn kein Verschattungsparameter vorhanden ist.
   * Bei unbekannter Ausrichtung wird Süd-Koeffizient verwendet.
   */
  def shadingFactor(
    orientation: String,
    overhangDepth: Double,    // a: Überstandtiefe [m]
    overhangDist: Double,     // d: Vertikalabstand Überhang→Fenster [m]
    sideShadingDepth: Double, // b: Tiefe Seitenblende [m]
    sideShadingDist: Double,  // e: Horizontalabstand Blende→Fensterrand [m]
    beidseitig: Boolean,
    horizont: Double          // γH: allgemeiner Horizontwinkel [°]
  ): Option[Double] =
    val hasOverhang    = overhangDepth > 0 && overhangDist > 0
    val hasHorizont    = horizont > 0
    val hasSideShading = sideShadingDepth > 0 && sideShadingDist > 0
    if !hasOverhang && !hasHorizont && !hasSideShading then return None

    val c = table.getOrElse(orientation.trim.toUpperCase, fallback)

    // Kombinierter horizontaler Schattenwinkel-Tangens
    val tanH =
      (if hasOverhang then overhangDepth / overhangDist else 0.0) +
      (if hasHorizont then math.tan(horizont * math.Pi / 180.0) else 0.0)

    // SIA 380/1:2016 Gl. B.11
    val fs =
      if hasSideShading then
        val tanV = sideShadingDepth / sideShadingDist
        if beidseitig then
          // Beide Seitenblenden: linker + rechter Koeffizient
          1.0 - c.fHor * tanH - c.fFinL * tanV - c.fFinR * tanV
        else
          // Einseitig: Mittelwert beider Koeffizienten (Seite unbekannt)
          1.0 - c.fHor * tanH - ((c.fFinL + c.fFinR) / 2.0) * tanV
      else
        1.0 - c.fHor * tanH

    Some(math.max(0.0, math.min(1.0, fs)))
