package pme123.geak4s.views

import com.raquo.laminar.api.L.{*, given}
import com.raquo.laminar.codecs.StringAsIsCodec
import org.scalajs.dom
import scala.scalajs.js
import pme123.geak4s.domain.uwert.ComponentType

/** Sidebar controls for the EBF calculator. */
object EBFSidebarView:

  private val areaTypeEvent    = "geak:ebf-area-type-selected"
  private val optgroupLabelAttr = htmlAttr("label", StringAsIsCodec)

  def apply(): HtmlElement =
    htmlTag("aside")(
      className := "sidebar",
      div(
        className := "sidebar-header",
        h1("Flächen-Rechner")
      ),
      div(
        className := "sidebar-body",
        planSection(),
        plansSection(),
        scaleSection(),
        areaTypeSection(),
        drawSection(),
        polygonsSection(),
        measurementsSection()
      ),
      div(
        className := "sidebar-footer",
        div(
          className := "shortcuts",
          div(className := "shortcut-title", "Tastaturkuerzel"),
          div(className := "shortcut-row", span("Zoom"), span(className := "shortcut-key", "Mausrad")),
          div(className := "shortcut-row", span("Ansicht verschieben"), span(className := "shortcut-key", "Klick + Ziehen")),
          div(className := "shortcut-row", span("Abbrechen"), span(className := "shortcut-key", "Esc")),
          div(className := "shortcut-row", span("Polygon schliessen"), span(className := "shortcut-key", "Doppelklick"))
        )
      )
    )

  private def planSection(): HtmlElement =
    div(
      className := "section",
      div(className := "section-label", "Plan"),
      label(
        className := "btn btn-primary",
        forId := "file-input",
        "Plan importieren",
        input(tpe := "file", idAttr := "file-input", accept := ".pdf,image/*", hidden := true)
      ),
      div(idAttr := "file-name", className := "file-name")
    )

  private def plansSection(): HtmlElement =
    div(
      className := "section",
      idAttr := "plans-section",
      display := "none",
      div(className := "section-label", "Pläne"),
      ul(idAttr := "plan-list", className := "plan-list")
    )

  private def scaleSection(): HtmlElement =
    div(
      className := "section",
      idAttr := "scale-section",
      display := "none",
      div(className := "section-label", "Massstab"),
      div(idAttr := "scale-status", className := "scale-status uncalibrated", "Nicht kalibriert"),
      button(className := "btn", idAttr := "calibrate-btn", "Massstab kalibrieren"),
      div(className := "tools-divider"),
      div(className := "section-label", styleAttr := "font-size:0.78rem;opacity:0.7", "Separate Massstäbe (verzerrte Pläne)"),
      div(
        className := "btn-row",
        button(className := "btn", idAttr := "calibrate-y-btn", title := "Vertikalen Massstab kalibrieren", "Vertikal"),
        button(className := "btn", idAttr := "calibrate-x-btn", title := "Horizontalen Massstab kalibrieren", "Horizontal")
      )
    )

  private def areaTypeSection(): HtmlElement =
    div(
      className := "section",
      idAttr := "area-type-section",
      display := "none",
      div(className := "section-label", "Flächentyp"),
      select(
        idAttr := "area-type-select",
        styleAttr := "width:100%;padding:0.35rem 0.5rem;background:var(--surface);color:var(--text-1);border:1px solid var(--border-hi);border-radius:var(--r);font-size:0.85rem;cursor:pointer;",
        htmlTag("optgroup")(
          optgroupLabelAttr := "EBF",
          option(value := ComponentType.EBF.polygonLabel, ComponentType.EBF.label)
        ),
        htmlTag("optgroup")(
          (Seq(optgroupLabelAttr := "Gebäudehülle") ++
            ComponentType.orderedVisibleTypes
              .filterNot(_ == ComponentType.EBF)
              .map(ct => option(value := ct.polygonLabel, ct.label))
          )*
        ),
        onChange --> Observer[dom.Event] { e =>
          val sel  = e.target.asInstanceOf[dom.html.Select]
          val init = js.Dynamic.literal(detail = sel.value, bubbles = false, cancelable = false)
          dom.window.dispatchEvent(new dom.CustomEvent(areaTypeEvent, init.asInstanceOf[dom.CustomEventInit]))
        }
      )
    )

  private def drawSection(): HtmlElement =
    div(
      className := "section",
      idAttr := "draw-section",
      display := "none",
      div(className := "section-label", "Zeichnen"),
      button(className := "btn btn-success btn-draw-main", idAttr := "draw-btn", "Neues Polygon"),
      div(className := "tools-divider"),
      div(className := "section-label", "Werkzeuge"),
      button(className := "btn btn-measure", idAttr := "measure-btn", "Distanz messen"),
      button(className := "btn btn-danger", idAttr := "clear-btn", "Alles loeschen"),
      div(className := "tools-divider"),
      div(className := "section-label", "Export"),
      div(
        className := "btn-row",
        button(className := "btn", idAttr := "export-excel-btn", "Excel"),
        button(className := "btn", idAttr := "export-xml-btn", "XML"),
        button(className := "btn", idAttr := "print-btn", "PDF")
      ),
      div(
        className := "btn-row",
        button(className := "btn", idAttr := "export-btn", "Exportieren"),
        button(className := "btn", idAttr := "import-btn", "Importieren"),
        input(tpe := "file", idAttr := "import-input", accept := ".json", hidden := true)
      )
    )

  private def polygonsSection(): HtmlElement =
    div(
      className := "section",
      idAttr := "polygons-section",
      display := "none",
      div(className := "section-label", "Polygone"),
      ul(idAttr := "polygon-list"),
      div(className := "total", idAttr := "total-surface")
    )

  private def measurementsSection(): HtmlElement =
    div(
      className := "section",
      idAttr := "measurements-section",
      display := "none",
      div(className := "section-label", "Messungen"),
      ul(idAttr := "measurement-list")
    )

end EBFSidebarView


