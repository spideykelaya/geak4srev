package pme123.geak4s.components

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import pme123.geak4s.domain.uwert.*
import pme123.geak4s.state.{UWertState, AppState}
import pme123.geak4s.components.WindowCalculationCard

/**
 * Reusable U-Wert calculation table component
 * Displays material layers and calculates thermal transmittance (U-value)
 * Now uses UWertState for persistence
 */
object UWertCalculationTable:

  // `label` in Laminar refers to the <label> element; use a custom HtmlAttr for <optgroup label="...">
  private val optGroupLabel = htmlAttr[String]("label", com.raquo.laminar.codecs.StringAsIsCodec)

  def apply(calculationId: String): HtmlElement =
    val calcSignal = UWertState.getCalculation(calculationId)

    div(
      className := "calculation-table-group",
      marginBottom := "3rem",
      padding := "1.5rem",
      backgroundColor <-- calcSignal.map(_.filter(_.componentLabel.nonEmpty).map(_.componentType.color).getOrElse("white")),
      borderRadius := "8px",
      border := "1px solid #ddd",


      // Component selector
      renderComponentSelector(calculationId, calcSignal),

      // Tables - only shown when a component is selected
      child <-- calcSignal.map {
        case Some(calc) if calc.componentLabel == "Fenster" =>
          WindowCalculationCard(calculationId, showDelete = false)
        case Some(calc) if calc.componentLabel.nonEmpty =>
          buildingComponents.find(_.label == calc.componentLabel) match
            case Some(component) =>
              div(
                backgroundColor := "white",
                padding := "1rem",
                borderRadius := "4px",
                marginBottom := "1.5rem",

                div(
                  display := "flex",
                  gap := "2rem",
                  marginTop := "1.5rem",

                  // IST table (left)
                  div(
                    flex := "1",
                    renderTable("IST", calculationId, component, calc.istCalculation)
                  ),

                  // SOLL table (right)
                  div(
                    flex := "1",
                    renderTable("SOLL", calculationId, component, calc.sollCalculation)
                  )
                )
              )
            case None =>
              div(
                marginTop := "1rem",
                marginBottom := "1rem",
                Label("Fehler: Bauteil nicht gefunden")
              )
        case _ =>
          div(
            marginTop := "1rem",
            marginBottom := "1rem",
            Label("Bitte wählen Sie ein Bauteil aus der Liste oben.")
          )
      },

      // Delete button
      div(
        marginTop := "1rem",
        textAlign := "right",
        Button(
          _.design := ButtonDesign.Negative,
          _.icon := IconName.delete,
          _.events.onClick.mapTo(()) --> { _ =>
            UWertState.removeCalculation(calculationId)
            AppState.saveUWertCalculations()
          },
          "Berechnung löschen"
        )
      )
    )

  private def renderComponentSelector(
    calculationId: String,
    calcSignal: Signal[Option[UWertCalculation]]
  ): HtmlElement =
    div(
      className := "component-selector",
      backgroundColor := "white",
      padding := "1rem",
      borderRadius := "4px",
      marginBottom := "1.5rem",
      display := "flex",
      flexDirection := "column",
      gap := "1rem",

      // Row 1: Bauteil selector
      div(
        Label(
          display := "block",
          marginBottom := "0.5rem",
          fontWeight := "600",
          "Beschrieb Bauteil"
        ),
        Select(
          _.value <-- calcSignal.map(_.map(_.componentLabel).getOrElse("")),
          _.events.onChange.mapToValue --> Observer[String] { label =>
            if label == "Fenster" then
              UWertState.selectWindow(calculationId)
              AppState.saveUWertCalculations()
            else if label.nonEmpty then
              buildingComponents.find(_.label == label).foreach { component =>
                UWertState.updateComponent(calculationId, component, None)
                AppState.saveUWertCalculations()
              }
          },
          Select.option(_.value := "", "-- Bauteil auswählen --"),
          buildingComponents.map { component =>
            Select.option(_.value := component.label, component.label)
          },
          Select.option(_.value := "Fenster", "Fenster")
        )
      ),

      // Row 2: Bezeichnung (links) + b-Wert (rechts) — flex-end richtet Inputs aus
      child <-- calcSignal.map {
        case Some(calc) if calc.componentLabel.nonEmpty && calc.componentLabel != "Fenster" =>
          val hasBWert = buildingComponents.find(_.label == calc.componentLabel).exists { c =>
            c.compType != ComponentType.PitchedRoof && c.compType != ComponentType.FlatRoof
          }
          div(
            display := "flex",
            gap := "2rem",
            alignItems := "flex-start",

            // Bezeichnung
            div(
              flex := "1",
              Label(
                display := "block",
                marginBottom := "0.5rem",
                fontWeight := "600",
                "Bezeichnung"
              ),
              Input(
                placeholder := "Bezeichnung",
                value       <-- calcSignal.map(_.fold("")(_.label)),
                _.events.onChange.mapToValue --> { v =>
                  UWertState.updateCalculation(calculationId, _.copy(label = v))
                  AppState.saveUWertCalculations()
                }
              )
            ),

            // b-Wert
            Option.when(hasBWert)(
              buildingComponents.find(_.label == calc.componentLabel).map { component =>
                div(
                  flex := "1",
                  Label(
                    display := "block",
                    marginBottom := "0.5rem",
                    fontWeight := "600",
                    "b-Wert"
                  ),
                  div(
                    display := "flex",
                    gap := "0.5rem",
                    alignItems := "center",
                    Select(
                      _.value <-- calcSignal.map(_.flatMap(_.bWertName).getOrElse("")),
                      _.events.onChange.mapToValue --> Observer[String] { bWertName =>
                        if bWertName.nonEmpty then
                          BWert.values.find(_.name == bWertName).foreach { bWert =>
                            UWertState.updateBFactor(calculationId, bWert.bValue)
                            UWertState.updateCalculation(calculationId, _.copy(bWertName = Some(bWertName)))
                            AppState.saveUWertCalculations()
                          }
                      },
                      Select.option(_.value := "", "-- b-Wert auswählen --"),
                      BWert.getByComponentType(component.compType).map { bWert =>
                        Select.option(_.value := bWert.name, s"${bWert.name} (${bWert.bValue})")
                      }
                    ),
                    span("oder", fontStyle := "italic", color := "#888", whiteSpace := "nowrap"),
                    input(
                      typ := "text",
                      placeholder := "0.00",
                      width := "5rem",
                      padding := "0.25rem 0.4rem",
                      border := "1px solid #ccc",
                      borderRadius := "4px",
                      value <-- calcSignal.map { calcOpt =>
                        calcOpt.flatMap(_.bWertName) match
                          case Some(_) => ""
                          case None    => calcOpt.map(_.istCalculation.bFactor)
                                            .filter(_ != 1.0).map(_.toString).getOrElse("")
                      },
                      onBlur.mapToValue --> Observer[String] { v =>
                        v.replace(',', '.').toDoubleOption.foreach { d =>
                          val clamped = math.max(0.0, math.min(1.0, d))
                          UWertState.updateBFactor(calculationId, clamped)
                          UWertState.updateCalculation(calculationId, _.copy(bWertName = None))
                          AppState.saveUWertCalculations()
                        }
                      },
                      onKeyDown --> Observer[org.scalajs.dom.KeyboardEvent] { e =>
                        if e.key == "Enter" then
                          val inputEl = e.target.asInstanceOf[org.scalajs.dom.HTMLInputElement]
                          inputEl.value.replace(',', '.').toDoubleOption.foreach { d =>
                            val clamped = math.max(0.0, math.min(1.0, d))
                            UWertState.updateBFactor(calculationId, clamped)
                            UWertState.updateCalculation(calculationId, _.copy(bWertName = None))
                            AppState.saveUWertCalculations()
                          }
                          inputEl.blur()
                      }
                    )
                  )
                )
              }
            )
          )
        case _ => emptyNode
      }
    )

  private def renderTable(
    tableType: String, // "IST" or "SOLL"
    calculationId: String,
    component: BuildingComponent,
    tableData: UWertTableData
  ): HtmlElement =
    val calcSignal = UWertState.getCalculation(calculationId)
    val materialsSignal = calcSignal.map { calcOpt =>
      calcOpt.map { calc =>
        if tableType == "IST" then calc.istCalculation.materials else calc.sollCalculation.materials
      }.getOrElse(List.empty)
    }

    div(
      // Table title
      div(
        marginBottom := "1rem",
        Title(
          _.level := TitleLevel.H4,
          s"U-Wert Berechnung $tableType"
        )
      ),

      table(
        width := "100%",
        border := "1px solid #e0e0e0",
        borderCollapse := "collapse",

        // Header
        thead(
          backgroundColor := "#f5f5f5",
          tr(
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Nr."),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "Was"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "d in m"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "λ"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "d/λ (R)"),
            th(border := "1px solid #e0e0e0", padding := "0.5rem", ""), // Move buttons
            th(border := "1px solid #e0e0e0", padding := "0.5rem", "") // Delete button
          )
        ),

        // Body - Material rows with dynamic rendering
        tbody(
          children <-- materialsSignal.split(_.nr) { (nr, _, layerSignal) =>
            val indexSignal = materialsSignal.map(_.indexWhere(_.nr == nr))
            renderMaterialRow(calculationId, tableType, layerSignal, indexSignal, component.compType)
          }
        ),

        // Footer - Totals and results
        tfoot(
          backgroundColor := "#f5f5f5",

          // R total row
          tr(
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 4, textAlign := "right", fontWeight := "600", "R total ="),
            td(
              border := "1px solid #e0e0e0",
              padding := "0.5rem",
              textAlign := "right",
              fontWeight := "600",
              child.text <-- calcSignal.map { calcOpt =>
                val data = calcOpt.map(c => if tableType == "IST" then c.istCalculation else c.sollCalculation).getOrElse(tableData)
                f"${data.rTotal}%.2f"
              }
            ),
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 2) // Empty cells for move+delete columns
          ),

          // b-Factor row
          tr(
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 4, textAlign := "right", "b-Faktor"),
            td(
              border := "1px solid #e0e0e0",
              padding := "0.5rem",
              textAlign := "right",
              child.text <-- calcSignal.map { calcOpt =>
                val data = calcOpt.map(c => if tableType == "IST" then c.istCalculation else c.sollCalculation).getOrElse(tableData)
                data.bFactor.toString
              }
            ),
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 2) // Empty cells for move+delete columns
          ),

          // U-Wert without b-factor
          tr(
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 4, textAlign := "right", "U-Wert (ohne b-Faktor) ="),
            td(
              border := "1px solid #e0e0e0",
              padding := "0.5rem",
              textAlign := "right",
              fontWeight := "600",
              child.text <-- calcSignal.map { calcOpt =>
                val data = calcOpt.map(c => if tableType == "IST" then c.istCalculation else c.sollCalculation).getOrElse(tableData)
                f"${data.uValueWithoutB}%.2f"
              }
            ),
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 2) // Empty cells for move+delete columns
          ),

          // U-Wert with b-factor
          tr(
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 4, textAlign := "right", fontWeight := "600", "U-Wert (mit b-Faktor) ="),
            td(
              border := "1px solid #e0e0e0",
              padding := "0.5rem",
              textAlign := "right",
              fontWeight := "600",
              backgroundColor := "#fff3cd",
              child.text <-- calcSignal.map { calcOpt =>
                val data = calcOpt.map(c => if tableType == "IST" then c.istCalculation else c.sollCalculation).getOrElse(tableData)
                f"${data.uValue}%.2f"
              }
            ),
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 2) // Empty cells for move+delete columns
          ),

          // Unit row
          tr(
            td(border := "1px solid #e0e0e0", padding := "0.5rem", colSpan := 6, textAlign := "right", fontStyle := "italic", "W/m²K")
          )
        )
      ),

      // Add row button
      div(
        marginTop := "1rem",
        Button(
          _.design := ButtonDesign.Transparent,
          _.icon := IconName.add,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            if tableType == "IST" then
              UWertState.addIstMaterialLayer(calculationId)
            else
              UWertState.addSollMaterialLayer(calculationId)
            AppState.saveUWertCalculations()
          },
          "Zeile hinzufügen"
        )
      )
    )

  private def renderMaterialRow(
    calculationId: String,
    tableType: String,
    layerSignal: Signal[MaterialLayer],
    indexSignal: Signal[Int],
    componentType: ComponentType
  ): HtmlElement =
    val (daemmungMaterials, baumaterialMaterials) = BuildingComponentCatalog.getByComponentTypeGrouped(componentType)

    def updateMaterials(nr: Int, updater: MaterialLayer => MaterialLayer): Unit =
      if tableType == "IST" then
        // IST edit → also mirror the change to the same SOLL row
        UWertState.updateCalculation(calculationId, calc =>
          val updatedIst = calc.istCalculation.copy(
            materials = calc.istCalculation.materials.map(m => if m.nr == nr then updater(m) else m)
          )
          val updatedSoll = calc.sollCalculation.copy(
            materials = calc.sollCalculation.materials.map(m => if m.nr == nr then updater(m) else m)
          )
          calc.copy(istCalculation = updatedIst, sollCalculation = updatedSoll)
        )
      else
        UWertState.updateCalculation(calculationId, calc =>
          calc.copy(sollCalculation = calc.sollCalculation.copy(
            materials = calc.sollCalculation.materials.map { m =>
              if m.nr == nr then updater(m) else m
            }
          ))
        )
      AppState.saveUWertCalculations()

    tr(
      // Nr - dynamically calculated based on index
      td(
        border := "1px solid #e0e0e0",
        padding := "0.25rem",
        textAlign := "center",
        backgroundColor <-- layerSignal.map(l => if !l.isEditable then "#f5f5f5" else "white"),
        child.text <-- indexSignal.map(idx => (idx + 1).toString)
      ),

      // Description - text when material selected, two dropdowns when empty
      td(
        border := "1px solid #e0e0e0",
        padding := "0.25rem",
        backgroundColor <-- layerSignal.map(l => if !l.isEditable then "#f5f5f5" else "white"),
        child <-- layerSignal.map { layer =>
          if !layer.isEditable || layer.description.nonEmpty then
            // Non-editable fixed rows or editable rows with a material selected: show plain text
            div(padding := "0.25rem", layer.description)
          else
            // Editable row, no material selected yet: show two dropdowns
            div(
              display := "flex",
              flexDirection := "column",
              gap := "0.25rem",
              select(
                onChange.mapToValue --> Observer[String] { materialName =>
                  if materialName.nonEmpty then
                    BuildingComponentCatalog.components.find(_.name == materialName).foreach { material =>
                      updateMaterials(layer.nr, _.copy(description = material.name, lambda = material.thermalConductivity))
                    }
                },
                width := "100%",
                padding := "0.25rem",
                border := "1px solid #ccc",
                borderRadius := "4px",
                option(value := "", "Dämmung..."),
                daemmungMaterials.map(m => option(value := m.name, s"${m.name} (λ = ${m.thermalConductivity})"))
              ),
              select(
                onChange.mapToValue --> Observer[String] { materialName =>
                  if materialName.nonEmpty then
                    BuildingComponentCatalog.components.find(_.name == materialName).foreach { material =>
                      updateMaterials(layer.nr, _.copy(description = material.name, lambda = material.thermalConductivity))
                    }
                },
                width := "100%",
                padding := "0.25rem",
                border := "1px solid #ccc",
                borderRadius := "4px",
                option(value := "", "Baumaterial..."),
                baumaterialMaterials.map(m => option(value := m.name, s"${m.name} (λ = ${m.thermalConductivity})"))
              )
            )
        }
      ),

      // Thickness (d in m)
      td(
        border := "1px solid #e0e0e0",
        padding := "0.25rem",
        textAlign := "right",
        backgroundColor <-- layerSignal.map(l => if !l.isEditable then "#f5f5f5" else "white"),
        child <-- layerSignal.map { layer =>
          input(
            typ := "text",
            width := "100%",
            padding := "0.25rem",
            border := "none",
            disabled := !layer.isEditable,
            value := (if layer.thickness == 0.0 then "" else layer.thickness.toString),
            onBlur.mapToValue --> Observer[String] { value =>
              updateMaterials(layer.nr, _.copy(thickness = value.replace(',', '.').toDoubleOption.getOrElse(0.0)))
              AppState.saveUWertCalculations()
            },
            onKeyDown --> Observer[org.scalajs.dom.KeyboardEvent] { e =>
              if e.key == "Enter" then
                val inputEl = e.target.asInstanceOf[org.scalajs.dom.HTMLInputElement]
                updateMaterials(layer.nr, _.copy(thickness = inputEl.value.replace(',', '.').toDoubleOption.getOrElse(0.0)))
                AppState.saveUWertCalculations()
                inputEl.blur()
            }
          )
        }
      ),

      // Lambda (λ)
      td(
        border := "1px solid #e0e0e0",
        padding := "0.25rem",
        textAlign := "right",
        backgroundColor <-- layerSignal.map(l => if !l.isEditable then "#f5f5f5" else "white"),
        child <-- layerSignal.map { layer =>
          input(
            typ := "text",
            width := "100%",
            padding := "0.25rem",
            border := "none",
            disabled := true,
            value := (if layer.lambda == 0.0 then "" else layer.lambda.toString)
          )
        }
      ),

      // R value (d/λ)
      td(
        border := "1px solid #e0e0e0",
        padding := "0.25rem",
        textAlign := "right",
        backgroundColor <-- layerSignal.map(l => if !l.isEditable then "#f5f5f5" else "white"),
        fontWeight <-- layerSignal.map(l => if !l.isEditable then "600" else "normal"),
        child.text <-- layerSignal.map(l => f"${l.rValue}%.2f")
      ),

      // Move up/down buttons (only for editable rows)
      td(
        border := "1px solid #e0e0e0",
        padding := "0.25rem",
        textAlign := "center",
        child <-- layerSignal.map { layer =>
          if layer.isEditable then
            div(
              display := "flex",
              flexDirection := "column",
              gap := "1px",
              Button(
                _.design := ButtonDesign.Transparent,
                _.icon := IconName.`slim-arrow-up`,
                _.tooltip := "Nach oben",
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  UWertState.moveMaterialLayer(calculationId, tableType, layer.nr, -1)
                  AppState.saveUWertCalculations()
                }
              ),
              Button(
                _.design := ButtonDesign.Transparent,
                _.icon := IconName.`slim-arrow-down`,
                _.tooltip := "Nach unten",
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  UWertState.moveMaterialLayer(calculationId, tableType, layer.nr, 1)
                  AppState.saveUWertCalculations()
                }
              )
            )
          else
            emptyNode
        }
      ),

      // Delete button (only for editable rows)
      td(
        border := "1px solid #e0e0e0",
        padding := "0.25rem",
        textAlign := "center",
        child <-- layerSignal.map { layer =>
          if layer.isEditable then
            Button(
              _.design := ButtonDesign.Transparent,
              _.icon := IconName.delete,
              _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                if tableType == "IST" then
                  UWertState.removeIstMaterialLayer(calculationId, layer.nr)
                else
                  UWertState.removeSollMaterialLayer(calculationId, layer.nr)
                AppState.saveUWertCalculations()
              }
            )
          else
            emptyNode
        }
      )
    )

end UWertCalculationTable

