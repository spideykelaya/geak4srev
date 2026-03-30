package pme123.geak4s.views

import com.raquo.laminar.api.L.{*, given}

/** Main drawing/canvas panel for the EBF calculator. */
object EBFMainPanelView:

  def apply(): HtmlElement =
    mainTag(
      className := "canvas-container",
      idAttr := "canvas-container",
      canvasTag(idAttr := "main-canvas"),
      div(idAttr := "instructions"),
      div(idAttr := "zoom-indicator")
    )

end EBFMainPanelView

