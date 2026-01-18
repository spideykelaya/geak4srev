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

end GisDataView
