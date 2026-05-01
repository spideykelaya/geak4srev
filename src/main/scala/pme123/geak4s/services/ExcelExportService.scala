package pme123.geak4s.services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.ArrayBuffer
import org.scalajs.dom
import scala.util.Try

/** Generates the GEAK Excel export from a serialized [[pme123.geak4s.domain.GeakProject]].
 *
 *  Loads `public/templates/GEAK-EXCEL-template.xlsx`, fills it from the project
 *  JSON via ExcelJS and returns the resulting `.xlsx` as a [[dom.Blob]].
 *
 *  Ported 1:1 from the backend `ExcelService` (Apache POI), with two changes:
 *    - 0-based POI indices are converted to 1-based ExcelJS indices in the
 *      cell helpers below;
 *    - the legacy "VelvetSweatshop" agile-encryption wrapper is dropped — the
 *      browser does not need it and ExcelJS cannot produce it.
 *
 *  ExcelJS is imported via the default export to stay compatible with Vite's
 *  ESM/CJS interop (same pattern as `WordExportService`).
 */
object ExcelExportService:

  @js.native
  @JSImport("exceljs", JSImport.Default)
  private object ExcelJS extends js.Object

  private val TemplateUrl  = "templates/GEAK-EXCEL-template.xlsx"
  private val XlsxMimeType =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

  def generate(projectJson: String): Future[dom.Blob] =
    val p = ujson.read(projectJson)
    fetchTemplate().flatMap { buffer =>
      val mod = ExcelJS.asInstanceOf[js.Dynamic]
      val wb  = js.Dynamic.newInstance(mod.Workbook)()
      val loadPromise = wb.xlsx.load(buffer.asInstanceOf[js.Any])
        .asInstanceOf[js.Promise[js.Any]]
      loadPromise.toFuture.flatMap { _ =>
        fillProjekt(wb, p)
        fillGebaeudenutzungen(wb, p)

        val areaCalcsOpt = Try(p("areaCalculations")).filter(_ != ujson.Null).toOption
        val uwerts       = av(p, "uwertCalculations")
        areaCalcsOpt match
          case Some(areaCalcs) =>
            fillRoofsFromArea(wb, areaCalcs, uwerts)
            fillWallsFromArea(wb, areaCalcs, uwerts)
            fillWindowsFromArea(wb, areaCalcs)
            fillFloorsFromArea(wb, areaCalcs, uwerts)
          case None =>
            fillEnvelopeSheet(wb, "Dächer und Decken", av(p, "roofsCeilings"), 10, fillRoofRow)
            fillEnvelopeSheet(wb, "Wände",             av(p, "walls"),          10, fillWallRow)
            fillEnvelopeSheet(wb, "Fenster und Türen", av(p, "windowsDoors"),    9, fillWindowRow)
            fillEnvelopeSheet(wb, "Böden",             av(p, "floors"),         10, fillFloorRow)
        fillEnvelopeSheet(wb, "Wärmebrücken",          av(p, "thermalBridges"),  7, fillBridgeRow)

        fillWaermeerzeuger(wb, p)
        fillSpeicher(wb, p)
        fillHeizungsverteilung(wb, p)
        fillWarmwasserverteilung(wb, p)
        fillLueftung(wb, p)
        fillElektrizitaet(wb, p)

        wb.updateDynamic("calcProperties")(js.Dynamic.literal(fullCalcOnLoad = true))

        wb.xlsx.writeBuffer().asInstanceOf[js.Promise[ArrayBuffer]].toFuture.map { buf =>
          new dom.Blob(js.Array(buf), dom.BlobPropertyBag(XlsxMimeType))
        }
      }
    }

  private def fetchTemplate(): Future[ArrayBuffer] =
    dom.fetch(TemplateUrl).toFuture.flatMap { resp =>
      if !resp.ok then
        throw new RuntimeException(s"Vorlage konnte nicht geladen werden: ${resp.status}")
      resp.arrayBuffer().toFuture
    }

  // ── Safe ujson accessors ─────────────────────────────────────────────────

  private def sv(v: ujson.Value, key: String): String =
    Try(v(key).str)
      .orElse(Try { val d = v(key).num; if d == d.toLong then d.toLong.toString else String.format("%.4g", d) })
      .getOrElse("")

  private def nv(v: ujson.Value, key: String): Double =
    Try(v(key).num).orElse(Try(v(key).str.toDouble)).getOrElse(0.0)

  private def av(v: ujson.Value, key: String): Seq[ujson.Value] =
    Try(v(key).arr.toSeq).getOrElse(Seq.empty)

  private def ov(v: ujson.Value, key: String): ujson.Value =
    Try(v(key)).filter(_ != ujson.Null).getOrElse(ujson.Obj())

  // ── ExcelJS helpers (row/col are 0-based, converted to 1-based here) ─────

  private def getSheet(wb: js.Dynamic, name: String): js.UndefOr[js.Dynamic] =
    wb.getWorksheet(name).asInstanceOf[js.UndefOr[js.Dynamic]]

  private def cell(sh: js.Dynamic, row: Int, col: Int): js.Dynamic =
    sh.getRow(row + 1).getCell(col + 1)

  private def sc(sh: js.Dynamic, row: Int, col: Int, value: String): Unit =
    if value.nonEmpty then cell(sh, row, col).value = value

  private def nc(sh: js.Dynamic, row: Int, col: Int, value: Double): Unit =
    if value != 0.0 then cell(sh, row, col).value = value

  private def ni(sh: js.Dynamic, row: Int, col: Int, value: Int): Unit =
    if value != 0 then cell(sh, row, col).value = value.toDouble

  private def niZ(sh: js.Dynamic, row: Int, col: Int, value: Int): Unit =
    cell(sh, row, col).value = value.toDouble

  /** Insert `count` empty rows at the 0-based `startRow0` and shift everything
   *  below downward (matches POI's `shiftRows` semantics).
   */
  private def insertEmptyRows(sh: js.Dynamic, startRow0: Int, count: Int): Unit =
    if count > 0 then
      val rows = js.Array[js.Any]()
      var i = 0
      while i < count do
        rows.push(js.Array[js.Any]())
        i += 1
      sh.insertRows(startRow0 + 1, rows)

  /** Last 0-based row index that contains values (mirrors POI's `getLastRowNum`). */
  private def lastRowIdx(sh: js.Dynamic): Int =
    sh.rowCount.asInstanceOf[Int] - 1

  // ── Sheet fillers ─────────────────────────────────────────────────────────

  private def fillProjekt(wb: js.Dynamic, p: ujson.Value): Unit =
    val sh   = wb.getWorksheet("Projekt").asInstanceOf[js.Dynamic]
    if sh == null || js.isUndefined(sh.asInstanceOf[js.Any]) then return
    val proj = ov(p, "project")
    val cli  = ov(proj, "client")
    val ca   = ov(cli, "address")
    val bloc = ov(proj, "buildingLocation")
    val ba   = ov(bloc, "address")
    val bd   = ov(proj, "buildingData")
    val desc = ov(proj, "descriptions")

    sc(sh, 1, 1, sv(proj, "projectName"))

    sc(sh, 4, 1, sv(cli, "salutation"))
    sc(sh, 5, 1, sv(cli, "name1"))
    sc(sh, 6, 1, sv(cli, "name2"))
    sc(sh, 7, 1, List(sv(ca,"street"), sv(ca,"houseNumber")).filter(_.nonEmpty).mkString(" "))
    sc(sh, 8, 1, sv(cli, "poBox"))
    sc(sh, 9, 1, sv(ca, "zipCode"))
    sc(sh, 10, 1, sv(ca, "city"))
    sc(sh, 11, 1, sv(ca, "country"))
    sc(sh, 12, 1, sv(cli, "email"))
    sc(sh, 13, 1, sv(cli, "phone1"))
    sc(sh, 14, 1, sv(cli, "phone2"))

    val egidEntries = av(ov(proj, "egidEdidGroup"), "entries")
    egidEntries.zipWithIndex.foreach { (e, i) =>
      val ea = ov(e, "address")
      val rowIdx = 5 + i
      sc(sh, rowIdx, 3, sv(e, "egid"))
      sc(sh, rowIdx, 4, sv(e, "edid"))
      sc(sh, rowIdx, 5, List(sv(ea,"street"), sv(ea,"houseNumber")).filter(_.nonEmpty).mkString(" "))
      sc(sh, rowIdx, 6, List(sv(ea,"zipCode"), sv(ea,"city")).filter(_.nonEmpty).mkString(" "))
    }

    sc(sh, 17, 1, sv(ba, "zipCode"))
    sc(sh, 18, 1, sv(ba, "city"))
    sc(sh, 19, 1, sv(bloc, "municipality"))
    sc(sh, 20, 1, sv(ba, "street"))
    sc(sh, 21, 1, sv(ba, "houseNumber"))
    sc(sh, 22, 1, sv(bloc, "buildingName"))
    sc(sh, 23, 1, sv(bd, "constructionYear"))
    sc(sh, 24, 1, sv(bd, "lastRenovationYear"))
    sc(sh, 25, 1, sv(bloc, "parcelNumber"))
    sc(sh, 26, 1, sv(bd, "weatherStation"))
    sc(sh, 27, 1, sv(bd, "weatherStationValues"))
    nc(sh, 28, 1, nv(bd, "altitude"))
    nc(sh, 29, 1, nv(bd, "energyReferenceArea"))
    nc(sh, 30, 1, nv(bd, "clearRoomHeight"))
    ni(sh, 31, 1, Try(nv(bd,"numberOfFloors").toInt).getOrElse(0))
    nc(sh, 32, 1, nv(bd, "buildingWidth"))
    sc(sh, 33, 1, sv(bd, "constructionType"))
    sc(sh, 34, 1, sv(bd, "groundPlanType"))

    sc(sh, 37, 1, sv(desc, "buildingDescription"))
    sc(sh, 40, 1, sv(desc, "envelopeDescription"))
    sc(sh, 43, 1, sv(desc, "hvacDescription"))

  private def fillGebaeudenutzungen(wb: js.Dynamic, p: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Gebäudenutzungen")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    val usages = av(p, "buildingUsages").take(3)
    sc(sh, 21, 0, "Anzahl Personen")
    sc(sh, 22, 0, "1-Zimmer Wohnungen")
    sc(sh, 23, 0, "2-Zimmer Wohnungen")
    sc(sh, 24, 0, "3-Zimmer Wohnungen")
    sc(sh, 25, 0, "4-Zimmer Wohnungen")
    sc(sh, 27, 0, "5-Zimmer Wohnungen")
    sc(sh, 28, 0, "6-Zimmer Wohnungen")
    sc(sh, 29, 0, ">6-Zimmer Wohnungen")

    usages.zipWithIndex.foreach { (u, i) =>
      val col    = 1 + i
      val valCol = 1 + i * 2
      sc(sh, 4, col, sv(u, "usageType"))
      sc(sh, 5, col, sv(u, "usageSubType"))
      nc(sh, 6, col, nv(u, "area"))
      nc(sh, 7, col, nv(u, "areaPercentage"))
      sc(sh, 8, col, sv(u, "constructionYear"))
      niZ(sh, 21, valCol, Try(u("numberOfResidents").num.toInt).getOrElse(0))
      niZ(sh, 22, valCol, Try(u("apartments1Room").num.toInt).getOrElse(0))
      niZ(sh, 23, valCol, Try(u("apartments2Room").num.toInt).getOrElse(0))
      niZ(sh, 24, valCol, Try(u("apartments3Room").num.toInt).getOrElse(0))
      niZ(sh, 25, valCol, Try(u("apartments4Room").num.toInt).getOrElse(0))
      niZ(sh, 27, valCol, Try(u("apartments5Room").num.toInt).getOrElse(0))
      niZ(sh, 28, valCol, Try(u("apartments6Room").num.toInt).getOrElse(0))
      niZ(sh, 29, valCol, Try(u("apartmentsOver6Room").num.toInt).getOrElse(0))
    }

  private def fillEnvelopeSheet(
    wb: js.Dynamic, sheetName: String,
    items: Seq[ujson.Value], dataStartRow: Int,
    fillRow: (js.Dynamic, Int, ujson.Value) => Unit
  ): Unit =
    val shOpt = getSheet(wb, sheetName)
    if shOpt.isEmpty || items.isEmpty then return
    val sh = shOpt.get
    if lastRowIdx(sh) >= dataStartRow then insertEmptyRows(sh, dataStartRow, items.length)
    items.zipWithIndex.foreach { (item, i) =>
      fillRow(sh, dataStartRow + i, item)
    }

  private def fillRoofRow(sh: js.Dynamic, rowIdx: Int, c: ujson.Value): Unit =
    sc(sh, rowIdx, 0, sv(c, "code"))
    sc(sh, rowIdx, 1, sv(c, "description"))
    sc(sh, rowIdx, 2, sv(c, "roofType"))
    sc(sh, rowIdx, 3, sv(c, "orientation"))
    sc(sh, rowIdx, 4, sv(c, "renovationYear"))
    nc(sh, rowIdx, 5, nv(c, "area"))
    nc(sh, rowIdx, 6, nv(c, "uValue"))
    nc(sh, rowIdx, 7, nv(c, "bFactor"))
    ni(sh, rowIdx, 8, Try(nv(c,"quantity").toInt).getOrElse(1))
    sc(sh, rowIdx, 9, if Try(c("wallHeating").bool).getOrElse(false) then "Ja" else "")

  private def fillWallRow(sh: js.Dynamic, rowIdx: Int, w: ujson.Value): Unit =
    sc(sh, rowIdx, 0, sv(w, "code"))
    sc(sh, rowIdx, 1, sv(w, "description"))
    sc(sh, rowIdx, 2, sv(w, "wallType"))
    sc(sh, rowIdx, 3, sv(w, "orientation"))
    sc(sh, rowIdx, 4, sv(w, "renovationYear"))
    nc(sh, rowIdx, 5, nv(w, "area"))
    nc(sh, rowIdx, 6, nv(w, "uValue"))
    nc(sh, rowIdx, 7, nv(w, "bFactor"))
    ni(sh, rowIdx, 8, Try(nv(w,"quantity").toInt).getOrElse(1))
    sc(sh, rowIdx, 9, if Try(w("wallHeating").bool).getOrElse(false) then "Ja" else "")

  private def fillWindowRow(sh: js.Dynamic, rowIdx: Int, w: ujson.Value): Unit =
    sc(sh, rowIdx, 0, sv(w, "code"))
    sc(sh, rowIdx, 1, sv(w, "description"))
    sc(sh, rowIdx, 2, sv(w, "windowType"))
    sc(sh, rowIdx, 3, sv(w, "orientation"))
    sc(sh, rowIdx, 4, sv(w, "renovationYear"))
    nc(sh, rowIdx, 5, nv(w, "area"))
    nc(sh, rowIdx, 6, nv(w, "uValue"))
    nc(sh, rowIdx, 7, nv(w, "gValue"))
    nc(sh, rowIdx, 8, nv(w, "bFactor"))
    nc(sh, rowIdx, 9, nv(w, "glassRatio"))
    nc(sh, rowIdx, 10, nv(w, "shading"))
    ni(sh, rowIdx, 11, Try(nv(w,"quantity").toInt).getOrElse(1))
    sc(sh, rowIdx, 12, sv(w, "installedIn"))

  private def fillFloorRow(sh: js.Dynamic, rowIdx: Int, f: ujson.Value): Unit =
    sc(sh, rowIdx, 0, sv(f, "code"))
    sc(sh, rowIdx, 1, sv(f, "description"))
    sc(sh, rowIdx, 2, sv(f, "floorType"))
    sc(sh, rowIdx, 3, sv(f, "renovationYear"))
    nc(sh, rowIdx, 4, nv(f, "area"))
    nc(sh, rowIdx, 5, nv(f, "uValue"))
    nc(sh, rowIdx, 6, nv(f, "bFactor"))
    ni(sh, rowIdx, 7, Try(nv(f,"quantity").toInt).getOrElse(1))

  private def fillBridgeRow(sh: js.Dynamic, rowIdx: Int, b: ujson.Value): Unit =
    sc(sh, rowIdx, 0, sv(b, "code"))
    sc(sh, rowIdx, 1, sv(b, "description"))
    sc(sh, rowIdx, 2, sv(b, "bridgeType"))
    sc(sh, rowIdx, 3, sv(b, "renovationYear"))
    nc(sh, rowIdx, 4, nv(b, "length"))
    nc(sh, rowIdx, 5, nv(b, "psiValue"))
    nc(sh, rowIdx, 6, nv(b, "chiValue"))
    ni(sh, rowIdx, 7, Try(nv(b,"quantity").toInt).getOrElse(1))
    nc(sh, rowIdx, 8, nv(b, "bFactor"))

  private def fillWaermeerzeuger(wb: js.Dynamic, p: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Wärmeerzeuger")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    av(p, "heatProducers").zipWithIndex.foreach { (h, i) =>
      val row = 3 + i
      sc(sh, row, 0, sv(h, "code"))
      sc(sh, row, 1, sv(h, "description"))
      sc(sh, row, 2, sv(h, "energySource"))
      nc(sh, row, 3, nv(h, "efficiencyHeating"))
      nc(sh, row, 4, nv(h, "efficiencyHotWater"))
      nc(sh, row, 5, nv(h, "oversizing"))
      sc(sh, row, 6, sv(h, "producerType"))
      sc(sh, row, 7, Try(h("suppliedDistributionSystems").arr.map(_.str).mkString(", ")).getOrElse(""))
      sc(sh, row, 8, sv(h, "heatEmissionType"))
      sc(sh, row, 9, sv(h, "constructionYear"))
      sc(sh, row, 10, sv(h, "condition"))
      sc(sh, row, 11, sv(h, "location"))
      nc(sh, row, 12, nv(h, "maintenanceCost"))
    }

  private def fillSpeicher(wb: js.Dynamic, p: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Speicher")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    av(p, "heatStorages").zipWithIndex.foreach { (s, i) =>
      val row = 3 + i
      sc(sh, row, 0, sv(s, "code"))
      sc(sh, row, 1, sv(s, "description"))
      sc(sh, row, 2, sv(s, "storageType"))
      nc(sh, row, 3, nv(s, "totalVolume"))
      nc(sh, row, 4, nv(s, "hotWaterVolume"))
      nc(sh, row, 5, nv(s, "heatingVolume"))
      sc(sh, row, 6, sv(s, "location"))
      sc(sh, row, 7, sv(s, "connectionQuality"))
      nc(sh, row, 8, nv(s, "heightToDiameterRatio"))
      nc(sh, row, 9, nv(s, "maintenanceCost"))
    }

  private def fillHeizungsverteilung(wb: js.Dynamic, p: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Versorgter Bereich Heizung")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    av(p, "heatingDistributions").zipWithIndex.foreach { (d, i) =>
      val row = 9 + i
      sc(sh, row, 0, sv(d, "code"))
      sc(sh, row, 1, sv(d, "description"))
      nc(sh, row, 2, nv(d, "area"))
      sc(sh, row, 3, sv(d, "distributionType"))
      sc(sh, row, 4, sv(d, "heatEmissionType"))
      Try(d("heatProducerCoverage").obj).foreach { cov =>
        cov.keys.toSeq.sorted.take(5).zipWithIndex.foreach { (code, ci) =>
          nc(sh, row, 5 + ci, Try(cov(code).num).getOrElse(0.0))
        }
      }
      sc(sh, row, 10, sv(d, "mainHeatProducer"))
      sc(sh, row, 11, if Try(d("distributionLinesInsulated").bool).getOrElse(false) then "Ja" else "Nein")
      nc(sh, row, 12, nv(d, "insulationThickness"))
      nc(sh, row, 13, nv(d, "insulationConductivity"))
      sc(sh, row, 14, sv(d, "flowReturnTemp"))
    }

  private def fillWarmwasserverteilung(wb: js.Dynamic, p: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Versorgter Bereich Warmwasser")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    av(p, "hotWaterDistributions").zipWithIndex.foreach { (d, i) =>
      val row = 9 + i
      sc(sh, row, 0, sv(d, "code"))
      sc(sh, row, 1, sv(d, "description"))
      nc(sh, row, 2, nv(d, "area"))
      sc(sh, row, 3, sv(d, "distributionType"))
      Try(d("heatProducerCoverage").obj).foreach { cov =>
        cov.keys.toSeq.sorted.take(5).zipWithIndex.foreach { (code, ci) =>
          nc(sh, row, 4 + ci, Try(cov(code).num).getOrElse(0.0))
        }
      }
      sc(sh, row, 9, if Try(d("distributionLinesInsulated").bool).getOrElse(false) then "Ja" else "Nein")
      nc(sh, row, 10, nv(d, "insulationThickness"))
      nc(sh, row, 11, nv(d, "insulationConductivity"))
      sc(sh, row, 12, sv(d, "warmKeeping"))
      sc(sh, row, 13, sv(d, "horizontalLinesLocation"))
    }

  private def fillLueftung(wb: js.Dynamic, p: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Lüftung")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    av(p, "ventilations").zipWithIndex.foreach { (v, i) =>
      val row = 8 + i
      sc(sh, row, 0, sv(v, "code"))
      sc(sh, row, 1, sv(v, "description"))
      sc(sh, row, 2, sv(v, "usageType"))
      sc(sh, row, 3, sv(v, "ventilationType"))
      sc(sh, row, 7, sv(v, "commissioningYear"))
      nc(sh, row, 8, nv(v, "maintenanceCost"))
      ni(sh, row, 9, Try(nv(v,"quantity").toInt).getOrElse(1))
      sc(sh, row, 10, sv(v, "roomsOrPersons"))
      sc(sh, row, 11, sv(v, "heatRecoveryType"))
      sc(sh, row, 12, sv(v, "fanType"))
      nc(sh, row, 13, nv(v, "airFlowRate"))
      nc(sh, row, 14, nv(v, "thermalAirRate"))
    }

  private def fillElektrizitaet(wb: js.Dynamic, p: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Elektrizitätsprod. ohne PVopti")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    av(p, "electricityProducers").zipWithIndex.foreach { (pr, i) =>
      val row = 8 + i
      sc(sh, row, 0, sv(pr, "code"))
      sc(sh, row, 1, sv(pr, "producerType"))
      sc(sh, row, 2, sv(pr, "connectedHeatProducer"))
      sc(sh, row, 3, sv(pr, "description"))
      sc(sh, row, 4, sv(pr, "commissioningYear"))
      nc(sh, row, 5, nv(pr, "annualProduction"))
      nc(sh, row, 6, nv(pr, "gridFeedIn"))
      nc(sh, row, 7, nv(pr, "maintenanceCost"))
    }

  // ── Area-based envelope fill (areaCalculations + uwertCalculations) ─────────

  /** Extract (uValueWithoutB, bFactor) from a single uwertCalculation JSON entry. */
  private def extractUVB(u: ujson.Value): (Double, Double) =
    val ist     = Try(u("istCalculation")).getOrElse(ujson.Obj())
    val directV = Try(ist("directUValueWithoutB").num).toOption.filter(_ != 0.0)
    val bFac    = Try(ist("bFactor").num).getOrElse(1.0)
    directV match
      case Some(v) => (v, bFac)
      case None =>
        val mats   = Try(ist("materials").arr.toSeq).getOrElse(Seq.empty)
        val rTotal = mats.foldLeft(0.0) { (acc, m) =>
          val d = Try(m("thickness").num).getOrElse(0.0)
          val l = Try(m("lambda").num).getOrElse(0.0)
          acc + (if l != 0 then d / l else 0.0)
        }
        (if rTotal != 0 then 1.0 / rTotal else 0.0, bFac)

  private def uValueFor(uwerts: Seq[ujson.Value], ct: String): (Double, Double) =
    uwerts.find(u => Try(u("componentType").str).getOrElse("") == ct) match
      case None    => (0.0, 1.0)
      case Some(u) => extractUVB(u)

  private def uValueForId(uwerts: Seq[ujson.Value], id: String): (Double, Double) =
    uwerts.find(u => Try(u("id").str).getOrElse("") == id) match
      case None    => (0.0, 1.0)
      case Some(u) => extractUVB(u)

  private def uvbForEntry(e: ujson.Value, ct: String, uwerts: Seq[ujson.Value]): (Double, Double) =
    val id = Try(e("uwertId").str).getOrElse("")
    if id.nonEmpty then uValueForId(uwerts, id) else uValueFor(uwerts, ct)

  private def areaEntriesFor(areaCalcs: ujson.Value, ct: String): Seq[ujson.Value] =
    Try(areaCalcs("calculations").arr.toSeq).getOrElse(Seq.empty)
      .find(c => Try(c("componentType").str).getOrElse("") == ct)
      .map(c => Try(c("entries").arr.toSeq).getOrElse(Seq.empty))
      .getOrElse(Seq.empty)
      .filter(e => nv(e, "area") > 0 || nv(e, "totalArea") > 0)

  private def fillRoofRowFromArea(sh: js.Dynamic, rowIdx: Int, e: ujson.Value, roofType: String, uVal: Double, bFac: Double): Unit =
    sc(sh, rowIdx, 0, sv(e, "kuerzel").take(5))
    sc(sh, rowIdx, 1, sv(e, "description"))
    sc(sh, rowIdx, 2, roofType)
    sc(sh, rowIdx, 3, sv(e, "orientation"))
    nc(sh, rowIdx, 5, nv(e, "area"))
    nc(sh, rowIdx, 6, uVal)
    nc(sh, rowIdx, 7, bFac)
    ni(sh, rowIdx, 8, math.max(1, Try(nv(e, "quantity").toInt).getOrElse(1)))

  private def fillWallRowFromArea(sh: js.Dynamic, rowIdx: Int, e: ujson.Value, wallType: String, uVal: Double, bFac: Double): Unit =
    sc(sh, rowIdx, 0, sv(e, "kuerzel").take(5))
    sc(sh, rowIdx, 1, sv(e, "description"))
    sc(sh, rowIdx, 2, wallType)
    sc(sh, rowIdx, 3, sv(e, "orientation"))
    nc(sh, rowIdx, 5, nv(e, "area"))
    nc(sh, rowIdx, 6, uVal)
    nc(sh, rowIdx, 7, bFac)
    ni(sh, rowIdx, 8, math.max(1, Try(nv(e, "quantity").toInt).getOrElse(1)))

  private def fillWindowRowFromArea(sh: js.Dynamic, rowIdx: Int, e: ujson.Value, winType: String): Unit =
    sc(sh, rowIdx, 0, sv(e, "kuerzel").take(5))
    sc(sh, rowIdx, 1, sv(e, "description"))
    sc(sh, rowIdx, 2, winType)
    sc(sh, rowIdx, 3, sv(e, "orientation"))
    nc(sh, rowIdx, 5, nv(e, "area"))
    ni(sh, rowIdx, 11, math.max(1, Try(nv(e, "quantity").toInt).getOrElse(1)))

  private def fillFloorRowFromArea(sh: js.Dynamic, rowIdx: Int, e: ujson.Value, floorType: String, uVal: Double, bFac: Double): Unit =
    sc(sh, rowIdx, 0, sv(e, "kuerzel").take(5))
    sc(sh, rowIdx, 1, sv(e, "description"))
    sc(sh, rowIdx, 2, floorType)
    nc(sh, rowIdx, 4, nv(e, "area"))
    nc(sh, rowIdx, 5, uVal)
    nc(sh, rowIdx, 6, bFac)
    ni(sh, rowIdx, 7, math.max(1, Try(nv(e, "quantity").toInt).getOrElse(1)))

  private def insertAndFillFromArea[A](
    sh: js.Dynamic, insertAt: Int, rows: Seq[A],
    fill: (Int, A) => Unit
  ): Unit =
    if rows.isEmpty then return
    if lastRowIdx(sh) >= insertAt then insertEmptyRows(sh, insertAt, rows.length)
    rows.zipWithIndex.foreach { (item, i) => fill(insertAt + i, item) }

  private def fillRoofsFromArea(wb: js.Dynamic, areaCalcs: ujson.Value, uwerts: Seq[ujson.Value]): Unit =
    val shOpt = getSheet(wb, "Dächer und Decken")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    val rows =
      areaEntriesFor(areaCalcs, "PitchedRoof").map(e => { val (u,b) = uvbForEntry(e, "PitchedRoof", uwerts); (e, "Schrägdach, unbeheizt", u, b) }) ++
      areaEntriesFor(areaCalcs, "FlatRoof").map(e => { val (u,b) = uvbForEntry(e, "FlatRoof", uwerts); (e, "Flachdach/Terrasse", u, b) }) ++
      areaEntriesFor(areaCalcs, "AtticFloor").map(e => { val (u,b) = uvbForEntry(e, "AtticFloor", uwerts); (e, "Decke gegen unbeheizt", u, b) })
    insertAndFillFromArea(sh, 10, rows, (rowIdx, t) => fillRoofRowFromArea(sh, rowIdx, t._1, t._2, t._3, t._4))

  private def fillWallsFromArea(wb: js.Dynamic, areaCalcs: ujson.Value, uwerts: Seq[ujson.Value]): Unit =
    val shOpt = getSheet(wb, "Wände")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    val rows =
      areaEntriesFor(areaCalcs, "ExteriorWall").map(e => { val (u,b) = uvbForEntry(e, "ExteriorWall", uwerts); (e, "Aussenwand", u, b) }) ++
      areaEntriesFor(areaCalcs, "BasementWallToEarth").map(e => { val (u,b) = uvbForEntry(e, "BasementWallToEarth", uwerts); (e, "Wand gegen Erdreich", u, b) }) ++
      areaEntriesFor(areaCalcs, "BasementWallToUnheated").map(e => { val (u,b) = uvbForEntry(e, "BasementWallToUnheated", uwerts); (e, "Wand gegen unbeheizt", u, b) }) ++
      areaEntriesFor(areaCalcs, "BasementWallToOutside").map(e => { val (u,b) = uvbForEntry(e, "BasementWallToOutside", uwerts); (e, "Kellerwand gegen Aussen", u, b) })
    insertAndFillFromArea(sh, 10, rows, (rowIdx, t) => fillWallRowFromArea(sh, rowIdx, t._1, t._2, t._3, t._4))

  private def fillWindowsFromArea(wb: js.Dynamic, areaCalcs: ujson.Value): Unit =
    val shOpt = getSheet(wb, "Fenster und Türen")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    val rows =
      areaEntriesFor(areaCalcs, "Window").map((_, "Fenster")) ++
      areaEntriesFor(areaCalcs, "Door").map((_, "Tür"))
    insertAndFillFromArea(sh, 9, rows, (rowIdx, t) => fillWindowRowFromArea(sh, rowIdx, t._1, t._2))

  private def fillFloorsFromArea(wb: js.Dynamic, areaCalcs: ujson.Value, uwerts: Seq[ujson.Value]): Unit =
    val shOpt = getSheet(wb, "Böden")
    if shOpt.isEmpty then return
    val sh = shOpt.get
    val rows =
      areaEntriesFor(areaCalcs, "BasementFloor").map(e => { val (u,b) = uvbForEntry(e, "BasementFloor", uwerts); (e, "Boden gegen Erdreich", u, b) }) ++
      areaEntriesFor(areaCalcs, "BasementCeiling").map(e => { val (u,b) = uvbForEntry(e, "BasementCeiling", uwerts); (e, "Boden gegen unbeheizt", u, b) }) ++
      areaEntriesFor(areaCalcs, "FloorToOutside").map(e => { val (u,b) = uvbForEntry(e, "FloorToOutside", uwerts); (e, "Boden gegen aussen", u, b) })
    insertAndFillFromArea(sh, 10, rows, (rowIdx, t) => fillFloorRowFromArea(sh, rowIdx, t._1, t._2, t._3, t._4))




end ExcelExportService
