package pme123.geak4s.components

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import pme123.geak4s.domain.area.*
import pme123.geak4s.state.{WaermebrueckeState, AppState}

object WaermebrueckeTable:

  def linearTable(): HtmlElement =
    val dataVar: Var[List[LinearWaermebruecke]] =
      Var(WaermebrueckeState.data.now().linearEntries)

    div(
      WaermebrueckeState.data.signal.map(_.linearEntries) --> Observer[List[LinearWaermebruecke]] { entries =>
        if dataVar.now() != entries then dataVar.set(entries)
      },
      child <-- dataVar.signal.map { entries =>
        renderLinearTable(entries, dataVar)
      },
      div(
        marginTop := "0.75rem",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val nextIdx = dataVar.now().length + 1
            val newEntry = LinearWaermebruecke.empty(s"WL$nextIdx", 0.0)
            val updated  = dataVar.now() :+ newEntry
            dataVar.set(updated)
            WaermebrueckeState.data.update(_.copy(linearEntries = updated))
            AppState.saveWaermebruecken()
          },
          "Zeile hinzufügen"
        )
      )
    )

  private def renderLinearTable(entries: List[LinearWaermebruecke], dataVar: Var[List[LinearWaermebruecke]]): HtmlElement =
    div(
      overflowX := "auto",
      table(
        width          := "100%",
        border         := "1px solid #e0e0e0",
        borderCollapse := "collapse",
        thead(
          backgroundColor := "#f5f5f5",
          tr(
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Kürzel"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Bezeichnung"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Typ"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8", "Länge [m]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Ψ-Wert [W/mK]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "b-Faktor [-]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Anzahl"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "")
          )
        ),
        tbody(
          entries.zipWithIndex.map { case (entry, idx) =>
            renderLinearRow(entry, idx, dataVar)
          }
        )
      )
    )

  private def renderLinearRow(entry: LinearWaermebruecke, index: Int, dataVar: Var[List[LinearWaermebruecke]]): HtmlElement =
    def saveUpdate(updater: LinearWaermebruecke => LinearWaermebruecke): Unit =
      val curr = dataVar.now()
      if index < curr.length then
        val updated = curr.updated(index, updater(curr(index)))
        dataVar.set(updated)
        WaermebrueckeState.data.update(_.copy(linearEntries = updated))
        AppState.saveWaermebruecken()

    tr(
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        textInput(entry.kuerzel, v => saveUpdate(_.copy(kuerzel = v)), dataVar.signal.map(es => if index < es.length then es(index).kuerzel else ""))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        textInput(entry.bezeichnung, v => saveUpdate(_.copy(bezeichnung = v)), dataVar.signal.map(es => if index < es.length then es(index).bezeichnung else ""))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        textInput(entry.typ, v => saveUpdate(_.copy(typ = v)), dataVar.signal.map(es => if index < es.length then es(index).typ else ""))
      ),
      // Länge: read-only, auto-filled
      td(border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#e8f4f8", textAlign := "right",
        child.text <-- dataVar.signal.map(es => if index < es.length then f"${es(index).laenge}%.2f" else "0.00")
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        numInput(entry.psiWert, v => saveUpdate(_.copy(psiWert = v)), dataVar.signal.map(es => if index < es.length then es(index).psiWert.toString else "0"))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        numInput(entry.bFaktor, v => saveUpdate(_.copy(bFaktor = v)), dataVar.signal.map(es => if index < es.length then es(index).bFaktor.toString else "1"))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        intInput(entry.anzahl, v => saveUpdate(_.copy(anzahl = v)), dataVar.signal.map(es => if index < es.length then es(index).anzahl.toString else "1"))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem", textAlign := "center",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.delete,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val updated = dataVar.now().patch(index, Nil, 1)
            dataVar.set(updated)
            WaermebrueckeState.data.update(_.copy(linearEntries = updated))
            AppState.saveWaermebruecken()
          }
        )
      )
    )

  def punktTable(): HtmlElement =
    val dataVar: Var[List[PunktWaermebruecke]] =
      Var(WaermebrueckeState.data.now().punktEntries)

    div(
      WaermebrueckeState.data.signal.map(_.punktEntries) --> Observer[List[PunktWaermebruecke]] { entries =>
        if dataVar.now() != entries then dataVar.set(entries)
      },
      child <-- dataVar.signal.map { entries =>
        renderPunktTable(entries, dataVar)
      },
      div(
        marginTop := "0.75rem",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val nextIdx = dataVar.now().length + 1
            val newEntry = PunktWaermebruecke.empty(s"WR$nextIdx", 0)
            val updated  = dataVar.now() :+ newEntry
            dataVar.set(updated)
            WaermebrueckeState.data.update(_.copy(punktEntries = updated))
            AppState.saveWaermebruecken()
          },
          "Zeile hinzufügen"
        )
      )
    )

  private def renderPunktTable(entries: List[PunktWaermebruecke], dataVar: Var[List[PunktWaermebruecke]]): HtmlElement =
    div(
      overflowX := "auto",
      table(
        width          := "100%",
        border         := "1px solid #e0e0e0",
        borderCollapse := "collapse",
        thead(
          backgroundColor := "#f5f5f5",
          tr(
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Kürzel"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Bezeichnung"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Typ"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "χ-Wert [W/K]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "b-Faktor [-]"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", backgroundColor := "#e8f4f8", "Anzahl"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "")
          )
        ),
        tbody(
          entries.zipWithIndex.map { case (entry, idx) =>
            renderPunktRow(entry, idx, dataVar)
          }
        )
      )
    )

  private def renderPunktRow(entry: PunktWaermebruecke, index: Int, dataVar: Var[List[PunktWaermebruecke]]): HtmlElement =
    def saveUpdate(updater: PunktWaermebruecke => PunktWaermebruecke): Unit =
      val curr = dataVar.now()
      if index < curr.length then
        val updated = curr.updated(index, updater(curr(index)))
        dataVar.set(updated)
        WaermebrueckeState.data.update(_.copy(punktEntries = updated))
        AppState.saveWaermebruecken()

    tr(
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        textInput(entry.kuerzel, v => saveUpdate(_.copy(kuerzel = v)), dataVar.signal.map(es => if index < es.length then es(index).kuerzel else ""))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        textInput(entry.bezeichnung, v => saveUpdate(_.copy(bezeichnung = v)), dataVar.signal.map(es => if index < es.length then es(index).bezeichnung else ""))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        textInput(entry.typ, v => saveUpdate(_.copy(typ = v)), dataVar.signal.map(es => if index < es.length then es(index).typ else ""))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        numInput(entry.chiWert, v => saveUpdate(_.copy(chiWert = v)), dataVar.signal.map(es => if index < es.length then es(index).chiWert.toString else "0"))
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem",
        numInput(entry.bFaktor, v => saveUpdate(_.copy(bFaktor = v)), dataVar.signal.map(es => if index < es.length then es(index).bFaktor.toString else "1"))
      ),
      // Anzahl: read-only, auto-filled from point placement
      td(border := "1px solid #e0e0e0", padding := "0.25rem", backgroundColor := "#e8f4f8", textAlign := "right",
        child.text <-- dataVar.signal.map(es => if index < es.length then es(index).anzahl.toString else "0")
      ),
      td(border := "1px solid #e0e0e0", padding := "0.25rem", textAlign := "center",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon   := IconName.delete,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            val updated = dataVar.now().patch(index, Nil, 1)
            dataVar.set(updated)
            WaermebrueckeState.data.update(_.copy(punktEntries = updated))
            AppState.saveWaermebruecken()
          }
        )
      )
    )

  private def textInput(initialValue: String, onSave: String => Unit, valueSignal: Signal[String]): HtmlElement =
    input(
      typ     := "text",
      width   := "100%",
      padding := "0.25rem",
      border  := "none",
      value <-- valueSignal,
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val v = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement].value
        onSave(v)
      }
    )

  private def numInput(initialValue: Double, onSave: Double => Unit, valueSignal: Signal[String]): HtmlElement =
    input(
      typ       := "number",
      width     := "100%",
      padding   := "0.25rem",
      border    := "none",
      stepAttr  := "0.001",
      textAlign := "right",
      value <-- valueSignal,
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val v = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement].value.toDoubleOption.getOrElse(0.0)
        onSave(v)
      }
    )

  private def intInput(initialValue: Int, onSave: Int => Unit, valueSignal: Signal[String]): HtmlElement =
    input(
      typ       := "number",
      width     := "100%",
      padding   := "0.25rem",
      border    := "none",
      stepAttr  := "1",
      textAlign := "right",
      value <-- valueSignal,
      onBlur --> Observer[org.scalajs.dom.FocusEvent] { event =>
        val v = event.target.asInstanceOf[org.scalajs.dom.HTMLInputElement].value.toIntOption.getOrElse(0)
        onSave(v)
      }
    )

end WaermebrueckeTable
