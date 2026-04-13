package pme123.geak4s.views

import upickle.default.write
import com.raquo.laminar.api.L.{*, given}
import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import scala.scalajs.js
import org.scalajs.dom
import pme123.geak4s.state.AppState
import pme123.geak4s.domain.*
import pme123.geak4s.domain.project.*
import pme123.geak4s.domain.building.BuildingUsage

object WordFormView:

  /** Parse "Musterstrasse 1, 8006 Zürich" into an Address.
   *  Preserves lat/lon from an existing address when provided. */
  private def parseFormAddress(adresse: String, existing: Address = Address.empty): Address =
    val parts = adresse.split(",", 2)
    val streetPart = parts.headOption.map(_.trim).getOrElse("")
    val tokens = streetPart.split(" ").filter(_.nonEmpty)
    val (street, houseNum) =
      if tokens.length > 1 && tokens.last.matches("""\d+[a-zA-Z]?""") then
        (tokens.dropRight(1).mkString(" "), Some(tokens.last))
      else
        (streetPart, None)
    val zipCity = parts.lift(1).map(_.trim).getOrElse("")
    val zcTokens = zipCity.split(" ", 2).filter(_.nonEmpty)
    val zip  = zcTokens.headOption.filter(_.forall(_.isDigit))
    val city = if zip.isDefined then zcTokens.lift(1) else Some(zipCity).filter(_.nonEmpty)
    Address(
      street     = Some(street).filter(_.nonEmpty),
      houseNumber = houseNum,
      zipCode    = zip,
      city       = city,
      country    = Some("Schweiz"),
      lat        = existing.lat,
      lon        = existing.lon
    )

  /** Format an Address back to "Strasse Nr, PLZ Ort" for display in the form. */
  private def formatAddress(addr: Address): String =
    val streetPart = List(addr.street, addr.houseNumber).flatten.mkString(" ").trim
    val cityPart   = List(addr.zipCode, addr.city).flatten.mkString(" ").trim
    List(streetPart, cityPart).filter(_.nonEmpty).mkString(", ")

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

  /** Project updater built from the current form state.
   *  Used both on mount and on every user edit so Schritt 7 always matches. */
  private def syncFormToProject(form: WordFormData): GeakProject => GeakProject = p =>
    val existingBuildingAddr = p.project.buildingLocation.address
    val existingClientAddr   = p.project.client.address
    val addr    = parseFormAddress(form.adresse, existingBuildingAddr)
    val ebfOpt  = form.ebf.replace(',', '.').toDoubleOption
    p.copy(
      // Persist the complete form data so all fields survive JSON export/import
      wordFormData = Some(form),
      // Keep BuildingUsages area in sync with EBF so <BuildingUsages> > <Area> in the XML is correct
      buildingUsages = ebfOpt match
        case Some(v) if p.buildingUsages.nonEmpty => p.buildingUsages.head.copy(area = v) :: p.buildingUsages.tail
        case Some(v)                              => List(BuildingUsage("Einfamilienhaus", None, v, None, None))
        case None                                 => p.buildingUsages,
      project = p.project.copy(
        projectName = if form.projektnummer.nonEmpty then form.projektnummer else p.project.projectName,
        client = p.project.client.copy(
          name1  = Some(form.auftraggeberin).filter(_.nonEmpty).orElse(p.project.client.name1),
          email  = Some(form.mail).filter(_.nonEmpty).orElse(p.project.client.email),
          phone1 = Some(form.tel).filter(_.nonEmpty).orElse(p.project.client.phone1),
          address = if form.adresse.nonEmpty then parseFormAddress(form.adresse, existingClientAddr)
                    else p.project.client.address
        ),
        buildingLocation = p.project.buildingLocation.copy(
          address      = if form.adresse.nonEmpty then addr else existingBuildingAddr,
          buildingName = Some(form.gebaudeart).filter(_.nonEmpty).orElse(p.project.buildingLocation.buildingName)
        ),
        buildingData = p.project.buildingData.copy(
          constructionYear    = form.baujahr.toIntOption.orElse(p.project.buildingData.constructionYear),
          energyReferenceArea = ebfOpt.orElse(p.project.buildingData.energyReferenceArea)
        ),
        egidEdidGroup = p.project.egidEdidGroup.copy(
          entries =
            if form.egid.isEmpty then p.project.egidEdidGroup.entries
            else
              val existing = p.project.egidEdidGroup.entries
              if existing.isEmpty then List(EgidEdidEntry(egid = Some(form.egid), edid = None, address = addr))
              else existing.head.copy(egid = Some(form.egid)) :: existing.tail
        )
      )
    )

  def apply(): HtmlElement =
    // On every mount: if the project already has persisted wordFormData, restore it directly.
    // Otherwise fall back to extracting individual fields from the project (e.g. on first visit).
    AppState.getCurrentProject.foreach { p =>
      p.wordFormData match
        case Some(saved) =>
          // Full form state was previously persisted – restore it exactly.
          formVar.set(saved)
        case None =>
          // First visit or legacy project without wordFormData: derive fields from project.
          val cur = formVar.now()
          def orProject(v: String, fallback: => String): String = if v.nonEmpty then v else fallback
          val fmtEbf = p.project.buildingData.energyReferenceArea
            .map(v => if v == v.toLong then v.toLong.toString else v.toString)
            .getOrElse("")
          val synced = cur.copy(
            projektnummer  = orProject(cur.projektnummer,  p.project.projectName),
            auftraggeberin = orProject(cur.auftraggeberin, p.project.client.name1.getOrElse("")),
            mail       = orProject(cur.mail,       p.project.client.email.getOrElse("")),
            tel        = orProject(cur.tel,        p.project.client.phone1.getOrElse("")),
            adresse    = orProject(cur.adresse,    formatAddress(p.project.buildingLocation.address)),
            baujahr    = orProject(cur.baujahr,    p.project.buildingData.constructionYear.map(_.toString).getOrElse("")),
            egid       = orProject(cur.egid,       p.project.egidEdidGroup.entries.headOption.flatMap(_.egid).getOrElse("")),
            ebf        = orProject(cur.ebf,        fmtEbf),
            gebaudeart = orProject(cur.gebaudeart, p.project.buildingLocation.buildingName.getOrElse(""))
          )
          formVar.set(synced)
          // Push the now-complete form state into the project immediately.
          // WorkflowView no longer re-renders on project changes, so this is loop-safe.
          AppState.updateProject(syncFormToProject(synced))
    }
    div(
      className := "project-view",

      // Reactive sync: on every user edit push the new values into the project.
      // Uses .changes so it does NOT fire on mount (only on actual edits).
      formVar.signal.changes --> Observer[WordFormData] { form =>
        AppState.updateProject(syncFormToProject(form))
      },

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