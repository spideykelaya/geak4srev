package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import pme123.geak4s.domain.energy.*
import pme123.geak4s.state.{EnergyState, AppState, AreaState}
import pme123.geak4s.domain.uwert.ComponentType

/**
 * Energieberechnung view.
 *
 * Replicates all functions of the "Energie_EBF" Excel sheet:
 *  – Strom (Elektro Allg.)  – yearly HT/NT, HGT-corrected, average, kWh/m2
 *  – Gas / Öl               – yearly volume → kWh, HGT-corrected, average, kWh/m2
 *  – Kaltwasser             – yearly m3, average, m3/m2
 *  – Heizleistungsbedarf    – calculated from average fuel consumption & EBF
 *  – Erdwärmesonde (EWS)    – calculated depths
 *
 * Input handling: separate display Vars control table structure (only updated on
 * row add/delete).  Field edits update EnergyState directly but do NOT touch the
 * display Var → no re-render → no focus loss.  Computed columns stay live via
 * index-based signals.  AppState.saveEnergyData() is called on onBlur.
 */
object EnergyCalculationView:

  // -------------------------------------------------------------------------
  // Display vars – control table structure; only updated on row add/delete
  // -------------------------------------------------------------------------

  private val elecDisplay:  Var[List[ElectricityEntry]] = Var(List.empty)
  private val fuelDisplay:  Var[List[FuelEntry]]        = Var(List.empty)
  private val waterDisplay: Var[List[WaterEntry]]       = Var(List.empty)

  private def syncDisplayFromState(): Unit =
    val d = EnergyState.energyData.now()
    elecDisplay.set(d.electricityEntries)
    fuelDisplay.set(d.fuelEntries)
    waterDisplay.set(d.waterEntries)

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private def addThousandsSep(intPart: String): String =
    intPart.reverse.grouped(3).mkString("'").reverse

  private def fmtNum(v: Double, decimals: Int = 0): String =
    val factor  = math.pow(10, decimals)
    val rounded = math.round(v * factor).toDouble / factor
    if decimals == 0 then
      addThousandsSep(rounded.toLong.toString)
    else
      val parts   = rounded.toString.split('.')
      val intPart = addThousandsSep(parts(0))
      val dec     = if parts.length > 1 then parts(1).take(decimals).padTo(decimals, '0') else "0" * decimals
      s"$intPart.$dec"

  private def fmtOpt(opt: Option[Double], decimals: Int = 0): String =
    opt.map(fmtNum(_, decimals)).getOrElse("–")

  private def parseDouble(s: String): Option[Double] =
    s.replace(",", ".").trim.toDoubleOption.filter(_.isFinite)

  private def parseInt(s: String): Option[Int] =
    s.trim.toIntOption

  // EBF from AreaState (sum of EBF polygon areas)
  private val ebfSignal: Signal[Double] =
    AreaState.areaCalculations.signal.map { maybeArea =>
      maybeArea
        .flatMap(_.get(ComponentType.EBF))
        .map(_.entries.map(_.totalArea).sum)
        .getOrElse(0.0)
    }

  // -------------------------------------------------------------------------
  // Sync computed values → WordForm (Step 4)
  // -------------------------------------------------------------------------

  private def syncToWordForm(d: EnergyConsumptionData, ebf: Double): Unit =
    val vals = d.fuelEntries.flatMap(_.correctedKwh(d.calorificValue))
    if vals.nonEmpty then
      val s         = d.heatingPowerSettings
      val avg       = vals.sum / vals.length
      val nwb       = avg * s.wirkungsgrad
      val hl        = math.round(nwb / s.volllaststunden * 10.0).toDouble / 10.0
      val hlr       = math.round(hl * (1 + s.reserve) * 10.0).toDouble / 10.0
      val z         = if hlr >= 15.0 then 0.2 else 0.0
      val hlNew     = math.round(hlr * (1 + z) * 10.0).toDouble / 10.0
      val ekz       = if ebf > 0 then fmtNum(avg / ebf, 1) else ""
      val ews       = d.ewsSettings
      val tiefeTotal    = hlNew * 1000.0 * ews.bezugAusErdreich / ews.spezifischeEntnahmeleistung
      val tiefeProSonde = tiefeTotal / ews.anzahlSonden
      val sondentiefe   = s"${ews.anzahlSonden} × ${fmtNum(tiefeProSonde, 1)} m"
      WordFormView.formVar.update(_.copy(
        energieverbrauch = fmtNum(avg, 0),
        energiekennzahl  = ekz,
        fossil           = fmtNum(hlr, 1),
        wp               = fmtNum(hlNew, 1),
        sondentiefe      = sondentiefe
      ))

  // -------------------------------------------------------------------------
  // Section header helper
  // -------------------------------------------------------------------------

  private def sectionTitle(text: String): HtmlElement =
    div(marginBottom := "0.75rem", Title(_.level := TitleLevel.H3, text))

  // -------------------------------------------------------------------------
  // Shared table style helpers
  // -------------------------------------------------------------------------

  private val cellStyle  = Seq(border := "1px solid #ddd", padding := "0.4rem 0.6rem", color := "black")
  private val headerCell = Seq(border := "1px solid #ddd", padding := "0.4rem 0.6rem",
                               backgroundColor := "#f0f0f0", fontWeight := "bold", textAlign := "center", color := "black")
  private val resultCell = Seq(border := "1px solid #ddd", padding := "0.4rem 0.6rem",
                               backgroundColor := "#e8f4e8", textAlign := "right", color := "black")
  private val labelCell  = Seq(border := "1px solid #ddd", padding := "0.4rem 0.6rem",
                               backgroundColor := "#f8f8f8", color := "black")

  private def numInput(initVal: String, w: String = "90px")(onIn: String => Unit)(onBl: => Unit): HtmlElement =
    input(
      typ := "text", color := "black", width := w,
      value   := initVal,
      onInput.mapToValue  --> Observer[String](onIn),
      onBlur.mapTo(())    --> Observer[Unit]  { _ => onBl }
    )

  // -------------------------------------------------------------------------
  // Strom (Elektro Allg.)
  // -------------------------------------------------------------------------

  private def electricitySection(): HtmlElement =

    val avgCorrected: Signal[Option[Double]] =
      EnergyState.energyData.signal.map { d =>
        val vals = d.electricityEntries.flatMap(_.correctedKwh)
        if vals.isEmpty then None else Some(vals.sum / vals.length)
      }

    def elecSig(idx: Int): Signal[Option[ElectricityEntry]] =
      EnergyState.energyData.signal.map(_.electricityEntries.lift(idx))

    div(
      marginBottom := "2rem", padding := "1.25rem",
      border := "1px solid #ddd", borderRadius := "6px",

      sectionTitle("Strom (Elektro Allg.)"),

      div(
        overflowX := "auto",
        child <-- elecDisplay.signal.map { rows =>
          table(
            width := "100%", borderCollapse := "collapse",
            thead(tr(
              th(headerCell, "Jahr"),
              th(headerCell, "Verbrauch HT [kWh]"),
              th(headerCell, "Verbrauch NT [kWh]"),
              th(headerCell, "Total [kWh]"),
              th(headerCell, "HGT-Faktor"),
              th(headerCell, "Bereinigt [kWh]"),
              th(headerCell, "")
            )),
            tbody(
              rows.zipWithIndex.map { case (entry, idx) =>
                val sig = elecSig(idx)
                tr(
                  // Jahr
                  td(cellStyle, numInput(entry.year.toString, "65px") { v =>
                    parseInt(v).foreach { yr =>
                      EnergyState.energyData.update(d => d.copy(electricityEntries =
                        d.electricityEntries.updated(idx, d.electricityEntries(idx).copy(year = yr))))
                    }
                  } { AppState.saveEnergyData() }),
                  // HT
                  td(cellStyle, numInput(entry.htKwh.map(fmtNum(_, 0)).getOrElse("")) { v =>
                    val opt = if v.trim.isEmpty then None else parseDouble(v)
                    EnergyState.energyData.update(d => d.copy(electricityEntries =
                      d.electricityEntries.updated(idx, d.electricityEntries(idx).copy(htKwh = opt))))
                  } { AppState.saveEnergyData() }),
                  // NT
                  td(cellStyle, numInput(entry.ntKwh.map(fmtNum(_, 0)).getOrElse("")) { v =>
                    val opt = if v.trim.isEmpty then None else parseDouble(v)
                    EnergyState.energyData.update(d => d.copy(electricityEntries =
                      d.electricityEntries.updated(idx, d.electricityEntries(idx).copy(ntKwh = opt))))
                  } { AppState.saveEnergyData() }),
                  // Computed: Total
                  td(resultCell, child.text <-- sig.map(_.flatMap(_.totalKwh).map(fmtNum(_, 0)).getOrElse("–"))),
                  // Computed: HGT-Faktor
                  td(resultCell, child.text <-- sig.map(_.map(e => fmtNum(HgtFactors(e.year), 4)).getOrElse("–"))),
                  // Computed: Bereinigt
                  td(resultCell, child.text <-- sig.map(_.flatMap(_.correctedKwh).map(fmtNum(_, 0)).getOrElse("–"))),
                  // Delete
                  td(cellStyle, Button(
                    _.design := ButtonDesign.Transparent, _.icon := IconName.delete,
                    _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                      EnergyState.energyData.update(d => d.copy(electricityEntries =
                        d.electricityEntries.patch(idx, Nil, 1)))
                      elecDisplay.set(EnergyState.energyData.now().electricityEntries)
                      AppState.saveEnergyData()
                    }
                  ))
                )
              } *
            ),
            tfoot(
              tr(
                td(labelCell, colSpan := 5, fontWeight := "bold", "Mittelwert (HGT-bereinigt)"),
                td(resultCell, fontWeight := "bold", child.text <-- avgCorrected.map(fmtOpt(_, 0))),
                td(cellStyle, "kWh")
              ),
              tr(
                td(labelCell, colSpan := 5, "kWh/m²"),
                td(resultCell, child.text <-- avgCorrected.combineWith(ebfSignal).map { case (avg, ebf) =>
                  if ebf > 0 then avg.map(a => fmtNum(a / ebf, 1)).getOrElse("–") else "–"
                }),
                td(cellStyle, "kWh/m²")
              )
            )
          )
        }
      ),

      div(marginTop := "0.75rem",
        Button(
          _.design := ButtonDesign.Transparent, _.icon := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val firstYr = EnergyState.energyData.now().electricityEntries.headOption.map(_.year).getOrElse(2026)
            EnergyState.energyData.update(d =>
              d.copy(electricityEntries = ElectricityEntry(firstYr - 1) +: d.electricityEntries))
            elecDisplay.set(EnergyState.energyData.now().electricityEntries)
            AppState.saveEnergyData()
          },
          "Jahr hinzufügen"
        )
      )
    )

  // -------------------------------------------------------------------------
  // Gas / Öl
  // -------------------------------------------------------------------------

  private def fuelSection(): HtmlElement =

    val fuelType: Signal[FuelType] = EnergyState.energyData.signal.map(_.fuelType)
    val calVal:   Signal[Double]   = EnergyState.energyData.signal.map(_.calorificValue)

    val volumeUnit: Signal[String] = fuelType.map {
      case FuelType.Gas => "m³"
      case FuelType.Oil => "l"
    }

    val avgCorrected: Signal[Option[Double]] =
      EnergyState.energyData.signal.map { d =>
        val vals = d.fuelEntries.flatMap(_.correctedKwh(d.calorificValue))
        if vals.isEmpty then None else Some(vals.sum / vals.length)
      }

    def fuelSig(idx: Int): Signal[Option[FuelEntry]] =
      EnergyState.energyData.signal.map(_.fuelEntries.lift(idx))

    def fuelCvSig(idx: Int): Signal[Option[Double]] =
      EnergyState.energyData.signal.map { d =>
        d.fuelEntries.lift(idx).flatMap(_.toKwh(d.calorificValue))
      }

    def fuelCorrSig(idx: Int): Signal[Option[Double]] =
      EnergyState.energyData.signal.map { d =>
        d.fuelEntries.lift(idx).flatMap(_.correctedKwh(d.calorificValue))
      }

    div(
      marginBottom := "2rem", padding := "1.25rem",
      border := "1px solid #ddd", borderRadius := "6px",

      sectionTitle("Gas / Öl"),

      div(
        display := "flex", gap := "2rem", alignItems := "center",
        marginBottom := "1rem", flexWrap := "wrap",

        // Energieträger selector
        div(
          display := "flex", alignItems := "center", gap := "0.5rem",
          label(color := "black", "Energieträger:"),
          child <-- fuelType.map { ft =>
            select(
              color := "black", padding := "0.25rem",
              onChange.mapToValue --> Observer[String] { v =>
                EnergyState.energyData.update(d =>
                  d.copy(fuelType = if v == "Oil" then FuelType.Oil else FuelType.Gas))
                AppState.saveEnergyData()
              },
              option(value := "Gas", selected := (ft == FuelType.Gas), "Gas"),
              option(value := "Oil", selected := (ft == FuelType.Oil), "Öl")
            )
          }
        ),

        // Heizwert
        div(
          display := "flex", alignItems := "center", gap := "0.5rem",
          label(color := "black", "Heizwert [kWh/m³ oder kWh/l]:"),
          input(
            typ := "text", color := "black", width := "70px",
            value <-- calVal.map(fmtNum(_, 1)),
            onInput.mapToValue --> Observer[String] { v =>
              parseDouble(v).foreach { cv =>
                EnergyState.energyData.update(_.copy(calorificValue = cv))
              }
            },
            onBlur.mapTo(()) --> Observer[Unit] { _ => AppState.saveEnergyData() }
          )
        )
      ),

      div(
        overflowX := "auto",
        child <-- fuelDisplay.signal.map { rows =>
          table(
            width := "100%", borderCollapse := "collapse",
            thead(tr(
              th(headerCell, "Jahr"),
              th(headerCell, child.text <-- volumeUnit.map(u => s"Verbrauch [$u]")),
              th(headerCell, "Verbrauch [kWh]"),
              th(headerCell, "Verwendete kWh"),
              th(headerCell, "HGT-Faktor"),
              th(headerCell, "Bereinigt [kWh]"),
              th(headerCell, "")
            )),
            tbody(
              rows.zipWithIndex.map { case (entry, idx) =>
                tr(
                  // Jahr
                  td(cellStyle, numInput(entry.year.toString, "65px") { v =>
                    parseInt(v).foreach { yr =>
                      EnergyState.energyData.update(d => d.copy(fuelEntries =
                        d.fuelEntries.updated(idx, d.fuelEntries(idx).copy(year = yr))))
                    }
                  } { AppState.saveEnergyData() }),
                  // Volumen
                  td(cellStyle, numInput(entry.volumeLOrM3.map(fmtNum(_, 0)).getOrElse("")) { v =>
                    val opt = if v.trim.isEmpty then None else parseDouble(v)
                    EnergyState.energyData.update(d => d.copy(fuelEntries =
                      d.fuelEntries.updated(idx, d.fuelEntries(idx).copy(volumeLOrM3 = opt))))
                  } { AppState.saveEnergyData() }),
                  // Direkt kWh
                  td(cellStyle, numInput(entry.directKwh.map(fmtNum(_, 0)).getOrElse("")) { v =>
                    val opt = if v.trim.isEmpty then None else parseDouble(v)
                    EnergyState.energyData.update(d => d.copy(fuelEntries =
                      d.fuelEntries.updated(idx, d.fuelEntries(idx).copy(directKwh = opt))))
                  } { AppState.saveEnergyData() }),
                  // Computed: Verwendete kWh
                  td(resultCell, child.text <-- fuelCvSig(idx).map(fmtOpt(_, 0))),
                  // Computed: HGT-Faktor
                  td(resultCell, child.text <-- fuelSig(idx).map(_.map(e => fmtNum(HgtFactors(e.year), 4)).getOrElse("–"))),
                  // Computed: Bereinigt
                  td(resultCell, child.text <-- fuelCorrSig(idx).map(fmtOpt(_, 0))),
                  // Delete
                  td(cellStyle, Button(
                    _.design := ButtonDesign.Transparent, _.icon := IconName.delete,
                    _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                      EnergyState.energyData.update(d => d.copy(fuelEntries =
                        d.fuelEntries.patch(idx, Nil, 1)))
                      fuelDisplay.set(EnergyState.energyData.now().fuelEntries)
                      AppState.saveEnergyData()
                    }
                  ))
                )
              } *
            ),
            tfoot(
              tr(
                td(labelCell, colSpan := 5, fontWeight := "bold", "Mittelwert (HGT-bereinigt)"),
                td(resultCell, fontWeight := "bold", child.text <-- avgCorrected.map(fmtOpt(_, 0))),
                td(cellStyle, "kWh")
              ),
              tr(
                td(labelCell, colSpan := 5, "kWh/m²"),
                td(resultCell, child.text <-- avgCorrected.combineWith(ebfSignal).map { case (avg, ebf) =>
                  if ebf > 0 then avg.map(a => fmtNum(a / ebf, 1)).getOrElse("–") else "–"
                }),
                td(cellStyle, "kWh/m²")
              )
            )
          )
        }
      ),

      div(marginTop := "0.75rem",
        Button(
          _.design := ButtonDesign.Transparent, _.icon := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val firstYr = EnergyState.energyData.now().fuelEntries.headOption.map(_.year).getOrElse(2026)
            EnergyState.energyData.update(d =>
              d.copy(fuelEntries = FuelEntry(firstYr - 1) +: d.fuelEntries))
            fuelDisplay.set(EnergyState.energyData.now().fuelEntries)
            AppState.saveEnergyData()
          },
          "Jahr hinzufügen"
        )
      )
    )

  // -------------------------------------------------------------------------
  // Kaltwasser
  // -------------------------------------------------------------------------

  private def waterSection(): HtmlElement =

    val avgM3: Signal[Option[Double]] =
      EnergyState.energyData.signal.map { d =>
        val vals = d.waterEntries.flatMap(_.consumptionM3)
        if vals.isEmpty then None else Some(vals.sum / vals.length)
      }

    def waterSig(idx: Int): Signal[Option[WaterEntry]] =
      EnergyState.energyData.signal.map(_.waterEntries.lift(idx))

    div(
      marginBottom := "2rem", padding := "1.25rem",
      border := "1px solid #ddd", borderRadius := "6px",

      sectionTitle("Kaltwasser"),

      div(
        overflowX := "auto",
        child <-- waterDisplay.signal.map { rows =>
          table(
            width := "100%", borderCollapse := "collapse",
            thead(tr(
              th(headerCell, "Jahr"),
              th(headerCell, "Verbrauch [m³]"),
              th(headerCell, "")
            )),
            tbody(
              rows.zipWithIndex.map { case (entry, idx) =>
                tr(
                  // Jahr
                  td(cellStyle, numInput(entry.year.toString, "65px") { v =>
                    parseInt(v).foreach { yr =>
                      EnergyState.energyData.update(d => d.copy(waterEntries =
                        d.waterEntries.updated(idx, d.waterEntries(idx).copy(year = yr))))
                    }
                  } { AppState.saveEnergyData() }),
                  // Verbrauch
                  td(cellStyle, numInput(entry.consumptionM3.map(fmtNum(_, 1)).getOrElse("")) { v =>
                    val opt = if v.trim.isEmpty then None else parseDouble(v)
                    EnergyState.energyData.update(d => d.copy(waterEntries =
                      d.waterEntries.updated(idx, d.waterEntries(idx).copy(consumptionM3 = opt))))
                  } { AppState.saveEnergyData() }),
                  // Delete
                  td(cellStyle, Button(
                    _.design := ButtonDesign.Transparent, _.icon := IconName.delete,
                    _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                      EnergyState.energyData.update(d => d.copy(waterEntries =
                        d.waterEntries.patch(idx, Nil, 1)))
                      waterDisplay.set(EnergyState.energyData.now().waterEntries)
                      AppState.saveEnergyData()
                    }
                  ))
                )
              } *
            ),
            tfoot(
              tr(
                td(labelCell, "m³/a"),
                td(resultCell, child.text <-- avgM3.map(fmtOpt(_, 1))),
                td(cellStyle, "m³")
              ),
              tr(
                td(labelCell, "m³/m²"),
                td(resultCell, child.text <-- avgM3.combineWith(ebfSignal).map { case (avg, ebf) =>
                  if ebf > 0 then avg.map(a => fmtNum(a / ebf, 2)).getOrElse("–") else "–"
                }),
                td(cellStyle, "m³/m²")
              )
            )
          )
        }
      ),

      div(marginTop := "0.75rem",
        Button(
          _.design := ButtonDesign.Transparent, _.icon := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val firstYr = EnergyState.energyData.now().waterEntries.headOption.map(_.year).getOrElse(2026)
            EnergyState.energyData.update(d =>
              d.copy(waterEntries = WaterEntry(firstYr - 1) +: d.waterEntries))
            waterDisplay.set(EnergyState.energyData.now().waterEntries)
            AppState.saveEnergyData()
          },
          "Jahr hinzufügen"
        )
      )
    )

  // -------------------------------------------------------------------------
  // Heizleistungsbedarf
  // -------------------------------------------------------------------------

  private def heatingPowerSection(): HtmlElement =
    val data = EnergyState.energyData

    val avgFuelKwh: Signal[Option[Double]] =
      data.signal.map { d =>
        val vals = d.fuelEntries.flatMap(_.correctedKwh(d.calorificValue))
        if vals.isEmpty then None else Some(vals.sum / vals.length)
      }

    val settings: Signal[HeatingPowerSettings] = data.signal.map(_.heatingPowerSettings)

    val energiekennzahl: Signal[Option[Double]] =
      avgFuelKwh.combineWith(ebfSignal).map { case (avg, ebf) =>
        if ebf > 0 then avg.map(_ / ebf) else None
      }

    val nutzwaermebedarf: Signal[Option[Double]] =
      avgFuelKwh.combineWith(settings).map { case (avg, s) =>
        avg.map(_ * s.wirkungsgrad)
      }

    val heizleistung: Signal[Option[Double]] =
      nutzwaermebedarf.combineWith(settings).map { case (nwb, s) =>
        nwb.map(n => math.round(n / s.volllaststunden * 10.0).toDouble / 10.0)
      }

    val spezHeizleistung: Signal[Option[Double]] =
      heizleistung.combineWith(ebfSignal).map { case (hl, ebf) =>
        if ebf > 0 then hl.map(h => math.round(h / ebf * 1000.0 * 10.0).toDouble / 10.0) else None
      }

    val heizleistungMitReserve: Signal[Option[Double]] =
      heizleistung.combineWith(settings).map { case (hl, s) =>
        hl.map(h => math.round(h * (1 + s.reserve) * 10.0).toDouble / 10.0)
      }

    // Zuschlag automatisch: >= 15 kW → 20%, sonst 0%
    val zuschlagEWSignal: Signal[Double] =
      heizleistungMitReserve.map(hlr => if hlr.exists(_ >= 15.0) then 0.2 else 0.0)

    val heizleistungNeueAnlage: Signal[Option[Double]] =
      heizleistungMitReserve.combineWith(zuschlagEWSignal).map { case (hlr, z) =>
        hlr.map(h => math.round(h * (1 + z) * 10.0).toDouble / 10.0)
      }

    def updateSettings(f: HeatingPowerSettings => HeatingPowerSettings): Unit =
      data.update(d => d.copy(heatingPowerSettings = f(d.heatingPowerSettings)))
      AppState.saveEnergyData()

    def resultRow(lbl: String, sig: Signal[Option[Double]], unit: String, dec: Int = 1): HtmlElement =
      tr(
        td(labelCell, lbl),
        td(resultCell, child.text <-- sig.map(fmtOpt(_, dec))),
        td(cellStyle, unit)
      )

    def settingsNumInput(initVal: String, w: String = "100px")(onIn: String => Unit): HtmlElement =
      input(
        typ := "text", color := "black", width := w, value := initVal,
        onInput.mapToValue --> Observer[String](onIn),
        onBlur.mapTo(()) --> Observer[Unit] { _ => AppState.saveEnergyData() }
      )

    div(
      marginBottom := "2rem", padding := "1.25rem",
      border := "1px solid #ddd", borderRadius := "6px",

      sectionTitle("Berechnung Heizleistungsbedarf"),

      div(
        display := "flex", gap := "2rem", flexWrap := "wrap",

        // Left: parameters
        div(
          flex := "1", minWidth := "320px",
          Title(_.level := TitleLevel.H4, "Parameter"),
          table(
            width := "100%", borderCollapse := "collapse", marginTop := "0.5rem",
            tbody(
              tr(
                td(labelCell, "Klimaregion"),
                td(cellStyle, colSpan := 2, color := "black", "Zürich-MeteoSchweiz")
              ),
              tr(
                td(labelCell, "Gebäudekategorie"),
                td(cellStyle, colSpan := 2,
                  child <-- settings.map { s =>
                    select(
                      color := "black", padding := "0.25rem",
                      onChange.mapToValue --> Observer[String] { v =>
                        updateSettings(_.copy(gebaeudekategorie = v))
                      },
                      option(value := "EFH",    selected := (s.gebaeudekategorie == "EFH"),    "EFH"),
                      option(value := "MFH",    selected := (s.gebaeudekategorie == "MFH"),    "MFH"),
                      option(value := "Büro",   selected := (s.gebaeudekategorie == "Büro"),   "Büro"),
                      option(value := "Andere", selected := (!List("EFH","MFH","Büro").contains(s.gebaeudekategorie)), "Andere")
                    )
                  }
                )
              ),
              tr(
                td(labelCell, "Energiebezugsfläche (EBF)"),
                td(resultCell, child.text <-- ebfSignal.map(fmtNum(_, 0))),
                td(cellStyle, "m²")
              ),
              tr(
                td(labelCell, "Warmwasser über Heizung"),
                td(cellStyle, colSpan := 2,
                  CheckBox(
                    _.checked <-- settings.map(_.warmwasserUeberHeizung),
                    _.events.onChange.map(_.target.checked) --> Observer[Boolean] { v =>
                      updateSettings(_.copy(
                        warmwasserUeberHeizung = v,
                        volllaststunden = if v then 2700.0 else 2300.0
                      ))
                    }
                  )
                )
              ),
              tr(
                td(labelCell, "Volllaststunden"),
                td(resultCell, child.text <-- settings.map(s => fmtNum(s.volllaststunden, 0))),
                td(cellStyle, "h")
              ),
              tr(
                td(labelCell, "Wirkungsgrad bestehende Wärmeerzeugung"),
                td(cellStyle, settingsNumInput(data.now().heatingPowerSettings.wirkungsgrad.toString) { v =>
                  parseDouble(v).foreach(n => updateSettings(_.copy(wirkungsgrad = n)))
                }),
                td(cellStyle, "[-]")
              ),
              tr(
                td(labelCell, "Reserve"),
                td(cellStyle, settingsNumInput(data.now().heatingPowerSettings.reserve.toString) { v =>
                  parseDouble(v).foreach(n => updateSettings(_.copy(reserve = n)))
                }),
                td(cellStyle, "[-]")
              ),
              tr(
                td(labelCell, "Zuschlag 2×2h Sperrung EW*"),
                td(resultCell, child.text <-- zuschlagEWSignal.map(z =>
                  if z > 0 then "20% (≥ 15 kW)" else "0% (< 15 kW)"
                )),
                td(cellStyle, "")
              )
            )
          )
        ),

        // Right: results
        div(
          flex := "1", minWidth := "320px",
          Title(_.level := TitleLevel.H4, "Berechnete Werte"),
          table(
            width := "100%", borderCollapse := "collapse", marginTop := "0.5rem",
            tbody(
              resultRow("Mittelwert Energieverbrauch (HGT-bereinigt)", avgFuelKwh, "kWh", 0),
              resultRow("Energiekennzahl",           energiekennzahl,       "kWh/m²"),
              resultRow("Nutzwärmebedarf",           nutzwaermebedarf,      "kWh", 0),
              resultRow("Heizleistung",              heizleistung,          "kW"),
              resultRow("spezifische Heizleistung",        spezHeizleistung,      "W/m²"),
              resultRow("Heizleistung Öl-/Gasheizung mit Reserve",  heizleistungMitReserve,"kW"),
              resultRow("Heizleistung WP",  heizleistungNeueAnlage,"kW")
            )
          ),
        )
      )
    )

  // -------------------------------------------------------------------------
  // Erdwärmesonde (EWS)
  // -------------------------------------------------------------------------

  private def ewsSection(): HtmlElement =
    val data = EnergyState.energyData

    val settings: Signal[EwsSettings] = data.signal.map(_.ewsSettings)

    val heizleistungNeueAnlage: Signal[Option[Double]] =
      data.signal.map { d =>
        val vals = d.fuelEntries.flatMap(_.correctedKwh(d.calorificValue))
        if vals.isEmpty then None
        else
          val avg   = vals.sum / vals.length
          val s     = d.heatingPowerSettings
          val nwb   = avg * s.wirkungsgrad
          val hl    = math.round(nwb / s.volllaststunden * 10.0).toDouble / 10.0
          val hlr   = math.round(hl * (1 + s.reserve) * 10.0).toDouble / 10.0
          val z     = if hlr >= 15.0 then 0.2 else 0.0
          Some(math.round(hlr * (1 + z) * 10.0).toDouble / 10.0)
      }

    val tiefeTotalSignal: Signal[Option[Double]] =
      heizleistungNeueAnlage.combineWith(settings).map { case (hlOpt, s) =>
        hlOpt.map(hl => hl * 1000.0 * s.bezugAusErdreich / s.spezifischeEntnahmeleistung)
      }

    val tiefeProSondeSignal: Signal[Option[Double]] =
      tiefeTotalSignal.combineWith(settings).map { case (totOpt, s) =>
        totOpt.map(_ / s.anzahlSonden.toDouble)
      }

    def ewsInput(initVal: String)(onIn: String => Unit): HtmlElement =
      input(
        typ := "text", color := "black", width := "100px", value := initVal,
        onInput.mapToValue --> Observer[String](onIn),
        onBlur.mapTo(()) --> Observer[Unit] { _ => AppState.saveEnergyData() }
      )

    def updateSettings(f: EwsSettings => EwsSettings): Unit =
      data.update(d => d.copy(ewsSettings = f(d.ewsSettings)))

    div(
      marginBottom := "2rem", padding := "1.25rem",
      border := "1px solid #ddd", borderRadius := "6px",

      sectionTitle("Erdwärmesonde (EWS)"),

      table(
        width := "100%", borderCollapse := "collapse",
        tbody(
          tr(
            td(labelCell, "Spezifische Entnahmeleistung"),
            td(cellStyle, color := "black", "35"),
            td(cellStyle, "W/m")
          ),
          tr(
            td(labelCell, "Bezug aus Erdreich"),
            td(cellStyle, color := "black", "0.75"),
            td(cellStyle, "[-]")
          ),
          tr(
            td(labelCell, fontWeight := "bold", "Tiefe Erdwärmesonde total"),
            td(resultCell, fontWeight := "bold", child.text <-- tiefeTotalSignal.map(fmtOpt(_, 1))),
            td(cellStyle, "m")
          ),
          tr(
            td(labelCell, "Anzahl Erdwärmesonden"),
            td(cellStyle, ewsInput(data.now().ewsSettings.anzahlSonden.toString) { v =>
              parseInt(v).foreach(n => updateSettings(_.copy(anzahlSonden = n)))
            }),
            td(cellStyle, "Stück")
          ),
          tr(
            td(labelCell, fontWeight := "bold", "Tiefe Erdwärmesonde pro Sonde"),
            td(resultCell, fontWeight := "bold", child.text <-- tiefeProSondeSignal.map(fmtOpt(_, 1))),
            td(cellStyle, "m")
          )
        )
      )
    )

  // -------------------------------------------------------------------------
  // Main render
  // -------------------------------------------------------------------------

  def apply(): HtmlElement =
    div(
      className := "energy-calculation-view",
      padding := "1.5rem",
      color := "black",

      // Initialise display vars when mounted; keep in sync on row-count changes
      onMountCallback { _ => syncDisplayFromState() },
      EnergyState.energyData.signal --> Observer[EnergyConsumptionData] { d =>
        if elecDisplay.now().length  != d.electricityEntries.length then elecDisplay.set(d.electricityEntries)
        if fuelDisplay.now().length  != d.fuelEntries.length         then fuelDisplay.set(d.fuelEntries)
        if waterDisplay.now().length != d.waterEntries.length        then waterDisplay.set(d.waterEntries)
      },
      // Push calculated values to WordForm (Step 4) whenever data or EBF changes
      EnergyState.energyData.signal.combineWith(ebfSignal) --> Observer[(EnergyConsumptionData, Double)] {
        case (d, ebf) => syncToWordForm(d, ebf)
      },

      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        marginBottom := "1.5rem",
        "Verbrauchswerte werden automatisch mit HGT-Faktoren (Klimakorrektur) bereinigt."
      ),

      div(
        marginBottom := "1.5rem",
        padding := "0.75rem 1rem",
        backgroundColor := "#f8f8f8",
        borderRadius := "4px",
        border := "1px solid #e0e0e0",
        display := "flex", gap := "1rem", alignItems := "center",
        Label("Energiebezugsfläche (EBF):"),
        b(color := "black", child.text <-- ebfSignal.map(ebf =>
          if ebf > 0 then s"${fmtNum(ebf, 0)} m²"
          else "Noch nicht erfasst (bitte im Schritt «Flächenauszug» eintragen)"
        ))
      ),

      electricitySection(),
      fuelSection(),
      waterSection(),
      heatingPowerSection(),
      ewsSection()
    )

end EnergyCalculationView
