package pme123.geak4s.domain.project

/** Building data and technical specifications */
case class BuildingData(
  constructionYear: Option[Int],              // Baujahr
  lastRenovationYear: Option[Int],            // Jahr der letzten Gesamtsanierung
  weatherStation: Option[String],             // Klimastation
  weatherStationValues: Option[String],       // Klimastation mit bestbekannten Werten
  altitude: Option[Double],                   // Höhe ü. M.
  energyReferenceArea: Option[Double],        // Energiebezugsfläche [m²]
  clearRoomHeight: Option[Double],            // Lichte Raumhöhe [m]
  numberOfFloors: Option[Int],                // Anzahl der Vollgeschosse
  buildingWidth: Option[Double],              // Gebäudebreite [m]
  constructionType: Option[String],           // Bauweise Gebäude
  groundPlanType: Option[String]              // Grundrisstyp
)

object BuildingData:
  lazy val example: BuildingData = BuildingData(
    constructionYear = Some(1975),
    lastRenovationYear = Some(2010),
    weatherStation = Some("Zürich-Fluntern"),
    weatherStationValues = Some("Standard"),
    altitude = Some(556.0),
    energyReferenceArea = Some(850.5),
    clearRoomHeight = Some(2.4),
    numberOfFloors = Some(4),
    buildingWidth = Some(12.5),
    constructionType = Some("Massivbau"),
    groundPlanType = Some("kompakt")
  )
end BuildingData

