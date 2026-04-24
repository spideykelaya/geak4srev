package pme123.geak4s.state

import com.raquo.laminar.api.L.*
import pme123.geak4s.domain.*
import pme123.geak4s.domain.area.*

object WaermebrueckeState:

  val data: Var[WaermebrueckenData] = Var(WaermebrueckenData.empty)

  def loadFromProject(project: GeakProject): Unit =
    data.set(project.waermebruecken.getOrElse(WaermebrueckenData.empty))

  def saveToProject(project: GeakProject): GeakProject =
    project.copy(waermebruecken = Some(data.now()))

  def clear(): Unit =
    data.set(WaermebrueckenData.empty)

  /** Add a new linear entry with auto-generated kürzel */
  def addLinear(laenge: Double): Unit =
    data.update { d =>
      val nextIdx = d.linearEntries.length + 1
      val kuerzel = s"WL$nextIdx"
      val entry   = LinearWaermebruecke.empty(kuerzel, laenge)
      d.copy(linearEntries = d.linearEntries :+ entry)
    }

  /** Update a linear entry at the given index */
  def updateLinear(index: Int, entry: LinearWaermebruecke): Unit =
    data.update { d =>
      if index >= 0 && index < d.linearEntries.length then
        d.copy(linearEntries = d.linearEntries.updated(index, entry))
      else d
    }

  /** Remove a linear entry at the given index */
  def removeLinear(index: Int): Unit =
    data.update { d =>
      d.copy(linearEntries = d.linearEntries.patch(index, Nil, 1))
    }

  /** Add a new punkt entry with auto-generated kürzel */
  def addPunkt(anzahl: Int): Unit =
    data.update { d =>
      val nextIdx = d.punktEntries.length + 1
      val kuerzel = s"WR$nextIdx"
      val entry   = PunktWaermebruecke.empty(kuerzel, anzahl)
      d.copy(punktEntries = d.punktEntries :+ entry)
    }

  /** Update a punkt entry at the given index */
  def updatePunkt(index: Int, entry: PunktWaermebruecke): Unit =
    data.update { d =>
      if index >= 0 && index < d.punktEntries.length then
        d.copy(punktEntries = d.punktEntries.updated(index, entry))
      else d
    }

  /** Remove a punkt entry at the given index */
  def removePunkt(index: Int): Unit =
    data.update { d =>
      d.copy(punktEntries = d.punktEntries.patch(index, Nil, 1))
    }

end WaermebrueckeState
