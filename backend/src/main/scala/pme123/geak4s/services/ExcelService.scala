import org.apache.poi.ss.usermodel.{Workbook, Sheet, Cell, Row}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.poifs.crypt.{EncryptionInfo, EncryptionMode}
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import java.io.ByteArrayOutputStream
import scala.util.Try

object ExcelService:

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

  // ── POI cell helpers ──────────────────────────────────────────────────────

  private def getCell(sheet: Sheet, rowIdx: Int, colIdx: Int): Cell =
    val row = Option(sheet.getRow(rowIdx)).getOrElse(sheet.createRow(rowIdx))
    Option(row.getCell(colIdx)).getOrElse(row.createCell(colIdx))

  private def sc(sheet: Sheet, row: Int, col: Int, value: String): Unit =
    if value.nonEmpty then getCell(sheet, row, col).setCellValue(value)

  private def nc(sheet: Sheet, row: Int, col: Int, value: Double): Unit =
    if value != 0.0 then getCell(sheet, row, col).setCellValue(value)

  private def ni(sheet: Sheet, row: Int, col: Int, value: Int): Unit =
    if value != 0 then getCell(sheet, row, col).setCellValue(value.toDouble)

  // ── Sheet fillers ─────────────────────────────────────────────────────────

  private def fillProjekt(wb: Workbook, p: ujson.Value): Unit =
    val sh   = wb.getSheet("Projekt")
    val proj = ov(p, "project")
    val cli  = ov(proj, "client")
    val ca   = ov(cli, "address")
    val bloc = ov(proj, "buildingLocation")
    val ba   = ov(bloc, "address")
    val bd   = ov(proj, "buildingData")
    val desc = ov(proj, "descriptions")

    // Row 2 (idx 1): Projektbezeichnung
    sc(sh, 1, 1, sv(proj, "projectName"))

    // Auftraggeber (rows 5–15 → idx 4–14)
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

    // EGID/EDID (header row 5 idx 4, data from row 6 idx 5)
    val egidEntries = av(ov(proj, "egidEdidGroup"), "entries")
    egidEntries.zipWithIndex.foreach { (e, i) =>
      val ea = ov(e, "address")
      val rowIdx = 5 + i
      sc(sh, rowIdx, 3, sv(e, "egid"))
      sc(sh, rowIdx, 4, sv(e, "edid"))
      sc(sh, rowIdx, 5, List(sv(ea,"street"), sv(ea,"houseNumber")).filter(_.nonEmpty).mkString(" "))
      sc(sh, rowIdx, 6, List(sv(ea,"zipCode"), sv(ea,"city")).filter(_.nonEmpty).mkString(" "))
    }

    // Gebäude (rows 18–35 → idx 17–34)
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

    // Beschreibungen (rows 38, 41, 44 → idx 37, 40, 43)
    sc(sh, 37, 1, sv(desc, "buildingDescription"))
    sc(sh, 40, 1, sv(desc, "envelopeDescription"))
    sc(sh, 43, 1, sv(desc, "hvacDescription"))

  private def fillGebaeudenutzungen(wb: Workbook, p: ujson.Value): Unit =
    val sh     = wb.getSheet("Gebäudenutzungen")
    if sh == null then return
    val usages = av(p, "buildingUsages").take(3)
    usages.zipWithIndex.foreach { (u, i) =>
      val col = 1 + i  // B=1, C=2, D=3
      sc(sh, 4, col, sv(u, "usageType"))
      sc(sh, 5, col, sv(u, "usageSubType"))
      nc(sh, 6, col, nv(u, "area"))
      nc(sh, 7, col, nv(u, "areaPercentage"))
      sc(sh, 8, col, sv(u, "constructionYear"))
    }

  private def fillEnvelopeSheet(
    wb: Workbook, sheetName: String,
    items: Seq[ujson.Value], dataStartRow: Int,
    fillRow: (Sheet, Int, ujson.Value) => Unit
  ): Unit =
    val sh = wb.getSheet(sheetName)
    if sh == null then return
    items.zipWithIndex.foreach { (item, i) =>
      fillRow(sh, dataStartRow + i, item)
    }

  private def fillRoofRow(sh: Sheet, rowIdx: Int, c: ujson.Value): Unit =
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

  private def fillWallRow(sh: Sheet, rowIdx: Int, w: ujson.Value): Unit =
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

  private def fillWindowRow(sh: Sheet, rowIdx: Int, w: ujson.Value): Unit =
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

  private def fillFloorRow(sh: Sheet, rowIdx: Int, f: ujson.Value): Unit =
    sc(sh, rowIdx, 0, sv(f, "code"))
    sc(sh, rowIdx, 1, sv(f, "description"))
    sc(sh, rowIdx, 2, sv(f, "floorType"))
    sc(sh, rowIdx, 3, sv(f, "renovationYear"))
    nc(sh, rowIdx, 4, nv(f, "area"))
    nc(sh, rowIdx, 5, nv(f, "uValue"))
    nc(sh, rowIdx, 6, nv(f, "bFactor"))
    ni(sh, rowIdx, 7, Try(nv(f,"quantity").toInt).getOrElse(1))

  private def fillBridgeRow(sh: Sheet, rowIdx: Int, b: ujson.Value): Unit =
    sc(sh, rowIdx, 0, sv(b, "code"))
    sc(sh, rowIdx, 1, sv(b, "description"))
    sc(sh, rowIdx, 2, sv(b, "bridgeType"))
    sc(sh, rowIdx, 3, sv(b, "renovationYear"))
    nc(sh, rowIdx, 4, nv(b, "length"))
    nc(sh, rowIdx, 5, nv(b, "psiValue"))
    nc(sh, rowIdx, 6, nv(b, "chiValue"))
    ni(sh, rowIdx, 7, Try(nv(b,"quantity").toInt).getOrElse(1))
    nc(sh, rowIdx, 8, nv(b, "bFactor"))

  private def fillWaermeerzeuger(wb: Workbook, p: ujson.Value): Unit =
    val sh = wb.getSheet("Wärmeerzeuger")
    if sh == null then return
    av(p, "heatProducers").zipWithIndex.foreach { (h, i) =>
      val row = 3 + i  // data starts row 4 (idx 3)
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

  private def fillSpeicher(wb: Workbook, p: ujson.Value): Unit =
    val sh = wb.getSheet("Speicher")
    if sh == null then return
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

  private def fillHeizungsverteilung(wb: Workbook, p: ujson.Value): Unit =
    val sh = Option(wb.getSheet("Versorgter Bereich Heizung")).orNull
    if sh == null then return
    av(p, "heatingDistributions").zipWithIndex.foreach { (d, i) =>
      val row = 9 + i  // data starts row 10 (idx 9)
      sc(sh, row, 0, sv(d, "code"))
      sc(sh, row, 1, sv(d, "description"))
      nc(sh, row, 2, nv(d, "area"))
      sc(sh, row, 3, sv(d, "distributionType"))
      sc(sh, row, 4, sv(d, "heatEmissionType"))
      // WE-1..5 coverage (cols F-J = 5-9)
      Try(d("heatProducerCoverage").obj).foreach { cov =>
        val codes = cov.keys.toSeq.sorted
        codes.take(5).zipWithIndex.foreach { (code, ci) =>
          nc(sh, row, 5 + ci, Try(cov(code).num).getOrElse(0.0))
        }
      }
      sc(sh, row, 10, sv(d, "mainHeatProducer"))
      sc(sh, row, 11, if Try(d("distributionLinesInsulated").bool).getOrElse(false) then "Ja" else "Nein")
      nc(sh, row, 12, nv(d, "insulationThickness"))
      nc(sh, row, 13, nv(d, "insulationConductivity"))
      sc(sh, row, 14, sv(d, "flowReturnTemp"))
    }

  private def fillWarmwasserverteilung(wb: Workbook, p: ujson.Value): Unit =
    val sh = Option(wb.getSheet("Versorgter Bereich Warmwasser")).orNull
    if sh == null then return
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

  private def fillLueftung(wb: Workbook, p: ujson.Value): Unit =
    val sh = wb.getSheet("Lüftung")
    if sh == null then return
    av(p, "ventilations").zipWithIndex.foreach { (v, i) =>
      val row = 8 + i  // data starts row 9 (idx 8)
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

  private def fillElektrizitaet(wb: Workbook, p: ujson.Value): Unit =
    val sh = Option(wb.getSheet("Elektrizitätsprod. ohne PVopti")).orNull
    if sh == null then return
    av(p, "electricityProducers").zipWithIndex.foreach { (pr, i) =>
      val row = 8 + i  // data starts row 9 (idx 8)
      sc(sh, row, 0, sv(pr, "code"))
      sc(sh, row, 1, sv(pr, "producerType"))
      sc(sh, row, 2, sv(pr, "connectedHeatProducer"))
      sc(sh, row, 3, sv(pr, "description"))
      sc(sh, row, 4, sv(pr, "commissioningYear"))
      nc(sh, row, 5, nv(pr, "annualProduction"))
      nc(sh, row, 6, nv(pr, "gridFeedIn"))
      nc(sh, row, 7, nv(pr, "maintenanceCost"))
    }

  // ── Public entry point ────────────────────────────────────────────────────

  def generateExcel(projectJson: String): Array[Byte] =
    val p = ujson.read(projectJson)

    val templateStream = getClass.getClassLoader.getResourceAsStream("GEAK-EXCEL-template.xlsx")
    if templateStream == null then throw new Exception("Template not found: GEAK-EXCEL-template.xlsx")

    val wb = new XSSFWorkbook(templateStream)
    templateStream.close()

    fillProjekt(wb, p)
    fillGebaeudenutzungen(wb, p)

    fillEnvelopeSheet(wb, "Dächer und Decken",   av(p, "roofsCeilings"),    10, fillRoofRow)
    fillEnvelopeSheet(wb, "Wände",               av(p, "walls"),             10, fillWallRow)
    fillEnvelopeSheet(wb, "Fenster und Türen",   av(p, "windowsDoors"),       9, fillWindowRow)
    fillEnvelopeSheet(wb, "Böden",               av(p, "floors"),            10, fillFloorRow)
    fillEnvelopeSheet(wb, "Wärmebrücken",        av(p, "thermalBridges"),     7, fillBridgeRow)

    fillWaermeerzeuger(wb, p)
    fillSpeicher(wb, p)
    fillHeizungsverteilung(wb, p)
    fillWarmwasserverteilung(wb, p)
    fillLueftung(wb, p)
    fillElektrizitaet(wb, p)

    // Step 1: write filled workbook to XLSX bytes
    val xlsxBytes = new ByteArrayOutputStream()
    wb.write(xlsxBytes)
    wb.close()

    // Step 2: wrap in OLE2 container with agile encryption (same as original template)
    // VelvetSweatshop is the standard Excel read-only default; tools open it without a password dialog
    val poifs    = new POIFSFileSystem()
    val encInfo  = new EncryptionInfo(EncryptionMode.agile)
    val encryptor = encInfo.getEncryptor
    encryptor.confirmPassword("VelvetSweatshop")
    val encStream = encryptor.getDataStream(poifs)
    encStream.write(xlsxBytes.toByteArray())
    encStream.close()

    val out = new ByteArrayOutputStream()
    poifs.writeFilesystem(out)
    poifs.close()
    out.toByteArray()
