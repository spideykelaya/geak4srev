package pme123.geak4s.views

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import pme123.geak4s.state.{AreaState, AppState}

object EBFCalculatorView:

  private val polygonSyncEvent = "geak:ebf-polygons-sync"

  private def decodePolygons(event: dom.Event): Seq[(String, Double)] =
    val payload = event.asInstanceOf[dom.CustomEvent].detail.asInstanceOf[js.Array[js.Dynamic]]
    payload.toSeq.flatMap { item =>
      val rawLabel = item.selectDynamic("label")
      val label =
        if js.isUndefined(rawLabel) || rawLabel == null then ""
        else rawLabel.toString.trim
      if label.isEmpty then None
      else
        val rawArea = item.selectDynamic("area")
        val parsed = js.Dynamic.global.Number(rawArea).asInstanceOf[Double]
        val area = if parsed.isNaN || parsed < 0 then 0.0 else parsed
        Some(label -> area)
    }

  def apply(): HtmlElement =
    var unmountHandle: Option[js.Function0[Unit]] = None
    var polygonSyncListener: Option[js.Function1[dom.Event, Unit]] = None

    div(
      className := "ebf-calculator-host",
      styleAttr := "display: block; width: 100%; height: 100%; min-height: 800px;",
      onMountCallback { ctx =>
        val listener: js.Function1[dom.Event, Unit] = (event: dom.Event) =>
          val polygons = decodePolygons(event)
          AreaState.syncEbfPolygons(polygons)
          AppState.saveAreaCalculations()
        dom.window.addEventListener(polygonSyncEvent, listener)
        polygonSyncListener = Some(listener)

        val mountFn = dom.window.asInstanceOf[js.Dynamic].mountEbfCalculator
        if js.isUndefined(mountFn) then
          dom.console.error("window.mountEbfCalculator is not available")
        else
          mountFn(ctx.thisNode.ref)
            .asInstanceOf[js.Promise[js.Function0[Unit]]]
            .`then`[Unit]((unmount: js.Function0[Unit]) =>
              unmountHandle = Some(unmount)
            )
            .`catch`((error: scala.Any) =>
              dom.console.error("Failed to mount EBF calculator", error)
            )
      },
      onUnmountCallback { _ =>
        polygonSyncListener.foreach(listener => dom.window.removeEventListener(polygonSyncEvent, listener))
        polygonSyncListener = None
        unmountHandle.foreach(_())
        unmountHandle = None
      },
      htmlTag("link")(rel := "stylesheet", href := "/ebf/styles.css?v=8"),
      div(
        className := "app",
        styleAttr := "height: 100%;",
        EBFSidebarView(),
        EBFMainPanelView()
      ),
      calibrationIntroModal(),
      clearConfirmModal(),
      scaleDialog()
    )

  private def calibrationIntroModal(): HtmlElement =
    div(
      idAttr := "calib-intro-modal",
      className := "dialog-overlay",
      display := "none",
      div(
        className := "dialog",
        h3("Massstabs-Kalibrierung"),
        p("Um Flaechen zu berechnen, muss die App den Massstab des Plans kennen."),
        ol(
          className := "calib-steps",
          li(span("Klicken Sie auf ", strong("2 Punkte"), ", deren echte Entfernung Sie kennen (z. B. eine Wand)")),
          li(span("Geben Sie die ", strong("reale Entfernung"), " zwischen diesen 2 Punkten ein")),
          li(span("Der Massstab wird berechnet und die Flaechen werden automatisch angezeigt"))
        ),
        div(
          className := "dialog-buttons",
          button(className := "btn btn-primary", idAttr := "calib-intro-start", "Starten"),
          button(className := "btn", idAttr := "calib-intro-skip", "Ueberspringen")
        )
      )
    )

  private def clearConfirmModal(): HtmlElement =
    div(
      idAttr := "clear-confirm-modal",
      className := "dialog-overlay",
      display := "none",
      div(
        className := "dialog",
        h3("Alles loeschen?"),
        p("Alle Polygone und Messungen werden geloescht. Diese Aktion ist nicht umkehrbar."),
        div(
          className := "dialog-buttons",
          button(className := "btn btn-danger-solid", idAttr := "clear-confirm-yes", "Loeschen"),
          button(className := "btn", idAttr := "clear-confirm-no", "Abbrechen")
        )
      )
    )

  private def scaleDialog(): HtmlElement =
    div(
      idAttr := "scale-dialog",
      className := "dialog-overlay",
      display := "none",
      div(
        className := "dialog",
        h3("Wahre Entfernung"),
        p("Geben Sie die reale Laenge der Linie ein, die Sie gerade gezeichnet haben:"),
        div(
          className := "input-group",
          input(
            tpe := "number",
            idAttr := "real-length",
            minAttr := "0.001",
            stepAttr := "any",
            placeholder := "z. B. 5"
          ),
          select(
            idAttr := "length-unit",
            option(value := "1", "m"),
            option(value := "0.01", "cm"),
            option(value := "0.001", "mm")
          )
        ),
        div(
          className := "dialog-buttons",
          button(className := "btn btn-primary", idAttr := "confirm-scale", "Bestaetigen"),
          button(className := "btn", idAttr := "cancel-scale", "Abbrechen")
        )
      )
    )
