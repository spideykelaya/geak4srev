package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import pme123.geak4s.components.{AreaCalculationTable, WaermebrueckeTable}
import pme123.geak4s.domain.area.*
import pme123.geak4s.state.{UWertState, AreaState, AppState, WaermebrueckeState}
import pme123.geak4s.domain.uwert.ComponentType

/**
 * Area calculation view (Flächenberechnung)
 * A section appears only when the ComponentType has polygon data OR a U-Wert calculation.
 * - Area exists, no U-Wert → "U-Wert fehlt"  (only for types where U-Wert applies)
 * - U-Wert exists, no area → "fehlende Fläche oder überflüssiger U-Wert"
 */
object AreaView:

  // These types have no U-Wert — warnings about missing U-Wert are suppressed for them
  private val noUWertTypes = Set(ComponentType.EBF)


  private val areaEntriesByType = scala.collection.mutable.Map[ComponentType, Var[List[AreaEntry]]]()

  private def entriesVar(ct: ComponentType): Var[List[AreaEntry]] =
    areaEntriesByType.getOrElseUpdate(ct, Var(List.empty))

  private def saveEntriesToState(ct: ComponentType, entries: List[AreaEntry]): Unit =
    AreaState.updateAreaCalculation(ct, entries, syncEbfToWordForm = false)
    AppState.saveAreaCalculations()

  def loadFromState(): Unit =
    AreaState.areaCalculations.now().foreach { area =>
      area.calculations.foreach { calc =>
        val v = entriesVar(calc.componentType)
        if v.now() != calc.entries then v.set(calc.entries)
      }
    }

  private def onAreaStateChange(maybeArea: Option[BuildingEnvelopeArea]): Unit =
    maybeArea.foreach { area =>
      area.calculations.foreach { calc =>
        val v = entriesVar(calc.componentType)
        if v.now() != calc.entries then v.set(calc.entries)
      }
    }

  // All ComponentTypes that have data in either source, sorted by the fixed display order
  private val allTypesSignal: Signal[List[ComponentType]] =
    UWertState.calculations.signal
      .combineWith(AreaState.areaCalculations.signal)
      .map { case (calcs, maybeArea) =>
        val fromUWert = calcs.filter(_.componentLabel.nonEmpty).map(_.componentType).toSet
        val fromArea  = maybeArea.toList.flatMap(_.calculations.map(_.componentType)).toSet
        val present   = fromUWert ++ fromArea
        ComponentType.orderedVisibleTypes.filter(present.contains).toList
      }

  // ComponentTypes not yet shown — available for manual addition
  private val missingTypesSignal: Signal[List[ComponentType]] =
    allTypesSignal.map { shown =>
      ComponentType.orderedVisibleTypes.filterNot(shown.contains).toList
    }

  private def addManualSection(ct: ComponentType): Unit =
    val entry = AreaEntry.empty("").copy(isManual = true)
    saveEntriesToState(ct, List(entry))

  private lazy val viewElement: HtmlElement =
    div(
      className := "area-view",
      onMountCallback { _ =>
        loadFromState()
        // Request EBF rechner to re-emit polygon labels so kuerzel is always in sync
        org.scalajs.dom.window.dispatchEvent(
          new org.scalajs.dom.CustomEvent("geak:ebf-request-sync",
            scala.scalajs.js.Dynamic.literal(bubbles = false, cancelable = false)
              .asInstanceOf[org.scalajs.dom.CustomEventInit])
        )
      },
      AreaState.areaCalculations.signal --> Observer[Option[BuildingEnvelopeArea]](onAreaStateChange),

      Card(
        className := "project-view",
        maxWidth  := "100%",
        display   := "flex",
        div(
          className := "card-content",
          padding   := "1.5rem",
          children <-- allTypesSignal.split(identity) { (ct, _, _) =>
            renderSection(ct)
          },
          waermebrueckenSection(),
          child <-- missingTypesSignal.map { missing =>
            if missing.isEmpty then div()
            else
              div(
                marginTop := "1.5rem",
                select(
                  styleAttr := "padding:0.4rem 0.6rem;min-width:280px;border:1px solid #ccc;border-radius:4px;font-size:0.9rem;cursor:pointer",
                  option(value := "", disabled := true, selected := true, "Abschnitt manuell hinzufügen …"),
                  missing.map(ct => option(value := ct.polygonLabel, ct.label)),
                  onChange --> Observer[org.scalajs.dom.Event] { e =>
                    val sel = e.target.asInstanceOf[org.scalajs.dom.html.Select]
                    ComponentType.orderedVisibleTypes
                      .find(_.polygonLabel == sel.value)
                      .foreach { ct =>
                        addManualSection(ct)
                        sel.value = "" // reset so same type can be selected again if needed
                      }
                  }
                )
              )
          }
        )
      )
    )

  def apply(): HtmlElement = viewElement

  private def renderSection(ct: ComponentType): HtmlElement =
    val entries = entriesVar(ct)

    val hasUWertSignal: Signal[Boolean] =
      UWertState.calculations.signal.map(
        _.exists(c => c.componentType == ct && c.componentLabel.nonEmpty)
      )

    val hasAreaSignal: Signal[Boolean] =
      AreaState.areaCalculations.signal.map(
        _.exists(_.get(ct).exists(_.entries.nonEmpty))
      )

    div(
      marginBottom    := "3rem",
      padding         := "1.5rem",
      backgroundColor := ct.color,
      borderRadius    := "8px",
      border          := "1px solid #ddd",

      div(marginBottom := "1rem", Title(_.level := TitleLevel.H3, ct.label)),

      // Area exists but U-Wert missing (not shown for EBF / Fenster / Tür)
      child.maybe <-- hasUWertSignal.map { hasUWert =>
        Option.when(!hasUWert && !noUWertTypes.contains(ct))(
          MessageStrip(
            _.design          := MessageStripDesign.Warning,
            _.hideCloseButton := true,
            "U-Wert fehlt"
          )
        )
      },

      // U-Wert exists but no area data yet
      child.maybe <-- hasAreaSignal.combineWith(hasUWertSignal).map { case (hasArea, hasUWert) =>
        Option.when(hasUWert && !hasArea)(
          MessageStrip(
            _.design          := MessageStripDesign.Warning,
            _.hideCloseButton := true,
            "fehlende Fläche oder überflüssiger U-Wert"
          )
        )
      },

      div(
        backgroundColor := "white",
        padding         := "1rem",
        borderRadius    := "4px",
        marginBottom    := "1.5rem",
        AreaCalculationTable(ct, entries, saveEntriesToState)
      )
    )

  private def waermebrueckenSection(): HtmlElement =
    div(
      marginBottom    := "3rem",
      padding         := "1.5rem",
      backgroundColor := "#f0fdf4",
      borderRadius    := "8px",
      border          := "1px solid #86efac",

      div(marginBottom := "1rem", Title(_.level := TitleLevel.H3, "Wärmebrücken")),

      div(
        marginBottom    := "1.5rem",
        padding         := "1rem",
        backgroundColor := "white",
        borderRadius    := "4px",
        div(
          fontWeight    := "600",
          marginBottom  := "0.75rem",
          fontSize      := "0.95rem",
          "Lineare Wärmebrücken"
        ),
        WaermebrueckeTable.linearTable()
      ),

      div(
        padding         := "1rem",
        backgroundColor := "white",
        borderRadius    := "4px",
        div(
          fontWeight    := "600",
          marginBottom  := "0.75rem",
          fontSize      := "0.95rem",
          "Punktförmige Wärmebrücken"
        ),
        WaermebrueckeTable.punktTable()
      )
    )

end AreaView
