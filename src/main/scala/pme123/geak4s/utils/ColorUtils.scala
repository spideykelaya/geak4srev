package pme123.geak4s.utils

object ColorUtils:

  private def hexToRgb(hex: String): (Int, Int, Int) =
    val h = hex.stripPrefix("#")
    (
      Integer.parseInt(h.substring(0, 2), 16),
      Integer.parseInt(h.substring(2, 4), 16),
      Integer.parseInt(h.substring(4, 6), 16)
    )

  private def rgbToHsl(r: Int, g: Int, b: Int): (Double, Double, Double) =
    val rf    = r / 255.0
    val gf    = g / 255.0
    val bf    = b / 255.0
    val cMax  = math.max(rf, math.max(gf, bf))
    val cMin  = math.min(rf, math.min(gf, bf))
    val delta = cMax - cMin
    val l     = (cMax + cMin) / 2.0
    val s     = if delta == 0 then 0.0 else delta / (1 - math.abs(2 * l - 1))
    val h =
      if delta == 0 then 0.0
      else if cMax == rf then 60 * (((gf - bf) / delta) % 6)
      else if cMax == gf then 60 * ((bf - rf) / delta + 2)
      else                    60 * ((rf - gf) / delta + 4)
    (if h < 0 then h + 360 else h, s, l)

  private def hslToRgb(h: Double, s: Double, l: Double): (Int, Int, Int) =
    val c = (1 - math.abs(2 * l - 1)) * s
    val x = c * (1 - math.abs((h / 60) % 2 - 1))
    val m = l - c / 2
    val (r1, g1, b1) =
      if      h < 60  then (c, x, 0.0)
      else if h < 120 then (x, c, 0.0)
      else if h < 180 then (0.0, c, x)
      else if h < 240 then (0.0, x, c)
      else if h < 300 then (x, 0.0, c)
      else                 (c, 0.0, x)
    def toInt(v: Double): Int = math.round((v + m) * 255).toInt.max(0).min(255)
    (toInt(r1), toInt(g1), toInt(b1))

  private def withLightness(hex: String, targetL: Double): String =
    val (r, g, b)    = hexToRgb(hex)
    val (h, s, _)    = rgbToHsl(r, g, b)
    val (nr, ng, nb) = hslToRgb(h, s, targetL.max(0.1).min(0.95))
    f"#$nr%02x$ng%02x$nb%02x"

  /**
   * Returns an adjusted color for a specific U-Wert value.
   * Smallest U-Wert → lightest (L=0.82), largest → darkest (L=0.32).
   * If only one distinct value exists, returns the base color unchanged.
   */
  def computeUWertColor(baseHex: String, uValue: Double, allUValues: Seq[Double]): String =
    val sorted = allUValues.distinct.sorted
    if sorted.size <= 1 then baseHex
    else
      val minU  = sorted.head
      val maxU  = sorted.last
      val t     = if maxU == minU then 0.5 else (uValue - minU) / (maxU - minU)
      withLightness(baseHex, 0.82 - t * 0.50)
