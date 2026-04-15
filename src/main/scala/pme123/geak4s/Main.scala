package pme123.geak4s

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js.annotation.JSExportTopLevel
import pme123.geak4s.state.AppState
import pme123.geak4s.state.AppState.ProjectState
import pme123.geak4s.views.{WelcomeView, ProjectEditorView, WorkflowView}
import pme123.geak4s.domain.JsonCodecs.given
import io.circe.parser.decode
import pme123.geak4s.domain.GeakProject

object Main:

  @JSExportTopLevel("main")
  def main(args: Array[String] = Array.empty): Unit =
    dom.console.log("🎯 Main.main() called")
    dom.console.log(s"📊 Document ready state: ${dom.document.readyState}")

    // Initialize application
    AppState.initializeGoogleDrive()

    val appContainer = dom.document.querySelector("#app")
    dom.console.log(s"📦 App container: $appContainer")

    // Check if DOM is already loaded
    if dom.document.readyState == "loading" then
      dom.console.log("⏳ DOM still loading, using renderOnDomContentLoaded")
      renderOnDomContentLoaded(appContainer, page)
    else
      dom.console.log("✅ DOM already loaded, rendering immediately")
      render(appContainer, page)

    dom.console.log("✅ Render scheduled/executed")
  end main

  private lazy val page =
    div(
      width := "100%",
      height := "100%",
      className := "app-container",

      // Main content - switches between Welcome, Project Editor, and Workflow Editor
      child <-- AppState.currentView.signal.map {
        case AppState.View.Welcome =>
          dom.console.log("📄 Rendering WelcomeView")
          WelcomeView()
        case AppState.View.ProjectEditor =>
          dom.console.log("📄 Rendering ProjectEditorView")
          ProjectEditorView()
        case AppState.View.WorkflowEditor =>
          dom.console.log("📄 Rendering WorkflowView")
          WorkflowView()
      }
    )
end Main

