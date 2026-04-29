package pme123.geak4s.components

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom.{HTMLInputElement, KeyboardEvent}
import org.scalajs.dom
import scala.scalajs.js
import pme123.geak4s.domain.area.*
import pme123.geak4s.domain.uwert.{ComponentType, ComponentTypeDefaults}
import pme123.geak4s.state.UWertState
import pme123.geak4s.utils.ColorUtils

/** Reusable area calculation table component Displays area entries for a specific category (EBF,
  * Dach, Wand, etc.)
  */
object AreaCalculationTable:

  def apply(
      category: ComponentType,
      entries: Var[List[AreaEntry]],
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    // Local display state - only updated when rows are added/deleted, not on field edits
    val displayEntries = Var[List[AreaEntry]](entries.now())
    println(s"AreaCalculationTable: Initial displayEntries set to ${displayEntries.now().length} entries")
    div(
      className := "area-calculation-table",

      // Keep display in sync with entries, but only for structural changes
      entries.signal --> Observer[List[AreaEntry]] { newEntries =>
        // Update display if rows changed structurally or kuerzel values changed
        val current = displayEntries.now()
        val kuerzelChanged = current.zip(newEntries).exists { case (a, b) => a.kuerzel != b.kuerzel }
        if current.length != newEntries.length || kuerzelChanged then
          displayEntries.set(newEntries)
      },

      // Table - only re-renders when rows are added/deleted
      child <-- displayEntries.signal.map { currentEntries =>
      //  println(s"AreaCalculationTable: Rendering table with ${currentEntries.length} rows")
        renderTable(category, currentEntries, entries, onSave)
      },

      // Add row button
      div(
        marginTop := "1rem",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val currentEntries = entries.now()
            val d = ComponentTypeDefaults.get(category)
            val newEntries     = currentEntries :+ AreaEntry.empty().copy(
              isManual = true,
              nutzungsdauer = d.nutzungsdauer,
              investition   = d.investitionRate
            )
            entries.set(newEntries)
            displayEntries.set(newEntries)  // Update display immediately
            onSave(category, newEntries)
          },
          "Zeile hinzufügen"
        )
      )
    )

  private def renderTable(
      componentType: ComponentType,
      displayEntries: List[AreaEntry],
      dataEntries: Var[List[AreaEntry]],
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    div(
      overflowX := "auto",
      table(
        width          := "100%",
        border         := "1px solid #e0e0e0",
        borderCollapse := "collapse",

        // Header
        thead(
          backgroundColor := "#f5f5f5",
          tr(
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Kürzel"),
            Option.when(!noOrientationTypes.contains(componentType))(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", "Ausrichtung")
            ),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Beschrieb"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Länge/Umfang [m]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Breite/Höhe [m]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Fläche [m²]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Anzahl [Stk.]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Fläche Total [m²]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Fläche Neu [m²]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Anzahl Neu [Stk.]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Fläche Total Neu [m²]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Beschrieb Neu"),
            Option.when(componentType != ComponentType.EBF)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8", "U-Wert [W/m²K]")
            ),
            Option.when(componentType != ComponentType.EBF)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8", "b-Wert [-]")
            ),
            Option.when(componentType != ComponentType.EBF)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fef9c3", "Werterhalt [CHF/m²]")
            ),
            Option.when(componentType != ComponentType.EBF)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fef9c3",
                if componentType == ComponentType.Door then "Investition [CHF/Stk.]"
                else "Investition [CHF/m²]")
            ),
            Option.when(componentType != ComponentType.EBF)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fef9c3", "Nutzungsdauer [a]")
            ),
            Option.when(componentType == ComponentType.Window)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8", "g-Wert [-]")
            ),
            Option.when(componentType == ComponentType.Window)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8", "Glasanteil [-]")
            ),
            Option.when(componentType == ComponentType.Window)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fff8e1", "Überhang [m]")
            ),
            Option.when(componentType == ComponentType.Window)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fff8e1", "Abstand Überhang [m]")
            ),
            Option.when(componentType == ComponentType.Window)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fff8e1", "Seitenblende [m]")
            ),
            Option.when(componentType == ComponentType.Window)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fff8e1", "Abstand Seitenblende [m]")
            ),
            Option.when(componentType == ComponentType.Window)(
              th(border := "1px solid #e0e0e0", padding := "0.5rem", textAlign := "center", "Beidseitig")
            ),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "") // Delete button column
          )
        ),

        // Body
        tbody(
          displayEntries.zipWithIndex.map { case (entry, index) =>
            renderRow(entry, index, dataEntries, componentType, onSave)
          }
        ),

        // Footer - Total row
        tfoot(
          backgroundColor := "#f5f5f5",
          tr(
            // Empty cells for columns 1-6 (Bauteil Nr. through Fläche)
            td(
              border     := "1px solid #e0e0e0",
              padding    := "0.5rem",
              colSpan    := (if noOrientationTypes.contains(componentType) then 5 else 6),
              textAlign  := "right",
              fontWeight := "600",
              "Total:"
            ),

            // Anzahl [Stk.] - column 7
            td(
              border     := "1px solid #e0e0e0",
              padding    := "0.5rem",
              textAlign  := "right",
              fontWeight := "600",
              child.text <-- dataEntries.signal.map(_.map(_.quantity).sum.toString)
            ),

            // Fläche Total [m²] - column 8
            td(
              border     := "1px solid #e0e0e0",
              padding    := "0.5rem",
              textAlign  := "right",
              fontWeight := "600",
              child.text <-- dataEntries.signal.map { entries =>
                val sum = entries.map(_.totalArea).sum
                if componentType == ComponentType.EBF then math.round(sum).toString
                else f"$sum%.1f"
              }
            ),

            // Empty cell for Fläche Neu - column 9
            td(border    := "1px solid #e0e0e0", padding := "0.5rem"),

            // Anzahl Neu [Stk.] - column 10
            td(
              border     := "1px solid #e0e0e0",
              padding    := "0.5rem",
              textAlign  := "right",
              fontWeight := "600",
              child.text <-- dataEntries.signal.map(_.map(_.quantityNew).sum.toString)
            ),

            // Fläche Total Neu [m²] - column 11
            td(
              border     := "1px solid #e0e0e0",
              padding    := "0.5rem",
              textAlign  := "right",
              fontWeight := "600",
              child.text <-- dataEntries.signal.map { entries =>
                val sum = entries.map(_.totalAreaNew).sum
                if componentType == ComponentType.EBF then math.round(sum).toString
                else f"$sum%.1f"
              }
            ),

            // Empty cells for Beschrieb Neu, u-wert cols, wirtschaftliche cols, optional Fenster shading cols, and Delete button
            td(border    := "1px solid #e0e0e0", padding := "0.5rem"),
            Option.when(componentType != ComponentType.EBF)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8")),
            Option.when(componentType != ComponentType.EBF)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8")),
            // Werterhalt / Investition / Nutzungsdauer — no totals (rates per m², not absolute values)
            Option.when(componentType != ComponentType.EBF)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fef9c3")),
            Option.when(componentType != ComponentType.EBF)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fef9c3")),
            Option.when(componentType != ComponentType.EBF)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fef9c3")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem")),
            td(border    := "1px solid #e0e0e0", padding := "0.5rem")
          )
        )
      )
    )

  private def renderRow(
      displayEntry: AreaEntry,
      index: Int,
      dataEntries: Var[List[AreaEntry]],
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    tr(
      // Kürzel - editable
      td(
        border  := "1px solid #e0e0e0",
        padding := "0.25rem",
        renderEditableCell(
          displayEntry,
          index,
          _.kuerzel,
          dataEntries,
          (e, v) => e.copy(kuerzel = v),
          componentType,
          onSave
        )
      ),

      // Ausrichtung (nur bei nicht-horizontalen Typen)
      Option.when(!noOrientationTypes.contains(componentType))(
        td(
          border  := "1px solid #e0e0e0",
          padding := "0.25rem",
          renderOrientationCell(displayEntry, index, dataEntries, componentType, onSave)
        )
      ),

      // Beschrieb — dropdown wenn U-Wert-Berechnungen vorhanden
      td(
        border  := "1px solid #e0e0e0",
        padding := "0.25rem",
        renderDescriptionCell(displayEntry, index, dataEntries, componentType, onSave)
      ),

      // Länge
      td(
        border    := "1px solid #e0e0e0",
        padding   := "0.25rem",
        textAlign := "right",
        renderNumericCell(
          displayEntry,
          index,
          _.length,
          dataEntries,
          (e, v) =>
            val updated        = e.copy(length = v)
            val calculatedArea = v * updated.width
            updated.copy(area = calculatedArea, totalArea = calculatedArea * updated.quantity,
              areaNew = calculatedArea, totalAreaNew = calculatedArea * updated.quantityNew)
          ,
          componentType,
          onSave
        )
      ),

      // Breite
      td(
        border    := "1px solid #e0e0e0",
        padding   := "0.25rem",
        textAlign := "right",
        renderNumericCell(
          displayEntry,
          index,
          _.width,
          dataEntries,
          (e, v) =>
            val updated        = e.copy(width = v)
            val calculatedArea = updated.length * v
            updated.copy(area = calculatedArea, totalArea = calculatedArea * updated.quantity,
              areaNew = calculatedArea, totalAreaNew = calculatedArea * updated.quantityNew)
          ,
          componentType,
          onSave
        )
      ),

      // Fläche (can be edited directly, resets length/width to 0)
      td(
        border    := "1px solid #e0e0e0",
        padding   := "0.25rem",
        textAlign := "right",
        renderNumericCell(
          displayEntry,
          index,
          _.area,
          dataEntries,
          (e, v) => e.copy(area = v, width = 0, length = 0, totalArea = v * e.quantity,
            areaNew = v, totalAreaNew = v * e.quantityNew),
          componentType,
          onSave
        )
      ),

      // Anzahl
      td(
        border    := "1px solid #e0e0e0",
        padding   := "0.25rem",
        textAlign := "right",
        renderIntCell(
          displayEntry,
          index,
          _.quantity,
          dataEntries,
          (e, v) =>
            val updated = e.copy(quantity = v)
            updated.copy(totalArea = updated.calculateTotalArea)
          ,
          componentType,
          onSave
        )
      ),

      // Fläche Total (calculated)
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        backgroundColor := "#f9f9f9",
        textAlign       := "right",
        child.text <-- dataEntries.signal.map { entries =>
          if index < entries.length then
            val v = entries(index).totalArea
            if componentType == ComponentType.EBF then f"$v%.0f" else f"$v%.1f"
          else "0.00"
        }
      ),

      // Fläche Neu
      td(
        border    := "1px solid #e0e0e0",
        padding   := "0.25rem",
        textAlign := "right",
        renderNumericCell(
          displayEntry,
          index,
          _.areaNew,
          dataEntries,
          (e, v) =>
            val updated = e.copy(areaNew = v)
            updated.copy(totalAreaNew = updated.calculateTotalAreaNew)
          ,
          componentType,
          onSave
        )
      ),

      // Anzahl Neu
      td(
        border    := "1px solid #e0e0e0",
        padding   := "0.25rem",
        textAlign := "right",
        renderIntCell(
          displayEntry,
          index,
          _.quantityNew,
          dataEntries,
          (e, v) =>
            val updated = e.copy(quantityNew = v)
            updated.copy(totalAreaNew = updated.calculateTotalAreaNew)
          ,
          componentType,
          onSave
        )
      ),

      // Fläche Total Neu (calculated)
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        backgroundColor := "#f9f9f9",
        textAlign       := "right",
        child.text <-- dataEntries.signal.map { entries =>
          if index < entries.length then
            val v = entries(index).totalAreaNew
            if componentType == ComponentType.EBF then f"$v%.0f" else f"$v%.1f"
          else "0.00"
        }
      ),

      // Beschrieb Neu
      td(
        border  := "1px solid #e0e0e0",
        padding := "0.25rem",
        renderEditableCell(
          displayEntry,
          index,
          _.descriptionNew,
          dataEntries,
          (e, v) => e.copy(descriptionNew = v),
          componentType,
          onSave
        )
      ),

      // U-Wert (read-only, from linked calculation via description dropdown)
      Option.when(componentType != ComponentType.EBF)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#e8f4f8", textAlign := "right",
          child.text <-- dataEntries.signal.map(es =>
            if index < es.length then es(index).uValue.fold("–")(v => f"$v%.2f") else "–"
          )
        )
      ),
      Option.when(componentType != ComponentType.EBF)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#e8f4f8", textAlign := "right",
          child.text <-- dataEntries.signal.map(es =>
            if index < es.length then es(index).bValue.fold("–")(v => f"$v%.2f") else "–"
          )
        )
      ),

      // Werterhalt / Investition / Nutzungsdauer — editable, auto-filled from component defaults
      Option.when(componentType != ComponentType.EBF)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#fef9c3",
          componentType match
            case ComponentType.Window =>
              // Fenster: dropdown PVC vs Holz-Metall (sets both Werterhalt + Investition)
              renderRateDropdownCell(
                displayEntry, index, dataEntries, componentType, onSave,
                options = List("pvc" -> "PVC – 800 CHF/m²", "holz" -> "Holz-Metall – 1'000 CHF/m²"),
                effectiveFn = ComponentTypeDefaults.effectiveWerterhalt
              )
            case _ =>
              renderEconomicCell(displayEntry, index, dataEntries, componentType, onSave,
                effectiveFn = ComponentTypeDefaults.effectiveWerterhalt,
                isAutoComputed = e => e.rateKey.isEmpty && e.werterhalt == 0.0,
                update = (e, v) => e.copy(werterhalt = v, rateKey = ""),
                decimals = 0
              )
        )
      ),
      Option.when(componentType != ComponentType.EBF)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#fef9c3",
          componentType match
            case ct if kellerwandTypes.contains(ct) =>
              // Kellerwand: dropdown einfach vs aufwändig (sets Investition; Werterhalt bleibt 0)
              renderRateDropdownCell(
                displayEntry, index, dataEntries, componentType, onSave,
                options = List("einfach" -> "Einfach – 150 CHF/m²", "aufwaendig" -> "Aufwändig – 200 CHF/m²"),
                effectiveFn = ComponentTypeDefaults.effectiveInvestition
              )
            case _ =>
              renderEconomicCell(displayEntry, index, dataEntries, componentType, onSave,
                effectiveFn = ComponentTypeDefaults.effectiveInvestition,
                isAutoComputed = e => e.rateKey.isEmpty && e.investition == 0.0,
                update = (e, v) => e.copy(investition = v, rateKey = ""),
                decimals = 0
              )
        )
      ),
      Option.when(componentType != ComponentType.EBF)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#fef9c3",
          renderNutzungsdauerCell(displayEntry, index, dataEntries, componentType, onSave)
        )
      ),

      Option.when(componentType == ComponentType.Window)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#e8f4f8", textAlign := "right",
          child.text <-- dataEntries.signal.map(es =>
            if index < es.length then es(index).gValue.fold("–")(v => f"$v%.2f") else "–"
          )
        )
      ),
      Option.when(componentType == ComponentType.Window)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#e8f4f8", textAlign := "right",
          child.text <-- dataEntries.signal.map(es =>
            if index < es.length then es(index).glassRatio.fold("–")(v => f"$v%.2f") else "–"
          )
        )
      ),

      // Fenster shading columns
      Option.when(componentType == ComponentType.Window)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#fffde7",
          renderNumericCell(displayEntry, index, _.overhang, dataEntries,
            (e, v) => e.copy(overhang = v), componentType, onSave)
        )
      ),
      Option.when(componentType == ComponentType.Window)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#fffde7",
          textAlign := "right",
          child.text <-- dataEntries.signal.map { entries =>
            if index < entries.length then f"${entries(index).overhangDist}%.2f" else "0.00"
          }
        )
      ),
      Option.when(componentType == ComponentType.Window)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#fffde7",
          renderNumericCell(displayEntry, index, _.sideShading, dataEntries,
            (e, v) => e.copy(sideShading = v), componentType, onSave)
        )
      ),
      Option.when(componentType == ComponentType.Window)(
        td(
          border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#fffde7",
          textAlign := "right",
          child.text <-- dataEntries.signal.map { entries =>
            if index < entries.length then f"${entries(index).sideShadingDist}%.2f" else "0.00"
          }
        )
      ),

      // Beidseitig checkbox (nur Fenster)
      Option.when(componentType == ComponentType.Window)(
        td(
          border    := "1px solid #e0e0e0",
          padding   := "0.25rem",
          textAlign := "center",
          input(
            typ := "checkbox",
            checked <-- dataEntries.signal.map(es => if index < es.length then es(index).beidseitig else false),
            onChange.mapToChecked --> Observer[Boolean] { v =>
              val curr = dataEntries.now()
              if index < curr.length then
                val newEntries = curr.updated(index, curr(index).copy(beidseitig = v))
                dataEntries.set(newEntries)
                onSave(componentType, newEntries)
            }
          )
        )
      ),

      // Delete button
      td(
        border    := "1px solid #e0e0e0",
        padding   := "0.25rem",
        textAlign := "center",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.delete,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val newEntries = dataEntries.now().patch(index, Nil, 1)
            dataEntries.set(newEntries)
            // Note: displayEntries will be updated via the sync subscription
            onSave(componentType, newEntries)
          }
        )
      )
    )

  /** Dispatch geak:update-polygon-color event so the EBF canvas reflects the new U-value color. */
  private def dispatchPolygonColorUpdate(kuerzel: String, color: String): Unit =
    val detail = js.Dynamic.literal(label = kuerzel, color = color)
    val init   = js.Dynamic.literal(detail = detail, bubbles = false, cancelable = false)
    dom.window.dispatchEvent(
      new dom.CustomEvent("geak:update-polygon-color", init.asInstanceOf[dom.CustomEventInit])
    )

  private def renderDescriptionCell(
      displayEntry: AreaEntry,
      index: Int,
      dataEntries: Var[List[AreaEntry]],
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    def plainText: HtmlElement =
      renderEditableCell(displayEntry, index, _.description, dataEntries, (e, v) => e.copy(description = v), componentType, onSave)

    componentType match
      case ComponentType.EBF =>
        plainText

      case ComponentType.Window =>
        div(
          child <-- UWertState.windowCalculations.signal.map { windowCalcs =>
            val named = windowCalcs.filter(_.label.nonEmpty)
            if named.isEmpty then plainText
            else
              select(
                width := "100%", padding := "0.25rem", border := "none",
                value <-- dataEntries.signal.map(es => if index < es.length then es(index).description else ""),
                onChange.mapToValue --> Observer[String] { label =>
                  val matched = named.find(_.label == label)
                  val curr = dataEntries.now()
                  if index < curr.length then
                    val updated = curr(index).copy(
                      description = label,
                      uwertId     = matched.map(_.id),
                      uValue      = matched.map(_.uValue),
                      gValue      = matched.map(_.gValue),
                      glassRatio  = matched.map(_.glassRatio)
                    )
                    val newEntries = curr.updated(index, updated)
                    dataEntries.set(newEntries)
                    onSave(componentType, newEntries)
                    // Sync color to floor plan canvas
                    val allUVals = named.map(_.uValue)
                    val color = matched
                      .map(wc => ColorUtils.computeUWertColor(ComponentType.Window.polygonColor, wc.uValue, allUVals))
                      .getOrElse(ComponentType.Window.polygonColor)
                    dispatchPolygonColorUpdate(curr(index).kuerzel, color)
                },
                option(value := "", "– wählen –"),
                named.map(wc => option(value := wc.label, wc.label))
              )
          }
        )

      case ct =>
        div(
          child <-- UWertState.calculations.signal.map { calcs =>
            val matching = calcs.filter(c => c.componentType == ct && c.componentLabel.nonEmpty)
            if matching.isEmpty then plainText
            else
              val opts = matching.zipWithIndex.map { (calc, idx) =>
                val base  = if calc.label.nonEmpty then calc.label else calc.componentLabel
                val label = if matching.length == 1 || calc.label.nonEmpty then base
                            else s"$base (${idx + 1})"
                (calc.id, label, calc.istCalculation.uValueWithoutB, calc.istCalculation.bFactor)
              }
              select(
                width := "100%", padding := "0.25rem", border := "none",
                value <-- dataEntries.signal.map(es => if index < es.length then es(index).description else ""),
                onChange.mapToValue --> Observer[String] { label =>
                  val matched = opts.find(_._2 == label)
                  val curr = dataEntries.now()
                  if index < curr.length then
                    val updated = curr(index).copy(
                      description = label,
                      uwertId     = matched.map(_._1),
                      uValue      = matched.map(_._3),
                      bValue      = matched.map(_._4)
                    )
                    val newEntries = curr.updated(index, updated)
                    dataEntries.set(newEntries)
                    onSave(componentType, newEntries)
                    // Sync color to floor plan canvas (use IST values, consistent with EBFCalculatorView)
                    val allUVals = calcs
                      .filter(c => c.componentType == ct && c.componentLabel.nonEmpty)
                      .map(_.istCalculation.uValueWithoutB)
                    val color = matched
                      .map { (_, _, uVal, _) => ColorUtils.computeUWertColor(ct.polygonColor, uVal, allUVals) }
                      .getOrElse(ct.polygonColor)
                    dispatchPolygonColorUpdate(curr(index).kuerzel, color)
                },
                option(value := "", "– wählen –"),
                opts.map { (_, label, _, _) => option(value := label, label) }
              )
          }
        )

  private val noOrientationTypes = Set(
    ComponentType.EBF,
    ComponentType.AtticFloor,
    ComponentType.BasementFloor,
    ComponentType.BasementCeiling,
    ComponentType.FloorToOutside
  )

  private val orientationOptions = List("", "N", "NO", "O", "SO", "S", "SW", "W", "NW", "Horiz")

  private def renderOrientationCell(
      displayEntry: AreaEntry,
      index: Int,
      dataEntries: Var[List[AreaEntry]],
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    select(
      width   := "100%",
      padding := "0.25rem",
      border  := "none",
      value <-- dataEntries.signal.map(es => if index < es.length then es(index).orientation else ""),
      onChange.mapToValue --> Observer[String] { v =>
        val curr = dataEntries.now()
        if index < curr.length then
          val newEntries = curr.updated(index, curr(index).copy(orientation = v))
          dataEntries.set(newEntries)
          onSave(componentType, newEntries)
      },
      orientationOptions.map(o => option(value := o, if o.isEmpty then "–" else o))
    )

  private def renderEditableCell(
      displayEntry: AreaEntry,
      index: Int,
      getValue: AreaEntry => String,
      dataEntries: Var[List[AreaEntry]],
      updateEntry: (AreaEntry, String) => AreaEntry,
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit,
      onValueChange: Option[(String, String) => Unit] = None
  ): HtmlElement =
    input(
      typ     := "text",
      width   := "100%",
      padding := "0.25rem",
      border  := "none",
      // Controlled input: value updates reactively from dataEntries
      value <-- dataEntries.signal.map { entries =>
        if index < entries.length then getValue(entries(index))
        else ""
      },
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val inputEl = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement]
        val value = inputEl.value
        val currentEntries = dataEntries.now()
        if index < currentEntries.length then
          val entry      = currentEntries(index)
          val oldVal     = getValue(entry)
          val updated    = updateEntry(entry, value)
          val newEntries = currentEntries.updated(index, updated)
          dataEntries.set(newEntries)
          onSave(componentType, newEntries)
          onValueChange.foreach(_(oldVal, value))
      }
    )

  private def renderNumericCell(
      displayEntry: AreaEntry,
      index: Int,
      getValue: AreaEntry => Double,
      dataEntries: Var[List[AreaEntry]],
      updateEntry: (AreaEntry, Double) => AreaEntry,
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    input(
      typ       := "number",
      width     := "100%",
      padding   := "0.25rem",
      border    := "none",
      stepAttr  := "0.01",
      textAlign := "right",
      // Controlled input: value updates reactively from dataEntries
      value <-- dataEntries.signal.map { entries =>
        if index < entries.length then getValue(entries(index)).toString
        else "0"
      },
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val inputEl = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement]
        val value = inputEl.value
        val numValue = value.toDoubleOption.getOrElse(0.0)
        val currentEntries = dataEntries.now()
        if index < currentEntries.length then
          val entry      = currentEntries(index)
          val updated    = updateEntry(entry, numValue)
          val newEntries = currentEntries.updated(index, updated)
          dataEntries.set(newEntries)
          onSave(componentType, newEntries)
      }
    )

  private def renderIntCell(
      displayEntry: AreaEntry,
      index: Int,
      getValue: AreaEntry => Int,
      dataEntries: Var[List[AreaEntry]],
      updateEntry: (AreaEntry, Int) => AreaEntry,
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    input(
      typ       := "number",
      width     := "100%",
      padding   := "0.25rem",
      border    := "none",
      stepAttr  := "1",
      textAlign := "right",
      // Controlled input: value updates reactively from dataEntries
      value <-- dataEntries.signal.map { entries =>
        if index < entries.length then getValue(entries(index)).toString
        else "0"
      },
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val inputEl = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement]
        val value = inputEl.value
        val numValue = value.toIntOption.getOrElse(0)
        val currentEntries = dataEntries.now()
        if index < currentEntries.length then
          val entry      = currentEntries(index)
          val updated    = updateEntry(entry, numValue)
          val newEntries = currentEntries.updated(index, updated)
          dataEntries.set(newEntries)
          onSave(componentType, newEntries)
      }
    )

  private val kellerwandTypes: Set[ComponentType] = Set(
    ComponentType.BasementWallToEarth,
    ComponentType.BasementWallToUnheated,
    ComponentType.BasementWallToOutside,
    ComponentType.BasementCeiling,
    ComponentType.BasementFloor
  )

  /** Rate preset dropdown cell (Fenster Werterhalt, Kellerwand Investition).
   *  Selecting a preset stores rateKey; the effective value is computed reactively from totalArea × rate.
   *  Selecting "— auswählen —" clears rateKey (reverts to stored/default). */
  private def renderRateDropdownCell(
      displayEntry: AreaEntry,
      index: Int,
      dataEntries: Var[List[AreaEntry]],
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit,
      options: List[(String, String)],     // (rateKey, display label)
      effectiveFn: (AreaEntry, ComponentType) => Double
  ): HtmlElement =
    div(
      display := "flex",
      flexDirection := "column",
      gap := "0.1rem",
      // Preset dropdown
      select(
        fontSize := "0.8rem",
        padding := "0.1rem 0.2rem",
        border := "1px solid #ccc",
        borderRadius := "3px",
        width := "100%",
        value <-- dataEntries.signal.map(es => if index < es.length then es(index).rateKey else ""),
        onChange.mapToValue --> Observer[String] { selectedKey =>
          val curr = dataEntries.now()
          if index < curr.length then
            val newEntries = curr.updated(index, curr(index).copy(
              rateKey = selectedKey,
              werterhalt = 0.0,   // cleared so effectiveX uses rateKey computation
              investition = 0.0
            ))
            dataEntries.set(newEntries)
            onSave(componentType, newEntries)
        },
        option(value := "", "— auswählen —"),
        options.map { (key, label) => option(value := key, label) }
      ),
      // Computed CHF total shown below dropdown when a preset is active
      child.text <-- dataEntries.signal.map { entries =>
        if index < entries.length && entries(index).rateKey.nonEmpty then
          val total = effectiveFn(entries(index), componentType)
          if total != 0.0 then f"= CHF $total%.0f" else ""
        else ""
      }
    )

  /** Editable cell for Werterhalt or Investition.
   *  Shows auto-computed default (grey/italic) when no explicit value and no rateKey is active.
   *  Typing a value clears rateKey (preset dropdown resets to "— auswählen —"). */
  private def renderEconomicCell(
      displayEntry: AreaEntry,
      index: Int,
      dataEntries: Var[List[AreaEntry]],
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit,
      effectiveFn: (AreaEntry, ComponentType) => Double,
      isAutoComputed: AreaEntry => Boolean,   // true = showing default, not user-set
      update: (AreaEntry, Double) => AreaEntry,
      decimals: Int
  ): HtmlElement =
    val fmt = if decimals == 0 then (v: Double) => f"$v%.0f" else (v: Double) => f"$v%.2f"
    input(
      typ       := "text",
      width     := "100%",
      padding   := "0.25rem",
      border    := "none",
      textAlign := "right",
      value <-- dataEntries.signal.map { entries =>
        if index < entries.length then
          val effective = effectiveFn(entries(index), componentType)
          if effective != 0.0 then fmt(effective) else ""
        else ""
      },
      color <-- dataEntries.signal.map { entries =>
        if index < entries.length && isAutoComputed(entries(index)) then "#999" else "inherit"
      },
      fontStyle <-- dataEntries.signal.map { entries =>
        if index < entries.length && isAutoComputed(entries(index)) then "italic" else "normal"
      },
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val inputEl = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement]
        val raw = inputEl.value.replace("'", "").replace(",", ".").trim
        val numValue = raw.toDoubleOption.getOrElse(0.0)
        val curr = dataEntries.now()
        if index < curr.length then
          val base = curr(index).copy(rateKey = "")
          val newEntries = curr.updated(index, update(base, numValue))
          dataEntries.set(newEntries)
          onSave(componentType, newEntries)
      }
    )

  /** Editable cell for Nutzungsdauer with auto-fill from ComponentTypeDefaults.
   *  stored == 0 → display computed default (grey/italic); stored != 0 → display stored (normal). */
  private def renderNutzungsdauerCell(
      displayEntry: AreaEntry,
      index: Int,
      dataEntries: Var[List[AreaEntry]],
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    input(
      typ       := "text",
      width     := "100%",
      padding   := "0.25rem",
      border    := "none",
      textAlign := "right",
      value <-- dataEntries.signal.map { entries =>
        if index < entries.length then
          ComponentTypeDefaults.effectiveNutzungsdauer(entries(index), componentType).toString
        else ""
      },
      color <-- dataEntries.signal.map { entries =>
        if index < entries.length && entries(index).nutzungsdauer == 0 then "#999"
        else "inherit"
      },
      fontStyle <-- dataEntries.signal.map { entries =>
        if index < entries.length && entries(index).nutzungsdauer == 0 then "italic"
        else "normal"
      },
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val inputEl = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement]
        val numValue = inputEl.value.trim.toIntOption.getOrElse(0)
        val curr = dataEntries.now()
        if index < curr.length then
          val newEntries = curr.updated(index, curr(index).copy(nutzungsdauer = numValue))
          dataEntries.set(newEntries)
          onSave(componentType, newEntries)
      }
    )

end AreaCalculationTable
