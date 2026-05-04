package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.*
import com.raquo.laminar.api.L.*
import pme123.geak4s.domain.*
import pme123.geak4s.domain.project.*
import pme123.geak4s.domain.building.BuildingUsage
import pme123.geak4s.state.AppState
import pme123.geak4s.components.FormField
import org.scalajs.dom

case class ProjectView(geakProject: GeakProject):

  private val hasEgid = Var(
    geakProject.project.egidEdidGroup.entries.headOption.flatMap(_.egid).isDefined
  )

  def render(): HtmlElement =
    div(
      className := "project-view",
      card1Auftraggeberschaft(),
      card2StandortPotenziale(),
      card3Gebaeude(),
      card4IstZustand(),
      card5Gebaeudenutzungen()
    )

  // ── helpers ────────────────────────────────────────────────────────────────

  private def card(title: String, subtitle: String)(content: HtmlElement): HtmlElement =
    div(
      marginBottom := "1.5rem",
      borderRadius := "0.5rem",
      border       := "1px solid #d9d9d9",
      overflow     := "hidden",
      // Header
      div(
        backgroundColor := "#f0f4ff",
        borderBottom    := "3px solid #0a6ed1",
        padding         := "0.9rem 1.5rem",
        div(
          fontSize   := "1.1rem",
          fontWeight := "700",
          color      := "#0a6ed1",
          title
        ),
        div(
          fontSize   := "0.8rem",
          color      := "#555",
          marginTop  := "0.2rem",
          subtitle
        )
      ),
      // Content
      div(
        padding := "1.25rem 1.5rem 1.5rem",
        width   := "100%",
        boxSizing := "border-box",
        content
      )
    )

  private def ff(
    meta:     FieldMetadata,
    get:      Signal[String],
    set:      String => Unit,
    validate: Boolean = false
  ): HtmlElement =
    FormField(
      metadata       = meta,
      value          = get,
      onChange       = set,
      showValidation = Val(validate)
    )

  private def proj = AppState.projectSignal

  private def upProj(f: GeakProject => GeakProject): Unit = AppState.updateProject(f)

  // ── Card 1: Auftraggeberschaft ─────────────────────────────────────────────

  private def card1Auftraggeberschaft(): HtmlElement =
    card("Auftraggeberschaft", "Kontakt- und Projektdaten") {
      div(
        // Projektbezeichnung
        ff(FieldMetadata.projectName,
          proj.map(_.map(_.project.projectName).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(projectName = v)))
        ),

        // Anrede
        ff(FieldMetadata.salutation,
          proj.map(_.map(_.project.client.salutation.toString).getOrElse(Anrede.Herr.toString)),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(salutation = Anrede.valueOf(v)))))
        ),

        // Name 1
        ff(FieldMetadata.clientName1,
          proj.map(_.flatMap(_.project.client.name1).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(name1 = opt(v)))))
        ),

        // Name 2
        ff(FieldMetadata.clientName2,
          proj.map(_.flatMap(_.project.client.name2).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(name2 = opt(v)))))
        ),

        // Adresse
        ff(FieldMetadata.street,
          proj.map(_.flatMap(_.project.client.address.street).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(address = p.project.client.address.copy(street = opt(v))))))
        ),
        ff(FieldMetadata.houseNumber,
          proj.map(_.flatMap(_.project.client.address.houseNumber).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(address = p.project.client.address.copy(houseNumber = opt(v))))))
        ),

        // PLZ / Ort
        ff(FieldMetadata.zipCode,
          proj.map(_.flatMap(_.project.client.address.zipCode).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(address = p.project.client.address.copy(zipCode = opt(v))))))
        ),
        ff(FieldMetadata.city,
          proj.map(_.flatMap(_.project.client.address.city).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(address = p.project.client.address.copy(city = opt(v))))))
        ),

        // Land
        ff(FieldMetadata.country,
          proj.map(_.flatMap(_.project.client.address.country).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(address = p.project.client.address.copy(country = opt(v))))))
        ),

        // E-Mail
        ff(FieldMetadata.email,
          proj.map(_.flatMap(_.project.client.email).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(email = opt(v)))))
        ),

        // Telefon 1 / 2
        ff(FieldMetadata.phone1,
          proj.map(_.flatMap(_.project.client.phone1).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(phone1 = opt(v)))))
        ),
        ff(FieldMetadata.phone2,
          proj.map(_.flatMap(_.project.client.phone2).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            client = p.project.client.copy(phone2 = opt(v)))))
        )
      )
    }

  // ── Card 2: Standort / Potenziale ──────────────────────────────────────────

  private def card2StandortPotenziale(): HtmlElement =
    card("Standort / Potenziale", "Gebäudestandort und Klimadaten") {
      div(
        // Gebäude mit EGID?
        div(
          marginBottom := "1rem",
          CheckBox(
            _.text    := "Gebäude mit EGID?",
            _.checked <-- hasEgid.signal,
            _.events.onChange.mapToChecked --> Observer[Boolean] { v =>
              hasEgid.set(v)
            }
          )
        ),
        child <-- hasEgid.signal.map { show =>
          if show then
            ff(FieldMetadata.egid,
              proj.map(_.map(_.project.egidEdidGroup.entries.headOption.flatMap(_.egid).getOrElse("")).getOrElse("")),
              v => upProj { p =>
                val entry = p.project.egidEdidGroup.entries.headOption.getOrElse(
                  EgidEdidEntry(egid = None, edid = None, address = Address.empty)
                ).copy(egid = opt(v))
                val entries = if p.project.egidEdidGroup.entries.isEmpty then List(entry)
                              else p.project.egidEdidGroup.entries.updated(0, entry)
                p.copy(project = p.project.copy(
                  egidEdidGroup = p.project.egidEdidGroup.copy(entries = entries)))
              }
            )
          else div()
        },

        // Parzellen-Nummer
        ff(FieldMetadata.parcelNumber,
          proj.map(_.flatMap(_.project.buildingLocation.parcelNumber).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingLocation = p.project.buildingLocation.copy(parcelNumber = opt(v)))))
        ),

        // Adresse im GEAK (Strasse + Hausnummer, PLZ + Ort)
        div(
          fontWeight := "600", fontSize := "0.875rem", marginBottom := "0.5rem", color := "#32363a",
          "Adresse im GEAK"
        ),
        ff(FieldMetadata.street,
          proj.map(_.flatMap(_.project.buildingLocation.address.street).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingLocation = p.project.buildingLocation.copy(
              address = p.project.buildingLocation.address.copy(street = opt(v))))))
        ),
        ff(FieldMetadata.houseNumber,
          proj.map(_.flatMap(_.project.buildingLocation.address.houseNumber).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingLocation = p.project.buildingLocation.copy(
              address = p.project.buildingLocation.address.copy(houseNumber = opt(v))))))
        ),
        ff(FieldMetadata.zipCode,
          proj.map(_.flatMap(_.project.buildingLocation.address.zipCode).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingLocation = p.project.buildingLocation.copy(
              address = p.project.buildingLocation.address.copy(zipCode = opt(v))))))
        ),
        ff(FieldMetadata.city,
          proj.map(_.flatMap(_.project.buildingLocation.address.city).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingLocation = p.project.buildingLocation.copy(
              address = p.project.buildingLocation.address.copy(city = opt(v))))))
        ),

        // Klimastation
        ff(FieldMetadata.weatherStation,
          proj.map(_.flatMap(_.project.buildingData.weatherStation).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(weatherStation = opt(v)))))
        ),

        // Höhe ü. M.
        div(
          className := "form-field",
          // Label row: "Höhe ü. M." + tooltip hint + button
          div(
            className := "form-field-label",
            display   := "flex",
            alignItems := "center",
            gap := "0.5rem",
            Label(_.showColon := true, "Höhe ü. M."),
            span(
              className := "field-tooltip-wrapper",
              dataAttr("tooltip") := "Höhe über Meer in Metern",
              Icon(
                _.name             := IconName.hint,
                _.design           := IconDesign.Information,
                _.accessibleName   := "Höhe über Meer in Metern"
              )
            ),
            // Hidden anchor for reliable new-tab navigation
            a(
              idAttr  := "hoehe-link",
              href    := "https://geo.zh.ch/maps?x=2697995&y=1246619&scale=270&basemap=arelkbackgroundzh",
              target  := "_blank",
              rel     := "noopener noreferrer",
              display := "none"
            ),
            Button(
              _.design := ButtonDesign.Default,
              _.icon   := IconName.map,
              _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                // Copy building address to clipboard, then open the map
                val address = AppState.getCurrentProject.flatMap { p =>
                  val loc = p.project.buildingLocation.address
                  val street = loc.street.getOrElse("")
                  val nr     = loc.houseNumber.getOrElse("")
                  val zip    = loc.zipCode.getOrElse("")
                  val city   = loc.city.getOrElse("")
                  val full   = s"$street $nr, $zip $city".trim.stripSuffix(",")
                  if full.nonEmpty then Some(full) else None
                }
                address.foreach { addr =>
                  val d  = scala.scalajs.js.Dynamic.global.document
                  val ta = d.createElement("textarea")
                  ta.value = addr
                  ta.style.cssText = "position:fixed;top:-9999px;left:-9999px;opacity:0"
                  d.body.appendChild(ta)
                  ta.focus()
                  ta.select()
                  d.execCommand("copy")
                  d.body.removeChild(ta)
                }
                dom.document.getElementById("hoehe-link").asInstanceOf[dom.HTMLAnchorElement].click()
              },
              "GIS Browser"
            )
          ),
          div(
            display     := "flex",
            alignItems  := "center",
            gap         := "0.5rem",
            Input(
              _.tpe         := InputType.Number,
              _.value      <-- proj.map(_.flatMap(_.project.buildingData.altitude).map(_.toString).getOrElse("")),
              _.placeholder := "556",
              onBlur.mapToValue --> Observer[String] { v =>
                upProj(p => p.copy(project = p.project.copy(
                  buildingData = p.project.buildingData.copy(altitude = v.toDoubleOption))))
              },
              className := "form-input"
            ),
            span(className := "input-unit", "m")
          )
        )
      )
    }

  // ── Card 3: Gebäude ────────────────────────────────────────────────────────

  private def card3Gebaeude(): HtmlElement =
    card("Gebäude", "Technische Gebäudedaten") {
      div(
        ff(FieldMetadata.constructionYear,
          proj.map(_.flatMap(_.project.buildingData.constructionYear).map(_.toString).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(constructionYear = v.toIntOption))))
        ),
        ff(FieldMetadata.lastRenovationYear,
          proj.map(_.flatMap(_.project.buildingData.lastRenovationYear).map(_.toString).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(lastRenovationYear = v.toIntOption))))
        ),
        ff(FieldMetadata.energyReferenceArea,
          proj.map(_.flatMap(_.project.buildingData.energyReferenceArea).map(_.toString).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(energyReferenceArea = v.toDoubleOption)))),
          validate = true
        ),
        ff(FieldMetadata.clearRoomHeight,
          proj.map(_.flatMap(_.project.buildingData.clearRoomHeight).map(_.toString).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(clearRoomHeight = v.toDoubleOption))))
        ),
        ff(FieldMetadata.numberOfFloors,
          proj.map(_.flatMap(_.project.buildingData.numberOfFloors).map(_.toString).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(numberOfFloors = v.toIntOption))))
        ),
        ff(FieldMetadata.buildingWidth,
          proj.map(_.flatMap(_.project.buildingData.buildingWidth).map(_.toString).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(buildingWidth = v.toDoubleOption))))
        ),
        ff(FieldMetadata.constructionType,
          proj.map(_.flatMap(_.project.buildingData.constructionType).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(constructionType = opt(v)))))
        ),
        ff(FieldMetadata.groundPlanType,
          proj.map(_.flatMap(_.project.buildingData.groundPlanType).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            buildingData = p.project.buildingData.copy(groundPlanType = opt(v)))))
        )
      )
    }

  // ── Card 4: IST-Zustand ────────────────────────────────────────────────────

  private def card4IstZustand(): HtmlElement =
    card("IST-Zustand", "Beschreibungen des aktuellen Zustands") {
      div(
        ff(FieldMetadata.buildingDescription,
          proj.map(_.flatMap(_.project.descriptions.buildingDescription).getOrElse("")),
          v => upProj(p => p.copy(project = p.project.copy(
            descriptions = p.project.descriptions.copy(buildingDescription = opt(v)))))
        )
      )
    }

  // ── Card 5: Gebäudenutzungen ───────────────────────────────────────────────

  private def card5Gebaeudenutzungen(): HtmlElement =
    card("Gebäudenutzungen", "Nutzungsarten und Wohnungsdaten") {
      div(
        children <-- proj.map { projOpt =>
          val usages = projOpt.map(_.buildingUsages).getOrElse(List.empty)
          usages.zipWithIndex.map { case (u, i) => usageBlock(u, i, usages.length) }
        },
        child <-- proj.map { projOpt =>
          val count = projOpt.map(_.buildingUsages.length).getOrElse(0)
          if count < 3 then
            div(
              marginTop := "1rem",
              Button(
                _.design := ButtonDesign.Default,
                _.icon   := IconName.`add`,
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  upProj(p => p.copy(buildingUsages = p.buildingUsages :+
                    BuildingUsage(
                      usageType         = "", area = 0.0,
                      apartments1Room   = Some(0), apartments2Room   = Some(0),
                      apartments3Room   = Some(0), apartments4Room   = Some(0),
                      apartments5Room   = Some(0), apartments6Room   = Some(0),
                      apartmentsOver6Room = Some(0)
                    )))
                },
                "Nutzung hinzufügen"
              )
            )
          else div(fontSize := "0.875rem", color := "#666", "Max. 3 Nutzungszonen erreicht.")
        }
      )
    }

  private def usageBlock(u: BuildingUsage, idx: Int, total: Int): HtmlElement =
    def uSig[A](f: BuildingUsage => A): Signal[String] =
      proj.map(_.map(_.buildingUsages.lift(idx).map(f).map(_.toString).getOrElse("")).getOrElse(""))
    def upU(f: BuildingUsage => BuildingUsage): Unit =
      upProj(p => p.copy(buildingUsages = p.buildingUsages.updated(idx, f(u))))

    div(
      marginBottom  := "1.5rem",
      paddingBottom := "1rem",
      borderBottom  := (if idx < total - 1 then "1px solid #e0e0e0" else "none"),

      div(
        display := "flex", alignItems := "center", justifyContent := "space-between",
        marginBottom := "0.75rem",
        div(fontWeight := "600", fontSize := "0.875rem", color := "#32363a", s"Nutzung ${idx + 1}"),
        Button(
          _.design := ButtonDesign.Transparent, _.icon := IconName.`delete`,
          _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
            upProj(p => p.copy(buildingUsages = p.buildingUsages.patch(idx, Nil, 1)))
          }
        )
      ),

      // Nutzungsart
      ff(FieldMetadata.usageType,
        proj.map(_.map(_.buildingUsages.lift(idx).map(_.usageType).getOrElse("")).getOrElse("")),
        v => upU(_.copy(usageType = v))
      ),

      // Energiebezugsfläche
      ff(FieldMetadata.energyReferenceArea,
        proj.map(_.flatMap(_.project.buildingData.energyReferenceArea).map(_.toString).getOrElse("")),
        v => upProj(p => p.copy(project = p.project.copy(
          buildingData = p.project.buildingData.copy(energyReferenceArea = v.toDoubleOption)))),
        validate = true
      ),

      // Anzahl Bewohner
      ff(FieldMetadata.numberOfResidents,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.numberOfResidents).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(numberOfResidents = v.toIntOption))
      ),

      // Zimmer-Wohnungen
      ff(FieldMetadata.apartments1Room,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.apartments1Room).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(apartments1Room = v.toIntOption))
      ),
      ff(FieldMetadata.apartments2Room,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.apartments2Room).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(apartments2Room = v.toIntOption))
      ),
      ff(FieldMetadata.apartments3Room,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.apartments3Room).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(apartments3Room = v.toIntOption))
      ),
      ff(FieldMetadata.apartments4Room,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.apartments4Room).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(apartments4Room = v.toIntOption))
      ),
      ff(FieldMetadata.apartments5Room,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.apartments5Room).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(apartments5Room = v.toIntOption))
      ),
      ff(FieldMetadata.apartments6Room,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.apartments6Room).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(apartments6Room = v.toIntOption))
      ),
      ff(FieldMetadata.apartmentsOver6Room,
        proj.map(_.map(_.buildingUsages.lift(idx).flatMap(_.apartmentsOver6Room).map(_.toString).getOrElse("")).getOrElse("")),
        v => upU(_.copy(apartmentsOver6Room = v.toIntOption))
      )
    )

  // ── utility ────────────────────────────────────────────────────────────────

  private def opt(v: String): Option[String] = if v.isEmpty then None else Some(v)

end ProjectView
