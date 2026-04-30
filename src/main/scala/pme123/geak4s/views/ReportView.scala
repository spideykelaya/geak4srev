package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import pme123.geak4s.state.AppState
import pme123.geak4s.domain.*
import pme123.geak4s.services.{BerechnungstoolExportService, ExcelExportService, XmlExportService}
import pme123.geak4s.components.FormField
import pme123.geak4s.domain.JsonCodecs.given
import io.circe.syntax.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Dedicated view for GEAK reports and exports Provides comprehensive report generation and export
  * functionality
  */
object ReportView:

  def apply(): HtmlElement =
    div(
      className := "report-view",
      Card(
        className := "project-view",
        maxWidth  := "100%",
        display   := "flex",
        div(
          className := "card-content",
          padding   := "1.5rem",

          // Header
          div(
            marginBottom := "2rem",
            MessageStrip(
              _.design := MessageStripDesign.Positive,
              _.hideCloseButton := true,
              "Projekt abgeschlossen! Erstellen Sie den GEAK-Bericht und exportieren Sie die Daten."
            )
          ),

          // Report sections
          child <-- AppState.projectSignal.map {
            case Some(project) => renderReportSections(project)
            case None          => div(
                MessageStrip(
                  _.design := MessageStripDesign.Warning,
                  _.hideCloseButton := true,
                  "Kein Projekt geladen"
                )
              )
          }
        )
      )
    )

  private def renderReportSections(project: GeakProject): HtmlElement =
    div(
      className := "report-sections-vertical",

      // XML Export Card (moved to first position)
      renderXmlExportCard(project),

      // Excel Export Card
      renderExcelExportCard(project),

      // GEAK Report Card
      renderGeakReportCard(project),

      // Project Summary Card
      renderProjectSummaryCard(project)
    )

  private def renderGeakReportCard(project: GeakProject): HtmlElement =
    Card(
      _.slots.header := CardHeader(
        _.titleText    := "GEAK-Bericht",
        _.subtitleText := "Finaler Bericht erstellen"
      ),
      marginBottom := "1.5rem",
      div(
        className    := "card-content",
        padding      := "1.5rem",
        Label("Funktion wird implementiert: GEAK-Bericht Generator"),
        div(
          marginTop := "1rem",
          Label("• Automatische Zusammenstellung aller Daten"),
          Label("• PDF-Export"),
          Label("• Mustertexte GEAK Plus"),
          Label("• Energieetikette und Beratungsbericht")
        ),
        div(
          marginTop := "1.5rem",
          Button(
            _.design   := ButtonDesign.Default,
            _.icon     := IconName.`pdf-attachment`,
            _.disabled := true,
            "PDF-Bericht erstellen (in Entwicklung)"
          )
        )
      )
    )

  private def renderXmlExportCard(project: GeakProject): HtmlElement =
    div(
      display := "flex",
      flexDirection := "column",
      gap := "1rem",
    Card(
      _.slots.header := CardHeader(
        _.titleText := "Projekt speichern",
        _.subtitleText := "Fortschritt mittels JSON-Datei speichern"
      ),
      
      div(
        padding := "1rem 1rem",
        width := "100%",
        Button(
          _.design := ButtonDesign.Default,
          _.icon   := IconName.`save`,
          width := "100%",
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            downloadProjectJson(project)
          },
          "als JSON speichern"
        )
      )
    ),

    Card(
      _.slots.header := CardHeader(
        _.titleText    := "GEAK XML Export",
        _.subtitleText := "Daten für GEAK Tool exportieren"
      ),
      marginBottom := "1.5rem",
      div(
        className    := "card-content",
        padding      := "1.5rem",
        div(
          marginTop := "1rem",
          FormField(
            metadata = FieldMetadata.geakId,
            value = AppState.projectSignal.map(_.flatMap(_.geakId).map(_.toString).getOrElse("")),
            onChange = value =>
              AppState.updateProject(p =>
                p.copy(
                  geakId = if value.isEmpty then None else value.toIntOption
                )
              )
          )
        ),
        Label("2. Exportieren Sie das Projekt als XML-Datei."),
        Button(
          _.design := ButtonDesign.Default,
          _.icon   := IconName.`upload-to-cloud`,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            XmlExportService.uploadXmlToGoogleDrive(project)
              .foreach { success =>
                if success then
                  dom.console.log("✅ XML successfully uploaded to Google Drive as GeakTool.xml")
                else
                  dom.console.error("❌ Failed to upload XML to Google Drive")
              }
          },
          "Zu Google Drive hochladen"
        ),
        div(marginTop := "0.5rem"),
        Button(
          _.design := ButtonDesign.Default,
          _.icon   := IconName.`download`,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            XmlExportService.downloadXml(project)
          },
          "Lokal herunterladen"
        ),
        div(marginTop := "0.5rem"),
        Label("3. Importieren Sie die XML-Datei vom Google Drive Ordner des Projekts in das GEAK Tool."),
        div(
          marginTop := "0.5rem",
          child <-- AppState.projectSignal.map { projectOpt =>
            val geakId = projectOpt.flatMap(_.geakId)
            val url    = geakId match
            case Some(id) => s"https://www.geak-tool.ch/portfolio/$id"
            case None     => "https://www.geak-tool.ch/portfolio/"

            Button(
              width := "100%",
              _.design := ButtonDesign.Default,
              _.icon   := IconName.`action`,
              _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                dom.window.open(url, "_blank")
              },
              "Zum GEAK Tool Portfolio"
            )
          }
        )
      )
    )
  )


  private def downloadExcel(project: GeakProject): Unit =
    val projectName = project.project.projectName.trim match
      case n if n.nonEmpty => n.replace(" ", "-")
      case _               => "geak_projekt"
    val json = project.asJson.noSpaces

    ExcelExportService.generate(json).onComplete {
      case scala.util.Success(blob) =>
        val url  = dom.URL.createObjectURL(blob)
        val link = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
        link.href = url
        link.download = s"GEAK_$projectName.xls"
        link.click()
        dom.URL.revokeObjectURL(url)
      case scala.util.Failure(ex) =>
        dom.console.error("Excel-Export fehlgeschlagen:", ex.getMessage)
        dom.window.alert(s"Excel-Export fehlgeschlagen:\n${ex.getMessage}")
    }

  private def downloadBerechnungstool(project: GeakProject): Unit =
    val projectName = project.project.projectName.trim match
      case n if n.nonEmpty => n.replace(" ", "-")
      case _               => "projekt"
    val json = project.asJson.noSpaces

    BerechnungstoolExportService.generate(json).onComplete {
      case scala.util.Success(blob) =>
        val url  = dom.URL.createObjectURL(blob)
        val link = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
        link.href = url
        link.download = s"Berechnungstool_260_$projectName.xlsx"
        link.click()
        dom.URL.revokeObjectURL(url)
      case scala.util.Failure(ex) =>
        dom.console.error("Berechnungstool-Export fehlgeschlagen:", ex.getMessage)
        dom.window.alert(s"Berechnungstool-Export fehlgeschlagen:\n${ex.getMessage}")
    }

  private def renderExcelExportCard(project: GeakProject): HtmlElement =
    Card(
      _.slots.header := CardHeader(
        _.titleText    := "Excel Export",
        _.subtitleText := "Ausgefüllte Vorlagen herunterladen"
      ),
      marginBottom := "1.5rem",
      div(
        padding := "1rem",
        display := "flex",
        flexDirection := "column",
        gap := "0.5rem",
        Button(
          _.design := ButtonDesign.Default,
          _.icon   := IconName.`excel-attachment`,
          width := "100%",
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            downloadExcel(project)
          },
          "GEAK-Excel herunterladen"
        ),
        Button(
          _.design := ButtonDesign.Default,
          _.icon   := IconName.`excel-attachment`,
          width := "100%",
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            downloadBerechnungstool(project)
          },
          "Berechnungstool herunterladen"
        )
      )
    )

  private def renderProjectSummaryCard(project: GeakProject): HtmlElement =
    Card(
      _.slots.header := CardHeader(
        _.titleText    := "Projekt-Zusammenfassung",
        _.subtitleText := "Übersicht der erfassten Daten"
      ),
      div(
        className    := "card-content",
        padding      := "1.5rem",
        div(
          className := "summary-grid",
          renderSummaryItem(
            "Projekt",
            project.project.projectName,
            IconName.`business-objects-experience`
          ),
          renderSummaryItem(
            "Gebäude",
            project.project.buildingLocation.address.street.getOrElse(""),
            IconName.`home`
          ),
          renderSummaryItem(
            "Dächer & Decken",
            project.roofsCeilings.length.toString,
            IconName.`home`
          ),
          renderSummaryItem("Wände", project.walls.length.toString, IconName.`home`),
          renderSummaryItem(
            "Fenster & Türen",
            project.windowsDoors.length.toString,
            IconName.`home`
          ),
          renderSummaryItem("Böden", project.floors.length.toString, IconName.`home`),
          renderSummaryItem(
            "Wärmebrücken",
            project.thermalBridges.length.toString,
            IconName.`temperature`
          ),
          renderSummaryItem(
            "Wärmeerzeuger",
            project.heatProducers.length.toString,
            IconName.`heating-cooling`
          ),
          renderSummaryItem("Lüftung", project.ventilations.length.toString, IconName.`add-filter`),
          renderSummaryItem(
            "Stromerzeuger",
            project.electricityProducers.length.toString,
            IconName.`energy-saving-lightbulb`
          )
        ),
        div(
          marginTop := "1.5rem",
          MessageStrip(
            _.design := MessageStripDesign.Information,
            _.hideCloseButton := true,
            s"U-Wert Berechnungen: ${project.uwertCalculations.length} | " +
              s"Flächenberechnungen: ${if project.areaCalculations.isDefined then "✓" else "—"}"
          )
        )
      )
    )

  private def downloadProjectJson(project: GeakProject): Unit =
    val projectName = project.project.projectName.trim match
      case n if n.nonEmpty => n
      case _               => "geak_projekt"
    val enriched = AppState.enrichProjectWithImages(project)
    val json     = enriched.asJson.noSpaces

    val win = dom.window.asInstanceOf[js.Dynamic]
    if js.typeOf(win.showSaveFilePicker) == "function" then
      // File System Access API: user picks the save location once,
      // then future auto-saves overwrite the file silently.
      val acceptObj = js.Object().asInstanceOf[js.Dynamic]
      acceptObj.updateDynamic("application/json")(js.Array(".json"))
      val typeItem = js.Dynamic.literal(description = "GEAK JSON Projektdatei", accept = acceptObj)
      val opts = js.Dynamic.literal(suggestedName = s"$projectName.json", types = js.Array(typeItem))

      val onError: js.Function1[js.Any, Unit] = err =>
        dom.console.warn("showSaveFilePicker fehlgeschlagen (abgebrochen?):", err)
        triggerDownload(json, s"$projectName.json")

      val onHandle: js.Function1[js.Any, Unit] = handle =>
        val h = handle.asInstanceOf[js.Dynamic]
        AppState.setLocalFileHandle(h, s"$projectName.json")
        val onWritable: js.Function1[js.Any, Unit] = writable =>
          val w = writable.asInstanceOf[js.Dynamic]
          val onWritten: js.Function1[js.Any, Unit] = _ => w.close()
          w.write(json).asInstanceOf[js.Dynamic].`then`(onWritten, onError)
        h.createWritable().asInstanceOf[js.Dynamic].`then`(onWritable, onError)

      win.showSaveFilePicker(opts).asInstanceOf[js.Dynamic].`then`(onHandle, onError)
    else
      triggerDownload(json, s"$projectName.json")

  private def triggerDownload(json: String, fileName: String): Unit =
    try
      val blob = new dom.Blob(js.Array(json), dom.BlobPropertyBag("application/json;charset=utf-8"))
      val url  = dom.URL.createObjectURL(blob)
      val link = dom.document.createElement("a").asInstanceOf[dom.html.Anchor]
      link.href = url
      link.download = fileName
      link.click()
      dom.URL.revokeObjectURL(url)
    catch case ex: Exception =>
      dom.console.error(s"JSON-Download fehlgeschlagen: ${ex.getMessage}")

  private def renderSummaryItem(label: String, value: String, icon: IconName): HtmlElement =
    div(
      className := "summary-item",
      div(
        display      := "flex",
        alignItems   := "center",
        gap          := "0.5rem",
        marginBottom := "0.5rem",
        Icon(
          _.name := icon,
          color      := "#0854a0"
        ),
        Label(
          label,
          fontWeight := "600",
          fontSize   := "0.875rem",
          color      := "#666"
        )
      ),
      div(
        fontSize     := "1.25rem",
        fontWeight   := "700",
        color        := "#333",
        value
      )
    )

end ReportView
