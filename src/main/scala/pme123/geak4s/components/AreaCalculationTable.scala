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
    div(
      className := "area-calculation-table",

      // Table
      renderTable(category, entries, onSave),

      // Add row button
      div(
        marginTop := "1rem",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val currentEntries = entries.now()
            val nextNr         = (currentEntries.length + 1).toString
            entries.set(currentEntries :+ AreaEntry.empty(nextNr))
            onSave(category, entries.now())
          },
          "Zeile hinzufügen"
        )
      )
    )

  private def renderTable(
      componentType: ComponentType,
      entries: Var[List[AreaEntry]],
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
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Bauteil Nr."),
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
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "") // Delete button column
          )
        ),

        // Body
        tbody(
          children <-- entries.signal.split(_.nr) { (nr, _, entrySignal) =>
            val indexSignal = entries.signal.map(_.indexWhere(_.nr == nr))
            renderRow(nr, entrySignal, indexSignal, entries, componentType, onSave)
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
              child.text <-- entries.signal.map { entries =>
                entries.map(_.quantity).sum.toString
              }
            ),

            // Fläche Total [m²] - column 8
            td(
              border     := "1px solid #e0e0e0",
              padding    := "0.5rem",
              textAlign  := "right",
              fontWeight := "600",
              child.text <-- entries.signal.map { entries =>
                f"${entries.map(_.totalArea).sum}%.2f"
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
              child.text <-- entries.signal.map { entries =>
                entries.map(_.quantityNew).sum.toString
              }
            ),

            // Fläche Total Neu [m²] - column 11
            td(
              border     := "1px solid #e0e0e0",
              padding    := "0.5rem",
              textAlign  := "right",
              fontWeight := "600",
              child.text <-- entries.signal.map { entries =>
                f"${entries.map(_.totalAreaNew).sum}%.2f"
              }
            ),

            // Empty cells for Beschrieb Neu and Delete button - columns 12-13
            td(border    := "1px solid #e0e0e0", padding := "0.5rem"),
            td(border    := "1px solid #e0e0e0", padding := "0.5rem")
          )
        )
      )
    )

  private def renderRow(
      nr: String,
      entrySignal: Signal[AreaEntry],
      indexSignal: Signal[Int],
      entries: Var[List[AreaEntry]],
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    tr(
      // Bauteil Nr. - dynamically calculated based on index
      td(
        border          := "1px solid #e0e0e0",
        backgroundColor := "#f9f9f9",
        padding         := "0.25rem",
        textAlign       := "center",
        child.text <-- indexSignal.map(idx => (idx + 1).toString)
      ),

      // Ausrichtung
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        renderEditableCell(
          nr,
          entrySignal,
          _.orientation,
          entries,
          (e, v) => e.copy(orientation = v),
          componentType,
          onSave
        )
      ),

      // Beschrieb
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        renderEditableCell(
          nr,
          entrySignal,
          _.description,
          entries,
          (e, v) => e.copy(description = v),
          componentType,
          onSave
        )
      ),

      // Länge
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        textAlign       := "right",
        renderNumericCell(
          nr,
          entrySignal,
          _.length,
          entries,
          (e, v) =>
            val updated        = e.copy(length = v)
            val calculatedArea = v * updated.width
            updated.copy(area = calculatedArea, totalArea = calculatedArea * updated.quantity)
          ,
          componentType,
          onSave
        )
      ),

      // Breite
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        textAlign       := "right",
        renderNumericCell(
          nr,
          entrySignal,
          _.width,
          entries,
          (e, v) =>
            val updated        = e.copy(width = v)
            val calculatedArea = updated.length * v
            updated.copy(area = calculatedArea, totalArea = calculatedArea * updated.quantity)
          ,
          componentType,
          onSave
        )
      ),

      // Fläche (disabled, auto-calculated from Länge × Breite)
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        textAlign       := "right",
        renderNumericCell(
          nr,
          entrySignal,
          _.area,
          entries,
          (e, v) =>
            e.copy(area = v, width = 0, length = 0, totalArea = v * e.quantity),
          componentType,
          onSave
        )
      ),

      // Anzahl
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        textAlign       := "right",
        renderIntCell(
          nr,
          entrySignal,
          _.quantity,
          entries,
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
        child.text <-- entrySignal.map(e => f"${e.totalArea}%.2f")
      ),

      // Fläche Neu
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        textAlign       := "right",
        renderNumericCell(
          nr,
          entrySignal,
          _.areaNew,
          entries,
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
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        textAlign       := "right",
        renderIntCell(
          nr,
          entrySignal,
          _.quantityNew,
          entries,
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
        child.text <-- entrySignal.map(e => f"${e.totalAreaNew}%.2f")
      ),

      // Beschrieb Neu
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        renderEditableCell(
          nr,
          entrySignal,
          _.descriptionNew,
          entries,
          (e, v) => e.copy(descriptionNew = v),
          componentType,
          onSave
        )
      ),

      // Delete button
      td(
        border          := "1px solid #e0e0e0",
        padding         := "0.25rem",
        textAlign       := "center",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.delete,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            entries.update(_.filterNot(_.nr == nr))
            onSave(componentType, entries.now())
          }
        )
      )
    )

  private def renderEditableCell(
      nr: String,
      entrySignal: Signal[AreaEntry],
      getValue: AreaEntry => String,
      entries: Var[List[AreaEntry]],
      updateEntry: (AreaEntry, String) => AreaEntry,
      componentType: ComponentType,
      onSave: (ComponentType, List[AreaEntry]) => Unit
  ): HtmlElement =
    input(
      typ     := "text",
      width   := "100%",
      padding := "0.25rem",
      border  := "none",
      onMountCallback { ctx =>
        ctx.thisNode.ref.value = getValue(entries.now().find(_.nr == nr).get)
      },
      onBlur.mapToValue --> Observer[String] { value =>
        val currentEntries = entries.now()
        val entry          = currentEntries.find(_.nr == nr).get
        val updated        = updateEntry(entry, value)
        val newEntries     = currentEntries.map(e => if e.nr == nr then updated else e)
        entries.set(newEntries)
        onSave(componentType, newEntries)
      }
    )

  private def renderNumericCell(
      nr: String,
      entrySignal: Signal[AreaEntry],
      getValue: AreaEntry => Double,
      entries: Var[List[AreaEntry]],
      updateEntry: (AreaEntry, Double) => AreaEntry,
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
      onMountCallback { ctx =>
        ctx.thisNode.ref.value = getValue(entries.now().find(_.nr == nr).get).toString
      },
      onBlur.mapToValue --> Observer[String] { value =>
        val numValue       = value.toDoubleOption.getOrElse(0.0)
        val currentEntries = entries.now()
        val entry          = currentEntries.find(_.nr == nr).get
        val updated        = updateEntry(entry, numValue)
        val newEntries     = currentEntries.map(e => if e.nr == nr then updated else e)
        entries.set(newEntries)
        onSave(componentType, newEntries)
      }
    )

  private def renderIntCell(
      nr: String,
      entrySignal: Signal[AreaEntry],
      getValue: AreaEntry => Int,
      entries: Var[List[AreaEntry]],
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
      onMountCallback { ctx =>
        ctx.thisNode.ref.value = getValue(entries.now().find(_.nr == nr).get).toString
      },
      onBlur.mapToValue --> Observer[String] { value =>
        val numValue       = value.toIntOption.getOrElse(0)
        val currentEntries = entries.now()
        val entry          = currentEntries.find(_.nr == nr).get
        val updated        = updateEntry(entry, numValue)
        val newEntries     = currentEntries.map(e => if e.nr == nr then updated else e)
        entries.set(newEntries)
        onSave(componentType, newEntries)
      }
    )

end AreaCalculationTable
