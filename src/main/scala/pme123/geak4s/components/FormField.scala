package pme123.geak4s.components

import com.raquo.laminar.api.L.*
import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import pme123.geak4s.domain.*

/**
 * Enhanced form field components with validation, tooltips, and appropriate UI controls
 */
object FormField:

  /** Create a form field based on metadata */
  def apply(
    metadata: FieldMetadata,
    value: Signal[String],
    onChange: String => Unit,
    showValidation: Signal[Boolean] = Val(false),
    disabled: Boolean = false
  ): HtmlElement =
    // Track whether field has been touched (lost focus)
    val touched = Var(false)

    div(
      className := "form-field",
      renderLabel(metadata),
      renderInput(metadata, value, onChange, touched, disabled),
      renderHelpText(metadata),
      renderValidationMessage(metadata, value, touched.signal.combineWith(showValidation).map((t, s) => t || s))
    )

  /** Render label with tooltip */
  private def renderLabel(metadata: FieldMetadata): HtmlElement =
    div(
      className := "form-field-label",
      Label(
        _.showColon := true,
        metadata.label
      ),
      metadata.tooltip.map { tooltipText =>
        span(
          className := "field-tooltip-wrapper",
          dataAttr("tooltip") := tooltipText,  // Store tooltip text in data attribute
          Icon(
            _.name := IconName.`hint`,
            _.design := IconDesign.Information,
            _.accessibleName := tooltipText
          )
        )
      }
    )

  /** Render input based on field type */
  private def renderInput(
    metadata: FieldMetadata,
    value: Signal[String],
    onChange: String => Unit,
    touched: Var[Boolean],
    disabled: Boolean
  ): HtmlElement =
    metadata.fieldType match
      case FieldType.Text | FieldType.Email | FieldType.Phone | FieldType.Year | FieldType.Number | FieldType.Integer =>
        renderTextInput(metadata, value, onChange, touched, disabled)
      case FieldType.Select =>
        renderSelect(metadata, value, onChange, touched, disabled)
      case FieldType.Checkbox =>
        renderCheckbox(metadata, value, onChange, touched, disabled)
      case FieldType.TextArea =>
        renderTextArea(metadata, value, onChange, touched, disabled)

  /** Render text input */
  private def renderTextInput(
    metadata: FieldMetadata,
    value: Signal[String],
    onChange: String => Unit,
    touched: Var[Boolean],
    disabled: Boolean
  ): HtmlElement =
    val inputType = metadata.fieldType match
      case FieldType.Email => InputType.Email
      case FieldType.Phone => InputType.Tel
      case FieldType.Number | FieldType.Integer => InputType.Number
      case _ => InputType.Text

    // Bind value to signal, use onInput for immediate updates
    div(
      display := "flex",
      alignItems := "center",
      gap := "0.5rem",
      Input(
        _.value <-- value,
        _.placeholder := metadata.placeholder.getOrElse(""),
        _.required := metadata.validation.exists(_.required),
        _.disabled := disabled,
        onBlur.mapToValue --> Observer[String](onChange),  // Update on blur
        onBlur.mapTo(true) --> touched.writer,  // Mark as touched on blur
        className := "form-input"
      ),
      metadata.unit.map { unit =>
        span(className := "input-unit", unit)
      }
    )

  /** Render select dropdown */
  private def renderSelect(
    metadata: FieldMetadata,
    value: Signal[String],
    onChange: String => Unit,
    touched: Var[Boolean],
    disabled: Boolean
  ): HtmlElement =
    // Debug logging
    org.scalajs.dom.console.log(s"Rendering select for ${metadata.name} with ${metadata.options.length} options")
    metadata.options.foreach(opt => org.scalajs.dom.console.log(s"  Option: ${opt.value} -> ${opt.label}"))

    Select(
      _.value <-- value,
      _.disabled := disabled,
      onBlur.mapToValue --> Observer[String] { v =>
        org.scalajs.dom.console.log(s"Select changed: $v")
        touched.set(true)  // Mark as touched when selection changes
        onChange(v)
      },
      className := "form-select",
      metadata.options.map { option =>
        Select.option(
          _.value := option.value,
          _.selected <-- value.map(_ == option.value),
          option.label
        )
      }
    )

  /** Render checkbox */
  private def renderCheckbox(
    metadata: FieldMetadata,
    value: Signal[String],
    onChange: String => Unit,
    touched: Var[Boolean],
    disabled: Boolean
  ): HtmlElement =
    CheckBox(
      _.text := metadata.label,
      _.checked <-- value.map(_ == "true"),
      _.disabled := disabled,
      onBlur.mapToChecked.map(_.toString) --> Observer[String](onChange),
      onBlur.mapTo(true) --> touched.writer,
      className := "form-checkbox"
    )

  /** Render textarea */
  private def renderTextArea(
    metadata: FieldMetadata,
    value: Signal[String],
    onChange: String => Unit,
    touched: Var[Boolean],
    disabled: Boolean
  ): HtmlElement =
    TextArea(
      _.value <-- value,
      _.placeholder := metadata.placeholder.getOrElse(""),
      _.required := metadata.validation.exists(_.required),
      _.disabled := disabled,
      _.rows := 4,
      onBlur.mapToValue --> Observer[String](onChange),
      onBlur.mapTo(true) --> touched.writer,  // Mark as touched on blur
      className := "form-textarea"
    )

  /** Render help text */
  private def renderHelpText(metadata: FieldMetadata): Option[HtmlElement] =
    metadata.helpText.map { text =>
      div(
        className := "form-help-text",
        text
      )
    }

  /** Render validation message */
  private def renderValidationMessage(
    metadata: FieldMetadata,
    value: Signal[String],
    showValidation: Signal[Boolean]
  ): Option[HtmlElement] =
    metadata.validation.map { validation =>
      div(
        className := "form-validation",
        child <-- showValidation.combineWith(value).map { case (show, v) =>
          // Debug logging
          org.scalajs.dom.console.log(s"Validation check for ${metadata.name}: show=$show, value=$v")
          if show then
            validateField(metadata, v, validation) match
              case Some(error) =>
                MessageStrip(
                  _.design := MessageStripDesign.Negative,
                  _.hideCloseButton := true,
                  error
                )
              case None => emptyNode
          else emptyNode
        }
      )
    }

  /** Validate field value */
  private def validateField(
    metadata: FieldMetadata,
    value: String,
    validation: ValidationRule
  ): Option[String] =
    // Required validation
    if validation.required && value.trim.isEmpty then
      Some(s"${metadata.label} ist ein Pflichtfeld")
    // Skip other validations if empty and not required
    else if value.trim.isEmpty then
      None
    // Pattern validation
    else
      validation.pattern.flatMap { pattern =>
        if !value.matches(pattern) then
          Some(validation.customMessage.getOrElse(s"${metadata.label} hat ein ungültiges Format"))
        else
          None
      }.orElse {
        // Number validations
        metadata.fieldType match
          case FieldType.Number | FieldType.Integer | FieldType.Year =>
            value.toDoubleOption match
              case Some(num) =>
                validation.min.flatMap { min =>
                  if num < min then
                    Some(validation.customMessage.getOrElse(s"${metadata.label} muss mindestens $min sein"))
                  else
                    None
                }.orElse {
                  validation.max.flatMap { max =>
                    if num > max then
                      Some(validation.customMessage.getOrElse(s"${metadata.label} darf höchstens $max sein"))
                    else
                      None
                  }
                }
              case None =>
                Some(s"${metadata.label} muss eine Zahl sein")
          case _ =>
            None
      }.orElse {
        // Length validations
        validation.minLength.flatMap { minLen =>
          if value.length < minLen then
            Some(s"${metadata.label} muss mindestens $minLen Zeichen lang sein")
          else
            None
        }.orElse {
          validation.maxLength.flatMap { maxLen =>
            if value.length > maxLen then
              Some(s"${metadata.label} darf höchstens $maxLen Zeichen lang sein")
            else
              None
          }
        }
      }

end FormField

