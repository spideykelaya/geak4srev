import cask.*
import java.io.*
import upickle.default.*

case class WordFormData(
  projektnummer: String = "",
  auftraggeberin: String = "",
  mail: String = "",
  tel: String = "",
  adresse: String = "",
  baujahr: String = "",
  datum: String = "",
  egid: String = "",
  heizung: String = "",
  warmwasser: String = "",
  gebaudeart: String = "",
  ebf: String = "",
  wohnungen: String = "",
  energieart: String = "",
  energieverbrauch: String = "",
  energiekennzahl: String = "",
  erdsonde: String = "",
  fernwärme: String = "",
  fossil: String = "",
  wp: String = "",
  sondentiefe: String = ""
) derives ReadWriter

object Main extends cask.MainRoutes {
  override def port = 8080
  override def host = "0.0.0.0"
  override def debugMode = true

  @cask.post("/generate")
  def generate(request: cask.Request) = {
    val bodyStr = new String(request.readAllBytes())
    val formData = read[WordFormData](bodyStr)
    println(s"Received data: $bodyStr")
    println(s"Parsed: $formData")

    // Map für Platzhalter erstellen
    val placeholders = Map(
      "projektnummer"  -> formData.projektnummer,
      "auftraggeberin" -> formData.auftraggeberin,
      "mail"           -> formData.mail,
      "tel"            -> formData.tel,
      "adresse"        -> formData.adresse,
      "baujahr"        -> formData.baujahr,
      "datum"          -> formData.datum,
      "egid"           -> formData.egid,
      "heizung"        -> formData.heizung,
      "warmwasser"     -> formData.warmwasser,
      "gebaudeart"     -> formData.gebaudeart,
      "ebf"            -> formData.ebf,
      "wohnungen"      -> formData.wohnungen,
      "energieart"     -> formData.energieart,
      "energieverbrauch" -> formData.energieverbrauch,
      "energiekennzahl"  -> formData.energiekennzahl,
      "erdsonde"       -> formData.erdsonde,
      "fernwärme"      -> formData.fernwärme,
      "fossil"         -> formData.fossil,
      "wp"             -> formData.wp,
      "sondentiefe"    -> formData.sondentiefe
    )

    val bytes = WordService.generateDoc(placeholders)

    cask.Response(
      bytes,
      headers = Seq(
        "Content-Type" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "Content-Disposition" -> "attachment; filename=\"Begehungsprotokoll.docx\""
      )
    )
  }

  @cask.post("/generate-excel")
  def generateExcel(request: cask.Request) = {
    try {
      val bodyStr = new String(request.readAllBytes(), "UTF-8")
      println(s"[generate-excel] received ${bodyStr.length} chars")
      val bytes = ExcelService.generateExcel(bodyStr)
      println(s"[generate-excel] OK, ${bytes.length} bytes")
      cask.Response(
        bytes,
        headers = Seq(
          "Content-Type"        -> "application/vnd.ms-excel",
          "Content-Disposition" -> "attachment; filename=\"GEAK_Export.xls\""
        )
      )
    } catch {
      case ex: Throwable =>
        println(s"[generate-excel] ERROR: ${ex.getClass.getName}: ${ex.getMessage}")
        ex.printStackTrace()
        val msg = s"Excel generation failed: ${ex.getClass.getSimpleName}: ${ex.getMessage}"
        cask.Response(
          msg.getBytes("UTF-8"),
          statusCode = 500,
          headers = Seq("Content-Type" -> "text/plain; charset=UTF-8")
        )
    }
  }

  @cask.post("/generate-berechnungstool")
  def generateBerechnungstool(request: cask.Request) = {
    try {
      val bodyStr = new String(request.readAllBytes(), "UTF-8")
      println(s"[generate-berechnungstool] received ${bodyStr.length} chars")
      val bytes = BerechnungstoolService.generate(bodyStr)
      println(s"[generate-berechnungstool] OK, ${bytes.length} bytes")
      cask.Response(
        bytes,
        headers = Seq(
          "Content-Type"        -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "Content-Disposition" -> "attachment; filename=\"Berechnungstool_260.xlsx\""
        )
      )
    } catch {
      case ex: Throwable =>
        println(s"[generate-berechnungstool] ERROR: ${ex.getClass.getName}: ${ex.getMessage}")
        ex.printStackTrace()
        cask.Response(
          s"Berechnungstool-Export fehlgeschlagen: ${ex.getClass.getSimpleName}: ${ex.getMessage}".getBytes("UTF-8"),
          statusCode = 500,
          headers = Seq("Content-Type" -> "text/plain; charset=UTF-8")
        )
    }
  }

  initialize()
}