package pme123.geak4s.domain.uwert

/** Building material with thermal conductivity and applicable component types */
case class BuildingMaterial(
    name: String,
    thermalConductivity: Double,                  // λ [W/(m·K)] - Lambda value
    applicableFor: Set[ComponentType] = Set.empty, // Component types this material can be used for
    isInsulation: Boolean = false                  // True = Dämmung, False = Baumaterial
)

/** Catalog of building components with their thermal conductivity values */
object BuildingComponentCatalog:

  import ComponentType.*

  /** All available building components */
  val components: List[BuildingMaterial] = List(
    // ── Dämmung ───────────────────────────────────────────────────────────────

    // Aerogel / Hochleistungsdämmung
    BuildingMaterial("Aerogel, Agitech Spaceloft", 0.015,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor, ShutterBoxCover), isInsulation = true),

    // Natürliche Dämmstoffe
    BuildingMaterial("Schilf (alte Gebäude)", 0.065, Set(PitchedRoof, ExteriorWall, AtticFloor), isInsulation = true),
    BuildingMaterial("Kork/Korkschrottmatte", 0.056,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Kokosfasermatte", 0.05, Set(ExteriorWall, PitchedRoof, AtticFloor), isInsulation = true),
    BuildingMaterial("Korkplatte", 0.042,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Korkschrot expandiert", 0.042,
      Set(ExteriorWall, PitchedRoof, FlatRoof, AtticFloor), isInsulation = true),
    BuildingMaterial("Korkschrot natur", 0.06, Set(AtticFloor, BasementCeiling), isInsulation = true),
    BuildingMaterial("Korkschrotmatte", 0.046, Set(ExteriorWall, PitchedRoof, AtticFloor), isInsulation = true),

    // Holzwolle-Platten
    BuildingMaterial("Heraklith, Holzwolle (HWL) Zementgebunden, Perfecta", 0.095,
      Set(PitchedRoof, ExteriorWall, AtticFloor, BasementCeiling), isInsulation = true),
    BuildingMaterial("Holzwollenplatte (Bspw. Gargendecke)", 0.09,
      Set(BasementCeiling, AtticFloor, PitchedRoof), isInsulation = true),
    BuildingMaterial("Zementgeb. Holzwollepl.", 0.11,
      Set(PitchedRoof, ExteriorWall, AtticFloor, BasementCeiling), isInsulation = true),
    BuildingMaterial("Holzspanplatte weich", 0.06, Set(PitchedRoof, AtticFloor), isInsulation = true),

    // Mineral-/Glaswolle
    BuildingMaterial("Glasfasern", 0.04,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Glasfaserplatte", 0.04,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Glaswolle", 0.04,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Steinwolle", 0.04,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Steinwollplatte", 0.04,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Thermo-Plus, Glaswolle, Kellerdecke", 0.031, Set(BasementCeiling), isInsulation = true),

    // EPS / XPS / PUR
    BuildingMaterial("Wärmedämmung, Prod. Unbekannt (bspw. Styropor bzw. EPS)", 0.038,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor, BasementFloor), isInsulation = true),
    BuildingMaterial("Polystyrolplatte exp.", 0.038,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor, BasementFloor), isInsulation = true),
    BuildingMaterial("Polystyrolplatte extr.", 0.034,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth,
          FlatRoof, BasementFloor), isInsulation = true),
    BuildingMaterial("Schaumpolystyrol exp.", 0.038,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Polyurehanhartschaum", 0.03,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, BasementFloor), isInsulation = true),

    // Swisspor / Flumroc / Foamglas Produkte
    BuildingMaterial("Flumroc COMPACT PRO, Fassade", 0.033, Set(ExteriorWall), isInsulation = true),
    BuildingMaterial("Swisspor Lamda White 031, EPS", 0.031,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("SwissporXPS Premium Plus 300", 0.027,
      Set(ExteriorWall, BasementFloor, BasementWallToOutside, BasementWallToUnheated,
          BasementWallToEarth, FlatRoof), isInsulation = true),
    BuildingMaterial("SwissporLAMDA Roof", 0.029, Set(PitchedRoof, FlatRoof), isInsulation = true),
    BuildingMaterial("Foamglas T4+", 0.041,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth, FlatRoof), isInsulation = true),
    BuildingMaterial("Foamglas T3+", 0.036,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth), isInsulation = true),
    BuildingMaterial("Foamglasplatte", 0.044,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth,
          FlatRoof, BasementFloor), isInsulation = true),
    BuildingMaterial("Schaumglasolatte", 0.048,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth,
          FlatRoof, BasementFloor), isInsulation = true),
    BuildingMaterial("Flumroc-Dämmplatte 3, 15% Holzanteil (innen wenig Sparren)", 0.039,
      Set(PitchedRoof, AtticFloor, BasementCeiling), isInsulation = true),
    BuildingMaterial("Flumroc Dachplatte", 0.04, Set(PitchedRoof, FlatRoof, AtticFloor), isInsulation = true),
    BuildingMaterial("Flumroc Isolierplatte", 0.036,
      Set(ExteriorWall, PitchedRoof, FlatRoof, BasementCeiling, AtticFloor), isInsulation = true),
    BuildingMaterial("Flumroc-Feingranulat", 0.04, Set(AtticFloor, BasementCeiling), isInsulation = true),
    BuildingMaterial("Flumroc ESTRA, Estrichbodenplatte", 0.034, Set(AtticFloor, FloorToOutside), isInsulation = true),
    BuildingMaterial("Multipor Innendämmung", 0.042,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth), isInsulation = true),
    BuildingMaterial("WILAN 3, mit einseitiger Fertigdeckschicht", 0.031,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth), isInsulation = true),

    // Sarna-Produkte
    BuildingMaterial("Sarna-Aussendämmpl.", 0.038, Set(ExteriorWall, FlatRoof), isInsulation = true),
    BuildingMaterial("Sarnapur-Platte", 0.03, Set(FlatRoof, BasementFloor), isInsulation = true),
    BuildingMaterial("Sarnatherm-Polystyrolpl.", 0.036, Set(ExteriorWall, FlatRoof), isInsulation = true),

    // Verschiedene Dämmstoffe
    BuildingMaterial("Pavatherm-Plus, Dämmung+Unterdach", 0.043, Set(PitchedRoof), isInsulation = true),
    BuildingMaterial("Isofloc oder bestehend, 15% Holzanteil (Standard)", 0.045,
      Set(PitchedRoof, AtticFloor), isInsulation = true),
    BuildingMaterial("PU-Dämm. für Flachdach/Terrasse/Boden, Vlies", 0.026,
      Set(FlatRoof, BasementFloor, FloorToOutside), isInsulation = true),
    BuildingMaterial("Perlit-Platte", 0.06, Set(ExteriorWall, PitchedRoof, FlatRoof, AtticFloor), isInsulation = true),
    BuildingMaterial("Perlit-Schüttung", 0.07, Set(AtticFloor, BasementCeiling), isInsulation = true),

    // ── Baumaterial ───────────────────────────────────────────────────────────

    // Mauerwerk
    BuildingMaterial("Ziegel", 0.8,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Bollenstein, ab ca. 50 cm dick", 0.9,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Backstein (Einsteinmauerwerk)", 0.44,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Backstein (Verbandmrwk) d=28-30", 0.37,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Kalksandstein", 0.8,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Gasbeton", 0.16,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Gasbeton", 0.19,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Gasbeton-Dachplatte", 0.2, Set(PitchedRoof, FlatRoof)),

    // Beton
    BuildingMaterial("Stahlbeton, 1% Stahl / Steinbodenplatten", 2.3,
      Set(BasementFloor, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth,
          BasementCeiling, FloorToOutside, ExteriorWall, PitchedRoof, AtticFloor)),

    // Holz
    BuildingMaterial("Holz", 0.14, Set(ExteriorWall, PitchedRoof, FloorToOutside, AtticFloor)),
    BuildingMaterial("Buchenholz", 0.17, Set(ExteriorWall, PitchedRoof, FloorToOutside, AtticFloor)),
    BuildingMaterial("Eichenholz", 0.21, Set(ExteriorWall, PitchedRoof, FloorToOutside, AtticFloor)),
    BuildingMaterial("Fichtenholz", 0.14, Set(ExteriorWall, PitchedRoof, FloorToOutside, AtticFloor)),
    BuildingMaterial("Holzspanplatte", 0.12, Set(ExteriorWall, PitchedRoof, AtticFloor)),
    BuildingMaterial("Holzspanplatte halbhart", 0.085, Set(ExteriorWall, PitchedRoof, AtticFloor)),
    BuildingMaterial("Holzspanplatte hart", 0.17, Set(ExteriorWall, FloorToOutside)),
    BuildingMaterial("Holzspanplatte Novophen", 0.12, Set(ExteriorWall, PitchedRoof, AtticFloor)),
    BuildingMaterial("Duripanelplatte", 0.26, Set(ExteriorWall, PitchedRoof, AtticFloor)),
    BuildingMaterial("Durisol-Dachplatte", 0.12, Set(PitchedRoof, FlatRoof)),

    // Putz
    BuildingMaterial("Aussenputz", 0.87,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth)),
    BuildingMaterial("Innenputz", 0.7,
      Set(ExteriorWall, BasementWallToOutside, BasementWallToUnheated, BasementWallToEarth,
          BasementCeiling, AtticFloor)),

    // Gips
    BuildingMaterial("Gipskartonplatte", 0.21, Set(ExteriorWall, AtticFloor, BasementCeiling)),
    BuildingMaterial("Gipsplatte", 0.4, Set(ExteriorWall, AtticFloor, BasementCeiling)),

    // Deckenelemente / Böden
    BuildingMaterial("Tonhourdis (Kellerdecke)", 0.44, Set(BasementCeiling, FloorToOutside)),
    BuildingMaterial("Tonisolierplatte", 0.44, Set(BasementCeiling, FloorToOutside)),
    BuildingMaterial("Unterlagsboden", 1.4, Set(BasementFloor, FloorToOutside, AtticFloor)),
    BuildingMaterial("Schlacke", 0.35, Set(BasementFloor, AtticFloor)),

    // Platten / Beläge
    BuildingMaterial("Keramische Platten", 1.0, Set(FloorToOutside, BasementFloor)),
    BuildingMaterial("Klinkerplatten", 1.0, Set(ExteriorWall, FloorToOutside)),
    BuildingMaterial("Marmorplatten", 2.3, Set(FloorToOutside, BasementFloor)),
    BuildingMaterial("Steinzeugplatten", 1.5, Set(FloorToOutside, BasementFloor)),
    BuildingMaterial("Tonplatten", 1.0, Set(FloorToOutside, BasementFloor, PitchedRoof)),
    BuildingMaterial("Gussasphaltbelag", 0.7, Set(FloorToOutside, FlatRoof)),
    BuildingMaterial("Zementüberzug", 1.5, Set(FloorToOutside, BasementFloor, FlatRoof)),

    // Schüttungen / Kies
    BuildingMaterial("Kies/Sand-Schutzschicht", 1.8, Set(FlatRoof, BasementFloor)),
    BuildingMaterial("Rundkies", 2.3, Set(BasementFloor, FloorToOutside)),
    BuildingMaterial("Splitt", 1.5, Set(BasementFloor, FloorToOutside))
  )

  /** Get thermal conductivity by component name */
  def getThermalConductivityByName(name: String): Option[Double] =
    components.find(_.name.equalsIgnoreCase(name)).map(_.thermalConductivity)

  /** Get all component names for autocomplete */
  def getAllNames: List[String] =
    components.map(_.name).distinct.sorted

  /** Search components by partial name match */
  def searchByName(partial: String): List[BuildingMaterial] =
    if partial.isEmpty then List.empty
    else components.filter(_.name.toLowerCase.contains(partial.toLowerCase)).distinct

  /** Get components grouped by thermal conductivity range */
  def getByThermalConductivityRange(min: Double, max: Double): List[BuildingMaterial] =
    components.filter(c => c.thermalConductivity >= min && c.thermalConductivity <= max)

  /** Get all unique thermal conductivity values */
  def getAllThermalConductivities: List[Double] =
    components.map(_.thermalConductivity).distinct.sorted

  /** Get materials applicable for a specific component type */
  def getByComponentType(componentType: ComponentType): List[BuildingMaterial] =
    components.filter(_.applicableFor.contains(componentType)).distinct.sortBy(_.name)

  /** Get materials grouped as (Dämmung, Baumaterial), each sorted by name */
  def getByComponentTypeGrouped(componentType: ComponentType): (List[BuildingMaterial], List[BuildingMaterial]) =
    val filtered = components.filter(_.applicableFor.contains(componentType)).distinct
    val (insulation, building) = filtered.partition(_.isInsulation)
    (insulation.sortBy(_.name), building.sortBy(_.name))

end BuildingComponentCatalog
