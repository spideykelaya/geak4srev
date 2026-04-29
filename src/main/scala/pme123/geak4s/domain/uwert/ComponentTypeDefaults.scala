package pme123.geak4s.domain.uwert

/**
 * Standard default values per component type for GEAK Plus reporting.
 *
 * Source: "Mustertexte GEAK Plus.docx", pages 7 and 13-14:
 *   - Werterhalt rates: Fenster 850/m², Türen 6'000 pauschal; roofs/walls/floors 0 (user fills)
 *   - Investition rates: Dach/Fassade 500/m², Boden 300/m², Estrich/Innendämmung 150/m²,
 *                        Fenster 850/m², Tür 6'000 pauschal; Kellerdecke/-wände 150/m²
 *   - Nutzungsdauer: Schrägdach 50a, Flachdach 40a, Wand/Boden gg. Aussen 25a,
 *                    Fenster/Türen 40a, Keller 50a
 *
 * @param werterhalRate  CHF per m² (or per unit if ratePerUnit = true) for like-for-like replacement
 * @param investitionRate CHF per m² (or per unit) for the improvement measure
 * @param nutzungsdauer  Component lifespan in years
 * @param ratePerUnit    If true, multiply by quantity instead of totalArea (used for doors)
 */
case class ComponentTypeDefaults(
  werterhalRate: Double,
  investitionRate: Double,
  nutzungsdauer: Int,
  ratePerUnit: Boolean = false
)

object ComponentTypeDefaults:

  private val table: Map[ComponentType, ComponentTypeDefaults] = Map(
    // Dächer — Nutzungsdauer 50 (Schrägdach) / 40 (Flachdach)
    ComponentType.PitchedRoof          -> ComponentTypeDefaults(0.0,   500.0, 50),
    ComponentType.FlatRoof             -> ComponentTypeDefaults(0.0,   500.0, 40),
    // Decke gegen unbeheizt (Estrichboden) — Innendämmung 150/m², 50 Jahre
    ComponentType.AtticFloor           -> ComponentTypeDefaults(0.0,   150.0, 50),
    // Aussenwand — Nutzungsdauer 25 Jahre, Investition Fassadendämmung 500/m²
    ComponentType.ExteriorWall         -> ComponentTypeDefaults(0.0,   500.0, 25),
    // Boden gegen aussen — Nutzungsdauer 25 Jahre (wie Wand), Investition 300/m²
    ComponentType.FloorToOutside       -> ComponentTypeDefaults(0.0,   300.0, 25),
    // Kellerbauteile — kein Werterhalt; Investition Innendämmung
    ComponentType.BasementWallToEarth  -> ComponentTypeDefaults(0.0,   200.0, 50),
    ComponentType.BasementWallToUnheated -> ComponentTypeDefaults(0.0, 150.0, 50),
    ComponentType.BasementWallToOutside  -> ComponentTypeDefaults(0.0, 200.0, 50),
    ComponentType.BasementCeiling      -> ComponentTypeDefaults(0.0,   150.0, 50),
    ComponentType.BasementFloor        -> ComponentTypeDefaults(0.0,   300.0, 50),
    // Fenster — Werterhalt 1000/m², Investition 1000/m², 40 Jahre
    ComponentType.Window               -> ComponentTypeDefaults(1000.0, 1000.0, 40),
    // Türen — 6'000 pauschal pro Stück, 40 Jahre
    ComponentType.Door                 -> ComponentTypeDefaults(6000.0, 6000.0, 40, ratePerUnit = true),
    // Storenkasten
    ComponentType.ShutterBoxCover      -> ComponentTypeDefaults(0.0,   150.0, 30),
    // EBF — kein Bauteil, keine Werte
    ComponentType.EBF                  -> ComponentTypeDefaults(0.0, 0.0, 0)
  )

  def get(ct: ComponentType): ComponentTypeDefaults =
    table.getOrElse(ct, ComponentTypeDefaults(0.0, 0.0, 0))

  // Rate presets: rateKey → (werterhalRate/m², investitionRate/m²)
  // Source: "Mustertexte GEAK Plus.docx" line 142 (Werterhalt), lines 292-296 (Investition)
  val ratePresets: Map[String, (Double, Double)] = Map(
    "pvc"        -> (800.0,  800.0),   // Fenster PVC Standard (doc: 850, user: 800)
    "holz"       -> (1000.0, 1000.0),  // Fenster Holz-Metall (doc: 1100, user: 1000)
    "einfach"    -> (0.0,    150.0),   // Kellerwand einfach (kein Werterhalt)
    "aufwaendig" -> (0.0,    200.0)    // Kellerwand aufwändig (kein Werterhalt)
  )

  /** Rate (CHF/m²) for Werterhalt — no area multiplication; the GEAK tool handles that. */
  def computedWerterhalt(entry: pme123.geak4s.domain.area.AreaEntry, ct: ComponentType): Double =
    get(ct).werterhalRate

  /** Rate (CHF/m²) for Investition — no area multiplication. */
  def computedInvestition(entry: pme123.geak4s.domain.area.AreaEntry, ct: ComponentType): Double =
    get(ct).investitionRate

  /** Effective Werterhalt rate: rateKey preset > stored value > component default rate */
  def effectiveWerterhalt(entry: pme123.geak4s.domain.area.AreaEntry, ct: ComponentType): Double =
    ratePresets.get(entry.rateKey) match
      case Some((wRate, _)) => wRate
      case None =>
        if entry.werterhalt != 0.0 then entry.werterhalt
        else get(ct).werterhalRate

  /** Effective Investition rate: rateKey preset > stored value > component default rate */
  def effectiveInvestition(entry: pme123.geak4s.domain.area.AreaEntry, ct: ComponentType): Double =
    ratePresets.get(entry.rateKey) match
      case Some((_, iRate)) => iRate
      case None =>
        if entry.investition != 0.0 then entry.investition
        else get(ct).investitionRate

  /** Effective Nutzungsdauer: stored value takes precedence; falls back to component default. */
  def effectiveNutzungsdauer(entry: pme123.geak4s.domain.area.AreaEntry, ct: ComponentType): Int =
    if entry.nutzungsdauer != 0 then entry.nutzungsdauer else get(ct).nutzungsdauer
