package pme123.geak4s.domain

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.*
import pme123.geak4s.domain.project.*
import pme123.geak4s.domain.building.*
import pme123.geak4s.domain.envelope.*
import pme123.geak4s.domain.hvac.*
import pme123.geak4s.domain.energy.*
import pme123.geak4s.domain.uwert.*
import pme123.geak4s.domain.area.*
import pme123.geak4s.domain.gis.*
import pme123.geak4s.domain.ebf.*
import pme123.geak4s.domain.energy.*

/**
 * Circe JSON codecs for all domain models using semiauto derivation
 * 
 * Import this object to get all encoders and decoders:
 * ```scala
 * import pme123.geak4s.domain.JsonCodecs.given
 * ```
 */
object JsonCodecs:
  
  // Enums
  given Encoder[Priority] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Priority] = Decoder.decodeString.map(Priority.valueOf)
  
  given Encoder[Condition] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Condition] = Decoder.decodeString.map(Condition.valueOf)
  
  given Encoder[Anrede] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Anrede] = Decoder.decodeString.map(Anrede.valueOf)
  
  // Project domain
  given Encoder[Address] = deriveEncoder[Address]
  given Decoder[Address] = deriveDecoder[Address]
  
  given Encoder[Client] = deriveEncoder[Client]
  given Decoder[Client] = deriveDecoder[Client]
  
  given Encoder[BuildingLocation] = deriveEncoder[BuildingLocation]
  given Decoder[BuildingLocation] = deriveDecoder[BuildingLocation]
  
  given Encoder[BuildingData] = deriveEncoder[BuildingData]
  given Decoder[BuildingData] = deriveDecoder[BuildingData]
  
  given Encoder[Descriptions] = deriveEncoder[Descriptions]
  given Decoder[Descriptions] = deriveDecoder[Descriptions]

  given Encoder[EgidEdidEntry] = deriveEncoder[EgidEdidEntry]
  given Decoder[EgidEdidEntry] = deriveDecoder[EgidEdidEntry]

  given Encoder[EgidEdidGroup] = deriveEncoder[EgidEdidGroup]
  given Decoder[EgidEdidGroup] = deriveDecoder[EgidEdidGroup]
  
  given Encoder[Project] = deriveEncoder[Project]
  given Decoder[Project] = deriveDecoder[Project]
  
  // Building domain
  given Encoder[BuildingUsage] = deriveEncoder[BuildingUsage]
  given Decoder[BuildingUsage] = deriveDecoder[BuildingUsage]
  
  // Envelope domain
  given Encoder[RoofCeiling] = deriveEncoder[RoofCeiling]
  given Decoder[RoofCeiling] = deriveDecoder[RoofCeiling]
  
  given Encoder[Wall] = deriveEncoder[Wall]
  given Decoder[Wall] = deriveDecoder[Wall]
  
  given Encoder[WindowDoor] = deriveEncoder[WindowDoor]
  given Decoder[WindowDoor] = deriveDecoder[WindowDoor]
  
  given Encoder[Floor] = deriveEncoder[Floor]
  given Decoder[Floor] = deriveDecoder[Floor]
  
  given Encoder[ThermalBridge] = deriveEncoder[ThermalBridge]
  given Decoder[ThermalBridge] = deriveDecoder[ThermalBridge]
  
  // HVAC domain
  given Encoder[HeatProducer] = deriveEncoder[HeatProducer]
  given Decoder[HeatProducer] = deriveDecoder[HeatProducer]
  
  given Encoder[HeatStorage] = deriveEncoder[HeatStorage]
  given Decoder[HeatStorage] = deriveDecoder[HeatStorage]
  
  given Encoder[HeatingDistribution] = deriveEncoder[HeatingDistribution]
  given Decoder[HeatingDistribution] = deriveDecoder[HeatingDistribution]
  
  given Encoder[HotWaterDistribution] = deriveEncoder[HotWaterDistribution]
  given Decoder[HotWaterDistribution] = deriveDecoder[HotWaterDistribution]
  
  given Encoder[Ventilation] = deriveEncoder[Ventilation]
  given Decoder[Ventilation] = deriveDecoder[Ventilation]
  
  // Energy domain
  given Encoder[ElectricityProducer] = deriveEncoder[ElectricityProducer]
  given Decoder[ElectricityProducer] = deriveDecoder[ElectricityProducer]
  
  // U-Wert calculations
  given Encoder[ComponentType] = Encoder.encodeString.contramap(_.toString)
  given Decoder[ComponentType] = Decoder.decodeString.map(ComponentType.valueOf)

  given Encoder[HeatTransfer] = deriveEncoder[HeatTransfer]
  given Decoder[HeatTransfer] = deriveDecoder[HeatTransfer]

  given Encoder[BuildingComponent] = deriveEncoder[BuildingComponent]
  given Decoder[BuildingComponent] = deriveDecoder[BuildingComponent]

  given Encoder[MaterialLayer] = deriveEncoder[MaterialLayer]
  given Decoder[MaterialLayer] = deriveDecoder[MaterialLayer]

  given Encoder[UWertTableData] = deriveEncoder[UWertTableData]
  given Decoder[UWertTableData] = deriveDecoder[UWertTableData]

  given Encoder[UWertCalculation] = deriveEncoder[UWertCalculation]
  given Decoder[UWertCalculation] = deriveDecoder[UWertCalculation]

  // Area calculations
  given Encoder[AreaEntry] = deriveEncoder[AreaEntry]
  // Backward-compatible decoder: reads "kuerzel" or falls back to legacy "nr" field
  given Decoder[AreaEntry] = Decoder.instance { c =>
    for
      rawNr      <- c.getOrElse[String]("nr")("")
      rawKuerzel <- c.getOrElse[String]("kuerzel")("")
      kuerzel     = if rawKuerzel.nonEmpty then rawKuerzel
                    else if rawNr.nonEmpty && !rawNr.forall(_.isDigit) then rawNr
                    else ""
      orientation    <- c.getOrElse[String]("orientation")("")
      description    <- c.getOrElse[String]("description")("")
      length         <- c.getOrElse[Double]("length")(0.0)
      width          <- c.getOrElse[Double]("width")(0.0)
      area           <- c.getOrElse[Double]("area")(0.0)
      quantity       <- c.getOrElse[Int]("quantity")(1)
      totalArea      <- c.getOrElse[Double]("totalArea")(area)
      areaNew        <- c.getOrElse[Double]("areaNew")(0.0)
      quantityNew    <- c.getOrElse[Int]("quantityNew")(1)
      totalAreaNew   <- c.getOrElse[Double]("totalAreaNew")(0.0)
      descriptionNew <- c.getOrElse[String]("descriptionNew")("")
    yield AreaEntry(kuerzel, orientation, description, length, width, area, quantity, totalArea, areaNew, quantityNew, totalAreaNew, descriptionNew)
  }

  given Encoder[AreaCalculation] = deriveEncoder[AreaCalculation]
  given Decoder[AreaCalculation] = deriveDecoder[AreaCalculation]

  given Encoder[BuildingEnvelopeArea] = deriveEncoder[BuildingEnvelopeArea]
  given Decoder[BuildingEnvelopeArea] = deriveDecoder[BuildingEnvelopeArea]

  // GIS data
  given Encoder[Status] = deriveEncoder[Status]
  given Decoder[Status] = deriveDecoder[Status]

  given Encoder[Coordinates] = deriveEncoder[Coordinates]
  given Decoder[Coordinates] = deriveDecoder[Coordinates]

  given Encoder[DateOfConstruction] = deriveEncoder[DateOfConstruction]
  given Decoder[DateOfConstruction] = deriveDecoder[DateOfConstruction]

  given Encoder[ThermotechnicalDevice] = deriveEncoder[ThermotechnicalDevice]
  given Decoder[ThermotechnicalDevice] = deriveDecoder[ThermotechnicalDevice]

  given Encoder[Building] = deriveEncoder[Building]
  given Decoder[Building] = deriveDecoder[Building]

  given Encoder[StreetName] = deriveEncoder[StreetName]
  given Decoder[StreetName] = deriveDecoder[StreetName]

  given Encoder[Street] = deriveEncoder[Street]
  given Decoder[Street] = deriveDecoder[Street]

  given Encoder[Locality] = deriveEncoder[Locality]
  given Decoder[Locality] = deriveDecoder[Locality]

  given Encoder[Dwelling] = deriveEncoder[Dwelling]
  given Decoder[Dwelling] = deriveDecoder[Dwelling]

  given Encoder[DwellingItem] = deriveEncoder[DwellingItem]
  given Decoder[DwellingItem] = deriveDecoder[DwellingItem]

  given Encoder[BuildingEntrance] = deriveEncoder[BuildingEntrance]
  given Decoder[BuildingEntrance] = deriveDecoder[BuildingEntrance]

  given Encoder[BuildingEntranceItem] = deriveEncoder[BuildingEntranceItem]
  given Decoder[BuildingEntranceItem] = deriveDecoder[BuildingEntranceItem]

  given Encoder[Municipality] = deriveEncoder[Municipality]
  given Decoder[Municipality] = deriveDecoder[Municipality]

  given Encoder[RealestateIdentificationItem] = deriveEncoder[RealestateIdentificationItem]
  given Decoder[RealestateIdentificationItem] = deriveDecoder[RealestateIdentificationItem]

  given Encoder[BuildingItem] = deriveEncoder[BuildingItem]
  given Decoder[BuildingItem] = deriveDecoder[BuildingItem]

  given Encoder[ResponseMetadata] = deriveEncoder[ResponseMetadata]
  given Decoder[ResponseMetadata] = deriveDecoder[ResponseMetadata]

  given Encoder[MaddResponse] = deriveEncoder[MaddResponse]
  given Decoder[MaddResponse] = deriveDecoder[MaddResponse]

  // EBF plans
  given Encoder[EbfPoint] = deriveEncoder[EbfPoint]
  given Decoder[EbfPoint] = deriveDecoder[EbfPoint]

  given Encoder[EbfPolygon] = deriveEncoder[EbfPolygon]
  given Decoder[EbfPolygon] = deriveDecoder[EbfPolygon]

  given Encoder[EbfMeasurement] = deriveEncoder[EbfMeasurement]
  given Decoder[EbfMeasurement] = deriveDecoder[EbfMeasurement]

  given Encoder[EbfPlan] = deriveEncoder[EbfPlan]
  given Decoder[EbfPlan] = deriveDecoder[EbfPlan]

  given Encoder[EbfPlans] = deriveEncoder[EbfPlans]
  given Decoder[EbfPlans] = deriveDecoder[EbfPlans]

  // Energy consumption data
  given Encoder[FuelType] = Encoder.encodeString.contramap(_.toString)
  given Decoder[FuelType] = Decoder.decodeString.map(FuelType.valueOf)

  given Encoder[ElectricityEntry] = deriveEncoder[ElectricityEntry]
  given Decoder[ElectricityEntry] = deriveDecoder[ElectricityEntry]

  given Encoder[FuelEntry] = deriveEncoder[FuelEntry]
  given Decoder[FuelEntry] = deriveDecoder[FuelEntry]

  given Encoder[WaterEntry] = deriveEncoder[WaterEntry]
  given Decoder[WaterEntry] = deriveDecoder[WaterEntry]

  given Encoder[HeatingPowerSettings] = deriveEncoder[HeatingPowerSettings]
  given Decoder[HeatingPowerSettings] = deriveDecoder[HeatingPowerSettings]

  given Encoder[EwsSettings] = deriveEncoder[EwsSettings]
  given Decoder[EwsSettings] = deriveDecoder[EwsSettings]

  given Encoder[EnergyConsumptionData] = deriveEncoder[EnergyConsumptionData]
  given Decoder[EnergyConsumptionData] = deriveDecoder[EnergyConsumptionData]

  // Main project
  given Encoder[GeakProject] = deriveEncoder[GeakProject]
  given Decoder[GeakProject] = deriveDecoder[GeakProject]

end JsonCodecs

