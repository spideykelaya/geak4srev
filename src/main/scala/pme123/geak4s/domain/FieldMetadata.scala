package pme123.geak4s.domain

import pme123.geak4s.domain.project.Anrede

/** Field metadata for form generation with validation, tooltips, and UI control types
  */

/** Field type determines the UI control and validation */
enum FieldType:
  case Text
  case Number
  case Integer
  case Email
  case Phone
  case Year
  case Select
  case Checkbox
  case TextArea
end FieldType

/** Validation rules for fields */
case class ValidationRule(
    required: Boolean = false,
    min: Option[Double] = None,
    max: Option[Double] = None,
    minLength: Option[Int] = None,
    maxLength: Option[Int] = None,
    pattern: Option[String] = None,
    customMessage: Option[String] = None
)

/** Select option for dropdown fields */
case class SelectOption(value: String, label: String, description: Option[String] = None)

/** Complete field metadata */
case class FieldMetadata(
    name: String,
    label: String,
    fieldType: FieldType,
    tooltip: Option[String] = None,
    placeholder: Option[String] = None,
    validation: Option[ValidationRule] = None,
    options: List[SelectOption] = List.empty,
    unit: Option[String] = None,
    helpText: Option[String] = None
)

/** Predefined field metadata for GEAK forms */
object FieldMetadata:

  // Project fields
  val projectName = FieldMetadata(
    name = "projectName",
    label = "Projektbezeichnung",
    fieldType = FieldType.Text,
    tooltip = Some("Name oder Bezeichnung des Projekts"),
    placeholder = Some("Testobjekt Zaida"),
    validation = Some(ValidationRule(required = true, maxLength = Some(200)))
  )

  val geakId = FieldMetadata(
    name = "geakId",
    label = "1. Kopieren Sie die Id des Portfolio Objekts aus dem GEAK Tool und fügen Sie sie hier ein.",
    fieldType = FieldType.Integer,
    tooltip = None,
    placeholder = Some("12345"),
    helpText = None
  )

  // Client fields
  val salutation = FieldMetadata(
    name = "salutation",
    label = "Anrede",
    fieldType = FieldType.Select,
    tooltip = Some("Anrede des Auftraggebers"),
    options =
      Anrede.values.map(anrede => SelectOption(anrede.toString, anrede.toString)).toList
  )

  val clientName1 = FieldMetadata(
    name = "name1",
    label = "Name 1",
    fieldType = FieldType.Text,
    tooltip = Some("Name des Auftraggebers (Person oder Firma)"),
    placeholder = Some("Max Mustermann"),
    validation = Some(ValidationRule(required = true, maxLength = Some(100)))
  )

  val clientName2 = FieldMetadata(
    name = "name2",
    label = "Name 2",
    fieldType = FieldType.Text,
    tooltip = Some("Zusätzlicher Name (z.B. Firma oder Abteilung)"),
    placeholder = Some("Firma AG"),
    validation = Some(ValidationRule(maxLength = Some(100)))
  )

  val email = FieldMetadata(
    name = "email",
    label = "E-Mail",
    fieldType = FieldType.Email,
    tooltip = Some("E-Mail-Adresse des Auftraggebers"),
    placeholder = Some("max.mustermann@example.com"),
    validation = Some(ValidationRule(
      pattern = Some("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"),
      customMessage = Some("Bitte geben Sie eine gültige E-Mail-Adresse ein")
    ))
  )

  val phone = FieldMetadata(
    name = "phone",
    label = "Telefon",
    fieldType = FieldType.Phone,
    tooltip = Some("Telefonnummer (mit Ländervorwahl)"),
    placeholder = Some("+41 44 123 45 67"),
    validation = Some(ValidationRule(
      pattern = Some("^\\+?[0-9\\s\\-\\(\\)]+$"),
      customMessage = Some("Bitte geben Sie eine gültige Telefonnummer ein")
    ))
  )

  val phone1 = FieldMetadata(
    name = "phone1",
    label = "Telefon 1",
    fieldType = FieldType.Phone,
    tooltip = Some("Erste Telefonnummer (mit Ländervorwahl)"),
    placeholder = Some("+41 44 123 45 67"),
    validation = Some(ValidationRule(
      pattern = Some("^\\+?[0-9\\s\\-\\(\\)]+$"),
      customMessage = Some("Bitte geben Sie eine gültige Telefonnummer ein")
    ))
  )

  val phone2 = FieldMetadata(
    name = "phone2",
    label = "Telefon 2",
    fieldType = FieldType.Phone,
    tooltip = Some("Zweite Telefonnummer (mit Ländervorwahl)"),
    placeholder = Some("+41 44 987 65 43"),
    validation = Some(ValidationRule(
      pattern = Some("^\\+?[0-9\\s\\-\\(\\)]+$"),
      customMessage = Some("Bitte geben Sie eine gültige Telefonnummer ein")
    ))
  )

  val poBox = FieldMetadata(
    name = "poBox",
    label = "Postfach",
    fieldType = FieldType.Text,
    tooltip = Some("Postfach-Nummer"),
    placeholder = Some("Postfach 456"),
    validation = Some(ValidationRule(maxLength = Some(50)))
  )

  // Address fields
  val street = FieldMetadata(
    name = "street",
    label = "Strasse",
    fieldType = FieldType.Text,
    tooltip = Some("Strassenname"),
    placeholder = Some("Musterstrasse"),
    validation = Some(ValidationRule(maxLength = Some(100)))
  )

  val houseNumber = FieldMetadata(
    name = "houseNumber",
    label = "Hausnummer",
    fieldType = FieldType.Text,
    tooltip = Some("Hausnummer"),
    placeholder = Some("123a"),
    validation = Some(ValidationRule(maxLength = Some(10)))
  )

  val zipCode = FieldMetadata(
    name = "zipCode",
    label = "PLZ",
    fieldType = FieldType.Text, // Use Text instead of Number to preserve leading zeros
    tooltip = Some("Postleitzahl"),
    placeholder = Some("8000"),
    validation = Some(ValidationRule(
      pattern = Some("^[0-9]{4}$"),
      customMessage = Some("Bitte geben Sie eine 4-stellige PLZ ein")
    ))
  )

  val city = FieldMetadata(
    name = "city",
    label = "Ort",
    fieldType = FieldType.Text,
    tooltip = Some("Ortschaft"),
    placeholder = Some("Zürich"),
    validation = Some(ValidationRule(maxLength = Some(100)))
  )

  val country = FieldMetadata(
    name = "country",
    label = "Land",
    fieldType = FieldType.Select,
    tooltip = Some("Land"),
    options = List(
      SelectOption("Schweiz", "Schweiz"),
      SelectOption("Deutschland", "Deutschland"),
      SelectOption("Österreich", "Österreich"),
      SelectOption("Liechtenstein", "Liechtenstein"),
      SelectOption("Frankreich", "Frankreich"),
      SelectOption("Italien", "Italien")
    ),
    validation = Some(ValidationRule(required = false))
  )

  // Building Data fields
  val constructionYear = FieldMetadata(
    name = "constructionYear",
    label = "Baujahr",
    fieldType = FieldType.Year,
    tooltip = Some("Jahr der Erstellung des Gebäudes"),
    placeholder = Some("1975"),
    validation = Some(ValidationRule(
      required = true,
      min = Some(1800),
      max = Some(2100),
      customMessage = Some("Bitte geben Sie ein gültiges Baujahr ein (1800-2100)")
    ))
  )

  val lastRenovationYear = FieldMetadata(
    name = "lastRenovationYear",
    label = "Jahr der letzten Gesamtsanierung",
    fieldType = FieldType.Year,
    tooltip = Some("Jahr der letzten umfassenden Sanierung"),
    placeholder = Some("2010"),
    validation = Some(ValidationRule(
      min = Some(1800),
      max = Some(2100),
      customMessage = Some("Bitte geben Sie ein gültiges Jahr ein (1800-2100)")
    ))
  )

  val weatherStation = FieldMetadata(
    name = "weatherStation",
    label = "Klimastation mit bestbekannten Werten",
    fieldType = FieldType.Select,
    tooltip = Some("Nächstgelegene Klimastation für Wetterdaten"),
    options = List(
      SelectOption("Zürich-MeteoSchweiz", "Zürich-MeteoSchweiz"),
      SelectOption("Zürich-Kloten", "Zürich-Kloten"),
      SelectOption("St. Gallen", "St. Gallen"),
      SelectOption("Bern-Liebefeld", "Bern-Liebefeld"),
      SelectOption("Adelboden", "Adelboden"),
      SelectOption("Aigle", "Aigle"),
      SelectOption("Altdorf", "Altdorf"),
      SelectOption("Basel-Binningen", "Basel-Binningen"),
      SelectOption("Buchs-Aarau", "Buchs-Aarau"),
      SelectOption("Chur", "Chur"),
      SelectOption("Davos", "Davos"),
      SelectOption("Disentis", "Disentis"),
      SelectOption("Engelberg", "Engelberg"),
      SelectOption("Genève-Cointrin", "Genève-Cointrin"),
      SelectOption("Glarus", "Glarus"),
      SelectOption("Grand-St-Bernard", "Grand-St-Bernard"),
      SelectOption("Güttingen", "Güttingen"),
      SelectOption("Interlaken", "Interlaken"),
      SelectOption("La Chaux-de-Fonds", "La Chaux-de-Fonds"),
      SelectOption("La Frétaz", "La Frétaz"),
      SelectOption("Locarno-Monti", "Locarno-Monti"),
      SelectOption("Lugano", "Lugano"),
      SelectOption("Luzern", "Luzern"),
      SelectOption("Magadino", "Magadino"),
      SelectOption("Montana", "Montana"),
      SelectOption("Neuchâtel", "Neuchâtel"),
      SelectOption("Payerne", "Payerne"),
      SelectOption("Piotta", "Piotta"),
      SelectOption("Pully", "Pully"),
      SelectOption("Robbia", "Robbia"),
      SelectOption("Rünenberg", "Rünenberg"),
      SelectOption("Samedan", "Samedan"),
      SelectOption("San Bernardino", "San Bernardino"),
      SelectOption("Schaffhausen", "Schaffhausen"),
      SelectOption("Scuol", "Scuol"),
      SelectOption("Sion", "Sion"),
      SelectOption("Ulrichen", "Ulrichen"),
      SelectOption("Vaduz", "Vaduz"),
      SelectOption("Wynau", "Wynau"),
      SelectOption("Zermatt", "Zermatt")
    )
  )


  val altitude = FieldMetadata(
    name = "altitude",
    label = "Höhe ü. M.",
    fieldType = FieldType.Number,
    tooltip = Some("Höhe über Meer in Metern"),
    placeholder = Some("556"),
    unit = Some("m"),
    validation = Some(ValidationRule(
      min = Some(0),
      max = Some(5000),
      customMessage = Some("Bitte geben Sie eine gültige Höhe ein (0-5000m)")
    ))
  )

  val energyReferenceArea = FieldMetadata(
    name = "energyReferenceArea",
    label = "Energiebezugsfläche (EBF)",
    fieldType = FieldType.Number,
    tooltip = Some("Energiebezugsfläche nach SIA 380/1 in m²"),
    placeholder = Some("850.5"),
    unit = Some("m²"),
    helpText = Some(
      "Die EBF ist die Summe aller ober- und unterirdischen Geschossflächen, die innerhalb der thermischen Gebäudehülle liegen"
    ),
    validation = Some(ValidationRule(
      required = true,
      min = Some(0),
      max = Some(100000),
      customMessage = Some("Bitte geben Sie eine gültige Fläche ein (0-100'000 m²)")
    ))
  )

  val clearRoomHeight = FieldMetadata(
    name = "clearRoomHeight",
    label = "Lichte Raumhöhe",
    fieldType = FieldType.Number,
    tooltip = Some("Durchschnittliche lichte Raumhöhe in Metern"),
    placeholder = Some("2.4"),
    unit = Some("m"),
    validation = Some(ValidationRule(
      min = Some(1.5),
      max = Some(10),
      customMessage = Some("Bitte geben Sie eine gültige Raumhöhe ein (1.5-10m)")
    ))
  )

  val numberOfFloors = FieldMetadata(
    name = "numberOfFloors",
    label = "Anzahl der Vollgeschosse",
    fieldType = FieldType.Integer,
    tooltip = Some("Anzahl der Vollgeschosse (ohne Dachgeschoss und Keller)"),
    placeholder = Some("4"),
    validation = Some(ValidationRule(
      min = Some(1),
      max = Some(50),
      customMessage = Some("Bitte geben Sie eine gültige Anzahl ein (1-50)")
    ))
  )

  val buildingWidth = FieldMetadata(
    name = "buildingWidth",
    label = "Gebäudebreite",
    fieldType = FieldType.Number,
    tooltip = Some("Durchschnittliche Gebäudebreite in Metern"),
    placeholder = Some("12.5"),
    unit = Some("m"),
    validation = Some(ValidationRule(
      min = Some(0),
      max = Some(500),
      customMessage = Some("Bitte geben Sie eine gültige Breite ein (0-500m)")
    ))
  )

  val constructionType = FieldMetadata(
    name = "constructionType",
    label = "Bauweise Gebäude",
    fieldType = FieldType.Select,
    tooltip = Some("Bauweise des Gebäudes"),
    options = List(
      SelectOption("schwer", "schwer"),
      SelectOption("mittel", "mittel"),
      SelectOption("leicht", "leicht"),
      SelectOption("sehr leicht", "sehr leicht")
    )
  )

  val groundPlanType = FieldMetadata(
    name = "groundPlanType",
    label = "Grundrisstyp",
    fieldType = FieldType.Select,
    tooltip = Some("Grundrisstyp des Gebäudes"),
    options = List(
      SelectOption("kompakt", "kompakt"),
      SelectOption("gestreckt", "gestreckt")
    ),
    helpText = Some("A/V = Verhältnis Gebäudehüllfläche zu Gebäudevolumen")
  )

  val municipality = FieldMetadata(
    name = "municipality",
    label = "Gemeinde",
    fieldType = FieldType.Text,
    tooltip = Some("Politische Gemeinde"),
    placeholder = Some("Zürich"),
    validation = Some(ValidationRule(maxLength = Some(100)))
  )

  val buildingName = FieldMetadata(
    name = "buildingName",
    label = "Gebäudebezeichnung",
    fieldType = FieldType.Text,
    tooltip = Some("Bezeichnung oder Name des Gebäudes"),
    placeholder = Some("Wohnhaus Musterstrasse"),
    validation = Some(ValidationRule(maxLength = Some(200)))
  )

  val parcelNumber = FieldMetadata(
    name = "parcelNumber",
    label = "Parzellen-Nummer",
    fieldType = FieldType.Text,
    tooltip = Some("Parzellennummer aus dem Grundbuch"),
    placeholder = Some("1234"),
    validation = Some(ValidationRule(maxLength = Some(50)))
  )

  val egid = FieldMetadata(
    name = "egid",
    label = "EGID",
    fieldType = FieldType.Text,
    tooltip = Some("Eidgenössischer Gebäudeidentifikator"),
    placeholder = Some("123456789"),
    helpText = Some("9-stellige Nummer aus dem eidgenössischen Gebäude- und Wohnungsregister"),
    validation = Some(ValidationRule(
      pattern = Some("^[0-9]{9}$"),
      customMessage = Some("EGID muss 9-stellig sein")
    ))
  )

  val edid = FieldMetadata(
    name = "edid",
    label = "EDID",
    fieldType = FieldType.Text,
    tooltip = Some("Eidgenössischer Eingangsidentifikator"),
    placeholder = Some("12"),
    helpText = Some("Nummer des Gebäudeeingangs"),
    validation = Some(ValidationRule(
      pattern = Some("^[0-9]{1,4}$"),
      customMessage = Some("EDID muss 1-4-stellig sein")
    ))
  )

  // Description fields
  val buildingDescription = FieldMetadata(
    name = "buildingDescription",
    label = "Beschreibung des Gebäudes",
    fieldType = FieldType.TextArea,
    tooltip = Some("Allgemeine Beschreibung des Gebäudes"),
    placeholder = Some("Das Gebäude ist ein..."),
    validation = Some(ValidationRule(maxLength = Some(2000)))
  )

  val envelopeDescription = FieldMetadata(
    name = "envelopeDescription",
    label = "Beschreibung der Gebäudehülle",
    fieldType = FieldType.TextArea,
    tooltip = Some("Beschreibung der Gebäudehülle (Dächer, Wände, Fenster, etc.)"),
    placeholder = Some("Die Gebäudehülle besteht aus..."),
    validation = Some(ValidationRule(maxLength = Some(2000)))
  )

  val hvacDescription = FieldMetadata(
    name = "hvacDescription",
    label = "Beschreibung Gebäudetechnik",
    fieldType = FieldType.TextArea,
    tooltip = Some("Beschreibung der Gebäudetechnik (Heizung, Lüftung, Warmwasser, etc.)"),
    placeholder = Some("Die Gebäudetechnik umfasst..."),
    validation = Some(ValidationRule(maxLength = Some(2000)))
  )

  val expectedGeakNumber = FieldMetadata(
    name        = "expectedGeakNumber",
    label       = "Erwartete Stammnummer",
    fieldType   = FieldType.Integer,
    tooltip     = Some("Erwartete Stammnummer im GEAK Tool"),
    placeholder = Some("12345")
  )

  // BuildingUsage (Gebäudenutzungen) fields
  val usageType = FieldMetadata(
    name = "usageType",
    label = "Nutzungsart",
    fieldType = FieldType.Select,
    tooltip = Some("Art der Gebäudenutzung gemäss GEAK"),
    validation = Some(ValidationRule(required = true)),
    options = List(
      SelectOption("Einfamilienhaus", "Einfamilienhaus (Kat. II)"),
      SelectOption("Mehrfamilienhaus", "Mehrfamilienhaus (Kat. I)"),
      SelectOption("Hotel", "Hotel (Kat. I)"),
      SelectOption("Büro/Verwaltung", "Büro/Verwaltung (Kat. III)"),
      SelectOption("Schule", "Schule (Kat. IV)"),
      SelectOption("Verkauf", "Verkauf (Kat. V)"),
      SelectOption("Restaurant", "Restaurant (Kat. VI)")
    )
  )

  val usageSubType = FieldMetadata(
    name = "usageSubType",
    label = "Nutzungsuntertyp",
    fieldType = FieldType.Text,
    tooltip = Some("Untertyp der Gebäudenutzung (z.B. Wohnung, Büro)"),
    placeholder = Some("Wohnung"),
    validation = Some(ValidationRule(maxLength = Some(100)))
  )

  val usageArea = FieldMetadata(
    name = "area",
    label = "Energiebezugsfläche",
    fieldType = FieldType.Number,
    placeholder = Some("850.5"),
    unit = Some("m²"),
    validation = Some(ValidationRule(required = true, min = Some(0), max = Some(100000)))
  )

  val usageAreaPercentage = FieldMetadata(
    name = "areaPercentage",
    label = "Flächenanteil",
    fieldType = FieldType.Number,
    tooltip = Some("Anteil der Nutzfläche am Gesamtgebäude in %"),
    placeholder = Some("100"),
    unit = Some("%"),
    validation = Some(ValidationRule(min = Some(0), max = Some(100)))
  )

  val usageConstructionYear = FieldMetadata(
    name = "usageConstructionYear",
    label = "Baujahr",
    fieldType = FieldType.Year,
    tooltip = Some("Baujahr der Nutzungszone"),
    placeholder = Some("1975"),
    validation = Some(ValidationRule(min = Some(1800), max = Some(2100)))
  )

  val numberOfResidents = FieldMetadata(
    name        = "numberOfResidents",
    label       = "Anzahl Bewohner",
    fieldType   = FieldType.Integer,
    placeholder = Some("12"),
    validation  = Some(ValidationRule(min = Some(0)))
  )

  private def apartmentsMeta(rooms: String, name: String) = FieldMetadata(
    name      = name,
    label     = s"$rooms-Zimmer-Wohnungen",
    fieldType = FieldType.Integer,
    placeholder = Some("0"),
    validation  = Some(ValidationRule(min = Some(0)))
  )

  val apartments1Room      = apartmentsMeta("1",       "apartments1Room")
  val apartments2Room      = apartmentsMeta("2",       "apartments2Room")
  val apartments3Room      = apartmentsMeta("3",       "apartments3Room")
  val apartments4Room      = apartmentsMeta("4",       "apartments4Room")
  val apartments5Room      = apartmentsMeta("5",       "apartments5Room")
  val apartments6Room      = apartmentsMeta("6",       "apartments6Room")
  val apartmentsOver6Room  = FieldMetadata(
    name        = "apartmentsOver6Room",
    label       = "Wohnungen mit über 6 Zimmer",
    fieldType   = FieldType.Integer,
    placeholder = Some("0"),
    validation  = Some(ValidationRule(min = Some(0)))
  )

end FieldMetadata
