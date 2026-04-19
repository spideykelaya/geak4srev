package pme123.geak4s.components

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom.{HTMLInputElement, KeyboardEvent}
import pme123.geak4s.domain.area.*
import pme123.geak4s.domain.uwert.ComponentType

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
            val newEntries     = currentEntries :+ AreaEntry.empty().copy(isManual = true)
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
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Ausrichtung"),
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
              colSpan    := 6,
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

            // Empty cells for Beschrieb Neu, optional Fenster shading cols, and Delete button
            td(border    := "1px solid #e0e0e0", padding := "0.5rem"),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
            Option.when(componentType == ComponentType.Window)(td(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#fffde7")),
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

      // Ausrichtung
      td(
        border  := "1px solid #e0e0e0",
        padding := "0.25rem",
        renderEditableCell(
          displayEntry,
          index,
          _.orientation,
          dataEntries,
          (e, v) => e.copy(orientation = v),
          componentType,
          onSave
        )
      ),

      // Beschrieb
      td(
        border  := "1px solid #e0e0e0",
        padding := "0.25rem",
        renderEditableCell(
          displayEntry,
          index,
          _.description,
          dataEntries,
          (e, v) => e.copy(description = v),
          componentType,
          onSave
        )
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

end AreaCalculationTable
