package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import pme123.geak4s.components.UWertCalculationTable
import pme123.geak4s.state.{UWertState, AppState}

/** U-Wert (U-Value) calculation view Allows users to select building components and calculate
  * thermal transmittance Supports multiple calculation tables
  */
object UWertView:

  def apply(): HtmlElement =
    div(
      className := "uwert-view",
      Card(
        className := "project-view",
        maxWidth  := "100%",
        display   := "flex",
        div(
          className    := "card-content",
          padding      := "1.5rem",

          // Compound key: loadNonce prefix + calc id.
          // When a project is loaded, loadNonce changes → all split keys become new →
          // Laminar recreates every component with fresh initialLayer/customMode state.
          children <-- UWertState.calculations.signal
            .combineWith(UWertState.loadNonce.signal)
            .map { (calcs, nonce) => calcs.map(c => (s"${nonce}_${c.id}", c)) }
            .split(_._1) { (_, initial, _) =>
              UWertCalculationTable(initial._2.id, initial._2.isDirectInput)
            },

          // Add calculation button
          div(
            marginTop := "1.5rem",
            textAlign := "center",
            Button(
              _.design := ButtonDesign.Default,
              _.icon   := IconName.add,
              _.events.onClick.mapTo(()) --> { _ =>
                UWertState.addCalculation()
                AppState.saveUWertCalculations()
              },
              "Weitere Berechnung hinzufügen"
            )
          ),

          // Add direct U-value button
          div(
            marginTop := "0.5rem",
            textAlign := "center",
            Button(
              _.design := ButtonDesign.Default,
              _.icon   := IconName.add,
              _.events.onClick.mapTo(()) --> { _ =>
                UWertState.addDirectCalculation()
                AppState.saveUWertCalculations()
              },
              "Weiterer U-Wert hinzufügen"
            )
          )
        )
      )
    )

end UWertView
