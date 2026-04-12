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
  * imageDataUrl is None during normal operation (image is kept in browser
  * localStorage keyed by plan id to avoid inflating every auto-save).
  * It is populated only when the user explicitly exports a local JSON file,
  * so that the file is fully self-contained and can be imported on any device.
  */
case class EbfPlan(
    id: String,
    label: String,
    driveFileId: Option[String] = None,
    imageW: Int = 0,
    imageH: Int = 0,
    scale: Option[Double] = None,
    nextId: Int = 1,
    nextMeasId: Int = 1,
    polygons: List[EbfPolygon] = List.empty,
    measurements: List[EbfMeasurement] = List.empty,
    imageDataUrl: Option[String] = None  // populated for local JSON export only
)

/** Collection of all imported plans for a project */
case class EbfPlans(
    plans: List[EbfPlan] = List.empty,
    activePlanId: Option[String] = None
)

object EbfPlans:
  val empty: EbfPlans = EbfPlans()
end EbfPlans

