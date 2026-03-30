package pme123.geak4s.domain.ebf

/** A 2D point in image pixel coordinates */
case class EbfPoint(x: Double, y: Double)

/** A drawn polygon on an EBF plan */
case class EbfPolygon(
    id: Int,
    label: String,
    points: List[EbfPoint],
    color: String,
    pixelArea: Double,
    area: Option[Double]   // None when not calibrated
)

/** A distance measurement on an EBF plan */
case class EbfMeasurement(
    id: Int,
    pt1: EbfPoint,
    pt2: EbfPoint
)

/** A single imported plan (PDF or image) with its drawing state.
  *
  * Note: imageDataUrl is intentionally excluded from JSON persistence –
  * it is stored in the browser's localStorage (keyed by plan id) and
  * cached in-memory, keeping the project JSON compact.
  * The original PDF is uploaded to Google Drive (driveFileId).
  */
case class EbfPlan(
    id: String,
    label: String,
    driveFileId: Option[String] = None, // Google Drive file ID of the uploaded PDF
    imageW: Int = 0,
    imageH: Int = 0,
    scale: Option[Double] = None,       // metres per pixel; None = not calibrated
    nextId: Int = 1,
    nextMeasId: Int = 1,
    polygons: List[EbfPolygon] = List.empty,
    measurements: List[EbfMeasurement] = List.empty
)

/** Collection of all imported plans for a project */
case class EbfPlans(
    plans: List[EbfPlan] = List.empty,
    activePlanId: Option[String] = None
)

object EbfPlans:
  val empty: EbfPlans = EbfPlans()
end EbfPlans

