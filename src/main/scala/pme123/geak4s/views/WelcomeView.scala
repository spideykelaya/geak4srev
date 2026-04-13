package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import pme123.geak4s.state.AppState
import pme123.geak4s.services.GoogleDriveService
import pme123.geak4s.domain.GeakProject
import pme123.geak4s.domain.JsonCodecs.given
import io.circe.parser.decode
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

/** Welcome screen with options to start new or import existing project */
object WelcomeView:

  /** Parse JSON text, optionally set a file handle for auto-save, then load the project */
  private def loadProjectFromText(
    text: String,
    fileName: String,
    handleOpt: Option[js.Dynamic],
    jsonError: Var[Option[String]]
  ): Unit =
    decode[GeakProject](text) match
      case Right(project) =>
        project.ebfPlans.foreach(_.plans.foreach { plan =>
          plan.imageDataUrl.foreach { img =>
            if img.nonEmpty then
              try dom.window.localStorage.setItem(s"ebf_plan_image_${plan.id}", img)
              catch case _: Exception => ()
          }
        })
        jsonError.set(None)
        handleOpt.foreach(h => AppState.setLocalFileHandle(h, fileName))
        AppState.loadProject(project, fileName)
      case Left(err) =>
        jsonError.set(Some(s"Ungültige JSON-Datei: ${err.getMessage.take(120)}"))

  def apply(): HtmlElement =
    val errorMessage = Var[Option[String]](None)
    val isLoading = Var(false)
    val existingProjects = Var[List[String]](List.empty)
    val loadingProjects = Var(false)
    val jsonError = Var[Option[String]](None)

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
        ),

        // Local JSON Card
        Card(
          _.slots.header := CardHeader(
            _.titleText := "Lokales Projekt",
            _.subtitleText := "JSON-Datei hochladen",
            _.slots.avatar := Icon(_.name := IconName.`upload`)
          ),
          div(
            className := "card-content",
            Label(
              _.wrappingType := WrappingType.Normal,
              "Laden Sie ein zuvor gespeichertes Projekt als JSON-Datei hoch, um dort weiterzumachen."
            ),
            child.maybe <-- jsonError.signal.map(_.map { msg =>
              div(
                marginTop := "0.5rem",
                MessageStrip(
                  _.design := MessageStripDesign.Negative,
                  _.hideCloseButton := true,
                  msg
                )
              )
            }),
            div(
              className := "card-actions",

              // Hidden fallback file input (for browsers without File System Access API)
              input(
                typ := "file",
                idAttr := "json-upload-input",
                accept := ".json",
                display := "none",
                onChange --> Observer[dom.Event] { e =>
                  val input = e.target.asInstanceOf[dom.html.Input]
                  val file = input.files(0)
                  if file != null then
                    val reader = new dom.FileReader()
                    reader.onload = _ =>
                      val text = reader.result.asInstanceOf[String]
                      loadProjectFromText(text, file.name, None, jsonError)
                      input.value = ""
                    reader.readAsText(file)
                }
              ),

              Button(
                _.design := ButtonDesign.Default,
                _.icon := IconName.`upload`,
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  val win = dom.window.asInstanceOf[js.Dynamic]
                  if js.typeOf(win.showOpenFilePicker) == "function" then
                    // File System Access API: writable handle for auto-save.
                    // "application/json" contains '/' so we cannot use named-arg literal — build the object manually.
                    val acceptObj = js.Object().asInstanceOf[js.Dynamic]
                    acceptObj.updateDynamic("application/json")(js.Array(".json"))
                    val typeItem = js.Dynamic.literal(description = "GEAK JSON Projektdatei", accept = acceptObj)
                    val opts = js.Dynamic.literal(types = js.Array(typeItem), multiple = false)

                    val onHandles: js.Function1[js.Any, Unit] = handles =>
                      val arr = handles.asInstanceOf[js.Array[js.Dynamic]]
                      if arr.length > 0 then
                        val handle = arr(0)
                        val onFile: js.Function1[js.Any, Unit] = file =>
                          val reader = new dom.FileReader()
                          reader.onload = _ =>
                            val text = reader.result.asInstanceOf[String]
                            val name = file.asInstanceOf[js.Dynamic].name.asInstanceOf[String]
                            loadProjectFromText(text, name, Some(handle), jsonError)
                          reader.readAsText(file.asInstanceOf[dom.Blob])
                        handle.getFile().asInstanceOf[js.Dynamic].`then`(onFile)

                    val onCancel: js.Function1[js.Any, Unit] = _ => () // user dismissed
                    win.showOpenFilePicker(opts).asInstanceOf[js.Dynamic].`then`(onHandles, onCancel)
                  else
                    // Fallback for Firefox / Safari
                    dom.document.getElementById("json-upload-input")
                      .asInstanceOf[dom.html.Input].click()
                },
                "JSON hochladen"
              )
            )
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

