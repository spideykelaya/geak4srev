package pme123.geak4s.views

import upickle.default.{ReadWriter, macroRW, write}
import com.raquo.laminar.api.L.{*, given}
import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import scala.scalajs.js
import org.scalajs.dom



// Datenmodell für das Formular
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
  gebäudeart: String = "",
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
)

object WordFormData {
  given ReadWriter[WordFormData] = upickle.default.macroRW
}

object WordFormView:

  val formVar: Var[WordFormData] = Var(WordFormData())

  // Hilfsfunktion für TextInput
  def textInput(labelStr: String, get: WordFormData => String, set: (WordFormData, String) => WordFormData): HtmlElement =
    div(
      className := "form-field",
      Label(labelStr),
      Input(
        value <-- formVar.signal.map(get),
        onInput.mapToValue --> Observer[String] { newValue =>
          formVar.update(old => set(old, newValue))
        }
      )
    )

  def apply(): HtmlElement =
    div(
      className := "word-form",
      h2("Begehungsprotokoll erfassen"),

      h3("Projektinfos"),
      textInput("Projektnummer", _.projektnummer, (d,v) => d.copy(projektnummer=v)),
      textInput("AuftraggeberIn", _.auftraggeberin, (d,v) => d.copy(auftraggeberin=v)),
      textInput("Mail", _.mail, (d,v) => d.copy(mail=v)),
      textInput("Tel.", _.tel, (d,v) => d.copy(tel=v)),
       textInput("Datum", _.datum, (d,v) => d.copy(datum=v)),
      textInput("Adresse", _.adresse, (d,v) => d.copy(adresse=v)),
      textInput("Baujahr", _.baujahr, (d,v) => d.copy(baujahr=v)),
      textInput("EGID", _.egid, (d,v) => d.copy(egid=v)),

      h3("Heizung"),
      textInput("Heizung", _.heizung, (d,v) => d.copy(heizung=v)),

      h3("Warmwasser"),
      textInput("Warmwasser", _.warmwasser, (d,v) => d.copy(warmwasser=v)),

      h3("Gebäude"),
      textInput("Gebäudeart", _.gebäudeart, (d,v) => d.copy(gebäudeart=v)),
      textInput("EBF", _.ebf, (d,v) => d.copy(ebf=v)),
      textInput("Wohnungen", _.wohnungen, (d,v) => d.copy(wohnungen=v)),
      textInput("Energieart", _.energieart, (d,v) => d.copy(energieart=v)),
      textInput("Energieverbrauch", _.energieverbrauch, (d,v) => d.copy(energieverbrauch=v)),
      textInput("Energiekennzahl", _.energiekennzahl, (d,v) => d.copy(energiekennzahl=v)),

      h3("zukünftige Heizung"),
      textInput("Erdsonde erlaubt", _.erdsonde, (d,v) => d.copy(erdsonde=v)),
      textInput("Fernwärme vorhanden", _.fernwärme, (d,v) => d.copy(fernwärme=v)),
      textInput("Fossil-Leistung", _.fossil, (d,v) => d.copy(fossil=v)),
      textInput("WP-Leistung", _.wp, (d,v) => d.copy(wp=v)),
      textInput("Sondentiefe", _.sondentiefe, (d,v) => d.copy(sondentiefe=v)),

      // Button zum Generieren
      Button(
        "Begehungsprotokoll erstellen",
        _.design := ButtonDesign.Emphasized,
        _.events.onClick.mapTo(()) --> Observer { _ =>
          sendToBackend()
        }
      )
    )

  // Funktion für POST
  def sendToBackend(): Unit =
    val dataJson = write(formVar.now())
    val url = "/generate"
    dom.fetch(
        url,
        new dom.RequestInit {
        method = dom.HttpMethod.POST
        body = dataJson
        headers = new dom.Headers(js.Array(js.Array("Content-Type", "application/json")))
        }
    )