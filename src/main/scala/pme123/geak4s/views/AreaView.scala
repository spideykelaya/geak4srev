package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import pme123.geak4s.components.AreaCalculationTable
import pme123.geak4s.domain.area.*
import pme123.geak4s.state.{UWertState, AreaState, AppState}
import pme123.geak4s.domain.uwert.{ComponentType, UWertCalculation}

/**
 * Area calculation view (Flächenberechnung)
 * Allows users to calculate building envelope areas for IST and SOLL states
 * Dynamically generates tables based on U-Wert calculations
 */
object AreaView:

  // Map to store area entries for each component type
  private val areaEntriesByComponentType = scala.collection.mutable.Map[ComponentType, Var[List[AreaEntry]]]()
  private val ebfEntries = Var[List[AreaEntry]](List.empty)

  // Get or create area entries for a component type
  private def getAreaEntries(componentType: ComponentType): Var[List[AreaEntry]] =
    areaEntriesByComponentType.getOrElseUpdate(componentType, Var[List[AreaEntry]](List.empty))

  // Save entries to state
  private def saveEntriesToState(componentType: ComponentType, entries: List[AreaEntry]): Unit =
    AreaState.updateAreaCalculation(componentType, entries)
    AppState.saveAreaCalculations()

  // Load area calculations from state when project is loaded
  def loadFromState(): Unit =
    AreaState.areaCalculations.now().foreach { buildingEnvelopeArea =>
      // Load EBF entries
      buildingEnvelopeArea.get(ComponentType.EBF).foreach { calc =>
        ebfEntries.set(calc.entries)
      }

      // Load entries for all other component types
      buildingEnvelopeArea.calculations.foreach { calc =>
        if calc.componentType != ComponentType.EBF then
          getAreaEntries(calc.componentType).set(calc.entries)
      }
    }

  // Create the view element once and reuse it
  private lazy val viewElement: HtmlElement =
    div(
      className := "area-view",
      // Load data from state when view is mounted
      onMountCallback { _ =>
        loadFromState()
      },
      Card(
        className := "project-view",
        maxWidth  := "100%",
        display   := "flex",
        div(
          className := "card-content",
          padding   := "1.5rem",
          // EBF (always required) - styled like other building components
          renderEBFGroup(),
          // Render one AreaCalculationTable per U-Wert calculation
          children <-- UWertState.calculations.signal.split(_.id) { (id, _, calcSignal) =>
            renderCalculationGroupReactive(calcSignal)
          }
        )
      )
    )

  def apply(): HtmlElement = viewElement

  /** Render a calculation group reactively */
  private def renderCalculationGroupReactive(calcSignal: Signal[UWertCalculation]): HtmlElement =
    div(
      child <-- calcSignal.map(renderCalculationGroup)
    )

  /** Render EBF group with same styling as other building components */
  private def renderEBFGroup(): HtmlElement =
    div(
      marginBottom := "3rem",
      padding := "1.5rem",
      backgroundColor := ComponentType.EBF.color,
      borderRadius := "8px",
      border := "1px solid #ddd",

      // Header with component label
      div(
        marginBottom := "1.5rem",
        Title(
          _.level := TitleLevel.H3,
          ComponentType.EBF.label
        )
      ),

      // Area calculation table
      div(
        backgroundColor := "white",
        padding := "1rem",
        borderRadius := "4px",
        marginBottom := "1.5rem",
        AreaCalculationTable(ComponentType.EBF, ebfEntries, saveEntriesToState)
      )
    )

  /** Render a calculation group (AreaCalculationTable + U-Wert summary) */
  private def renderCalculationGroup(calc: UWertCalculation): HtmlElement =
    if calc.componentLabel.nonEmpty then
      val entries = getAreaEntries(calc.componentType)
      div(
        marginBottom := "3rem",
        padding := "1.5rem",
        backgroundColor := calc.componentType.color,
        borderRadius := "8px",
        border := "1px solid #ddd",

        // Header with component label
        div(
          marginBottom := "1.5rem",
          Title(
            _.level := TitleLevel.H3,
            calc.componentLabel
          )
        ),

        // Area calculation table - use componentType instead of calc.id
        div(
          backgroundColor := "white",
          padding := "1rem",
          borderRadius := "4px",
          marginBottom := "1.5rem",
          AreaCalculationTable(calc.componentType, entries, saveEntriesToState)
        )
      )
    else
      div(display := "none")


end AreaView

