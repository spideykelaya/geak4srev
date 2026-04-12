package pme123.geak4s.services

import org.scalajs.dom
import pme123.geak4s.domain.*
import pme123.geak4s.domain.project.*
import pme123.geak4s.domain.building.*
import pme123.geak4s.domain.envelope.*
import pme123.geak4s.domain.area.*
import pme123.geak4s.domain.uwert.*
import scala.scalajs.js
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Service for exporting GEAK project data to SIAImportPlus XML format
 * Based on SIAImportPlus.xsd schema version 6.5.0
 */
object XmlExportService:

  private val XML_VERSION = "6.5.0"
  private val NAMESPACE = "http://www.geak.ch/Import/v4/"

  /** Export GeakProject to XML string according to SIAImportPlus.xsd */
  def exportToXml(project: GeakProject): String =
    val xml = new StringBuilder()
    
    // XML declaration and root element
    xml.append("""<?xml version="1.0" encoding="utf-8"?>""")
    xml.append("\n")
    xml.append(s"""<SIARecord xmlns="$NAMESPACE">""")
    xml.append("\n")
    
    // Version
    xml.append(s"  <Version>$XML_VERSION</Version>\n")
    
    // General section
    xml.append(generateGeneral(project))
    
    // CurrentState section
    xml.append(generateCurrentState(project))
    
    // Close root element
    xml.append("</SIARecord>")
    
    xml.toString()
  end exportToXml

  /** Generate General section (owner, building data, building usages) */
  private def generateGeneral(project: GeakProject): String =
    val xml = new StringBuilder()
    val proj = project.project
    
    xml.append(s"""  <General ProjectName="${escapeXml(proj.projectName)}">""")
    xml.append("\n")
    
    // OwnerAddress
    xml.append(generateOwnerAddress(proj.client))
    
    // BuildingData
    xml.append(generateBuildingData(proj))
    
    // BuildingUsages always required by XSD — fall back to EnergyReferenceArea for area
    val fallbackArea = proj.buildingData.energyReferenceArea.map(_.toInt).getOrElse(0)
    xml.append(generateBuildingUsages(project.buildingUsages, fallbackArea))
    
    xml.append("  </General>\n")
    
    xml.toString()
  end generateGeneral

  /** Generate OwnerAddress section */
  private def generateOwnerAddress(client: Client): String =
    val xml = new StringBuilder()
    
    xml.append("    <OwnerAddress>\n")
    xml.append(s"      <Title>${mapAnredeToTitle(client.salutation)}</Title>\n")
    xml.append(s"      <Name1>${escapeXml(client.name1.getOrElse(""))}</Name1>\n")
    
    if client.name2.isDefined then
      xml.append(s"      <Name2>${escapeXml(client.name2.get)}</Name2>\n")
    
    xml.append(s"      <Address>${escapeXml(formatAddress(client.address))}</Address>\n")
    
    if client.poBox.isDefined then
      xml.append(s"      <POBox>${escapeXml(client.poBox.get)}</POBox>\n")
    
    val zipCode = client.address.zipCode.flatMap(_.toIntOption).getOrElse(0)
    xml.append(s"      <Zip>$zipCode</Zip>\n")
    xml.append(s"      <City>${escapeXml(client.address.city.getOrElse(""))}</City>\n")
    xml.append(s"      <Country>${escapeXml(client.address.country.getOrElse("Schweiz"))}</Country>\n")
    
    if client.email.isDefined then
      xml.append(s"      <EMail>${escapeXml(client.email.get)}</EMail>\n")
    
    xml.append(s"      <Phone1>${escapeXml(client.phone1.getOrElse(""))}</Phone1>\n")
    
    if client.phone2.isDefined then
      xml.append(s"      <Phone2>${escapeXml(client.phone2.get)}</Phone2>\n")
    
    xml.append("    </OwnerAddress>\n")
    
    xml.toString()
  end generateOwnerAddress

  /** Generate BuildingData section */
  private def generateBuildingData(proj: Project): String =
    val xml = new StringBuilder()
    val loc = proj.buildingLocation
    val data = proj.buildingData
    
    xml.append("    <BuildingData>\n")
    
    val zipCode = loc.address.zipCode.flatMap(_.toIntOption).getOrElse(0)
    xml.append(s"      <Zip>$zipCode</Zip>\n")
    xml.append(s"      <City>${escapeXml(loc.address.city.getOrElse(""))}</City>\n")
    
    if loc.municipality.isDefined then
      xml.append(s"      <Community>${escapeXml(loc.municipality.get)}</Community>\n")
    
    xml.append(s"      <StreetName>${escapeXml(loc.address.street.getOrElse(""))}</StreetName>\n")
    xml.append(s"      <HouseNumber>${escapeXml(loc.address.houseNumber.getOrElse(""))}</HouseNumber>\n")
    
    if loc.buildingName.isDefined then
      xml.append(s"      <BuildingName>${escapeXml(loc.buildingName.get)}</BuildingName>\n")
    
    if data.weatherStation.isDefined then
      xml.append(s"      <WeatherStation>${escapeXml(data.weatherStation.get)}</WeatherStation>\n")
    
    xml.append(s"      <YearOfConstruction>${data.constructionYear.getOrElse(1900)}</YearOfConstruction>\n")
    
    if data.lastRenovationYear.isDefined then
      xml.append(s"      <YearOfTotalRenovation>${data.lastRenovationYear.get}</YearOfTotalRenovation>\n")
    
    if loc.parcelNumber.isDefined then
      xml.append(s"      <PlotNumber>${escapeXml(loc.parcelNumber.get)}</PlotNumber>\n")
    
    xml.append(s"      <EnergyReferenceArea>${data.energyReferenceArea.map(_.toInt).getOrElse(0)}</EnergyReferenceArea>\n")
    xml.append(s"      <AverageRoomHeight>${data.clearRoomHeight.getOrElse(2.5)}</AverageRoomHeight>\n")
    xml.append(s"      <FullFloorsCount>${data.numberOfFloors.getOrElse(1)}</FullFloorsCount>\n")
    xml.append(s"      <BuildingDepth>${data.buildingWidth.getOrElse(10.0)}</BuildingDepth>\n")
    xml.append(s"      <GroundPlanType>${mapGroundPlanType(data.groundPlanType)}</GroundPlanType>\n")
    xml.append(s"      <ConstructionType>${mapConstructionType(data.constructionType)}</ConstructionType>\n")
    
    if data.weatherStationValues.isDefined then
      xml.append(s"      <BestKnownWeatherStation>${escapeXml(data.weatherStationValues.get)}</BestKnownWeatherStation>\n")
    
    if data.altitude.isDefined then
      xml.append(s"      <Altitude>${data.altitude.get.toInt}</Altitude>\n")
    
    xml.append("    </BuildingData>\n")
    
    xml.toString()
  end generateBuildingData

  /** Generate BuildingUsages section — always required by XSD */
  private def generateBuildingUsages(usages: List[building.BuildingUsage], fallbackArea: Int = 0): String =
    val xml      = new StringBuilder()
    val area     = usages.headOption.map(_.area.toInt).filter(_ > 0).getOrElse(fallbackArea)
    val perPerson = if area > 0 then area else 1
    val elem     = usages.headOption.map(u => mapUsageElementName(u.usageType)).getOrElse("SingleHomeBuildingUsage")
    xml.append("    <BuildingUsages>\n")
    xml.append(s"      <$elem>\n")
    xml.append(s"        <Area>$area</Area>\n")
    xml.append("        <RoomTemperature>20</RoomTemperature>\n")
    xml.append(s"        <PerPersonArea>$perPerson</PerPersonArea>\n")
    xml.append("        <WarmWaterNeed>8</WarmWaterNeed>\n")
    xml.append("        <ResidentsCount>0</ResidentsCount>\n")
    xml.append("        <RoomCount>0</RoomCount>\n")
    xml.append("        <ApartmentsCount1Rooms>0</ApartmentsCount1Rooms>\n")
    xml.append("        <ApartmentsCount2Rooms>0</ApartmentsCount2Rooms>\n")
    xml.append("        <ApartmentsCount3Rooms>0</ApartmentsCount3Rooms>\n")
    xml.append("        <ApartmentsCount4Rooms>0</ApartmentsCount4Rooms>\n")
    xml.append("        <ApartmentsCount5Rooms>0</ApartmentsCount5Rooms>\n")
    xml.append("        <ApartmentsCount6Rooms>0</ApartmentsCount6Rooms>\n")
    xml.append("        <ApartmentsCountOver6Rooms>0</ApartmentsCountOver6Rooms>\n")
    xml.append(s"      </$elem>\n")
    xml.append("    </BuildingUsages>\n")
    xml.toString()
  end generateBuildingUsages

  private def mapUsageElementName(usageType: String): String =
    val lower = usageType.toLowerCase
    if lower.contains("mehrfamilienhaus") || lower.contains("mfh") then "MultipleHomeBuildingUsage"
    else "SingleHomeBuildingUsage"

  /** Generate CurrentState section (building envelope).
    * Primary source: drawn areas (areaCalculations) + U-Wert calculations.
    * Fallback: manually entered envelope lists.
    */
  private def generateCurrentState(project: GeakProject): String =
    val xml = new StringBuilder()

    xml.append("  <CurrentState>\n")

    project.project.descriptions.buildingDescription.foreach { d =>
      xml.append(s"    <Remarks>${escapeXml(d)}</Remarks>\n")
    }

    xml.append("    <BuildingEnvelope>\n")

    project.areaCalculations match
      case Some(areaCalcs) =>
        val uwerts = project.uwertCalculations
        generateRoofsAndCeilingsFromArea(areaCalcs, uwerts).foreach(xml.append)
        generateWallsFromArea(areaCalcs, uwerts).foreach(xml.append)
        generateWindowsAndDoorsFromArea(areaCalcs, uwerts).foreach(xml.append)
        generateFloorsFromArea(areaCalcs, uwerts).foreach(xml.append)
        if project.thermalBridges.nonEmpty then
          xml.append(generateThermalBridges(project.thermalBridges))
      case None =>
        if project.roofsCeilings.nonEmpty then
          xml.append(generateRoofsAndCeilings(project.roofsCeilings))
        if project.walls.nonEmpty then
          xml.append(generateWalls(project.walls))
        if project.windowsDoors.nonEmpty then
          xml.append(generateWindowsAndDoors(project.windowsDoors))
        if project.floors.nonEmpty then
          xml.append(generateFloors(project.floors))
        if project.thermalBridges.nonEmpty then
          xml.append(generateThermalBridges(project.thermalBridges))

    xml.append("    </BuildingEnvelope>\n")
    xml.append("  </CurrentState>\n")
    xml.toString()
  end generateCurrentState

  // ── Area-based generators (from drawn polygons + U-Wert calculations) ─────────

  private def uwertFor(uwerts: List[UWertCalculation], ct: ComponentType): Option[UWertTableData] =
    uwerts.find(_.componentType == ct).map(_.istCalculation)

  private def fmtU(u: UWertTableData): String =
    if u.rTotal != 0 then
      BigDecimal(u.uValueWithoutB).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble.toString
    else "1.0"

  private def entriesFor(areaCalcs: BuildingEnvelopeArea, ct: ComponentType): List[AreaEntry] =
    areaCalcs.get(ct).map(_.entries).getOrElse(List.empty).filter(e => e.area > 0 || e.totalArea > 0)

  private def appendAreaBase(xml: StringBuilder, entry: AreaEntry, uData: Option[UWertTableData], bFactor: Boolean = true): Unit =
    if entry.kuerzel.nonEmpty then xml.append(s"            <ExpertCode>${escapeXml(entry.kuerzel.take(5))}</ExpertCode>\n")
    if entry.description.nonEmpty then xml.append(s"            <ExpertDescription>${escapeXml(entry.description.take(250))}</ExpertDescription>\n")
    xml.append(s"            <Quantity>${entry.quantity}</Quantity>\n")
    xml.append("            <InvestmentCosts>0</InvestmentCosts>\n")
    xml.append("            <CostsCalculationBase>PerSquareMeter</CostsCalculationBase>\n")
    xml.append("            <LifeSpan>50</LifeSpan>\n")
    xml.append("            <ValuePreservation>Keine Arbeiten</ValuePreservation>\n")
    xml.append(s"            <Area>${entry.area}</Area>\n")
    val uVal = uData.map(fmtU).getOrElse("1.0")
    val bVal = uData.map(_.bFactor.toString).getOrElse("1")
    xml.append(s"            <UValue>$uVal</UValue>\n")
    if bFactor then xml.append(s"            <BFactor>$bVal</BFactor>\n")

  private def appendOrientation(xml: StringBuilder, orientation: String): Unit =
    val v = if orientation.nonEmpty then mapOrientation(Some(orientation)) else "South"
    xml.append(s"            <Orientation>$v</Orientation>\n")

  private def generateRoofsAndCeilingsFromArea(areaCalcs: BuildingEnvelopeArea, uwerts: List[UWertCalculation]): Option[String] =
    val pitched  = entriesFor(areaCalcs, ComponentType.PitchedRoof)
    val flat     = entriesFor(areaCalcs, ComponentType.FlatRoof)
    val ceilings = entriesFor(areaCalcs, ComponentType.AtticFloor)
    if pitched.isEmpty && flat.isEmpty && ceilings.isEmpty then return None

    val xml      = new StringBuilder()
    val pitchedU = uwertFor(uwerts, ComponentType.PitchedRoof)
    val flatU    = uwertFor(uwerts, ComponentType.FlatRoof)
    val ceilU    = uwertFor(uwerts, ComponentType.AtticFloor)

    xml.append("      <RoofsAndCeilings>\n")
    xml.append("        <Condition>Used</Condition>\n")
    xml.append("        <Description>-</Description>\n")
    xml.append("        <Renewals>-</Renewals>\n")
    if pitched.nonEmpty || flat.nonEmpty then
      val genRoof = if flat.nonEmpty && pitched.isEmpty then "FlatRoof" else "PitchedRoofPartiallyHeated"
      xml.append(s"        <GeneralRoof>$genRoof</GeneralRoof>\n")
    if ceilings.nonEmpty then
      xml.append("        <ConditionOtherCeilings>Used</ConditionOtherCeilings>\n")
      xml.append("        <DescriptionOtherCeilings>-</DescriptionOtherCeilings>\n")
      xml.append("        <RenewalsOtherCeilings>-</RenewalsOtherCeilings>\n")
    xml.append("        <Items>\n")

    pitched.foreach { e =>
      xml.append("          <Roof>\n")
      appendAreaBase(xml, e, pitchedU)
      appendOrientation(xml, e.orientation)
      xml.append("            <Type>PitchedRoof</Type>\n")
      xml.append("          </Roof>\n")
    }
    flat.foreach { e =>
      xml.append("          <Roof>\n")
      appendAreaBase(xml, e, flatU)
      appendOrientation(xml, e.orientation)
      xml.append("            <Type>FlatRoof</Type>\n")
      xml.append("          </Roof>\n")
    }
    ceilings.foreach { e =>
      xml.append("          <Ceiling>\n")
      appendAreaBase(xml, e, ceilU)
      xml.append("            <Type>CeilingUnheated</Type>\n")
      xml.append("          </Ceiling>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </RoofsAndCeilings>\n")
    Some(xml.toString())

  private def generateWallsFromArea(areaCalcs: BuildingEnvelopeArea, uwerts: List[UWertCalculation]): Option[String] =
    val extWalls   = entriesFor(areaCalcs, ComponentType.ExteriorWall) ++ entriesFor(areaCalcs, ComponentType.BasementWallToOutside)
    val earthWalls = entriesFor(areaCalcs, ComponentType.BasementWallToEarth)
    val unheaWalls = entriesFor(areaCalcs, ComponentType.BasementWallToUnheated)
    if extWalls.isEmpty && earthWalls.isEmpty && unheaWalls.isEmpty then return None

    val xml    = new StringBuilder()
    val extU   = uwertFor(uwerts, ComponentType.ExteriorWall)
    val earthU = uwertFor(uwerts, ComponentType.BasementWallToEarth)
    val unheaU = uwertFor(uwerts, ComponentType.BasementWallToUnheated)

    xml.append("      <Walls>\n")
    xml.append("        <Condition>Used</Condition>\n")
    xml.append("        <Description>-</Description>\n")
    xml.append("        <Renewals>-</Renewals>\n")
    xml.append("        <FacadeStructure>NormallyStructured</FacadeStructure>\n")
    if earthWalls.nonEmpty || unheaWalls.nonEmpty then
      xml.append("        <ConditionOtherWalls>Used</ConditionOtherWalls>\n")
      xml.append("        <DescriptionOtherWalls>-</DescriptionOtherWalls>\n")
      xml.append("        <RenewalsOtherWalls>-</RenewalsOtherWalls>\n")
    xml.append("        <Items>\n")

    extWalls.foreach { e =>
      xml.append("          <WallExterior>\n")
      appendAreaBase(xml, e, extU)
      appendOrientation(xml, e.orientation)
      xml.append("            <Type>Facade</Type>\n")
      xml.append("          </WallExterior>\n")
    }
    earthWalls.foreach { e =>
      xml.append("          <WallOther>\n")
      appendAreaBase(xml, e, earthU)
      appendOrientation(xml, e.orientation)
      xml.append("            <Type>TowardsEarthUpToTwoMeters</Type>\n")
      xml.append("          </WallOther>\n")
    }
    unheaWalls.foreach { e =>
      xml.append("          <WallOther>\n")
      appendAreaBase(xml, e, unheaU)
      appendOrientation(xml, e.orientation)
      xml.append("            <Type>TowardsUnheatedNoIsolation</Type>\n")
      xml.append("          </WallOther>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </Walls>\n")
    Some(xml.toString())

  private def generateWindowsAndDoorsFromArea(areaCalcs: BuildingEnvelopeArea, uwerts: List[UWertCalculation]): Option[String] =
    val windows = entriesFor(areaCalcs, ComponentType.Window)
    val doors   = entriesFor(areaCalcs, ComponentType.Door)
    if windows.isEmpty && doors.isEmpty then return None

    val xml  = new StringBuilder()
    val winU = uwertFor(uwerts, ComponentType.Window)
    val dooU = uwertFor(uwerts, ComponentType.Door)

    xml.append("      <WindowsAndDoors>\n")
    xml.append("        <Condition>Used</Condition>\n")
    xml.append("        <Description>-</Description>\n")
    xml.append("        <Renewals>-</Renewals>\n")
    xml.append("        <AlwaysSubtractOpeningAreas>true</AlwaysSubtractOpeningAreas>\n")
    xml.append("        <Items>\n")

    def winBlock(tag: String, e: AreaEntry, u: Option[UWertTableData]): Unit =
      xml.append(s"          <$tag>\n")
      if e.kuerzel.nonEmpty then xml.append(s"            <ExpertCode>${escapeXml(e.kuerzel.take(5))}</ExpertCode>\n")
      if e.description.nonEmpty then xml.append(s"            <ExpertDescription>${escapeXml(e.description.take(250))}</ExpertDescription>\n")
      xml.append(s"            <Quantity>${e.quantity}</Quantity>\n")
      xml.append("            <InvestmentCosts>0</InvestmentCosts>\n")
      xml.append("            <CostsCalculationBase>PerSquareMeter</CostsCalculationBase>\n")
      xml.append("            <LifeSpan>40</LifeSpan>\n")
      xml.append("            <ValuePreservation>Keine Arbeiten</ValuePreservation>\n")
      xml.append(s"            <Area>${e.area}</Area>\n")
      xml.append(s"            <UValue>${u.map(fmtU).getOrElse("1.0")}</UValue>\n")
      if tag == "Door" then
        xml.append("            <GValue>0</GValue>\n")
        xml.append("            <FF>0</FF>\n")
      else
        xml.append("            <GValue>0.7</GValue>\n")
        xml.append("            <FF>0.7</FF>\n")
      xml.append("            <FS>1</FS>\n")
      appendOrientation(xml, e.orientation)
      xml.append(s"          </$tag>\n")

    windows.foreach(e => winBlock("Window", e, winU))
    doors.foreach(e   => winBlock("Door",   e, dooU))

    xml.append("        </Items>\n")
    xml.append("      </WindowsAndDoors>\n")
    Some(xml.toString())

  private def generateFloorsFromArea(areaCalcs: BuildingEnvelopeArea, uwerts: List[UWertCalculation]): Option[String] =
    val exterior   = entriesFor(areaCalcs, ComponentType.FloorToOutside)
    val baseFloors = entriesFor(areaCalcs, ComponentType.BasementFloor)
    val baseCeils  = entriesFor(areaCalcs, ComponentType.BasementCeiling)
    if exterior.isEmpty && baseFloors.isEmpty && baseCeils.isEmpty then return None

    val xml   = new StringBuilder()
    val extU  = uwertFor(uwerts, ComponentType.FloorToOutside)
    val baseU = uwertFor(uwerts, ComponentType.BasementFloor)
    val ceilU = uwertFor(uwerts, ComponentType.BasementCeiling)

    xml.append("      <Floors>\n")
    xml.append("        <Condition>Used</Condition>\n")
    xml.append("        <Description>-</Description>\n")
    xml.append("        <Renewals>-</Renewals>\n")
    if baseFloors.nonEmpty || baseCeils.nonEmpty then
      xml.append("        <ConditionOtherFloors>Used</ConditionOtherFloors>\n")
      xml.append("        <DescriptionOtherFloors>-</DescriptionOtherFloors>\n")
      xml.append("        <RenewalsOtherFloors>-</RenewalsOtherFloors>\n")
    xml.append("        <Items>\n")

    exterior.foreach { e =>
      xml.append("          <FloorExterior>\n")
      appendAreaBase(xml, e, extU)
      xml.append("          </FloorExterior>\n")
    }
    baseFloors.foreach { e =>
      xml.append("          <FloorOther>\n")
      appendAreaBase(xml, e, baseU)
      xml.append("            <Type>TowardsEarthUpToTwoMeters</Type>\n")
      xml.append("          </FloorOther>\n")
    }
    baseCeils.foreach { e =>
      xml.append("          <FloorOther>\n")
      appendAreaBase(xml, e, ceilU)
      xml.append("            <Type>TowardsUnheatedNoIsolation</Type>\n")
      xml.append("          </FloorOther>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </Floors>\n")
    Some(xml.toString())

  /** Generate RoofsAndCeilings section */
  private def generateRoofsAndCeilings(items: List[RoofCeiling]): String =
    val xml = new StringBuilder()

    xml.append("      <RoofsAndCeilings>\n")
    xml.append("        <Condition>Used</Condition>\n") // Default condition
    xml.append("        <Items>\n")

    items.foreach { roof =>
      val isRoof = roof.roofType.toLowerCase.contains("dach") && !roof.roofType.toLowerCase.contains("decke")

      if isRoof then
        xml.append("          <Roof>\n")

        if roof.code.nonEmpty then
          xml.append(s"            <ExpertCode>${escapeXml(roof.code.take(5))}</ExpertCode>\n")

        if roof.description.nonEmpty then
          xml.append(s"            <ExpertDescription>${escapeXml(roof.description.take(250))}</ExpertDescription>\n")

        xml.append(s"            <Quantity>${roof.quantity}</Quantity>\n")

        // RenovationYear must come before Area (part of base class xsBuildingEnvelopeEntity)
        if roof.renovationYear.isDefined then
          xml.append(s"            <RenovationYear>${roof.renovationYear.get}</RenovationYear>\n")

        xml.append(s"            <Area>${roof.area}</Area>\n")
        xml.append(s"            <UValue>${roof.uValue}</UValue>\n")
        xml.append(s"            <BFactor>${roof.bFactor}</BFactor>\n")

        val orientation = mapOrientation(roof.orientation)
        xml.append(s"            <Orientation>$orientation</Orientation>\n")

        val roofType = if roof.roofType.toLowerCase.contains("flach") then "FlatRoof" else "PitchedRoof"
        xml.append(s"            <Type>$roofType</Type>\n")

        if roof.floorHeating then
          xml.append(s"            <HeatingElement>true</HeatingElement>\n")

        xml.append("          </Roof>\n")
      else
        // It's a ceiling
        xml.append("          <Ceiling>\n")

        if roof.code.nonEmpty then
          xml.append(s"            <ExpertCode>${escapeXml(roof.code.take(5))}</ExpertCode>\n")

        if roof.description.nonEmpty then
          xml.append(s"            <ExpertDescription>${escapeXml(roof.description.take(250))}</ExpertDescription>\n")

        xml.append(s"            <Quantity>${roof.quantity}</Quantity>\n")

        // RenovationYear must come before Area (part of base class xsBuildingEnvelopeEntity)
        if roof.renovationYear.isDefined then
          xml.append(s"            <RenovationYear>${roof.renovationYear.get}</RenovationYear>\n")

        xml.append(s"            <Area>${roof.area}</Area>\n")
        xml.append(s"            <UValue>${roof.uValue}</UValue>\n")
        xml.append(s"            <BFactor>${roof.bFactor}</BFactor>\n")

        val ceilingType = "TowardsUnheated" // Default
        xml.append(s"            <Type>$ceilingType</Type>\n")

        if roof.neighborRoomTemp.isDefined then
          xml.append(s"            <TemperatureNextRoom>${roof.neighborRoomTemp.get}</TemperatureNextRoom>\n")

        if roof.floorHeating then
          xml.append(s"            <HeatingElement>true</HeatingElement>\n")

        xml.append("          </Ceiling>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </RoofsAndCeilings>\n")

    xml.toString()
  end generateRoofsAndCeilings

  /** Generate Walls section */
  private def generateWalls(items: List[Wall]): String =
    val xml = new StringBuilder()

    xml.append("      <Walls>\n")
    xml.append("        <Condition>Used</Condition>\n") // Default condition
    xml.append("        <FacadeStructure>NormallyStructured</FacadeStructure>\n") // Default
    xml.append("        <Items>\n")

    items.foreach { wall =>
      val isExterior = wall.wallType.toLowerCase.contains("aussen")

      if isExterior then
        xml.append("          <WallExterior>\n")

        if wall.code.nonEmpty then
          xml.append(s"            <ExpertCode>${escapeXml(wall.code.take(5))}</ExpertCode>\n")

        if wall.description.nonEmpty then
          xml.append(s"            <ExpertDescription>${escapeXml(wall.description.take(250))}</ExpertDescription>\n")

        xml.append(s"            <Quantity>${wall.quantity}</Quantity>\n")

        // RenovationYear must come before Area (part of base class xsBuildingEnvelopeEntity)
        if wall.renovationYear.isDefined then
          xml.append(s"            <RenovationYear>${wall.renovationYear.get}</RenovationYear>\n")

        xml.append(s"            <Area>${wall.area}</Area>\n")
        xml.append(s"            <UValue>${wall.uValue}</UValue>\n")
        xml.append(s"            <BFactor>${wall.bFactor}</BFactor>\n")

        val orientation = mapOrientation(wall.orientation)
        xml.append(s"            <Orientation>$orientation</Orientation>\n")

        xml.append(s"            <Type>Facade</Type>\n")

        if wall.wallHeating then
          xml.append(s"            <HeatingElement>true</HeatingElement>\n")

        xml.append("          </WallExterior>\n")
      else
        // Interior or other wall
        xml.append("          <WallOther>\n")

        if wall.code.nonEmpty then
          xml.append(s"            <ExpertCode>${escapeXml(wall.code.take(5))}</ExpertCode>\n")

        if wall.description.nonEmpty then
          xml.append(s"            <ExpertDescription>${escapeXml(wall.description.take(250))}</ExpertDescription>\n")

        xml.append(s"            <Quantity>${wall.quantity}</Quantity>\n")

        // RenovationYear must come before Area (part of base class xsBuildingEnvelopeEntity)
        if wall.renovationYear.isDefined then
          xml.append(s"            <RenovationYear>${wall.renovationYear.get}</RenovationYear>\n")

        xml.append(s"            <Area>${wall.area}</Area>\n")
        xml.append(s"            <UValue>${wall.uValue}</UValue>\n")
        xml.append(s"            <BFactor>${wall.bFactor}</BFactor>\n")

        if wall.orientation.isDefined then
          val orientation = mapOrientation(wall.orientation)
          xml.append(s"            <Orientation>$orientation</Orientation>\n")

        val wallType = "TowardsUnheatedNoIsolation" // Default
        xml.append(s"            <Type>$wallType</Type>\n")

        if wall.neighborRoomTemp.isDefined then
          xml.append(s"            <TemperatureNextRoom>${wall.neighborRoomTemp.get}</TemperatureNextRoom>\n")

        if wall.wallHeating then
          xml.append(s"            <HeatingElement>true</HeatingElement>\n")

        xml.append("          </WallOther>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </Walls>\n")

    xml.toString()
  end generateWalls

  /** Generate WindowsAndDoors section */
  private def generateWindowsAndDoors(items: List[WindowDoor]): String =
    val xml = new StringBuilder()

    xml.append("      <WindowsAndDoors>\n")
    xml.append("        <Condition>Used</Condition>\n") // Default condition
    xml.append("        <Items>\n")

    items.foreach { item =>
      val isDoor = item.windowType.toLowerCase.contains("tür") || item.windowType.toLowerCase.contains("door")
      val elementType = if isDoor then "Door" else "Window"

      xml.append(s"          <$elementType>\n")

      if item.code.nonEmpty then
        xml.append(s"            <ExpertCode>${escapeXml(item.code.take(5))}</ExpertCode>\n")

      if item.description.nonEmpty then
        xml.append(s"            <ExpertDescription>${escapeXml(item.description.take(250))}</ExpertDescription>\n")

      xml.append(s"            <Quantity>${item.quantity}</Quantity>\n")

      // RenovationYear must come before Area (part of base class xsBuildingEnvelopeEntity)
      if item.renovationYear.isDefined then
        xml.append(s"            <RenovationYear>${item.renovationYear.get}</RenovationYear>\n")

      xml.append(s"            <Area>${item.area}</Area>\n")
      xml.append(s"            <UValue>${item.uValue}</UValue>\n")
      xml.append(s"            <GValue>${item.gValue}</GValue>\n")
      xml.append(s"            <FF>${item.glassRatio}</FF>\n")
      xml.append(s"            <FS>${item.shading}</FS>\n")
      xml.append(s"            <BFactor>${item.bFactor}</BFactor>\n")

      val orientation = mapOrientation(item.orientation)
      xml.append(s"            <Orientation>$orientation</Orientation>\n")

      if item.installedIn.isDefined then
        xml.append(s"            <ExpertCodeParent>${escapeXml(item.installedIn.get.take(5))}</ExpertCodeParent>\n")

      xml.append(s"          </$elementType>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </WindowsAndDoors>\n")

    xml.toString()
  end generateWindowsAndDoors

  /** Generate Floors section */
  private def generateFloors(items: List[Floor]): String =
    val xml = new StringBuilder()

    xml.append("      <Floors>\n")
    xml.append("        <Condition>Used</Condition>\n") // Default condition
    xml.append("        <Items>\n")

    items.foreach { floor =>
      val isExterior = floor.floorType.toLowerCase.contains("aussenluft") ||
                       floor.floorType.toLowerCase.contains("exterior")

      if isExterior then
        xml.append("          <FloorExterior>\n")
      else
        xml.append("          <FloorOther>\n")

      if floor.code.nonEmpty then
        xml.append(s"            <ExpertCode>${escapeXml(floor.code.take(5))}</ExpertCode>\n")

      if floor.description.nonEmpty then
        xml.append(s"            <ExpertDescription>${escapeXml(floor.description.take(250))}</ExpertDescription>\n")

      xml.append(s"            <Quantity>${floor.quantity}</Quantity>\n")

      // RenovationYear must come before Area (part of base class xsBuildingEnvelopeEntity)
      if floor.renovationYear.isDefined then
        xml.append(s"            <RenovationYear>${floor.renovationYear.get}</RenovationYear>\n")

      xml.append(s"            <Area>${floor.area}</Area>\n")
      xml.append(s"            <UValue>${floor.uValue}</UValue>\n")
      xml.append(s"            <BFactor>${floor.bFactor}</BFactor>\n")

      if floor.floorHeating then
        xml.append(s"            <HeatingElement>true</HeatingElement>\n")

      if !isExterior then
        val floorType = mapFloorType(floor.floorType)
        xml.append(s"            <Type>$floorType</Type>\n")

        if floor.neighborRoomTemp.isDefined then
          xml.append(s"            <TemperatureNextRoom>${floor.neighborRoomTemp.get}</TemperatureNextRoom>\n")

      if isExterior then
        xml.append("          </FloorExterior>\n")
      else
        xml.append("          </FloorOther>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </Floors>\n")

    xml.toString()
  end generateFloors

  /** Generate ThermalBridges section */
  private def generateThermalBridges(items: List[ThermalBridge]): String =
    val xml = new StringBuilder()

    xml.append("      <ThermalBridges>\n")
    xml.append("        <Items>\n")

    items.foreach { bridge =>
      val isLinear = bridge.bridgeType.toLowerCase.contains("linear") ||
                     bridge.psiValue.isDefined

      if isLinear then
        xml.append("          <ThermalBridgeLinear>\n")

        if bridge.code.nonEmpty then
          xml.append(s"            <ExpertCode>${escapeXml(bridge.code.take(5))}</ExpertCode>\n")

        if bridge.description.nonEmpty then
          xml.append(s"            <ExpertDescription>${escapeXml(bridge.description.take(250))}</ExpertDescription>\n")

        xml.append(s"            <Quantity>${bridge.quantity}</Quantity>\n")

        // RenovationYear must come before BFactor (part of base class xsBuildingEnvelopeEntity)
        if bridge.renovationYear.isDefined then
          xml.append(s"            <RenovationYear>${bridge.renovationYear.get}</RenovationYear>\n")

        xml.append(s"            <BFactor>${bridge.bFactor}</BFactor>\n")
        xml.append(s"            <PsiValue>${bridge.psiValue.getOrElse(0.0)}</PsiValue>\n")
        xml.append(s"            <Length>${bridge.length.getOrElse(0.0)}</Length>\n")

        val bridgeType = mapThermalBridgeType(bridge.bridgeType)
        xml.append(s"            <Type>$bridgeType</Type>\n")

        xml.append("          </ThermalBridgeLinear>\n")
      else
        // Dot-like thermal bridge
        xml.append("          <ThermalBridgeDotLike>\n")

        if bridge.code.nonEmpty then
          xml.append(s"            <ExpertCode>${escapeXml(bridge.code.take(5))}</ExpertCode>\n")

        if bridge.description.nonEmpty then
          xml.append(s"            <ExpertDescription>${escapeXml(bridge.description.take(250))}</ExpertDescription>\n")

        xml.append(s"            <Quantity>${bridge.quantity}</Quantity>\n")

        // RenovationYear must come before BFactor (part of base class xsBuildingEnvelopeEntity)
        if bridge.renovationYear.isDefined then
          xml.append(s"            <RenovationYear>${bridge.renovationYear.get}</RenovationYear>\n")

        xml.append(s"            <BFactor>${bridge.bFactor}</BFactor>\n")
        xml.append(s"            <ChiValue>${bridge.chiValue.getOrElse(0.0)}</ChiValue>\n")

        xml.append(s"            <Type>CustomType</Type>\n")

        xml.append("          </ThermalBridgeDotLike>\n")
    }

    xml.append("        </Items>\n")
    xml.append("      </ThermalBridges>\n")

    xml.toString()
  end generateThermalBridges

  // ========== Helper Functions ==========

  /** Escape XML special characters */
  private def escapeXml(text: String): String =
    text
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  /** Format address as single line */
  private def formatAddress(address: Address): String =
    val parts = List(
      address.street,
      address.houseNumber
    ).flatten
    if parts.nonEmpty then parts.mkString(" ") else ""

  /** Map Anrede enum to XML Title enumeration */
  private def mapAnredeToTitle(anrede: Anrede): String = anrede match
    case Anrede.Frau => "Ms"
    case Anrede.Herr => "Mr"
    case Anrede.`Herr u. Frau` => "MrAndMs"
    case Anrede.Firma => "Company"
    case Anrede.Familie => "Family"
    case Anrede.`Hauseigentümer` => "HomeOwner"
    case Anrede.`Eigentümergemeinschaft` => "CommunityAssociation"
    case Anrede.Liegenschaftsverwaltung => "PropertyManagement"
    case Anrede.Gemeindeverwaltung => "MunicipalAdministration"

  /** Map ground plan type to XML enumeration */
  private def mapGroundPlanType(groundPlan: Option[String]): String =
    groundPlan.map(_.toLowerCase) match
      case Some(gp) if gp.contains("kompakt") || gp.contains("compact") => "Compact"
      case Some(gp) if gp.contains("länglich") || gp.contains("elongated") => "Elongated"
      case _ => "Compact" // Default

  /** Map construction type to XML enumeration */
  private def mapConstructionType(construction: Option[String]): String =
    construction.map(_.toLowerCase) match
      case Some(c) if c.contains("massiv") || c.contains("heavy") => "Heavy"
      case Some(c) if c.contains("mittel") || c.contains("middle") => "Middle"
      case Some(c) if c.contains("leicht") && !c.contains("sehr") => "Light"
      case Some(c) if c.contains("sehr leicht") || c.contains("very light") => "VeryLight"
      case _ => "Heavy" // Default

  /** Map orientation to XML enumeration */
  private def mapOrientation(orientation: Option[String]): String =
    orientation.map(_.toUpperCase) match
      case Some("N") => "North"
      case Some("NO") | Some("NE") => "NorthEast"
      case Some("O") | Some("E") => "East"
      case Some("SO") | Some("SE") => "SouthEast"
      case Some("S") => "South"
      case Some("SW") => "SouthWest"
      case Some("W") => "West"
      case Some("NW") => "NorthWest"
      case Some("HORIZONTAL") => "Horizontal"
      case _ => "South" // Default

  /** Map floor type to XML enumeration */
  private def mapFloorType(floorType: String): String =
    val lower = floorType.toLowerCase
    if lower.contains("unbeheizt") && (lower.contains("ungedämmt") || lower.contains("undicht")) then
      "TowardsUnheatedNoIsolation"
    else if lower.contains("erdreich") && lower.contains("2") then
      "TowardsEarthMoreThanTwoMeters"
    else if lower.contains("erdreich") then
      "TowardsEarthUpToTwoMeters"
    else if lower.contains("beheizt") then
      "TowardsHeated"
    else if lower.contains("unbeheizt") && lower.contains("gedämmt") then
      "TowardsUnheatedWithIsolation"
    else
      "TowardsUnheatedNoIsolation" // Default

  /** Map thermal bridge type to XML enumeration */
  private def mapThermalBridgeType(bridgeType: String): String =
    val lower = bridgeType.toLowerCase
    if lower.contains("decke") || lower.contains("wand") then "CeilingOrWall"
    else if lower.contains("gebäudesockel") || lower.contains("base") then "BuildingBase"
    else if lower.contains("balkon") then "Balcony"
    else if lower.contains("fenster") then "WindowStop"
    else if lower.contains("kellerdecke") then "FloorCellar"
    else if lower.contains("dach") && lower.contains("fassade") then "RoofOrFacade"
    else if lower.contains("verschattung") then "Shade"
    else if lower.contains("dachrand") then "RoofEdge"
    else if lower.contains("innenwand") then "InnerToExteriorWall"
    else "CustomType"

  /** Download XML file to user's computer */
  def downloadXml(project: GeakProject, fileName: String = ""): Unit =
    try
      val xml = exportToXml(project)

      val exportFileName = if fileName.isEmpty then
        val projectName = if project.project.projectName.isEmpty then "geak_export" else project.project.projectName
        projectName.replace(" ", "-") + ".xml"
      else
        fileName

      // Create blob with explicit UTF-8 charset to avoid encoding issues
      val blob = new dom.Blob(js.Array(xml), dom.BlobPropertyBag("text/xml;charset=utf-8"))
      val url = dom.URL.createObjectURL(blob)

      val link = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
      link.href = url
      link.download = exportFileName
      link.click()

      dom.URL.revokeObjectURL(url)

      // Clear the work-in-progress snapshot — project is now exported
      pme123.geak4s.state.AppState.clearWip()

      dom.console.log(s"✅ XML exported successfully: $exportFileName")
    catch
      case ex: Exception =>
        dom.console.error(s"Error exporting XML: ${ex.getMessage}")
        ex.printStackTrace()

  /** Upload XML file to Google Drive
    * @param project The GEAK project to export
    * @return Future with upload result
    */
  def uploadXmlToGoogleDrive(project: GeakProject): Future[Boolean] =
    try
      val xml = exportToXml(project)
      val projectName = if project.project.projectName.isEmpty then "geak_export" else project.project.projectName
      val sanitizedName = projectName.replaceAll("[^a-zA-Z0-9-_]", "_")
      val projectFolder = s"${pme123.geak4s.config.GoogleDriveConfig.rootFolder}/$sanitizedName"
      val fileName = "GeakTool.xml"

      // Convert XML string to ArrayBuffer
      val xmlBytes = xml.getBytes("UTF-8")
      val arrayBuffer = js.typedarray.ArrayBuffer(xmlBytes.length)
      val uint8Array = new js.typedarray.Uint8Array(arrayBuffer)
      xmlBytes.zipWithIndex.foreach { case (byte, i) => uint8Array(i) = byte }

      // Upload to Google Drive
      GoogleDriveService.uploadFile(projectFolder, fileName, arrayBuffer, "text/xml")
    catch
      case ex: Exception =>
        dom.console.error(s"Failed to upload XML to Google Drive: ${ex.getMessage}")
        Future.successful(false)

end XmlExportService

