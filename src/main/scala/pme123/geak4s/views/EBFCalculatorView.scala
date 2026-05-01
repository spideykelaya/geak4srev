package pme123.geak4s.views

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*
import pme123.geak4s.state.{AreaState, AppState, EbfState, UWertState, WaermebrueckeState}
import pme123.geak4s.domain.JsonCodecs.given
import pme123.geak4s.domain.ebf.EbfPlans
import pme123.geak4s.domain.uwert.ComponentType
import pme123.geak4s.services.GoogleDriveService
import pme123.geak4s.config.GoogleDriveConfig
import pme123.geak4s.utils.ColorUtils
import io.circe.parser.*
import io.circe.syntax.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object EBFCalculatorView:

  private val polygonSyncEvent    = "geak:ebf-polygons-sync"
  private val plansSyncEvent      = "geak:ebf-plans-sync"
  private val planUploadEvent     = "geak:ebf-plan-upload"
  private val loadPlansEvent      = "geak:ebf-load-plans"
  private val polygonRenamedEvent = "geak:ebf-polygon-renamed"
  private val polygonResetEvent   = "geak:ebf-polygon-reset"
  private val updateColorEvent    = "geak:update-polygon-color"
  private val wbLinearDoneEvent   = "geak:wb-linear-done"
  private val wbPointsDoneEvent   = "geak:wb-points-done"

  private case class UWertOption(
    id: String,
    displayLabel: String,
    uValue: Double,
    gValue: Option[Double],
    glassRatio: Option[Double],
    adjustedColor: String,
    bValue: Option[Double]
  )

  private case class PendingAssignment(
    polygonLabel: String,
    compType: ComponentType,
    options: Seq[UWertOption]
  )

  private case class PolygonSyncEntry(
    label: String,
    areaType: String,
    area: Double,
    overhangDist: Option[Double],
    sideShadingDist: Option[Double],
    installedIn: Option[String]
  )

  // Types for which the orientation popup is shown (wall and roof types that have an orientation column)
  private val orientationPopupTypes: Set[ComponentType] = Set(
    ComponentType.PitchedRoof,
    ComponentType.ExteriorWall,
    ComponentType.BasementWallToEarth,
    ComponentType.BasementWallToUnheated,
    ComponentType.BasementWallToOutside,
    ComponentType.Door
  )

  private def decodePolygons(event: dom.Event): Seq[PolygonSyncEntry] =
    val payload = event.asInstanceOf[dom.CustomEvent].detail.asInstanceOf[js.Array[js.Dynamic]]
    payload.toSeq.flatMap { item =>
      val rawLabel = item.selectDynamic("label")
      val label =
        if js.isUndefined(rawLabel) || rawLabel == null then ""
        else rawLabel.toString.trim
      if label.isEmpty then None
      else
        val rawAreaType = item.selectDynamic("areaType")
        val areaType =
          if js.isUndefined(rawAreaType) || rawAreaType == null then ""
          else rawAreaType.toString.trim
        val rawArea = item.selectDynamic("area")
        val parsed  = js.Dynamic.global.Number(rawArea).asInstanceOf[Double]
        val area    = if parsed.isNaN || parsed < 0 then 0.0 else parsed
        def decodeOptDouble(field: String): Option[Double] =
          val raw = item.selectDynamic(field)
          if js.isUndefined(raw) || raw == null then None
          else
            val d = js.Dynamic.global.Number(raw).asInstanceOf[Double]
            if d.isNaN then None else Some(d)
        def decodeOptString(field: String): Option[String] =
          val raw = item.selectDynamic(field)
          if js.isUndefined(raw) || raw == null then None
          else
            val s = raw.toString.trim
            if s.isEmpty || s == "null" then None else Some(s)
        val overhangDist = decodeOptDouble("overhangDist")
        val sideDist     = decodeOptDouble("sideDist")
        val installedIn  = decodeOptString("installedIn")
        Some(PolygonSyncEntry(label, areaType, area, overhangDist, sideDist, installedIn))
    }

  private def buildPendingAssignment(polygonLabel: String, compType: ComponentType): Option[PendingAssignment] =
    val baseColor = compType.polygonColor
    val options: Seq[UWertOption] =
      if compType == ComponentType.Window || compType == ComponentType.Door then
        val calcs     = UWertState.windowCalculations.now().filter(_.label.nonEmpty)
        val allUVals  = calcs.map(_.uValue)
        calcs.map { wc =>
          UWertOption(
            id           = wc.id,
            displayLabel = wc.label,
            uValue       = wc.uValue,
            gValue       = Some(wc.gValue),
            glassRatio   = Some(wc.glassRatio),
            adjustedColor = ColorUtils.computeUWertColor(baseColor, wc.uValue, allUVals),
            bValue       = None
          )
        }
      else
        val calcs    = UWertState.calculations.now()
          .filter(c => c.componentType == compType && c.label.nonEmpty)
        val allUVals = calcs.map(_.istCalculation.uValue)
        calcs.map { calc =>
          UWertOption(
            id           = calc.id,
            displayLabel = calc.label,
            uValue       = calc.istCalculation.uValueWithoutB,
            gValue       = None,
            glassRatio   = None,
            adjustedColor = ColorUtils.computeUWertColor(baseColor, calc.istCalculation.uValue, allUVals),
            bValue       = Some(calc.istCalculation.bFactor)
          )
        }
    // Show popup if ≥1 U-Wert options, OR if this type benefits from orientation selection in the popup
    if options.size >= 1 || orientationPopupTypes.contains(compType) then
      Some(PendingAssignment(polygonLabel, compType, options))
    else None

  def apply(): HtmlElement =
    var unmountHandle:          Option[js.Function0[Unit]]            = None
    var polygonSyncListener:    Option[js.Function1[dom.Event, Unit]] = None
    var plansSyncListener:      Option[js.Function1[dom.Event, Unit]] = None
    var planUploadListener:     Option[js.Function1[dom.Event, Unit]] = None
    var polygonRenamedListener: Option[js.Function1[dom.Event, Unit]] = None
    var polygonResetListener:   Option[js.Function1[dom.Event, Unit]] = None
    var wbLinearListener:       Option[js.Function1[dom.Event, Unit]] = None
    var wbPointsListener:       Option[js.Function1[dom.Event, Unit]] = None

    val pendingAssignments: Var[List[PendingAssignment]] = Var(List.empty)
    var seenPolygonLabels: Set[String] = Set.empty
    var isFirstSync: Boolean = true

    div(
      className := "ebf-calculator-host",
      styleAttr := "display: block; width: 100%; height: 100%; min-height: 800px;",
      onMountCallback { ctx =>
        // ── polygon sync → AreaState ──
        val polyListener: js.Function1[dom.Event, Unit] = (event: dom.Event) =>
          val polygons      = decodePolygons(event)
          val currentLabels = polygons.map(_.label).toSet
          dom.console.log(s"[AreaState] polygon-sync received ${polygons.length}: ${polygons.map(p => s"${p.label}(${p.areaType})=%.2f".format(p.area)).mkString(", ")}")
          val installedInMap = polygons.map(p => p.label -> p.installedIn).toMap
          AreaState.syncPolygons(polygons.map(p => (p.label, p.areaType, p.area, p.overhangDist, p.sideShadingDist)), installedInMap)
          dom.console.log(s"[AreaState] after sync, entries: ${AreaState.areaCalculations.now().map(_.calculations.flatMap(c => c.entries.map(e => s"${c.componentType.polygonLabel}/${e.kuerzel}")).mkString(", ")).getOrElse("None")}")
          AppState.saveAreaCalculations()

          if isFirstSync then
            seenPolygonLabels = currentLabels
            isFirstSync = false
          else
            val newLabels  = currentLabels -- seenPolygonLabels
            val newPending = newLabels.toSeq.sorted.flatMap { label =>
              polygons.find(_.label == label).flatMap { p =>
                val compType = ComponentType.fromPolygonLabel(p.areaType)
                  .orElse(ComponentType.fromPolygonLabel(label))
                  .getOrElse(ComponentType.EBF)
                if compType == ComponentType.EBF then None
                else buildPendingAssignment(label, compType)
              }
            }
            if newPending.nonEmpty then
              pendingAssignments.update(_ ++ newPending)
            seenPolygonLabels = currentLabels

        dom.window.addEventListener(polygonSyncEvent, polyListener)
        polygonSyncListener = Some(polyListener)

        // ── plans sync → EbfState ──
        val plansListener: js.Function1[dom.Event, Unit] = (event: dom.Event) =>
          val detail  = event.asInstanceOf[dom.CustomEvent].detail
          val jsonStr = js.JSON.stringify(detail.asInstanceOf[js.Any])
          decode[EbfPlans](jsonStr) match
            case Right(plans) =>
              val existing = EbfState.getEbfPlans.plans.map(p => p.id -> p.imageDataUrl).toMap
              val merged   = plans.copy(plans = plans.plans.map { p =>
                if p.imageDataUrl.isDefined then p
                else p.copy(imageDataUrl = existing.getOrElse(p.id, None))
              })
              EbfState.updatePlans(merged)
              AppState.saveEbfPlans()
            case Left(err) =>
              dom.console.error(s"ebf-plans-sync parse error: $err")
        dom.window.addEventListener(plansSyncEvent, plansListener)
        plansSyncListener = Some(plansListener)

        // ── polygon renamed in EBF sidebar → rename description in AreaState ──
        val renamedListener: js.Function1[dom.Event, Unit] = (event: dom.Event) =>
          val d        = event.asInstanceOf[dom.CustomEvent].detail.asInstanceOf[js.Dynamic]
          val oldLabel = d.selectDynamic("oldLabel").toString
          val newLabel = d.selectDynamic("newLabel").toString
          if oldLabel.nonEmpty && newLabel.nonEmpty then
            AreaState.renameDescription(oldLabel, newLabel)
            AppState.saveAreaCalculations()
        dom.window.addEventListener(polygonRenamedEvent, renamedListener)
        polygonRenamedListener = Some(renamedListener)

        // ── import reset → clear stale area entries before fresh sync ──
        val resetListener: js.Function1[dom.Event, Unit] = (_: dom.Event) =>
          seenPolygonLabels = Set.empty
          isFirstSync = true
          AreaState.initializeEmpty()
        dom.window.addEventListener(polygonResetEvent, resetListener)
        polygonResetListener = Some(resetListener)

        // ── WB linear measurement done → WaermebrueckeState ──
        val wbLinListener: js.Function1[dom.Event, Unit] = (event: dom.Event) =>
          val d      = event.asInstanceOf[dom.CustomEvent].detail.asInstanceOf[js.Dynamic]
          val length = js.Dynamic.global.Number(d.selectDynamic("length")).asInstanceOf[Double]
          if !length.isNaN && length > 0 then
            WaermebrueckeState.addLinear(length)
            AppState.saveWaermebruecken()
        dom.window.addEventListener(wbLinearDoneEvent, wbLinListener)
        wbLinearListener = Some(wbLinListener)

        // ── WB points session done → WaermebrueckeState ──
        val wbPtsListener: js.Function1[dom.Event, Unit] = (event: dom.Event) =>
          val d     = event.asInstanceOf[dom.CustomEvent].detail.asInstanceOf[js.Dynamic]
          val count = js.Dynamic.global.Number(d.selectDynamic("count")).asInstanceOf[Double].toInt
          if count > 0 then
            WaermebrueckeState.addPunkt(count)
            AppState.saveWaermebruecken()
        dom.window.addEventListener(wbPointsDoneEvent, wbPtsListener)
        wbPointsListener = Some(wbPtsListener)

        // ── plan upload → Google Drive ──
        val uploadListener: js.Function1[dom.Event, Unit] = (event: dom.Event) =>
          val d        = event.asInstanceOf[dom.CustomEvent].detail.asInstanceOf[js.Dynamic]
          val fileName = d.fileName.toString
          val mimeType = d.mimeType.toString
          val buffer   = d.buffer.asInstanceOf[js.typedarray.ArrayBuffer]
          AppState.getCurrentProject.foreach { project =>
            val projectName = project.project.projectName match
              case name if name.nonEmpty => name
              case _                     => "Unnamed_Project"
            val sanitized  = projectName.replaceAll("[^a-zA-Z0-9-_]", "_")
            val folderPath = s"${GoogleDriveConfig.rootFolder}/$sanitized/07_Unterlagen"
            GoogleDriveService.uploadFile(folderPath, fileName, buffer, mimeType).foreach { ok =>
              if ok then dom.console.log(s"✅ Plan PDF uploaded: $fileName")
              else        dom.console.error(s"❌ Plan PDF upload failed: $fileName")
            }
          }
        dom.window.addEventListener(planUploadEvent, uploadListener)
        planUploadListener = Some(uploadListener)

        // ── mount EBF component ──
        val mountFn = dom.window.asInstanceOf[js.Dynamic].mountEbfCalculator
        if js.isUndefined(mountFn) then
          dom.console.error("window.mountEbfCalculator is not available")
        else
          mountFn(ctx.thisNode.ref)
            .asInstanceOf[js.Promise[js.Function0[Unit]]]
            .`then`[Unit] { (unmount: js.Function0[Unit]) =>
              unmountHandle = Some(unmount)
              val saved      = EbfState.getEbfPlans
              val plansJson  = saved.asJson.noSpaces
              val plansJsObj = js.JSON.parse(plansJson)
              val init = js.Dynamic.literal(detail = plansJsObj, bubbles = false, cancelable = false)
              dom.window.dispatchEvent(
                new dom.CustomEvent(loadPlansEvent, init.asInstanceOf[dom.CustomEventInit])
              )
              ()
            }
            .`catch` { (error: scala.Any) =>
              dom.console.error("Failed to mount EBF calculator", error)
            }
      },
      onUnmountCallback { _ =>
        polygonSyncListener.foreach(l    => dom.window.removeEventListener(polygonSyncEvent, l))
        plansSyncListener.foreach(l      => dom.window.removeEventListener(plansSyncEvent, l))
        planUploadListener.foreach(l     => dom.window.removeEventListener(planUploadEvent, l))
        polygonRenamedListener.foreach(l => dom.window.removeEventListener(polygonRenamedEvent, l))
        polygonResetListener.foreach(l   => dom.window.removeEventListener(polygonResetEvent, l))
        wbLinearListener.foreach(l       => dom.window.removeEventListener(wbLinearDoneEvent, l))
        wbPointsListener.foreach(l       => dom.window.removeEventListener(wbPointsDoneEvent, l))
        polygonSyncListener = None; plansSyncListener = None; planUploadListener = None
        polygonRenamedListener = None; polygonResetListener = None
        wbLinearListener = None; wbPointsListener = None
        unmountHandle.foreach(_())
        unmountHandle = None
      },
      htmlTag("link")(rel := "stylesheet", href := "ebf/styles.css?v=33"),
      div(
        className := "app",
        styleAttr := "height: 100%;",
        EBFSidebarView(),
        EBFMainPanelView()
      ),
      calibrationIntroModal(),
      clearConfirmModal(),
      scaleDialog(),
      child <-- pendingAssignments.signal.map {
        case Nil => emptyNode
        case PendingAssignment(polygonLabel, compType, options) :: _ =>
          val orientationVar = Var("")
          val showOrientation = orientationPopupTypes.contains(compType)
          val orientationOptions = List("", "N", "NO", "O", "SO", "S", "SW", "W", "NW", "Horiz")
          def applyOrientation(): Unit =
            val ori = orientationVar.now()
            if ori.nonEmpty then
              AreaState.updateOrientation(polygonLabel, ori)
              AppState.saveAreaCalculations()
          div(
            styleAttr := "position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: 9999; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.55);",
            div(
              styleAttr := "background: #1a1a28; border: 1px solid rgba(255,255,255,0.1); border-radius: 12px; padding: 24px; min-width: 320px; max-width: 480px; box-shadow: 0 8px 32px rgba(0,0,0,0.6); font-family: system-ui,sans-serif; color: #f0f0f8;",
              h3(
                styleAttr := "margin: 0 0 6px; font-size: 16px; font-weight: 600;",
                if options.nonEmpty then s"Einstellungen für Polygon «$polygonLabel»"
                else s"Ausrichtung für Polygon «$polygonLabel»"
              ),
              // Orientation dropdown (shown for wall/roof types)
              Option.when(showOrientation)(
                div(
                  styleAttr := "margin-bottom: 14px;",
                  p(styleAttr := "margin: 0 0 6px; font-size: 13px; color: #8888aa;", "Ausrichtung wählen:"),
                  select(
                    styleAttr := "width: 100%; padding: 8px 10px; font-size: 13px; background: #2a2a3a; color: #f0f0f8; border: 1px solid rgba(255,255,255,0.15); border-radius: 6px; cursor: pointer;",
                    onChange.mapToValue --> orientationVar.writer,
                    orientationOptions.map(o => option(value := o, if o.isEmpty then "– Ausrichtung wählen –" else o))
                  )
                )
              ),
              // U-Wert options (shown when ≥1 option exists)
              Option.when(options.nonEmpty)(
                div(
                  p(styleAttr := "margin: 0 0 8px; font-size: 13px; color: #8888aa;", "U-Wert wählen:"),
                  div(
                    (Seq[Modifier[HtmlElement]](
                      styleAttr := "display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px;"
                    ) ++ options.map { opt =>
                      button(
                        styleAttr := s"padding: 10px 14px; font-size: 13px; font-weight: 600; background: ${opt.adjustedColor}; color: #111; border: none; border-radius: 8px; cursor: pointer; text-align: left;",
                        s"${opt.displayLabel}  (${f"${opt.uValue}%.2f"} W/m²K)",
                        onClick --> { _ =>
                          applyOrientation()
                          AreaState.linkUWert(polygonLabel, opt.id, opt.displayLabel, Some(opt.uValue), opt.gValue, opt.glassRatio, opt.bValue)
                          AppState.saveAreaCalculations()
                          val detail = js.Dynamic.literal(label = polygonLabel, color = opt.adjustedColor)
                          val init   = js.Dynamic.literal(detail = detail, bubbles = false, cancelable = false)
                          dom.window.dispatchEvent(
                            new dom.CustomEvent(updateColorEvent, init.asInstanceOf[dom.CustomEventInit])
                          )
                          pendingAssignments.update(_.tail)
                        }
                      ): Modifier[HtmlElement]
                    })*
                  )
                )
              ),
              // "Bestätigen" button — only shown when there are no U-Wert options (orientation-only popup)
              Option.when(options.isEmpty)(
                button(
                  styleAttr := "padding: 8px 14px; font-size: 13px; font-weight: 600; background: #4f46e5; color: #fff; border: none; border-radius: 8px; cursor: pointer; margin-bottom: 8px; width: 100%;",
                  "Bestätigen",
                  onClick --> { _ =>
                    applyOrientation()
                    pendingAssignments.update(_.tail)
                  }
                )
              ),
              button(
                styleAttr := "padding: 8px 14px; font-size: 12px; font-weight: 600; background: transparent; color: #666; border: 1px solid rgba(255,255,255,0.15); border-radius: 8px; cursor: pointer;",
                "Überspringen",
                onClick --> { _ => pendingAssignments.update(_.tail) }
              )
            )
          )
      }
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
        h3(idAttr := "scale-dialog-title", "Wahre Entfernung"),
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

end EBFCalculatorView
