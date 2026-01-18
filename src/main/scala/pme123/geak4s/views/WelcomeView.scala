package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import pme123.geak4s.state.AppState
import pme123.geak4s.services.GoogleDriveService
import scala.concurrent.ExecutionContext.Implicits.global

/** Welcome screen with options to start new or import existing project */
object WelcomeView:

  def apply(): HtmlElement =
    val errorMessage = Var[Option[String]](None)
    val isLoading = Var(false)
    val existingProjects = Var[List[String]](List.empty)
    val loadingProjects = Var(false)

    // Load existing projects from Google Drive on mount
    def loadExistingProjects(): Unit =
      loadingProjects.set(true)
      GoogleDriveService.listProjects().foreach { projects =>
        existingProjects.set(projects)
        loadingProjects.set(false)
      }
    
    div(
      className := "welcome-view",

      // Load projects on mount
      onMountCallback { _ =>
        loadExistingProjects()
      },

      // Hero section
      div(
        className := "welcome-hero",
        Icon(_.name := IconName.`building`),
        Title(
          _.level := TitleLevel.H1,
          "GEAK Expert Tool"
        ),
        Label(
          _.wrappingType := WrappingType.Normal,
          "Gebäudeenergieausweis der Kantone - Professional Project Management"
        )
      ),

      // Global error message
      child.maybe <-- errorMessage.signal.map(_.map { msg =>
        div(
          marginBottom := "1rem",
          MessageStrip(
            _.design := MessageStripDesign.Negative,
            _.hideCloseButton := true,
            _.events.onClose.mapTo(()) --> Observer[Unit] { _ =>
              errorMessage.set(None)
            },
            msg
          )
        )
      }),

      // Global loading indicator
      child.maybe <-- isLoading.signal.map {
        case true => Some(
          div(
            marginBottom := "1rem",
            textAlign := "center",
            BusyIndicator(_.active := true, _.size := BusyIndicatorSize.Medium)
          )
        )
        case false => None
      },

      // Action cards
      div(
        className := "welcome-actions",

        // New Project Card
        Card(
          _.slots.header := CardHeader(
            _.titleText := "New Project",
            _.subtitleText := "Start a fresh GEAK assessment",
            _.slots.avatar := Icon(_.name := IconName.`add-document`)
          ),
          div(
            className := "card-content",
            Label(
              _.wrappingType := WrappingType.Normal,
              "Create a new GEAK project from scratch. You'll be guided through all required sections."
            ),
            div(
              className := "card-actions",
              Button(
                _.design := ButtonDesign.Default,
                _.icon := IconName.`create`,
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  AppState.createNewProject()
                },
                "Create New Project"
              )
            )
          )
        ),

        // Existing Projects Card
        Card(
          _.slots.header := CardHeader(
            _.titleText := "Existing Projects",
            _.subtitleText := "Load from Google Drive",
            _.slots.avatar := Icon(_.name := IconName.`folder-blank`),
            _.slots.action := Button(
              _.icon := IconName.`refresh`,
              _.design := ButtonDesign.Default,
              _.disabled <-- loadingProjects.signal.combineWith(isLoading.signal).map { (loading, globalLoading) =>
                loading || globalLoading
              },
              _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                loadExistingProjects()
              }
            )
          ),
          div(
            className := "card-content",

            // Projects list
            child <-- loadingProjects.signal.combineWith(existingProjects.signal).map { (loading, projects) =>
              if loading then
                div(
                  textAlign := "center",
                  padding := "1rem",
                  BusyIndicator(_.active := true, _.size := BusyIndicatorSize.Medium)
                )
              else if projects.isEmpty then
                Label(
                  _.wrappingType := WrappingType.Normal,
                  "No existing projects found in Google Drive GEAK4S folder. Create a new project to get started."
                )
              else
                div(
                  className := "projects-list",
                  projects.map { projectName =>
                    div(
                      className := "project-item",
                      Button(
                        _.design := ButtonDesign.Default,
                        _.icon := IconName.`document`,
                        _.disabled <-- isLoading.signal,
                        _.events.onClick.mapTo(projectName) --> Observer[String] { name =>
                          isLoading.set(true)
                          errorMessage.set(None)
                          GoogleDriveService.loadProjectState(name).foreach {
                            case Some(project) =>
                              isLoading.set(false)
                              AppState.loadProject(project, name)
                            case None =>
                              isLoading.set(false)
                              errorMessage.set(Some(s"Failed to load project: $name"))
                          }
                        },
                        projectName
                      )
                    )
                  }
                )
            }
          )
        )
      ),
      
      // Info section
      div(
        className := "welcome-info",
        Title(
          _.level := TitleLevel.H3,
          "Features"
        ),
        div(
          className := "feature-list",
          featureItem(IconName.`edit`, "Guided Data Entry", "Step-by-step process for all GEAK sections"),
          featureItem(IconName.`validate`, "Data Validation", "Automatic validation of all inputs"),
          featureItem(IconName.`excel-attachment`, "Excel Export", "Export to standard GEAK Excel format"),
          featureItem(IconName.`save`, "Auto-Save", "Your work is automatically saved in the browser")
        )
      )
    )
  
  private def featureItem(icon: IconName, title: String, description: String): HtmlElement =
    div(
      className := "feature-item",
      Icon(_.name := icon),
      div(
        className := "feature-text",
        Title(_.level := TitleLevel.H5, title),
        Label(_.wrappingType := WrappingType.Normal, description)
      )
    )

end WelcomeView

