package pme123.geak4s.components

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import pme123.geak4s.domain.uwert.WindowCalculation
import pme123.geak4s.state.{UWertState, AppState}

object WindowCalculationCard:

  def apply(calcId: String, showDelete: Boolean = true): HtmlElement =
    val calcSignal = UWertState.getWindowCalculation(calcId)

    def save(update: WindowCalculation => WindowCalculation): Unit =
      UWertState.updateWindowCalculation(calcId, update)
      AppState.saveUWertCalculations()

    div(
      className := "uwert-calculation",
      Card(
        maxWidth := "100%",
        div(
          className := "card-content",
          padding   := "1.5rem",
          display   := "flex",
          flexDirection := "column",
          gap := "1rem",

          // Header: label + delete button
          div(
            display := "flex",
            alignItems := "center",
            gap := "0.5rem",
            Input(
              placeholder := "Bezeichnung Fenster",
              value <-- calcSignal.map(_.fold("")(_.label)),
              onInput.mapToValue --> { v => save(_.copy(label = v)) }
            ),
            Option.when(showDelete)(
              Button(
                _.design := ButtonDesign.Negative,
                _.icon   := IconName.delete,
                _.events.onClick.mapTo(()) --> { _ =>
                  UWertState.removeWindowCalculation(calcId)
                  AppState.saveUWertCalculations()
                }
              )
            )
          ),

          // U-Wert row
          valueRow(
            label       = "U-Wert [W/m²K]",
            presets     = WindowCalculation.uValuePresets,
            valueSignal = calcSignal.map(_.fold(0.0)(_.uValue)),
            onUpdate    = v => save(_.copy(uValue = v))
          ),

          // g-Wert row
          valueRow(
            label       = "g-Wert [-]",
            presets     = WindowCalculation.gValuePresets,
            valueSignal = calcSignal.map(_.fold(0.0)(_.gValue)),
            onUpdate    = v => save(_.copy(gValue = v))
          ),

          // Glasanteil row
          valueRow(
            label       = "Glasanteil [-]",
            presets     = WindowCalculation.glassRatioPresets,
            valueSignal = calcSignal.map(_.fold(0.0)(_.glassRatio)),
            onUpdate    = v => save(_.copy(glassRatio = v))
          )
        )
      )
    )

  private def valueRow(
    label:       String,
    presets:     Seq[Double],
    valueSignal: Signal[Double],
    onUpdate:    Double => Unit
  ): HtmlElement =
    div(
      display := "flex",
      alignItems := "center",
      gap := "0.5rem",
      Label(label, minWidth := "130px"),
      Select(
        _.events.onChange.map(_.detail.selectedOption.dataset.get("value").flatMap(_.toDoubleOption).getOrElse(0.0)) --> { v =>
          if v != 0.0 then onUpdate(v)
        },
        Select.option(
          _.selected <-- valueSignal.map(v => !presets.contains(v)),
          dataAttr("value") := "0",
          "– wählen –"
        ),
        presets.map { p =>
          Select.option(
            _.selected <-- valueSignal.map(_ == p),
            dataAttr("value") := p.toString,
            p.toString
          )
        }
      ),
      Input(
        `type`      := "number",
        stepAttr    := "0.01",
        minWidth    := "80px",
        value <-- valueSignal.map(v => if v == 0.0 then "" else v.toString),
        onInput.mapToValue --> { s => s.replace(',', '.').toDoubleOption.foreach(onUpdate) }
      )
    )


end WindowCalculationCard
