package pme123.geak4s.domain.building

/** Gebäudenutzungen - Building usage zone */
case class BuildingUsage(
  usageType: String,
  usageSubType: Option[String]      = None,
  area: Double                      = 0.0,
  areaPercentage: Option[Double]    = None,
  constructionYear: Option[Int]     = None,
  // Wohnungsdaten (relevant für MFH)
  numberOfResidents: Option[Int]    = None,
  apartments1Room: Option[Int]      = None,
  apartments2Room: Option[Int]      = None,
  apartments3Room: Option[Int]      = None,
  apartments4Room: Option[Int]      = None,
  apartments5Room: Option[Int]      = None,
  apartments6Room: Option[Int]      = None,
  apartmentsOver6Room: Option[Int]  = None
)

object BuildingUsage:
  lazy val example: BuildingUsage = BuildingUsage(
    usageType        = "Mehrfamilienhaus",
    area             = 850.5,
    areaPercentage   = Some(100.0),
    constructionYear = Some(1975)
  )
end BuildingUsage
