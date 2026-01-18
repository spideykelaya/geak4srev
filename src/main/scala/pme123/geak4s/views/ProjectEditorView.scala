package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import pme123.geak4s.state.AppState
import pme123.geak4s.domain.*

/** Main project editor view with navigation and content area */
object ProjectEditorView:
  
  enum Section:
    case ProjectInfo
    case BuildingUsage
    case Envelope
    case HVAC
    case Energy
    case Summary
  
  def apply(): HtmlElement =
    val currentSection = Var[Section](Section.ProjectInfo)
    val projectSignal = AppState.projectState.signal.map {
      case AppState.ProjectState.Loaded(project, _) => Some(project)
      case _ => None
    }

    div(
      className := "project-editor",

      // Top bar with project info and actions
      topBar(projectSignal),

      // Main content area with sidebar and content
      div(
        className := "editor-content",

        // Sidebar navigation
        sidebar(currentSection),

        // Content area
        div(
          className := "editor-main",
          child <-- currentSection.signal.combineWith(projectSignal).map {
            case (section, Some(project)) => renderSection(section, project)
            case (_, None) => div("No project loaded")
          }
        )
      )
    )
  
  private def topBar(projectSignal: Signal[Option[GeakProject]]): HtmlElement =
    Bar(
      _.design := BarDesign.Header,
      _.slots.startContent := div(
        className := "project-info",
        Button(
          _.icon := IconName.`nav-back`,
          _.design := ButtonDesign.Default,
          _.tooltip := "Back to Welcome",
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            AppState.clearProject()
          }
        ),
        child <-- projectSignal.map {
          case Some(project) =>
            div(
              Title(_.level := TitleLevel.H4, project.project.projectName),
              Label(s"Template: ${project.project.templateVersion}")
            )
          case None =>
            Title(_.level := TitleLevel.H4, "GEAK Project")
        }
      ),
      _.slots.endContent := div(
        className := "action-buttons",
        Button(
          _.icon := IconName.`save`,
          _.design := ButtonDesign.Default,
          _.tooltip := "Save to Browser",
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            // TODO: Implement browser storage
            dom.console.log("Save to browser storage")
          },
          "Save"
        )
      )
    )
  
  private def sidebar(currentSection: Var[Section]): HtmlElement =
    div(
      className := "editor-sidebar",
      Title(_.level := TitleLevel.H5, "Sections"),
      div(
        Section.values.toSeq.map { section =>
          Button(
            _.design <-- currentSection.signal.map(current =>
              if current == section then ButtonDesign.Default else ButtonDesign.Default
            ),
            _.icon := sectionIcon(section),
            _.events.onClick.mapTo(section) --> currentSection,
            width := "100%",
            marginBottom := "0.5rem",
            sectionTitle(section)
          )
        }
      )
    )
  
  private def sectionIcon(section: Section): IconName = section match
    case Section.ProjectInfo => IconName.`project-definition-triangle`
    case Section.BuildingUsage => IconName.`building`
    case Section.Envelope => IconName.`home`
    case Section.HVAC => IconName.`heating-cooling`
    case Section.Energy => IconName.`energy-saving-lightbulb`
    case Section.Summary => IconName.`document`
  
  private def sectionTitle(section: Section): String = section match
    case Section.ProjectInfo => "Project Information"
    case Section.BuildingUsage => "Building Usage"
    case Section.Envelope => "Building Envelope"
    case Section.HVAC => "HVAC Systems"
    case Section.Energy => "Energy Production"
    case Section.Summary => "Summary & Export"

  private def renderSection(section: Section, project: GeakProject): HtmlElement =
    section match
      case Section.ProjectInfo => ProjectView(project.project).render()
      case Section.BuildingUsage => renderBuildingUsage(project)
      case Section.Envelope => renderEnvelope(project)
      case Section.HVAC => renderHVAC(project)
      case Section.Energy => renderEnergy(project)
      case Section.Summary => renderSummary(project)

  private def renderBuildingUsage(project: GeakProject): HtmlElement =
    div(
      className := "section-content",
      Title(_.level := TitleLevel.H2, "Building Usage"),
      Label(s"${project.buildingUsages.length} usage types defined"),
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        "Define the different usage types and areas in your building"
      )
    )
  
  private def renderEnvelope(project: GeakProject): HtmlElement =
    div(
      className := "section-content",
      Title(_.level := TitleLevel.H2, "Building Envelope"),
      Label("Roofs, walls, windows, doors, floors, and thermal bridges"),
      div(
        className := "stats-grid",
        statCard("Roofs & Ceilings", project.roofsCeilings.length.toString, IconName.`home`),
        statCard("Walls", project.walls.length.toString, IconName.`home`),
        statCard("Windows & Doors", project.windowsDoors.length.toString, IconName.`home`),
        statCard("Floors", project.floors.length.toString, IconName.`home`)
      )
    )
  
  private def renderHVAC(project: GeakProject): HtmlElement =
    div(
      className := "section-content",
      Title(_.level := TitleLevel.H2, "HVAC Systems"),
      Label("Heating, hot water, ventilation, and storage systems"),
      div(
        className := "stats-grid",
        statCard("Heat Producers", project.heatProducers.length.toString, IconName.`heating-cooling`),
        statCard("Heat Storage", project.heatStorages.length.toString, IconName.`database`),
        statCard("Heating Distribution", project.heatingDistributions.length.toString, IconName.`pipeline-analysis`),
        statCard("Ventilation", project.ventilations.length.toString, IconName.`weather-proofing`)
      )
    )
  
  private def renderEnergy(project: GeakProject): HtmlElement =
    div(
      className := "section-content",
      Title(_.level := TitleLevel.H2, "Energy Production"),
      Label("Photovoltaic systems and combined heat & power units"),
      div(
        className := "stats-grid",
        statCard("Electricity Producers", project.electricityProducers.length.toString, IconName.`energy-saving-lightbulb`)
      )
    )
  
  private def renderSummary(project: GeakProject): HtmlElement =
    div(
      className := "section-content",
      Title(_.level := TitleLevel.H2, "Summary & Export"),
      Label("Review your project and export to Excel"),
      MessageStrip(
        _.design := MessageStripDesign.Positive,
        _.hideCloseButton := true,
        "Your project is complete"
      )
    )
  
  private def statCard(title: String, value: String, icon: IconName): HtmlElement =
    Card(
      div(
        className := "stat-card",
        Icon(_.name := icon),
        Title(_.level := TitleLevel.H3, value),
        Label(title)
      )
    )

end ProjectEditorView

