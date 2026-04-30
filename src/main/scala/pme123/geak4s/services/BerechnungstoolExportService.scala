package pme123.geak4s.services

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.ArrayBuffer
import org.scalajs.dom
import scala.util.Try
import scala.scalajs.js.RegExp

/** Generates the Berechnungstool 260 XLSX from a serialized [[pme123.geak4s.domain.GeakProject]].
 *
 *  Loads `public/templates/Berechnungstool_260.xlsx`, fills it from the project
 *  JSON via ExcelJS and returns the resulting `.xlsx` as a [[dom.Blob]].
 *
 *  Ported 1:1 from the backend `BerechnungstoolService` (Apache POI). Two
 *  concerns are critical here, both inherited from the backend:
 *    - **Formula preservation** — the template carries a network of formulas
 *      (averages, EBF/area links, Heizleistungsbedarf). Writers `sc`/`nc`
 *      check `cell.type == Formula` (ValueType 6) before writing.
 *    - **Polygon area override** — for entries without L×W, the column G
 *      (Fläche) formula is overwritten via `ncForce`. The Excel Table column
 *      formula on G is removed up-front so Excel does not restore it on open.
 */
object BerechnungstoolExportService:

  @js.native
  @JSImport("exceljs", JSImport.Default)
  private object ExcelJS extends js.Object

  @js.native
  @JSImport("pizzip", JSImport.Default)
  private class PizZip(data: js.Any) extends js.Object

  private val TemplateUrl  = "templates/Berechnungstool_260.xlsx"
  private val XlsxMimeType =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

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

  def generate(projectJson: String): Future[dom.Blob] =
    val p = ujson.read(projectJson)
    fetchTemplate().flatMap { buffer =>
      val mod      = ExcelJS.asInstanceOf[js.Dynamic]
      val wb       = js.Dynamic.newInstance(mod.Workbook)()
      val patched  = sanitizeTemplate(buffer)
      wb.xlsx.load(patched.asInstanceOf[js.Any])
        .asInstanceOf[js.Promise[js.Any]].toFuture.flatMap { _ =>
        fillEnergieEbf(wb, p)
        fillFlaechen(wb, p)

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

  /** Strip child elements from every `<tableColumn>` in `xl/tables/table*.xml`.
   *
   *  Workaround for ExcelJS issue #1240: `TableColumnXform.parseOpen` returns
   *  `false` for any unrecognised child node (e.g. `calculatedColumnFormula`,
   *  `totalsRowFormula`), which causes the surrounding `ListXform` to drop the
   *  column from `tableColumns.model`. Later, `TableXform.parseClose` walks
   *  `autoFilter.model.columns` and writes `model.columns[index].filterButton`
   *  — throwing on the missing entries.
   *
   *  Removing the child elements is harmless: cell formulas live in the
   *  worksheet XML; the table-column `calculatedColumnFormula` is only used by
   *  Excel to extend the formula when new rows are inserted, which we do not
   *  do at runtime. Filter, totals and styling metadata are preserved.
   */
  private def sanitizeTemplate(buffer: ArrayBuffer): ArrayBuffer =
    val zip       = new PizZip(buffer.asInstanceOf[js.Any]).asInstanceOf[js.Dynamic]
    val tableRx   = new RegExp("^xl/tables/table[0-9]+\\.xml$")
    // Require whitespace right after `tableColumn` so we don't match the
    // parent `<tableColumns>`, and exclude `/` so we never match self-closing
    // tags (their lazy match would otherwise jump across siblings).
    val openTag   = new RegExp("<tableColumn(\\s[^/>]*)>[\\s\\S]*?</tableColumn>", "g")
    val files     = zip.file(tableRx).asInstanceOf[js.Array[js.Dynamic]]
    files.foreach { f =>
      val name    = f.name.asInstanceOf[String]
      val xml     = f.asText().asInstanceOf[String]
      val cleaned = xml.asInstanceOf[js.Dynamic].replace(openTag, "<tableColumn$1/>").asInstanceOf[String]
      if cleaned != xml then zip.file(name, cleaned)
    }
    zip.generate(js.Dynamic.literal(`type` = "arraybuffer")).asInstanceOf[ArrayBuffer]



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

  // ── ExcelJS cell helpers (row/col 0-based to match backend semantics) ─────

  private def cellAt(sh: js.Dynamic, row: Int, col: Int): js.Dynamic =
    sh.getRow(row + 1).getCell(col + 1)

  /** ExcelJS ValueType.Formula = 6 */
  private def isFormula(sh: js.Dynamic, row: Int, col: Int): Boolean =
    val t = cellAt(sh, row, col).`type`.asInstanceOf[js.UndefOr[Int]]
    t.toOption.contains(6)

  /** Write string — skips formula cells */
  private def sc(sh: js.Dynamic, row: Int, col: Int, value: String): Unit =
    if value.nonEmpty && !isFormula(sh, row, col) then
      cellAt(sh, row, col).value = value

  /** Write number — skips formula cells */
  private def nc(sh: js.Dynamic, row: Int, col: Int, value: Double): Unit =
    if !isFormula(sh, row, col) then
      cellAt(sh, row, col).value = value

  /** Write number regardless of formula (overwrite). */
  private def ncForce(sh: js.Dynamic, row: Int, col: Int, value: Double): Unit =
    cellAt(sh, row, col).value = value

  private def clearIfNotFormula(sh: js.Dynamic, row: Int, col: Int): Unit =
    if !isFormula(sh, row, col) then
      cellAt(sh, row, col).value = null

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

  private def fillEnergieEbf(wb: js.Dynamic, p: ujson.Value): Unit =
    val sh = wb.getWorksheet("Energie_EBF").asInstanceOf[js.Dynamic]
    if sh == null || js.isUndefined(sh.asInstanceOf[js.Any]) then return

    val proj = ov(p, "project")
    val bloc = ov(proj, "buildingLocation")
    val ba   = ov(bloc, "address")
    val bd   = ov(proj, "buildingData")

    val projName = sv(proj, "projectName")
    val address  = List(sv(ba, "street"), sv(ba, "houseNumber"), sv(ba, "zipCode"), sv(ba, "city"))
                     .filter(_.nonEmpty).mkString(" ")
    val projObj  = List(projName, address).filter(_.nonEmpty).mkString(" | ")
    sc(sh, 1, 3, projObj)
    sc(sh, 2, 3, sv(bd, "constructionYear"))

    val energyOpt = Try(p("energyConsumption")).filter(_ != ujson.Null).toOption
    energyOpt.foreach { energy =>
      val calorific = Try(energy("calorificValue").num).getOrElse(10.0)

      // B29 (idx 28,1): calorific value — feeds D19 = C19*B29
      nc(sh, 28, 1, calorific)

      // Elektro Allg. — rows 8–12 (idx 7–11): B=Jahr, C=HT, D=NT, E=Bereinigt
      av(energy, "electricityEntries").take(5).zipWithIndex.foreach { (e, i) =>
        val rowIdx    = 7 + i
        val year      = Try(e("year").num.toInt).getOrElse(0)
        val ht        = Try(e("htKwh").num).getOrElse(0.0)
        val nt        = Try(e("ntKwh").num).getOrElse(0.0)
        val bereinigt = (ht + nt) * hgt(year)
        if year != 0     then nc(sh, rowIdx, 1, year.toDouble)
        if ht > 0        then nc(sh, rowIdx, 2, ht)
        if nt > 0        then nc(sh, rowIdx, 3, nt)
        if bereinigt > 0 then nc(sh, rowIdx, 4, bereinigt)
      }

      // Gas/Öl — rows 19–23 (idx 18–22). All 5 D-cells written with ncForce
      // so D24=AVERAGE(D19:D23) stays consistent (D19 carries a formula).
      av(energy, "fuelEntries").take(5).zipWithIndex.foreach { (e, i) =>
        val rowIdx    = 18 + i
        val year      = Try(e("year").num.toInt).getOrElse(0)
        val vol       = Try(e("volumeLOrM3").num).getOrElse(0.0)
        val kwh       = Try(e("directKwh").num).filter(_ > 0)
                          .getOrElse(if vol > 0 then vol * calorific else 0.0)
        val bereinigt = kwh * hgt(year)
        if year != 0     then nc(sh, rowIdx, 1, year.toDouble)
        if vol > 0       then nc(sh, rowIdx, 2, vol)
        if bereinigt > 0 then ncForce(sh, rowIdx, 3, bereinigt)
      }

      // Kalt-Wasser — rows 40–44 (idx 39–43)
      av(energy, "waterEntries").take(5).zipWithIndex.foreach { (e, i) =>
        val rowIdx = 39 + i
        val year   = Try(e("year").num.toInt).getOrElse(0)
        val m3     = Try(e("consumptionM3").num).getOrElse(0.0)
        if year != 0 then nc(sh, rowIdx, 1, year.toDouble)
        if m3 > 0    then nc(sh, rowIdx, 2, m3)
      }
    }

  // ── Sheet: Flächen ────────────────────────────────────────────────────────
  //
  // EBF table (rows 7–11, idx 6–10):
  //   B=Kürzel, C=Ausrichtung, D=Beschrieb, E=Länge, F=Breite (inputs);
  //   G=Fläche (formula =E*F), H=Anzahl (=1), I=Fläche Total (=G*H);
  //   J=Fläche Neu, K=Anzahl Neu, L=Fläche Total Neu, M=Beschrieb Neu (inputs).
  //
  // Polygon-only areas (no L/W): write totalArea to E and 1 to F so
  // G=E*F=totalArea computes correctly via the existing per-row formula.

  private def fillFlaechen(wb: js.Dynamic, p: ujson.Value): Unit =
    val sh = (wb.getWorksheet("Flächen").asInstanceOf[js.UndefOr[js.Dynamic]])
      .toOption
      .orElse {
        // Fallback: scan worksheets for one whose name contains "chen"
        val arr = wb.worksheets.asInstanceOf[js.UndefOr[js.Array[js.Dynamic]]]
        arr.toOption.flatMap(_.find(s => s.name.asInstanceOf[String].contains("chen")))
      }
      .orNull
    if sh == null then return

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

        sc(sh, rowIdx, 1, sv(e, "kuerzel"))     // B: Kürzel
        sc(sh, rowIdx, 2, sv(e, "orientation"))  // C: Ausrichtung
        sc(sh, rowIdx, 3, sv(e, "description"))  // D: Beschrieb

        if length > 0 && width > 0 then
          nc(sh, rowIdx, 4, length)
          nc(sh, rowIdx, 5, width)
        else if totalArea > 0 then
          ncForce(sh, rowIdx, 4, math.round(totalArea).toDouble)  // E: Länge
          ncForce(sh, rowIdx, 5, 1.0)                             // F: Breite

        // J–M: Fläche Neu / Anzahl Neu / Fläche Total Neu / Beschrieb Neu
        val areaNew  = Try(e("areaNew").num).getOrElse(0.0)
        val qtyNew   = Try(e("quantityNew").num.toInt).getOrElse(0)
        val totalNew = Try(e("totalAreaNew").num).getOrElse(0.0)
        val descNew  = sv(e, "descriptionNew")
        if areaNew  > 0     then nc(sh, rowIdx, 9,  areaNew)
        if qtyNew   > 0     then nc(sh, rowIdx, 10, qtyNew.toDouble)
        if totalNew > 0     then nc(sh, rowIdx, 11, totalNew)
        if descNew.nonEmpty then sc(sh, rowIdx, 12, descNew)
    }


end BerechnungstoolExportService
