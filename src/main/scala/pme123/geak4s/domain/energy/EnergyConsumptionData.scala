package pme123.geak4s.domain.energy

/** HGT (Heizgradtage) correction factors for Zürich-SMA.
  *
  * Mittelwert 2011–2020 = 3124.7 HGT.
  * Korrekturfaktor = 3124.7 / Jahres-HGT.
  *
  * To normalise a yearly consumption value to the reference average:
  *   bereinigt = raw × hgtFactor(year)
  */
object HgtFactors:
  val factors: Map[Int, Double] = Map(
    2014 -> 1.1223778735632184,
    2015 -> 1.0211437908496732,
    2016 -> 0.9369415292353822,
    2017 -> 0.9670999690498298,
    2018 -> 1.0646337308347529,
    2019 -> 1.0040809768637530,
    2020 -> 1.0653596999659052,
    2021 -> 0.9187591884739782,
    2022 -> 1.1260180180180180,
    2023 -> 1.0748882008943927,
    2024 -> 1.0872303409881698,
    2025 -> 1.0191454664057402
  )

  /** Returns the HGT correction factor for a given year, or 1.0 if unknown. */
  def apply(year: Int): Double = factors.getOrElse(year, 1.0)

  val sortedYears: List[Int] = factors.keys.toList.sorted

end HgtFactors

// ---------------------------------------------------------------------------
// Fuel type
// ---------------------------------------------------------------------------

enum FuelType:
  case Gas
  case Oil

// ---------------------------------------------------------------------------
// Annual entry models
// ---------------------------------------------------------------------------

/** One row in the Elektro-Allg. table. */
case class ElectricityEntry(
    year: Int,
    htKwh: Option[Double] = None,
    ntKwh: Option[Double] = None
):
  def totalKwh: Option[Double] =
    (htKwh, ntKwh) match
      case (Some(ht), Some(nt)) => Some(ht + nt)
      case (Some(ht), None)     => Some(ht)
      case (None, Some(nt))     => Some(nt)
      case _                    => None

  /** HGT-bereinigter Gesamtverbrauch (kWh). */
  def correctedKwh: Option[Double] =
    totalKwh.map(_ * HgtFactors(year))

/** One row in the Gas/Öl table.
  *
  * If `directKwh` is set it takes priority over the volume-based calculation.
  * This lets users enter consumption that is already known in kWh.
  */
case class FuelEntry(
    year: Int,
    volumeLOrM3: Option[Double] = None,
    directKwh: Option[Double] = None
):
  def toKwh(calorificValue: Double): Option[Double] =
    directKwh.orElse(volumeLOrM3.map(_ * calorificValue))

  /** HGT-bereinigter Verbrauch (kWh). */
  def correctedKwh(calorificValue: Double): Option[Double] =
    toKwh(calorificValue).map(_ * HgtFactors(year))

/** One row in the Kaltwasser table. */
case class WaterEntry(
    year: Int,
    consumptionM3: Option[Double] = None
)

// ---------------------------------------------------------------------------
// Calculation parameters
// ---------------------------------------------------------------------------

/** Parameters for the Heizleistungsbedarf calculation. */
case class HeatingPowerSettings(
    klimaregion: String = "Zürich-MeteoSchweiz",
    gebaeudekategorie: String = "EFH",
    warmwasserUeberHeizung: Boolean = true,
    volllaststunden: Double = 2700.0,
    wirkungsgrad: Double = 0.9,
    reserve: Double = 0.1,
    zuschlagEW: Double = 0.2
)

object HeatingPowerSettings:
  val default: HeatingPowerSettings = HeatingPowerSettings()

/** Deduction settings for household electricity and electric cars. */
case class HaushaltsstromSettings(
    enabled: Boolean = false,
    buildingType: String = "MFH",
    numPersonsPerUnit: Int = 2,
    numUnits: Int = 1,
    numElectricCars: Int = 0
):
  def kwhPerUnit: Double =
    if buildingType == "MFH" then
      if numPersonsPerUnit <= 4 then 2190.0 + (numPersonsPerUnit - 2) * 458.5
      else 2190.0 + 2 * 458.5 + (numPersonsPerUnit - 4) * 408.5
    else
      if numPersonsPerUnit <= 4 then 4048.0 + (numPersonsPerUnit - 4) * 593.5
      else 4048.0 + (numPersonsPerUnit - 4) * 543.5

  def totalHaushaltsstromKwh: Double = kwhPerUnit * numUnits
  def totalElectricCarsKwh: Double   = numElectricCars * 2000.0
  def totalDeductionKwh: Double      = totalHaushaltsstromKwh + totalElectricCarsKwh

object HaushaltsstromSettings:
  val default: HaushaltsstromSettings = HaushaltsstromSettings()

/** Parameters for the Erdwärmesonden (EWS) calculation. */
case class EwsSettings(
    spezifischeEntnahmeleistung: Double = 35.0, // W/m
    bezugAusErdreich: Double = 0.75,
    anzahlSonden: Int = 1
)

object EwsSettings:
  val default: EwsSettings = EwsSettings()

// ---------------------------------------------------------------------------
// Top-level model
// ---------------------------------------------------------------------------

case class EnergyConsumptionData(
    fuelType: FuelType = FuelType.Gas,
    calorificValue: Double = 10.0, // kWh/m3 (Gas default)
    electricityEntries: List[ElectricityEntry] = List.empty,
    fuelEntries: List[FuelEntry] = List.empty,
    waterEntries: List[WaterEntry] = List.empty,
    heatingPowerSettings: HeatingPowerSettings = HeatingPowerSettings.default,
    ewsSettings: EwsSettings = EwsSettings.default,
    haushaltsstromSettings: HaushaltsstromSettings = HaushaltsstromSettings.default
)

object EnergyConsumptionData:
  val empty: EnergyConsumptionData = EnergyConsumptionData()
