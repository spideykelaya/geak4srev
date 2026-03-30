package pme123.geak4s.views

import com.raquo.laminar.api.L.{*, given}

object EBFCalculatorView:

  def apply(): HtmlElement =
    div(
      styleAttr := "display: flex; width: 100%; height: 100%; flex: 1; min-height: 0;",
      htmlTag("ebf-calculator")(
        styleAttr := "display: block; width: 100%; height: 100%; flex: 1; min-height: 0;"
      )
    )
