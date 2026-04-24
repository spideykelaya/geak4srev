import org.apache.poi.ss.usermodel.{Workbook, Sheet, Cell, Row, CellType}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import scala.util.Try

object BerechnungstoolService:

  // ── HGT correction factors (Zürich-SMA, Mittelwert 2011–2020 = 3124.7) ───
  private val hgtFactors: Map[Int, Double] = Map(
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
  private def hgt(year: Int): Double = hgtFactors.getOrElse(year, 1.0)

  // ── Safe ujson accessors ──────────────────────────────────────────────────

  private def sv(v: ujson.Value, key: String): String =
    Try(v(key).str)
      .orElse(Try { val d = v(key).num; if d == d.toLong then d.toLong.toString else d.toString })
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

  private def isFormula(sheet: Sheet, rowIdx: Int, colIdx: Int): Boolean =
    Option(sheet.getRow(rowIdx))
      .flatMap(r => Option(r.getCell(colIdx)))
      .exists(_.getCellType == CellType.FORMULA)

  /** Write string — skips formula cells */
  private def sc(sheet: Sheet, row: Int, col: Int, value: String): Unit =
    if value.nonEmpty && !isFormula(sheet, row, col) then
      getCell(sheet, row, col).setCellValue(value)

  /** Write number — skips formula cells */
  private def nc(sheet: Sheet, row: Int, col: Int, value: Double): Unit =
    if !isFormula(sheet, row, col) then
      getCell(sheet, row, col).setCellValue(value)

  /** Write number regardless of formula (overwrite) */
  private def ncForce(sheet: Sheet, row: Int, col: Int, value: Double): Unit =
    getCell(sheet, row, col).setCellValue(value)

  private def clearIfNotFormula(sheet: Sheet, rowIdx: Int, colIdx: Int): Unit =
    if !isFormula(sheet, rowIdx, colIdx) then
      Option(sheet.getRow(rowIdx)).flatMap(r => Option(r.getCell(colIdx))).foreach(_.setBlank())

  // ── Public entry point ────────────────────────────────────────────────────

  def generate(projectJson: String): Array[Byte] =
    val p = ujson.read(projectJson)

    val stream = getClass.getClassLoader.getResourceAsStream("Berechnungstool_260.xlsx")
    if stream == null then throw new Exception("Template nicht gefunden: Berechnungstool_260.xlsx")

    val wb = new XSSFWorkbook(stream)
    stream.close()

    fillEnergieEbf(wb, p)
    fillFlaechen(wb, p)

    // Force Excel to recalculate all formulas when opening the file
    wb.setForceFormulaRecalculation(true)

    val out = new ByteArrayOutputStream()
    wb.write(out)
    wb.close()
    out.toByteArray()

  // ── Sheet: Energie_EBF ────────────────────────────────────────────────────
  //
  // Formula overview (preserved):
  //   K7  = C34              (total EBF m2)
  //   E13 = AVERAGE(E8:E12)  (Mittelwert Strom)
  //   E14 = E13/C34          (Energiekennzahl Strom)
  //   D19 = C19*B29          (kWh from volume × calorific value, row 1 only)
  //   C24 = AVERAGE(C19:C23) (Mittelwert Gas Volumen)
  //   D24 = AVERAGE(D19:D23) (Mittelwert Gas kWh)
  //   D25 = D24/C34          (Energiekennzahl Gas)
  //   C34 = Tabelle315[Fläche Total total] (Total EBF, links to Flächen sheet)
  //   C45 = AVERAGE(C40:C44) (Mittelwert Wasser)
  //   C46 = C45/C34          (Spezifischer Wasserverbrauch)
  //   Right-side K-column: Heizleistungsbedarf calculations — all preserved

  private def fillEnergieEbf(wb: Workbook, p: ujson.Value): Unit =
    val sh = wb.getSheet("Energie_EBF")
    if sh == null then return

    val proj = ov(p, "project")
    val bloc = ov(proj, "buildingLocation")
    val ba   = ov(bloc, "address")
    val bd   = ov(proj, "buildingData")

    // Row 2 (idx 1), col D (idx 3): Projektnummer und Objekt
    val projName = sv(proj, "projectName")
    val address  = List(sv(ba, "street"), sv(ba, "houseNumber"), sv(ba, "zipCode"), sv(ba, "city"))
                     .filter(_.nonEmpty).mkString(" ")
    val projObj  = List(projName, address).filter(_.nonEmpty).mkString(" | ")
    sc(sh, 1, 3, projObj)

    // Row 3 (idx 2), col D (idx 3): Baujahr
    sc(sh, 2, 3, sv(bd, "constructionYear"))

    // C34 is a formula (= table total from Flächen) — do NOT write, it auto-updates.

    val energyOpt = Try(p("energyConsumption")).filter(_ != ujson.Null).toOption
    energyOpt.foreach { energy =>
      val calorific = Try(energy("calorificValue").num).getOrElse(10.0)

      // Row 29 (idx 28), col B (idx 1): calorific value (Heizwert Gas/Öl)
      // This feeds D19=C19*B29, so it must reflect the project's calorific value.
      nc(sh, 28, 1, calorific)

      // ── Elektro Allg. — data rows 8–12 (idx 7–11) ─────────────────────────
      // Input cols: B=Jahr, C=HT, D=NT, E=Bereinigt [kWh] (all plain cells).
      // Preserved formulas: E13=AVERAGE(E8:E12), E14=E13/C34
      av(energy, "electricityEntries").take(5).zipWithIndex.foreach { (e, i) =>
        val rowIdx   = 7 + i
        val year     = Try(e("year").num.toInt).getOrElse(0)
        val ht       = Try(e("htKwh").num).getOrElse(0.0)
        val nt       = Try(e("ntKwh").num).getOrElse(0.0)
        val total    = ht + nt
        val bereinigt = total * hgt(year)
        if year != 0    then nc(sh, rowIdx, 1, year.toDouble)
        if ht    > 0    then nc(sh, rowIdx, 2, ht)
        if nt    > 0    then nc(sh, rowIdx, 3, nt)
        if bereinigt > 0 then nc(sh, rowIdx, 4, bereinigt)  // col E — HGT-bereinigt
      }

      // ── Gas bzw. Öl — data rows 19–23 (idx 18–22) ────────────────────────
      // Col C = volume (input). Col D = Bereinigt [kWh].
      //   All 5 D-cells written with ncForce so D24=AVERAGE is consistent.
      // Preserved formulas: C24=AVERAGE(C19:C23), D24=AVERAGE(D19:D23), D25=D24/C34
      av(energy, "fuelEntries").take(5).zipWithIndex.foreach { (e, i) =>
        val rowIdx   = 18 + i
        val year     = Try(e("year").num.toInt).getOrElse(0)
        val vol      = Try(e("volumeLOrM3").num).getOrElse(0.0)
        val kwh      = Try(e("directKwh").num).filter(_ > 0)
                         .getOrElse(if vol > 0 then vol * calorific else 0.0)
        val bereinigt = kwh * hgt(year)
        if year != 0    then nc(sh, rowIdx, 1, year.toDouble)
        if vol   > 0    then nc(sh, rowIdx, 2, vol)
        // Force-write bereinigt to all D rows (D19 has a formula → override it)
        if bereinigt > 0 then ncForce(sh, rowIdx, 3, bereinigt)
      }

      // ── Kalt-Wasser — data rows 40–44 (idx 39–43) ────────────────────────
      // Preserved formulas: C45=AVERAGE(C40:C44), C46=C45/C34
      av(energy, "waterEntries").take(5).zipWithIndex.foreach { (e, i) =>
        val rowIdx = 39 + i
        val year   = Try(e("year").num.toInt).getOrElse(0)
        val m3     = Try(e("consumptionM3").num).getOrElse(0.0)
        if year != 0 then nc(sh, rowIdx, 1, year.toDouble)
        if m3    > 0  then nc(sh, rowIdx, 2, m3)
      }
    }

  // ── Sheet: Flächen ────────────────────────────────────────────────────────
  //
  // EBF table (rows 7–11, idx 6–10):
  //   Col B = Kürzel       (input, overwrites the 1–5 sequence numbers)
  //   Col C = Ausrichtung  (input)
  //   Col D = Beschrieb    (input)
  //   Col E = Länge        (input)
  //   Col F = Breite       (input)
  //   Col G = Fläche       — all rows: formula =Tabelle315[Länge]*Tabelle315[Breite]
  //   Col H = Anzahl       — formula =1 in all rows  → never write
  //   Col I = Fläche Total — formula =G*H in all rows → never write
  //   Col J = Fläche Neu   (input, plain)
  //   Col K = Anzahl Neu   (input, plain)
  //   Col L = Fläche Total Neu (input, plain)
  //   Col M = Beschrieb Neu (input, plain)
  // Row 12 = SUBTOTAL totals → never write
  //
  // Polygon-only areas (no L/W): G is written directly with ncForce; the
  // table's calculated-column formula for G is cleared so Excel does not
  // restore it on open (Excel Tables propagate column formulas to all rows).

  private def clearTableCalcFormulas(sh: Sheet): Unit =
    import org.apache.poi.xssf.usermodel.XSSFSheet
    import scala.jdk.CollectionConverters.*
    sh match
      case xsh: XSSFSheet =>
        xsh.getTables.asScala.foreach { tbl =>
          val cols = tbl.getCTTable.getTableColumns.getTableColumnArray
          cols.foreach { col =>
            if col.isSetCalculatedColumnFormula then
              col.unsetCalculatedColumnFormula()
          }
        }
      case _ =>

  private def fillFlaechen(wb: Workbook, p: ujson.Value): Unit =
    val sh = Option(wb.getSheet("Flächen"))
               .orElse((0 until wb.getNumberOfSheets).map(wb.getSheetAt)
                 .find(s => s.getSheetName.contains("chen")))
               .orNull
    if sh == null then return

    // Remove Excel-Table calculated-column formulas so ncForce values survive
    clearTableCalcFormulas(sh)

    val areaCalcsOpt = Try(p("areaCalculations")).filter(_ != ujson.Null).toOption
    val ebfEntries = areaCalcsOpt.flatMap { areaCalcs =>
      Try(areaCalcs("calculations").arr.toSeq).toOption
        .flatMap(_.find(c => Try(c("componentType").str).getOrElse("") == "EBF"))
        .map(c => Try(c("entries").arr.toSeq).getOrElse(Seq.empty))
    }.getOrElse(Seq.empty)

    // EBF rows 7–11 (idx 6–10), 5 rows in template
    (0 until 5).foreach { i =>
      val rowIdx = 6 + i

      // Clear input cols B–F and J–M (skip formula cols G/H/I)
      Seq(1, 2, 3, 4, 5, 9, 10, 11, 12).foreach(col => clearIfNotFormula(sh, rowIdx, col))

      if i < ebfEntries.length then
        val e         = ebfEntries(i)
        val length    = Try(e("length").num).getOrElse(0.0)
        val width     = Try(e("width").num).getOrElse(0.0)
        val area      = Try(e("area").num).getOrElse(0.0)
        val quantity  = math.max(1, Try(e("quantity").num.toInt).getOrElse(1))
        val totalArea = Try(e("totalArea").num).getOrElse(area * quantity)

        sc(sh, rowIdx, 1, sv(e, "kuerzel"))          // B: Kürzel
        sc(sh, rowIdx, 2, sv(e, "orientation"))       // C: Ausrichtung
        sc(sh, rowIdx, 3, sv(e, "description"))       // D: Beschrieb

        // Measured areas: write L and W so the formula G=E×F computes correctly.
        // Polygon areas (no L×W): write totalArea to E and 1 to F → G=totalArea×1=totalArea.
        if length > 0 && width > 0 then
          nc(sh, rowIdx, 4, length)
          nc(sh, rowIdx, 5, width)
        else if totalArea > 0 then
          ncForce(sh, rowIdx, 4, math.round(totalArea).toDouble)  // E: Länge = totalArea
          ncForce(sh, rowIdx, 5, 1.0)                             // F: Breite = 1

        // H = formula =1,  I = formula =G*H  → both auto-compute, never write

        // J–M: Fläche Neu / Anzahl Neu / Fläche Total Neu / Beschrieb Neu
        val areaNew   = Try(e("areaNew").num).getOrElse(0.0)
        val qtyNew    = Try(e("quantityNew").num.toInt).getOrElse(0)
        val totalNew  = Try(e("totalAreaNew").num).getOrElse(0.0)
        val descNew   = sv(e, "descriptionNew")
        if areaNew  > 0 then nc(sh, rowIdx, 9,  areaNew)
        if qtyNew   > 0 then nc(sh, rowIdx, 10, qtyNew.toDouble)
        if totalNew > 0 then nc(sh, rowIdx, 11, totalNew)
        if descNew.nonEmpty then sc(sh, rowIdx, 12, descNew)
    }
