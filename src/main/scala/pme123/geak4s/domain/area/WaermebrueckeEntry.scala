package pme123.geak4s.domain.area

case class LinearWaermebruecke(
    kuerzel: String,       // auto-generated: WL1, WL2, ...
    bezeichnung: String,
    typ: String,
    laenge: Double,        // auto-filled from measurement [m]
    psiWert: Double,       // Ψ-Wert [W/mK]
    bFaktor: Double,       // b-Faktor [-]
    anzahl: Int
)

object LinearWaermebruecke:
  def empty(kuerzel: String, laenge: Double): LinearWaermebruecke =
    LinearWaermebruecke(kuerzel, "", "", laenge, 0.0, 1.0, 1)

case class PunktWaermebruecke(
    kuerzel: String,       // auto-generated: WR1, WR2, ...
    bezeichnung: String,
    typ: String,
    chiWert: Double,       // χ-Wert [W/K]
    bFaktor: Double,       // b-Faktor [-]
    anzahl: Int            // auto-filled from point placement
)

object PunktWaermebruecke:
  def empty(kuerzel: String, anzahl: Int): PunktWaermebruecke =
    PunktWaermebruecke(kuerzel, "", "", 0.0, 1.0, anzahl)

case class WaermebrueckenData(
    linearEntries: List[LinearWaermebruecke] = List.empty,
    punktEntries: List[PunktWaermebruecke] = List.empty
)

object WaermebrueckenData:
  val empty: WaermebrueckenData = WaermebrueckenData()
