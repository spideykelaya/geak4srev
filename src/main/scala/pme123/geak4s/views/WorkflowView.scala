package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import pme123.geak4s.state.{AppState, WorkflowState}
import pme123.geak4s.state.WorkflowState.Step
import pme123.geak4s.views.WordFormView
import pme123.geak4s.domain.*

/**
 * Workflow-based project view with step-by-step wizard
 * Guides users through the GEAK assessment process
 */
object WorkflowView:

  def apply(): HtmlElement =
    val projectSignal = AppState.projectState.signal.map {
      case AppState.ProjectState.Loaded(project, _) => Some(project)
      case _ => None
    }

    div(
      className := "workflow-view",

      // Top bar with progress and actions
      topBar(projectSignal),

      // Progress indicator
      progressBar(),

      // Main content area
      div(
        className := "workflow-content",

        // Step navigation sidebar
        stepNavigator(),

        // Content area for current step
        workflowMain(projectSignal)
      ),

      // Bottom navigation
      bottomNavigation()
    )

  private def topBar(projectSignal: Signal[Option[GeakProject]]): HtmlElement =
    Bar(
      _.design := BarDesign.Header,
      _.slots.startContent := div(
        className := "project-info",
        Button(
          _.icon := IconName.`nav-back`,
          _.design := ButtonDesign.Default,
          _.tooltip := "Zurück zur Startseite",
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            AppState.clearProject()
            WorkflowState.reset()
          }
        ),
        child <-- projectSignal.map {
          case Some(project) =>
            div(
              Title(_.level := TitleLevel.H4, project.project.projectName),
              Label(s"GEAK Projekt")
            )
          case None =>
            Title(_.level := TitleLevel.H4, "GEAK Projekt")
        }
      ),
      _.slots.endContent := div(
        className := "action-buttons",

        // Local file auto-save status
        child.maybe <-- AppState.localFileName.signal.map(_.map { name =>
          div(
            display := "flex", alignItems := "center", gap := "0.25rem",
            Icon(_.name := IconName.`save`, color := "#107e3e"),
            span(
              fontSize := "0.75rem",
              color := "#107e3e",
              fontWeight := "600",
              s"Auto-Save: $name"
            )
          )
        }),

      )
    )

  private def progressBar(): HtmlElement =
    div(
      className := "workflow-progress",
      ProgressIndicator(
        _.value <-- WorkflowState.progressPercentage,
        _.displayValue <-- WorkflowState.progressPercentage.map(p => s"$p%")
      )
    )

  private def stepNavigator(): HtmlElement =
    div(
      className := "workflow-navigator",
      Title(_.level := TitleLevel.H5, "Arbeitsschritte"),
      div(
        className := "step-list",
        Step.values.toSeq.map { step =>
          stepButton(step)
        }
      )
    )

  private def stepButton(step: Step): HtmlElement =
    val statusSignal = WorkflowState.getStepStatus(step)
    val isCurrentSignal = WorkflowState.currentStep.signal.map(_ == step)

    div(
      className := "step-button-container",
      Button(
        _.design <-- isCurrentSignal.map(isCurrent =>
          if isCurrent then ButtonDesign.Default else ButtonDesign.Default
        ),
        _.icon := stepIcon(step),
        _.events.onClick.mapTo(step) --> Observer[Step] { s =>
          WorkflowState.goToStep(s)
        },
        width := "100%",
        marginBottom := "0.5rem",
        div(
          className := "step-button-content",
          div(
            className := "step-number",
            s"${step.order}"
          ),
          div(
            className := "step-info",
            div(className := "step-title", step.title),
            div(className := "step-desc", step.description)
          ),
          child <-- statusSignal.map(status => statusBadge(status))
        )
      )
    )

  private def stepIcon(step: Step): IconName = step match
    case Step.WordForm => IconName.`project-definition-triangle`
    case Step.GISData => IconName.`map`
    case Step.EBFCalculation => IconName.`area-chart`
    case Step.EnergyCalculation => IconName.`energy-saving-lightbulb`
    case Step.UWertCalculation => IconName.`temperature`
    case Step.Calculations => IconName.`number-sign`
    case Step.DataEntry => IconName.`edit`
    case Step.Reports => IconName.`document`
    case Step.ProjectSetup => IconName.`project-definition-triangle`

  private def statusBadge(status: WorkflowState.StepStatus): HtmlElement =
    status match
      case WorkflowState.StepStatus.Completed =>
        Icon(_.name := IconName.`accept`, className := "status-completed")
      case WorkflowState.StepStatus.InProgress =>
        Icon(_.name := IconName.`in-progress`, className := "status-in-progress")
      case WorkflowState.StepStatus.Skipped =>
        Icon(_.name := IconName.`decline`, className := "status-skipped")
      case WorkflowState.StepStatus.NotStarted =>
        span()

  private def bottomNavigation(): HtmlElement =
    Bar(
      _.design := BarDesign.Footer,
      _.slots.startContent := Button(
        _.icon := IconName.`nav-back`,
        _.design := ButtonDesign.Default,
        _.disabled <-- WorkflowState.canGoPrevious.map(!_),
        _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
          WorkflowState.previousStep()
        },
        "Zurück"
      ),
      _.slots.endContent := Button(
        _.endIcon := IconName.`navigation-right-arrow`,
        _.design := ButtonDesign.Default,
        _.disabled <-- WorkflowState.canGoNext.map(!_),
        _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
          WorkflowState.nextStep()
        },
        "Weiter"
      )
    )

  /**
   * Main content area.
   *
   * The EBF calculator carries heavy JS state (canvas drawings, loaded plan).
   * To avoid losing that state on step navigation we keep the EBF element
   * permanently in the DOM after the user first opens it, and merely toggle
   * its CSS `display` property.  All other steps are rendered dynamically as
   * before.
   */
  private def workflowMain(@annotation.unused projectSignal: Signal[Option[GeakProject]]): HtmlElement =
    // Becomes true the first time the user navigates to the EBF step.
    // After that it is never reset, so the element stays mounted forever.
    val ebfEverShown = Var(false)

    div(
      className := "workflow-main",

      // Subscribe to step changes; flip the flag on first EBF visit.
      WorkflowState.currentStep.signal --> Observer[Step] { step =>
        if step == Step.EBFCalculation && !ebfEverShown.now() then
          ebfEverShown.set(true)
      },

      // EBF calculator – created exactly once (on first visit) and kept in
      // the DOM afterwards.  Visibility is controlled by `display`.
      child.maybe <-- ebfEverShown.signal.map { hasShown =>
        if !hasShown then None
        else Some(
          div(
            display <-- WorkflowState.currentStep.signal.map(s =>
              if s == Step.EBFCalculation then "block" else "none"
            ),
            height := "100%",
            // When switching back, trigger a resize AFTER the browser has
            // reflowed (display:none → block).  A synchronous dispatch would
            // still see getBoundingClientRect() == 0×0.
            WorkflowState.currentStep.signal --> Observer[Step] { step =>
              if step == Step.EBFCalculation then
                dom.window.requestAnimationFrame { _ =>
                  dom.window.dispatchEvent(new dom.Event("resize"))
                }
            },
            EBFCalculatorView()
          )
        )
      },

      // Every other step is rendered dynamically.
      // We deliberately do NOT combine with projectSignal here: subscribing to
      // projectSignal would cause the step to be torn down and rebuilt on every
      // project update (e.g. every keystroke in WordFormView), losing DOM focus.
      // Steps that need live project data (ReportView, etc.) subscribe to
      // AppState.projectSignal internally.
      child <-- WorkflowState.currentStep.signal.map { step =>
        if step == Step.EBFCalculation then emptyNode
        else
          AppState.getCurrentProject match
            case Some(project) => renderStep(step, project)
            case None          => div("No project loaded")
      }
    )

  private def renderStep(step: Step, project: GeakProject): HtmlElement =
    step match
      case Step.GISData => renderGISData(project)
      case Step.EBFCalculation => renderEBFStep(project)
      case Step.EnergyCalculation => EnergyCalculationView()
      case Step.WordForm => WordFormView()
      case Step.UWertCalculation => renderUWertCalculation(project)
      case Step.Calculations => renderCalculations(project)
      case Step.DataEntry => renderDataEntry(project)
      case Step.Reports => ReportView()
      case Step.ProjectSetup => renderProjectSetup(project)

  // New Step: EBF Calculation
  private def renderEBFStep(project: GeakProject): HtmlElement =
      EBFCalculatorView()

   // Step 1: GIS Data
  private def renderGISData(project: GeakProject): HtmlElement =
    GisDataView()

  def generateDoc(): Unit =
   println("Dokument wird erstellt!")
 

  // Step 2: Project Setup durch Word Form ersetzt
  private def renderProjectSetup(project: GeakProject): HtmlElement =
    ProjectView(project).render()
  

  // Step 3: U-Wert Calculation
  private def renderUWertCalculation(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        marginBottom := "1.5rem",
        "Berechnen Sie die Wärmedurchgangskoeffizienten (U-Werte) für verschiedene Bauteile."
      ),
      UWertView()
    )

  // Step 4: Calculations (Area Calculation)
  private def renderCalculations(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        marginBottom := "1.5rem",
        "Erfassen Sie die Flächen der Gebäudehülle (IST und SOLL Zustand)."
      ),
      AreaView()
    )


  // Step 5: Data Entry
  private def renderDataEntry(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        marginBottom := "1.5rem",
        "Erfassen Sie alle Gebäudedaten: Hülle, HLKK-Systeme, Energieproduktion."
      ),
      div(
        className := "data-entry-sections",
        Card(
          _.slots.header := CardHeader(
            _.titleText := "Gebäudehülle",
            _.subtitleText := s"${project.roofsCeilings.length + project.walls.length + project.windowsDoors.length + project.floors.length} Bauteile"
          ),
          div(
            className := "card-content",
            Label(s"• Dächer/Decken: ${project.roofsCeilings.length}"),
            Label(s"• Wände: ${project.walls.length}"),
            Label(s"• Fenster/Türen: ${project.windowsDoors.length}"),
            Label(s"• Böden: ${project.floors.length}")
          )
        ),
        Card(
          _.slots.header := CardHeader(
            _.titleText := "HLKK-Systeme",
            _.subtitleText := s"${project.heatProducers.length + project.ventilations.length} Systeme"
          ),
          div(
            className := "card-content",
            Label(s"• Wärmeerzeuger: ${project.heatProducers.length}"),
            Label(s"• Lüftung: ${project.ventilations.length}"),
            Label(s"• Wärmespeicher: ${project.heatStorages.length}")
          )
        ),
        Card(
          _.slots.header := CardHeader(
            _.titleText := "Energieproduktion",
            _.subtitleText := s"${project.electricityProducers.length} Anlagen"
          ),
          div(
            className := "card-content",
            Label(s"• Stromproduzenten: ${project.electricityProducers.length}")
          )
        )
      )
    )

end WorkflowView

