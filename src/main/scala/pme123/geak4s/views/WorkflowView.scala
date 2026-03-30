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

      // Google Drive error notification
      child.maybe <-- AppState.driveError.signal.map(_.map { errorMsg =>
        MessageStrip(
          _.design := MessageStripDesign.Warning,
          _.hideCloseButton := true,
          _.events.onClose.mapTo(()) --> Observer[Unit] { _ =>
            AppState.driveError.set(None)
          },
          div(
            Icon(_.name := IconName.`warning`),
            span(s" $errorMsg")
          )
        )
      }),

      // Google Drive login notification
      child <-- AppState.driveLoginPrompt.signal.map { showPrompt =>
        if showPrompt then
          MessageStrip(
            _.design := MessageStripDesign.Information,
            _.hideCloseButton := true,
            _.events.onClose.mapTo(()) --> Observer[Unit] { _ =>
              AppState.driveLoginPrompt.set(false)
            },
            div(
              Icon(_.name := IconName.`cloud`),
              span(" Google Drive-Anmeldung erforderlich - Bitte melden Sie sich im Popup-Fenster an")
            )
          )
        else
          emptyNode
      },

      // Progress indicator
      progressBar(),

      // Main content area
      div(
        className := "workflow-content",

        // Step navigation sidebar
        stepNavigator(),

        // Content area for current step
        div(
          className := "workflow-main",
          child <-- WorkflowState.currentStep.signal.combineWith(projectSignal).map {
            case (step, Some(project)) => renderStep(step, project)
            case (step, None) => div("No project loaded")
          }
        )
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

        // Google Drive connection status with last sync time
        child <-- AppState.driveConnected.signal.combineWith(AppState.lastSyncTime.signal).map {
          case (connected, syncTime) =>
            if connected then
              div(
                className := "drive-status",
                display := "flex",
                alignItems := "center",
                gap := "0.5rem",
                Icon(_.name := IconName.`cloud`),
                Label(
                  syncTime match
                    case Some(time) =>
                      val date = new js.Date(time.toDouble)
                      val hours = date.getHours().toInt
                      val minutes = date.getMinutes().toInt
                      val formattedTime = f"$hours%02d:$minutes%02d"
                      s"Gespeichert $formattedTime"
                    case None => "Verbunden"
                )
              )
            else
              // Show connect button when not connected
              Button(
                _.icon := IconName.`cloud`,
                _.design := ButtonDesign.Default,
                _.tooltip := "Mit Google Drive verbinden",
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  AppState.signInToGoogleDrive()
                },
                "Verbinden"
              )
        }
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
    case Step.UWertCalculation => IconName.`temperature`
    case Step.Calculations => IconName.`number-sign`
    case Step.Inspection => IconName.`checklist-item`
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

  private def renderStep(step: Step, project: GeakProject): HtmlElement =
    step match
      case Step.GISData => renderGISData(project)
      case Step.EBFCalculation => renderEBFStep(project)
      case Step.WordForm => WordFormView()
      case Step.UWertCalculation => renderUWertCalculation(project)
      case Step.Calculations => renderCalculations(project)
      case Step.Inspection => renderInspection(project)
      case Step.DataEntry => renderDataEntry(project)
      case Step.Reports => ReportView()
      case Step.ProjectSetup => renderProjectSetup(project)

  // New Step: EBF Calculation
  private def renderEBFStep(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      Title(_.level := TitleLevel.H2, "EBF berechnen"),
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        "Berechnen Sie die Energiebezugsfläche (EBF) mit dem integrierten Tool."
      ),
      // Render the EBF calculator as a web component (no iframe)
      div(
        styleAttr := "display: flex; justify-content: center; align-items: flex-start; width: 100%; height: 90vh;",
        htmlTag("ebf-calculator")(
          styleAttr := "display: block; width: 90%; height: 85vh; min-height: 800px; min-width: 1200px;"
        )
      )
    )

   // Step 1: GIS Data
  private def renderGISData(project: GeakProject): HtmlElement =
    GisDataView()

  def generateDoc(): Unit =
   println("Dokument wird erstellt!")
 

  // Step 2: Project Setup durch Word Form ersetzt
  private def renderProjectSetup(project: GeakProject): HtmlElement =
    ProjectView(project.project).render()
  

  // Step 3: U-Wert Calculation
  private def renderUWertCalculation(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      Title(_.level := TitleLevel.H2, "U-Wert-Berechnung"),
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        "Berechnen Sie die Wärmedurchgangskoeffizienten (U-Werte) für verschiedene Bauteile."
      ),
      UWertView()
    )

  // Step 4: Calculations (Area Calculation)
  private def renderCalculations(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      Title(_.level := TitleLevel.H2, "Flächenberechnung"),
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        "Erfassen Sie die Flächen der Gebäudehülle (IST und SOLL Zustand)."
      ),
      AreaView()
    )


  // Step 4: Inspection
  private def renderInspection(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      Title(_.level := TitleLevel.H2, "Begehung"),
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        "Begehungsprotokoll vor Ort ausfüllen. Tablet-freundlich mit Handschrifterkennung."
      ),
      Card(
        _.slots.header := CardHeader(
          _.titleText := "Begehungsprotokoll",
          _.subtitleText := "Vor Ort Datenerfassung"
        ),
        div(
          className := "card-content",
          Label("Funktion wird implementiert: Interaktives Begehungsprotokoll"),
          Label("• Tablet-optimierte Eingabe"),
          Label("• Handschrifterkennung"),
          Label("• Foto-Upload"),
          Label("• Offline-Fähigkeit")
        )
      )
    )

  // Step 5: Data Entry
  private def renderDataEntry(project: GeakProject): HtmlElement =
    div(
      className := "step-content",
      Title(_.level := TitleLevel.H2, "Dateneingabe"),
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
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


