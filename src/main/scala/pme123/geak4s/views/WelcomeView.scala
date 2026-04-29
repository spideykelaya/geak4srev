package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import pme123.geak4s.state.AppState
import pme123.geak4s.domain.GeakProject
import pme123.geak4s.domain.JsonCodecs.given
import pme123.geak4s.domain.project.WordFormData
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

  /** Try to load the last work-in-progress project from localStorage. */
  private def loadWipFromStorage(): Option[(GeakProject, String)] =
    for
      fileName <- Option(dom.window.localStorage.getItem(AppState.WIP_FILE_KEY)).filter(_.nonEmpty)
      json     <- Option(dom.window.localStorage.getItem(AppState.WIP_KEY)).filter(_.nonEmpty)
      project  <- decode[GeakProject](json).toOption
    yield (project, fileName)

  def apply(): HtmlElement =
    val errorMessage    = Var[Option[String]](None)
    val isLoading       = Var(false)
    val jsonError       = Var[Option[String]](None)
    val showDialog      = Var(false)
    val newProjNummer   = Var("")
    val newProjAdresse  = Var("")

    // Compute WIP restore card once at render time (localStorage is synchronous)
    val wipRestoreCard: HtmlElement = loadWipFromStorage() match
      case None => div(display := "none")
      case Some((project, fileName)) =>
        val displayName = project.project.projectName.trim match
          case n if n.nonEmpty => n
          case _               => fileName
        Card(
          _.slots.header := CardHeader(
            _.titleText    := "Letztes Projekt fortsetzen",
            _.subtitleText := displayName,
            _.slots.avatar := Icon(_.name := IconName.`history`)
          ),
          div(
            className := "card-content",
            Label(
              _.wrappingType := WrappingType.Normal,
              "Zuvor bearbeitetes Projekt aus dem lokalen Arbeitsspeicher wiederherstellen."
            ),
            div(
              className := "card-actions",
              Button(
                _.design := ButtonDesign.Emphasized,
                _.icon   := IconName.`play`,
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  AppState.loadProject(project, fileName)
                },
                "Fortsetzen"
              )
            )
          )
        )

    def confirmNewProject(): Unit =
      val nummer  = newProjNummer.now().trim
      val adresse = newProjAdresse.now().trim.replace(",", "")
      showDialog.set(false)
      AppState.createNewProject()
      if nummer.nonEmpty || adresse.nonEmpty then
        val projektbezeichnung = List(nummer, adresse).filter(_.nonEmpty).mkString(" ")
        AppState.updateProject(p =>
          p.copy(project = p.project.copy(projectName = projektbezeichnung)))
        WordFormView.formVar.update(_.copy(projektnummer = nummer, adresse = adresse))
        AppState.updateProject(p =>
          p.copy(wordFormData = Some(WordFormView.formVar.now())))
      if adresse.nonEmpty then
        dom.window.navigator.clipboard.writeText(adresse)

    div(
      // New-project dialog overlay
      child.maybe <-- showDialog.signal.map {
        case false => None
        case true  => Some(
          div(
            position := "fixed", top := "0", left := "0",
            width := "100%", height := "100%",
            backgroundColor := "rgba(0,0,0,0.5)",
            zIndex := "9999",
            display := "flex", alignItems := "center", justifyContent := "center",
            // Stop clicks on backdrop from bubbling through
            onClick.stopPropagation --> Observer[dom.MouseEvent] { e =>
              if e.target == e.currentTarget then showDialog.set(false)
            },
            div(
              backgroundColor := "white", borderRadius := "8px",
              padding := "2rem", minWidth := "360px", maxWidth := "480px",
              boxShadow := "0 8px 32px rgba(0,0,0,0.18)",
              display := "flex", flexDirection := "column", gap := "1.25rem",

              h3(margin := "0", color := "black", "Neues Projekt erstellen"),

              div(
                display := "flex", flexDirection := "column", gap := "0.4rem",
                label(color := "black", fontWeight := "600", "Projektnummer"),
                input(
                  typ := "text", padding := "0.5rem", fontSize := "1rem",
                  border := "1px solid #ccc", borderRadius := "4px", color := "black",
                  placeholder := "z.B. 260670",
                  value := newProjNummer.now(),
                  onInput.mapToValue --> newProjNummer,
                  onKeyDown.filter(_.key == "Enter") --> Observer[dom.KeyboardEvent] { _ =>
                    confirmNewProject()
                  }
                )
              ),

              div(
                display := "flex", flexDirection := "column", gap := "0.4rem",
                label(color := "black", fontWeight := "600", "Adresse"),
                input(
                  typ := "text", padding := "0.5rem", fontSize := "1rem",
                  border := "1px solid #ccc", borderRadius := "4px", color := "black",
                  placeholder := "z.B. Musterstrasse 1",
                  value := newProjAdresse.now(),
                  onInput.mapToValue --> newProjAdresse,
                  onKeyDown.filter(_.key == "Enter") --> Observer[dom.KeyboardEvent] { _ =>
                    confirmNewProject()
                  }
                )
              ),

              div(
                display := "flex", gap := "0.75rem", justifyContent := "flex-end",
                button(
                  padding := "0.5rem 1.25rem", fontSize := "0.95rem",
                  border := "1px solid #ccc", borderRadius := "4px",
                  backgroundColor := "white", color := "black", cursor := "pointer",
                  onClick --> Observer[dom.MouseEvent] { _ => showDialog.set(false) },
                  "Abbrechen"
                ),
                button(
                  padding := "0.5rem 1.25rem", fontSize := "0.95rem",
                  border := "none", borderRadius := "4px",
                  backgroundColor := "#0070f3", color := "white", cursor := "pointer",
                  fontWeight := "600",
                  onClick --> Observer[dom.MouseEvent] { _ => confirmNewProject() },
                  "Erstellen"
                )
              )
            )
          )
        )
      },
      className := "welcome-view",

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

        // WIP restore card (only rendered when localStorage has a saved project)
        wipRestoreCard,

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
                  newProjNummer.set("")
                  newProjAdresse.set("")
                  showDialog.set(true)
                },
                "Create New Project"
              )
            )
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
                    val opts = js.Dynamic.literal(types = js.Array(typeItem), multiple = false, mode = "readwrite")

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
          featureItem(IconName.`excel-attachment`, "Excel Export", "Export to standard GEAK Excel format")
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

