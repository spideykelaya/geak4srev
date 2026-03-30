package pme123.geak4s.views

import be.doeraene.webcomponents.ui5.*
import be.doeraene.webcomponents.ui5.configkeys.{
  ButtonDesign,
  IconName,
  LinkDesign,
  LinkTarget,
  MessageStripDesign,
  TitleLevel,
  WrappingType
}
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import pme123.geak4s.domain.GeakProject
import pme123.geak4s.domain.gis.MaddResponse
import pme123.geak4s.services.GisXmlParser
import pme123.geak4s.state.AppState
import scala.util.{Success, Failure}
import pme123.geak4s.views.WordFormView

object GisDataView:

  // State for file upload
  private val fileInputRef = Var[Option[dom.html.Input]](None)
  private val xmlContent = Var[Option[String]](None)
  private val uploadError = Var[Option[String]](None)
  private val parsedGisData = Var[Option[MaddResponse]](None)

  def apply(): HtmlElement =
    div(
      className := "step-content",
      Title(_.level := TitleLevel.H2, "GIS-Daten beziehen"),
      MessageStrip(
        _.design := MessageStripDesign.Information,
        _.hideCloseButton := true,
        "Beziehen Sie Gebäudedaten vom kantonalen GIS (Zürich)."
      ),
      Card(
        // Reactively display address and coordinates from AppState
        child <-- AppState.projectSignal.map:
          case Some(project) =>
            val address = project.project.buildingLocation.address

            // Initialize parsedGisData from project if available
            project.gisData.foreach(gisData => parsedGisData.set(Some(gisData)))

            div(
              className := "card-content",
              Button(
                _.design := ButtonDesign.Default,
                _.icon   := IconName.map,
                _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                  dom.window.open(
                    s"https://www.geoportal.ch/ktzh/map/104?y=${address.lat.mkString}&x=${address.lon.mkString}&scale=500&rotation=0",
                    "_blank"
                  )
                },
                "Auf Geoportal anzeigen"
              ),

              // XML File Upload Section
              div(
                marginTop := "1.5rem",
                Label(
                  fontWeight := "600",
                  "GIS-Daten aus XML importieren:"
                ),

                // Error message
                child.maybe <-- uploadError.signal.map(_.map { msg =>
                  MessageStrip(
                    _.design := MessageStripDesign.Negative,
                    _.hideCloseButton := true,
                    _.events.onClose.mapTo(()) --> Observer[Unit] { _ =>
                      uploadError.set(None)
                    },
                    msg
                  )
                }),

                // Success message
                child.maybe <-- xmlContent.signal.map(_.map { content =>
                  MessageStrip(
                    _.design := MessageStripDesign.Positive,
                    _.hideCloseButton := true,
                    _.events.onClose.mapTo(()) --> Observer[Unit] { _ =>
                      xmlContent.set(None)
                      parsedGisData.set(None)
                    },
                    s"XML-Datei erfolgreich geladen (${content.length} Zeichen)"
                  )
                }),

                // Display parsed GIS data
                child.maybe <-- parsedGisData.signal.map(_.map { maddResponse =>
                  renderGisDataSummary(maddResponse)
                }),

                div(
                  marginTop := "0.5rem",
                  Button(
                    _.design := ButtonDesign.Default,
                    _.icon := IconName.upload,
                    _.events.onClick.mapTo(()) --> Observer[Unit] { _ =>
                      fileInputRef.now().foreach(_.click())
                    },
                    "XML-Datei auswählen"
                  ),

                  // Hidden file input
                  input(
                    tpe := "file",
                    accept := ".xml,text/xml,application/xml",
                    display := "none",
                    onMountCallback { ctx =>
                      fileInputRef.set(Some(ctx.thisNode.ref))
                    },
                    onChange --> Observer[dom.Event] { e =>
                      val input = e.target.asInstanceOf[dom.html.Input]
                      val files = input.files

                      if files != null && files.length > 0 then
                        val file = files(0)
                        uploadError.set(None)

                        // Read XML file
                        val reader = new dom.FileReader()

                        reader.onload = (_: dom.Event) => {
                          try
                            val content = reader.result.asInstanceOf[String]
                            xmlContent.set(Some(content))
                            dom.console.log(s"XML file loaded: ${file.name}")

                            // Parse XML to case classes
                            GisXmlParser.parse(content) match
                              case Success(maddResponse) =>
                                parsedGisData.set(Some(maddResponse))

                                // Print summary to console
                                val summary = MaddResponse.summary(maddResponse)
                                dom.console.log(summary)

                                // Print detailed structure
                                dom.console.log("Parsed GIS Data Structure:")
                                dom.console.log(maddResponse)

                                // Save to AppState
                                AppState.saveGisData(maddResponse)
                                dom.console.log("✅ GIS data saved to project state")

                               // Auto-fill WordFormView
                                val building = maddResponse.buildingList.headOption
                                building.foreach { b =>
                                  val entrance = b.buildingEntranceList.headOption
                                  val addr = entrance.map(_.buildingEntrance)
                                  val adresseStr = addr.map { a =>
                                    s"${a.street.streetName.descriptionLong} ${a.buildingEntranceNo}, ${a.locality.swissZipCode} ${a.locality.placeName}"
                                  }.getOrElse("")
                                  val totalWohnungen = b.buildingEntranceList.flatMap(_.dwellingList).length

                                  val heizung = b.building.thermotechnicalDeviceForHeating1
                                    .flatMap(_.heatGenerator)
                                    .map(translateHeatGenerator)
                                    .getOrElse("")

                                  val warmwasser = b.building.thermotechnicalDeviceForWarmWater1
                                    .flatMap(_.heatGenerator)
                                    .map(translateHeatGenerator)
                                    .getOrElse("")

                                  val gebäudeart = if totalWohnungen > 1 then "MFH" else "EFH"

                                  WordFormView.formVar.update(old => old.copy(
                                    adresse     = adresseStr,
                                    egid        = b.egid.toString,
                                    baujahr     = b.building.dateOfConstruction.flatMap(_.dateOfConstruction).getOrElse(""),
                                    wohnungen   = totalWohnungen.toString,
                                    gebäudeart  = gebäudeart,
                                    heizung     = heizung,
                                    warmwasser  = warmwasser
                                    
                                  ))
                                }
                              case Failure(ex) =>
                                uploadError.set(Some(s"Fehler beim Parsen der XML-Datei: ${ex.getMessage}"))
                                dom.console.error("XML parsing error:", ex)
                          catch
                            case ex: Exception =>
                              uploadError.set(Some(s"Fehler beim Lesen der XML-Datei: ${ex.getMessage}"))
                        }

                        reader.onerror = (_: dom.Event) => {
                          uploadError.set(Some("Fehler beim Lesen der Datei"))
                        }

                        reader.readAsText(file)

                        // Reset input
                        input.value = ""
                    }
                  )
                )
              ),

              Label(s"Address: ${address.asCopyString}"),
              Label(s"Coordinates: ${address.coordString}"),
              Label("• Energiebezugsfläche (EBF)"),
              Label("• Gebäudetyp und Baujahr"),
              Label("• EGID/EDID Daten")
            )
          case None          =>
            div(Label("No project loaded"))
      )
    )

  /** Render a summary of the parsed GIS data */
  private def renderGisDataSummary(maddResponse: MaddResponse): HtmlElement =
    div(
      marginTop := "1.5rem",
      maddResponse.buildingList.map { buildingItem =>
        Card(
          _.slots.header := CardHeader(
            _.titleText := s"Gebäude EGID: ${buildingItem.egid}",
            _.subtitleText := s"${buildingItem.municipality.municipalityName} (${buildingItem.municipality.cantonAbbreviation})"
          ),
          div(
            className := "card-content",

            // Building info
            Title(_.level := TitleLevel.H4, "Gebäudeinformationen"),
            Label(s"Gebäudenummer: ${buildingItem.building.officialBuildingNo.getOrElse("N/A")}"),
            Label(s"Koordinaten: E=${buildingItem.building.coordinates.east}, N=${buildingItem.building.coordinates.north}"),
            Label(s"Gebäudefläche: ${buildingItem.building.surfaceAreaOfBuilding.getOrElse("N/A")} m²"),
            Label(s"Anzahl Stockwerke: ${buildingItem.building.numberOfFloors.getOrElse("N/A")}"),
            Label(s"Baujahr: ${buildingItem.building.dateOfConstruction.flatMap(_.dateOfConstruction).getOrElse("N/A")}"),

            // Entrances
            div(
              marginTop := "1rem",
              Title(_.level := TitleLevel.H4, s"Eingänge (${buildingItem.buildingEntranceList.length})"),
              buildingItem.buildingEntranceList.map { entrance =>
                val addr = entrance.buildingEntrance
                div(
                  marginTop := "0.5rem",
                  Label(
                    fontWeight := "600",
                    s"${addr.street.streetName.descriptionLong} ${addr.buildingEntranceNo}, ${addr.locality.swissZipCode} ${addr.locality.placeName}"
                  ),
                  Label(s"  Wohnungen: ${entrance.dwellingList.length}"),
                  entrance.dwellingList.map { dwelling =>
                    Label(
                      marginLeft := "1rem",
                      fontSize := "0.875rem",
                      s"• Whg ${dwelling.dwelling.administrativeDwellingNo}: ${dwelling.dwelling.noOfHabitableRooms.getOrElse("?")} Zimmer, ${dwelling.dwelling.surfaceAreaOfDwelling.getOrElse("?")} m²"
                    )
                  }
                )
              }
            )
          )
        )
      }
    )

  private def translateHeatGenerator(code: String): String = code.toIntOption.getOrElse(0) match
    case 7410 => "Wärmepumpe"
    case 7420 => "Elektro-Direktheizung"
    case 7430 => "Heizkessel"
    case 7440 => "Fernwärme"
    case 7450 => "Elektrowiderstandsheizung"
    case 7499 => "Andere"
    case 7610 => "Wärmepumpe Boiler"
    case 7620 => "Elektro-Boiler"
    case 7630 => "Boiler (konventionell)"
    case 7640 => "Fernwärme Boiler"
    case 7699 => "Andere"
    case other => s"Code $other"

  private def translateEnergySource(code: Int): String = code match
    case 7500 => "Öl"
    case 7501 => "Gas"
    case 7510 => "Holz"
    case 7511 => "Holzpellets"
    case 7512 => "Holzschnitzel"
    case 7520 => "Gas"
    case 7530 => "Umweltwärme (Luft/Wasser/Erdreich)"
    case 7540 => "Sonnenenergie"
    case 7550 => "Fernwärme"
    case 7560 => "Elektrizität"
    case 7570 => "Kohle"
    case 7580 => "Abwärme"
    case 7599 => "Andere"
    case other => s"Code $other"

end GisDataView
