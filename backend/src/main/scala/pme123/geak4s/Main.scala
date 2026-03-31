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

  initialize()
}