package pme123.geak4s

import pme123.geak4s.domain.project.*
import pme123.geak4s.domain.building.*
import pme123.geak4s.domain.envelope.*
import pme123.geak4s.domain.hvac.*
import pme123.geak4s.domain.energy.*
import pme123.geak4s.domain.uwert.UWertCalculation
import pme123.geak4s.domain.area.BuildingEnvelopeArea
import pme123.geak4s.domain.ebf.EbfPlans
import pme123.geak4s.domain.energy.EnergyConsumptionData
import scala.scalajs.js

/** GEAK Domain Model Package
  *
  * This package contains all domain objects for the GEAK (Gebäudeenergieausweis der Kantone)
  * system.
  *
  * Package Structure:
  *   - project: Project information (Project, Client, Expert, BuildingLocation, BuildingData,
  *     EgidEdidGroup)
  *   - building: Building usage (BuildingUsage)
  *   - envelope: Building envelope (RoofCeiling, Wall, WindowDoor, Floor, ThermalBridge)
  *   - hvac: HVAC systems (HeatProducer, HeatStorage, HeatingDistribution, HotWaterDistribution,
  *     Ventilation)
  *   - energy: Energy production (ElectricityProducer)
  *
  * Each domain object has:
  *   - Case class definition with all properties
  *   - Companion object with example data
  *
  * JavaScript interop helpers are provided in JSHelpers for reading Excel data.
  */
package object domain:

  /** Complete GEAK project with all components */
  case class GeakProject(
      geakId: Option[Int] = None,
      project: Project,
      buildingUsages: List[BuildingUsage],
      roofsCeilings: List[RoofCeiling],
      walls: List[Wall],
      windowsDoors: List[WindowDoor],
      floors: List[Floor],
      thermalBridges: List[ThermalBridge],
      heatProducers: List[HeatProducer],
      heatStorages: List[HeatStorage],
      heatingDistributions: List[HeatingDistribution],
      hotWaterDistributions: List[HotWaterDistribution],
      ventilations: List[Ventilation],
      electricityProducers: List[ElectricityProducer],
      uwertCalculations: List[UWertCalculation] = List.empty,
      areaCalculations: Option[BuildingEnvelopeArea] = None,
      gisData: Option[gis.MaddResponse] = None,
      ebfPlans: Option[EbfPlans] = None,
      energyConsumption: Option[EnergyConsumptionData] = None,
      wordFormData: Option[WordFormData] = None,
      gisXmlContent: Option[String] = None
  )

  object GeakProject:
    /** Complete example project with all components */
    lazy val example: GeakProject = GeakProject(
      project = Project.example,
      buildingUsages = List(BuildingUsage.example),
      roofsCeilings = List(RoofCeiling.example, RoofCeiling.exampleFlat),
      walls = List(Wall.example, Wall.exampleRenovated),
      windowsDoors = List(WindowDoor.example, WindowDoor.exampleNew, WindowDoor.exampleDoor),
      floors = List(Floor.example, Floor.exampleRenovated),
      thermalBridges = List(ThermalBridge.example, ThermalBridge.examplePoint),
      heatProducers =
        List(HeatProducer.example, HeatProducer.exampleHeatPump, HeatProducer.exampleSolar),
      heatStorages = List(HeatStorage.example, HeatStorage.exampleCombi),
      heatingDistributions = List(HeatingDistribution.example, HeatingDistribution.exampleModern),
      hotWaterDistributions =
        List(HotWaterDistribution.example, HotWaterDistribution.exampleDecentralized),
      ventilations = List(Ventilation.example, Ventilation.exampleSimple),
      electricityProducers = List(
        ElectricityProducer.examplePV,
        ElectricityProducer.exampleCHP,
        ElectricityProducer.examplePVPlanned
      ),
      uwertCalculations = List.empty,
      areaCalculations = None,
      gisData = None,
      geakId = None
    )

    /** Empty project template for new projects */
    lazy val empty: GeakProject =
      val now     = new js.Date()
      val dateStr =
        f"${now.getFullYear().toInt}%04d-${(now.getMonth() + 1).toInt}%02d-${now.getDate().toInt}%02d"
      GeakProject(
        project = Project(
          projectName = "",
          client = Client(Anrede.Herr, None, None, Address.empty, None, None, None, None),
          buildingLocation = BuildingLocation(Address.empty, None, None, None),
          buildingData =
            BuildingData(None, None, None, None, None, None, None, None, None, None, None),
          descriptions = Descriptions(None, None, None),
          egidEdidGroup = EgidEdidGroup(List.empty),
          templateVersion = "R6.8",
          generatedDate = dateStr
        ),
        buildingUsages = List.empty,
        roofsCeilings = List.empty,
        walls = List.empty,
        windowsDoors = List.empty,
        floors = List.empty,
        thermalBridges = List.empty,
        heatProducers = List.empty,
        heatStorages = List.empty,
        heatingDistributions = List.empty,
        hotWaterDistributions = List.empty,
        ventilations = List.empty,
        electricityProducers = List.empty,
        uwertCalculations = List.empty,
        areaCalculations = None,
        gisData = None,
        geakId = None
      )
    end empty
  end GeakProject

  /** Priority levels for renovations */
  enum Priority:
    case High   // Hohe Priorität: Umsetzung in < 2 Jahren
    case Medium // Mittlere Priorität: Umsetzung in 2-5 Jahren
    case Low    // Geringe Priorität: Umsetzung in 5-10 Jahren
    case None   // Keine Priorität
  end Priority

  object Priority:
    def fromString(s: String): Priority = s match
    case s if s.contains("Hohe")     => High
    case s if s.contains("Mittlere") => Medium
    case s if s.contains("Geringe")  => Low
    case _                           => None
  end Priority

  /** General condition of building components */
  enum Condition:
    case New       // neuwertig
    case Good      // gebraucht
    case Worn      // abgenutzt
    case EndOfLife // Lebensdauer erreicht
    case Unknown
  end Condition

  object Condition:
    def fromString(s: String): Condition = s.toLowerCase match
    case s if s.contains("neuwertig")   => New
    case s if s.contains("gebraucht")   => Good
    case s if s.contains("abgenutzt")   => Worn
    case s if s.contains("lebensdauer") => EndOfLife
    case _                              => Unknown
  end Condition

  /** Orientation for building components */
  enum Orientation:
    case N, NO, O, SO, S, SW, W, NW
    case Horizontal
    case Unknown

  object Orientation:
    def fromString(s: String): Orientation = s.toUpperCase match
    case "N"  => N
    case "NO" => NO
    case "O"  => O
    case "SO" => SO
    case "S"  => S
    case "SW" => SW
    case "W"  => W
    case "NW" => NW
    case _    => Unknown
  end Orientation

end domain
