package pme123.geak4s.domain.gis

/** GIS data from eCH-0206 MADD response (Swiss building registry) */

case class MaddResponse(
  status: Status,
  buildingList: List[BuildingItem],
  responseMetadata: ResponseMetadata
)

case class Status(
  code: String,
  message: String
)

case class BuildingItem(
  egid: String,  // EGID - Eidgenössischer Gebäudeidentifikator
  building: Building,
  buildingEntranceList: List[BuildingEntranceItem],
  municipality: Municipality,
  realestateIdentificationList: List[RealestateIdentificationItem]
)

case class Building(
  officialBuildingNo: Option[String],
  coordinates: Coordinates,
  buildingStatus: Option[String],
  buildingCategory: Option[String],
  buildingClass: Option[String],
  dateOfConstruction: Option[DateOfConstruction],
  surfaceAreaOfBuilding: Option[Int],
  numberOfFloors: Option[Int],
  thermotechnicalDeviceForHeating1: Option[ThermotechnicalDevice],
  thermotechnicalDeviceForWarmWater1: Option[ThermotechnicalDevice]
)

case class Coordinates(
  east: Double,
  north: Double,
  originOfCoordinates: Option[String]
)

case class DateOfConstruction(
  dateOfConstruction: Option[String],
  periodOfConstruction: Option[String]
)

case class ThermotechnicalDevice(
  heatGenerator: Option[String],
  energySource: Option[String],
  informationSource: Option[String],
  revisionDate: Option[String]
)

case class BuildingEntranceItem(
  edid: String,  // EDID - Entrance ID
  buildingEntrance: BuildingEntrance,
  dwellingList: List[DwellingItem]
)

case class BuildingEntrance(
  egaid: String,  // EGAID - Entrance Address ID
  buildingEntranceNo: String,
  coordinates: Coordinates,
  isOfficialAddress: Boolean,
  street: Street,
  locality: Locality
)

case class Street(
  esid: String,  // ESID - Street ID
  isOfficialDescription: Boolean,
  streetName: StreetName
)

case class StreetName(
  language: String,
  descriptionLong: String,
  descriptionShort: String,
  descriptionIndex: String
)

case class Locality(
  swissZipCode: String,
  swissZipCodeAddOn: String,
  placeName: String
)

case class DwellingItem(
  ewid: String,  // EWID - Dwelling ID
  dwelling: Dwelling
)

case class Dwelling(
  administrativeDwellingNo: String,
  yearOfConstruction: Option[Int],
  noOfHabitableRooms: Option[Int],
  floor: Option[String],
  multipleFloor: Option[Int],
  kitchen: Option[Int],
  surfaceAreaOfDwelling: Option[Int],
  dwellingStatus: Option[String]
)

case class Municipality(
  municipalityId: String,
  municipalityName: String,
  cantonAbbreviation: String
)

case class RealestateIdentificationItem(
  egrid: String,  // EGRID - Real Estate ID
  number: String,
  subDistrict: String
)

case class ResponseMetadata(
  lastUpdateDate: String,
  exportDate: String
)

object MaddResponse:
  /** Create a summary string for console output */
  def summary(response: MaddResponse): String =
    val sb = new StringBuilder
    sb.append("=" * 80 + "\n")
    sb.append("GIS DATA IMPORT SUMMARY\n")
    sb.append("=" * 80 + "\n\n")
    
    response.buildingList.foreach { buildingItem =>
      sb.append(s"EGID: ${buildingItem.egid}\n")
      sb.append(s"Official Building No: ${buildingItem.building.officialBuildingNo.getOrElse("N/A")}\n")
      sb.append(s"Coordinates: E=${buildingItem.building.coordinates.east}, N=${buildingItem.building.coordinates.north}\n")
      sb.append(s"Building Category: ${buildingItem.building.buildingCategory.getOrElse("N/A")}\n")
      sb.append(s"Building Class: ${buildingItem.building.buildingClass.getOrElse("N/A")}\n")
      sb.append(s"Construction Date: ${buildingItem.building.dateOfConstruction.flatMap(_.dateOfConstruction).getOrElse("N/A")}\n")
      sb.append(s"Surface Area: ${buildingItem.building.surfaceAreaOfBuilding.getOrElse("N/A")} m²\n")
      sb.append(s"Number of Floors: ${buildingItem.building.numberOfFloors.getOrElse("N/A")}\n")
      sb.append(s"Municipality: ${buildingItem.municipality.municipalityName} (${buildingItem.municipality.cantonAbbreviation})\n\n")
      
      sb.append(s"Building Entrances: ${buildingItem.buildingEntranceList.length}\n")
      buildingItem.buildingEntranceList.foreach { entrance =>
        val addr = entrance.buildingEntrance
        sb.append(s"  - ${addr.street.streetName.descriptionLong} ${addr.buildingEntranceNo}, ${addr.locality.swissZipCode} ${addr.locality.placeName}\n")
        sb.append(s"    Dwellings: ${entrance.dwellingList.length}\n")
      }
    }
    
    sb.append("\n" + "=" * 80 + "\n")
    sb.toString

