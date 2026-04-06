package pme123.geak4s.state

import com.raquo.laminar.api.L.*
import pme123.geak4s.domain.energy.*
import pme123.geak4s.domain.GeakProject

/** State management for the Energieberechnung step. */
object EnergyState:

  val energyData: Var[EnergyConsumptionData] = Var(EnergyConsumptionData.empty)

  def loadFromProject(project: GeakProject): Unit =
    energyData.set(project.energyConsumption.getOrElse(EnergyConsumptionData.empty))

  def saveToProject(project: GeakProject): GeakProject =
    project.copy(energyConsumption = Some(energyData.now()))

  def clear(): Unit =
    energyData.set(EnergyConsumptionData.empty)

end EnergyState
