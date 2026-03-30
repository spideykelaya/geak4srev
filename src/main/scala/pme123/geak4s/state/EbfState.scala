package pme123.geak4s.state

import com.raquo.laminar.api.L.*
import pme123.geak4s.domain.GeakProject
import pme123.geak4s.domain.ebf.*

/** State management for EBF plans (imported floor-plan PDFs and their polygons). */
object EbfState:

  val ebfPlans: Var[EbfPlans] = Var(EbfPlans.empty)

  /** Restore state from a loaded project */
  def loadFromProject(project: GeakProject): Unit =
    ebfPlans.set(project.ebfPlans.getOrElse(EbfPlans.empty))

  /** Snapshot for persistence */
  def getEbfPlans: EbfPlans = ebfPlans.now()

  /** Replace the full plans collection (called from JS sync event) */
  def updatePlans(plans: EbfPlans): Unit =
    ebfPlans.set(plans)

  /** Record the Google Drive file ID for an uploaded plan PDF */
  def updatePlanDriveFileId(planId: String, fileId: String): Unit =
    ebfPlans.update { current =>
      current.copy(plans = current.plans.map { plan =>
        if plan.id == planId then plan.copy(driveFileId = Some(fileId))
        else plan
      })
    }

  /** Write current state back into a project */
  def saveToProject(project: GeakProject): GeakProject =
    project.copy(ebfPlans = Some(ebfPlans.now()))

  /** Clear all plan state (e.g. when project is closed) */
  def clear(): Unit =
    ebfPlans.set(EbfPlans.empty)

end EbfState

