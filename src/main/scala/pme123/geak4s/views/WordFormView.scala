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
      className := "project-view",

      renderSection("Projektinfos", div(
        textInput("Projektnummer", _.projektnummer, (d,v) => d.copy(projektnummer=v)),
        textInput("AuftraggeberIn", _.auftraggeberin, (d,v) => d.copy(auftraggeberin=v)),
        textInput("Mail", _.mail, (d,v) => d.copy(mail=v)),
        textInput("Tel.", _.tel, (d,v) => d.copy(tel=v)),
        textInput("Datum", _.datum, (d,v) => d.copy(datum=v)),
        textInput("Adresse", _.adresse, (d,v) => d.copy(adresse=v)),
        textInput("Baujahr", _.baujahr, (d,v) => d.copy(baujahr=v)),
        textInput("EGID", _.egid, (d,v) => d.copy(egid=v)),
      )),

      renderSection("Heizung", div(
        textInput("Heizung", _.heizung, (d,v) => d.copy(heizung=v)),
      )),


      renderSection("Gebäude", div(
        textInput("Gebäudeart", _.gebaudeart, (d,v) => d.copy(gebaudeart=v)),
        textInput("EBF", _.ebf, (d,v) => d.copy(ebf=v)),
        textInput("Wohnungen", _.wohnungen, (d,v) => d.copy(wohnungen=v)),
        textInput("Energieart", _.energieart, (d,v) => d.copy(energieart=v)),
        textInput("Energieverbrauch", _.energieverbrauch, (d,v) => d.copy(energieverbrauch=v)),
        textInput("Energiekennzahl", _.energiekennzahl, (d,v) => d.copy(energiekennzahl=v)),
      )),

      renderSection("zukünftige Heizung", div(
        div(
          className := "form-field",
          div(
            display := "flex",
            alignItems := "center",
            gap := "0.5rem",
            Label("Erdsonde erlaubt"),
            // Hidden anchor for reliable new-tab navigation from a UI5 Button
            a(
              idAttr := "erdsonde-link",
              href := "https://maps.zh.ch/?topic=AwelGSWaermewwwZH&x=2689780.975&y=1246927.965&scale=114040.94495999988",
              target := "_blank",
              rel := "noopener noreferrer",
              display := "none"
            ),
            Button(
              _.design := ButtonDesign.Default,
              _.icon := IconName.map,
              _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                val fullAddress = formVar.now().adresse
                val address = fullAddress.takeWhile(_ != ',').trim
                if address.nonEmpty then
                  val d = scala.scalajs.js.Dynamic.global.document
                  val ta = d.createElement("textarea")
                  ta.value = address
                  ta.style.cssText = "position:fixed;top:-9999px;left:-9999px;opacity:0"
                  d.body.appendChild(ta)
                  ta.focus()
                  ta.select()
                  d.execCommand("copy")
                  d.body.removeChild(ta)
                dom.document.getElementById("erdsonde-link").asInstanceOf[dom.HTMLAnchorElement].click()
              },
              "Wärmenutzungsatlas ZH"
            )
          ),
          Input(
            value <-- formVar.signal.map(_.erdsonde),
            onInput.mapToValue --> Observer[String] { newValue =>
              formVar.update(old => old.copy(erdsonde = newValue))
            }
          )
        ),
        div(
          className := "form-field",
          Label("Fernwärme vorhanden"),
          div(
            display := "flex", gap := "0.5rem", alignItems := "stretch",
            Select(
              _.events.onChange.map(_.detail.selectedOption.dataset.get("value").getOrElse("")) --> Observer[String] { v =>
                if v.nonEmpty then formVar.update(_.copy(fernwärme = v))
              },
              Select.option(_.selected <-- formVar.signal.map(_.fernwärme == ""), dataAttr("value") := "", "– wählen –"),
              Select.option(_.selected <-- formVar.signal.map(_.fernwärme == "Ja"), dataAttr("value") := "Ja", "Ja"),
              Select.option(_.selected <-- formVar.signal.map(_.fernwärme == "Nein"), dataAttr("value") := "Nein", "Nein"),
              Select.option(_.selected <-- formVar.signal.map(_.fernwärme == "Geplant"), dataAttr("value") := "Geplant", "Geplant"),
            ),
            Input(
              placeholder := "oder manuell eingeben",
              value <-- formVar.signal.map(_.fernwärme),
              onInput.mapToValue --> Observer[String] { v =>
                formVar.update(_.copy(fernwärme = v))
              }
            )
          )
        ),
        textInput("Fossil-Leistung", _.fossil, (d,v) => d.copy(fossil=v)),
        textInput("WP-Leistung", _.wp, (d,v) => d.copy(wp=v)),
        textInput("Sondentiefe", _.sondentiefe, (d,v) => d.copy(sondentiefe=v)),
      )),

      // Button zum Generieren
      Button(
        "Begehungsprotokoll erstellen",
        _.design := ButtonDesign.Emphasized,
        _.events.onClick.mapTo(()) --> Observer { _ =>
          sendToBackend()
        }
      )
    )

  private def renderSection(title: String, content: HtmlElement): HtmlElement =
    div(
      className := "form-section",
      div(
        className := "section-header",
        Title(_.level := TitleLevel.H3, title)
      ),
      content
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
    ).`then`[Unit] { response =>
      response.blob().`then`[Unit] { blob =>
        val objectUrl = dom.URL.createObjectURL(blob)
        val link = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
        link.href = objectUrl
        link.download = "Begehungsprotokoll.docx"
        dom.document.body.appendChild(link)
        link.click()
        dom.document.body.removeChild(link)
        dom.URL.revokeObjectURL(objectUrl)
      }
    }