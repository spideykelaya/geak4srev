package pme123.geak4s.state

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.concurrent.duration.*
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import pme123.geak4s.domain.*
import pme123.geak4s.domain.project.*
import pme123.geak4s.domain.building.*
import pme123.geak4s.domain.envelope.*
import pme123.geak4s.domain.hvac.*
import pme123.geak4s.domain.energy.*
import pme123.geak4s.services.GoogleDriveService
import pme123.geak4s.state.{EbfState, EnergyState}
import pme123.geak4s.domain.JsonCodecs.given
import io.circe.syntax.*

/** Application state management */
object AppState:
  
  /** Current view in the application */
  enum View:
    case Welcome
    case ProjectEditor
    case WorkflowEditor  // New workflow-based editor
  
  /** Current project state */
  sealed trait ProjectState
  object ProjectState:
    case object NoProject extends ProjectState
    case class Loading(fileName: String) extends ProjectState
    case class Loaded(project: GeakProject, fileName: String) extends ProjectState
    case class Error(message: String) extends ProjectState
  
  // LocalStorage keys for work-in-progress persistence
  val WIP_KEY      = "geak4s_wip_project"
  val WIP_FILE_KEY = "geak4s_wip_filename"

  /** Global application state */
  val currentView: Var[View] = Var(View.Welcome)
  val projectState: Var[ProjectState] = Var(ProjectState.NoProject)

  /** Signal for current project */
  val projectSignal: Signal[Option[GeakProject]] = projectState.signal.map {
    case ProjectState.Loaded(project, _) => Some(project)
    case _ => None
  }

  /** Google Drive sync state */
  val driveConnected: Var[Boolean] = Var(false)
  val driveSyncing: Var[Boolean] = Var(false)
  val driveLoginPrompt: Var[Boolean] = Var(false) // Shows when auto-login is triggered
  val driveError: Var[Option[String]] = Var(None) // Shows configuration or other errors
  val lastSyncTime: Var[Option[Long]] = Var(None)
  val autoSaveEnabled: Var[Boolean] = Var(true) // Each user logs in with their own account
  val syncInitialized: Var[Boolean] = Var(false) // Tracks if sync has been explicitly started for this project

  // Auto-save timer (Google Drive)
  private var autoSaveTimer: Option[Int] = None
  private val AUTO_SAVE_DELAY_MS = 5000 // 5 seconds after last change

  // Periodic sync timer
  private var periodicSyncTimer: Option[Int] = None
  private val PERIODIC_SYNC_INTERVAL_MS = 30000 // 30 seconds

  // Track which projects have had their folder structure created
  private var projectsWithFolderStructure = Set.empty[String]

  // Local file auto-save (File System Access API)
  val localFileHandle: Var[Option[js.Dynamic]] = Var(None)
  val localFileName: Var[Option[String]]        = Var(None)
  val localSaveError: Var[Option[String]]       = Var(None)   // shown in UI when write fails
  val localSaveSuccess: Var[Boolean]            = Var(false)  // briefly true after successful write
  private var localFileSaveTimer: Option[Int]   = None
  private var successClearTimer: Option[Int]    = None
  private val LOCAL_SAVE_DELAY_MS               = 3000 // 3 seconds debounce

  // IndexedDB constants for cross-session file handle persistence
  private val IDB_DB_NAME    = "geak4s_files"
  private val IDB_STORE_NAME = "handles"
  private val IDB_HANDLE_KEY = "lastProjectHandle"
  private val IDB_NAME_KEY   = "lastProjectName"

  /** Store the FileSystemFileHandle obtained from showOpenFilePicker / showSaveFilePicker.
   *  Also persists the handle in IndexedDB so it survives browser restarts. */
  def setLocalFileHandle(handle: js.Dynamic, name: String): Unit =
    localFileHandle.set(Some(handle))
    localFileName.set(Some(name))
    dom.console.log(s"✅ Lokales Auto-Save aktiviert für: $name")
    saveHandleToIndexedDb(handle, name)

  def clearLocalFileHandle(): Unit =
    localFileHandle.set(None)
    localFileName.set(None)
    localFileSaveTimer.foreach(dom.window.clearTimeout)
    localFileSaveTimer = None

  /** Persist FileSystemFileHandle and filename in IndexedDB. */
  private def saveHandleToIndexedDb(handle: js.Dynamic, name: String): Unit =
    try
      val req = dom.window.asInstanceOf[js.Dynamic].indexedDB.open(IDB_DB_NAME, 1)
      req.asInstanceOf[js.Dynamic].onupgradeneeded = { (e: js.Dynamic) =>
        val db = e.target.result.asInstanceOf[js.Dynamic]
        if js.isUndefined(db.objectStoreNames.contains(IDB_STORE_NAME)) ||
           !db.objectStoreNames.contains(IDB_STORE_NAME).asInstanceOf[Boolean] then
          db.createObjectStore(IDB_STORE_NAME)
      }: js.Function1[js.Dynamic, Unit]
      req.asInstanceOf[js.Dynamic].onsuccess = { (e: js.Dynamic) =>
        val db = e.target.result.asInstanceOf[js.Dynamic]
        val tx = db.transaction(IDB_STORE_NAME, "readwrite")
        val store = tx.objectStore(IDB_STORE_NAME)
        store.put(handle, IDB_HANDLE_KEY)
        store.put(name, IDB_NAME_KEY)
      }: js.Function1[js.Dynamic, Unit]
    catch case ex: Exception =>
      dom.console.warn(s"IndexedDB write failed: ${ex.getMessage}")

  /** Try to restore a FileSystemFileHandle from IndexedDB and re-request write permission.
   *  Called on startup after WIP project is restored from localStorage. */
  private def restoreHandleFromIndexedDb(): Unit =
    try
      val req = dom.window.asInstanceOf[js.Dynamic].indexedDB.open(IDB_DB_NAME, 1)
      req.asInstanceOf[js.Dynamic].onupgradeneeded = { (e: js.Dynamic) =>
        val db = e.target.result.asInstanceOf[js.Dynamic]
        db.createObjectStore(IDB_STORE_NAME)
      }: js.Function1[js.Dynamic, Unit]
      req.asInstanceOf[js.Dynamic].onsuccess = { (e: js.Dynamic) =>
        val db = e.target.result.asInstanceOf[js.Dynamic]
        val tx = db.transaction(IDB_STORE_NAME, "readonly")
        val getReq = tx.objectStore(IDB_STORE_NAME).get(IDB_HANDLE_KEY)
        val nameReq = tx.objectStore(IDB_STORE_NAME).get(IDB_NAME_KEY)
        getReq.asInstanceOf[js.Dynamic].onsuccess = { (ge: js.Dynamic) =>
          val handle = ge.target.result
          nameReq.asInstanceOf[js.Dynamic].onsuccess = { (ne: js.Dynamic) =>
            val storedName = ne.target.result
            val name = if !js.isUndefined(storedName) && storedName != null
                       then storedName.toString
                       else localFileName.now().getOrElse("Projekt")
            if !js.isUndefined(handle) && handle != null then
              if js.typeOf(handle.asInstanceOf[js.Dynamic].requestPermission) == "function" then
                val h = handle.asInstanceOf[js.Dynamic]
                val onPerm: js.Function1[js.Any, Unit] = result =>
                  if result.toString == "granted" then
                    localFileHandle.set(Some(h))
                    localFileName.set(Some(name))
                    dom.console.log(s"✅ Dateihandle aus IndexedDB wiederhergestellt: $name")
                  else
                    dom.console.log("⚠️ Schreibberechtigung verweigert – Auto-Save deaktiviert")
                h.requestPermission(js.Dynamic.literal(mode = "readwrite"))
                  .asInstanceOf[js.Dynamic].`then`(onPerm)
          }: js.Function1[js.Dynamic, Unit]
        }: js.Function1[js.Dynamic, Unit]
      }: js.Function1[js.Dynamic, Unit]
      req.asInstanceOf[js.Dynamic].onerror = { (_: js.Dynamic) =>
        dom.console.warn("IndexedDB open failed – Auto-Save nicht wiederherstellbar")
      }: js.Function1[js.Dynamic, Unit]
    catch case ex: Exception =>
      dom.console.warn(s"IndexedDB restore failed: ${ex.getMessage}")

  /** Restore the last session from localStorage (WIP project) and IndexedDB (file handle).
   *  Called once at application startup. Returns true if a project was restored. */
  def restoreSession(): Boolean =
    val wipJson  = dom.window.localStorage.getItem(WIP_KEY)
    val wipFile  = dom.window.localStorage.getItem(WIP_FILE_KEY)
    if wipJson != null && wipFile != null then
      import io.circe.parser.decode
      import pme123.geak4s.domain.JsonCodecs.given
      decode[pme123.geak4s.domain.GeakProject](wipJson) match
        case Right(project) =>
          dom.console.log(s"🔄 WIP-Projekt aus localStorage geladen: $wipFile")
          loadProjectData(project, wipFile)
          // Separately try to restore the file handle (shows browser permission prompt)
          restoreHandleFromIndexedDb()
          true
        case Left(err) =>
          dom.console.warn(s"WIP konnte nicht geladen werden: ${err.getMessage}")
          clearWip()
          false
    else false
  
  /** Navigation helpers */
  def navigateToWelcome(): Unit = currentView.set(View.Welcome)
  def navigateToProjectEditor(): Unit = currentView.set(View.ProjectEditor)
  def navigateToWorkflowEditor(): Unit = currentView.set(View.WorkflowEditor)
  
  /** Project management */
  def createNewProject(): Unit =
    clearWip()
    clearLocalFileHandle()
    UndoState.clear()
    UWertState.clear()
    AreaState.clear()
    EbfState.clear()
    EnergyState.clear()
    WaermebrueckeState.clear()
    pme123.geak4s.views.WordFormView.formVar.set(pme123.geak4s.domain.project.WordFormData())
    syncInitialized.set(false)
    val emptyProject = GeakProject.empty
    projectState.set(ProjectState.Loaded(emptyProject, "geak_newproject.xlsx"))
    navigateToWorkflowEditor()  // Use workflow editor by default
    // Don't auto-connect for new projects - wait until project name is set

  def createExampleProject(): Unit =
    clearLocalFileHandle()
    val exampleProject = GeakProject.example
    projectState.set(ProjectState.Loaded(exampleProject, "geak_example.xlsx"))
    navigateToWorkflowEditor()  // Use workflow editor by default
    // Auto-connect for example projects since they already have a name
    autoConnectToGoogleDrive()

  private def loadProjectData(project: GeakProject, fileName: String): Unit =
    UndoState.clear()
    projectState.set(ProjectState.Loaded(project, fileName))
    UWertState.loadFromProject(project)
    AreaState.loadFromProject(project)
    WaermebrueckeState.loadFromProject(project)
    EbfState.loadFromProject(project)
    EnergyState.loadFromProject(project)
    pme123.geak4s.views.WordFormView.formVar.set(
      project.wordFormData.getOrElse(pme123.geak4s.domain.project.WordFormData())
    )
    syncInitialized.set(true)
    autoConnectToGoogleDrive()

  def loadProject(project: GeakProject, fileName: String): Unit =
    loadProjectData(project, fileName)
    navigateToWorkflowEditor()
  
  def setLoading(fileName: String): Unit =
    projectState.set(ProjectState.Loading(fileName))
  
  def setError(message: String): Unit =
    projectState.set(ProjectState.Error(message))
  
  def clearProject(): Unit =
    clearWip()
    projectState.set(ProjectState.NoProject)
    UWertState.clear()
    AreaState.clear()
    EbfState.clear()
    EnergyState.clear()
    WaermebrueckeState.clear()
    stopPeriodicSync()
    syncInitialized.set(false)
    clearLocalFileHandle()
    // Don't clear projectsWithFolderStructure - keep track across sessions
    navigateToWelcome()
  
  /** Get current project if loaded */
  def getCurrentProject: Option[GeakProject] =
    projectState.now() match
      case ProjectState.Loaded(project, _) => Some(project)
      case _ => None
  
  /** Update current project (with auto-save trigger).
   *  Pushes the current snapshot to UndoState before applying the change. */
  def updateProject(updater: GeakProject => GeakProject): Unit =
    projectState.now() match
      case ProjectState.Loaded(project, fileName) =>
        println(s"Updating project: ${project.project.projectName} - $fileName")
        UndoState.push(project)   // snapshot before change
        val updated = updater(project)
        projectState.set(ProjectState.Loaded(updated, fileName))
        saveWip(updated, fileName)
        triggerAutoSave()
      case _ => // ignore if no project loaded

  /** Undo the last change (Ctrl+Z / Cmd+Z).
   *  Restores the previous GeakProject snapshot and re-syncs all sub-states.
   *  Bypasses updateProject intentionally so the restore is not added to the undo stack. */
  def undo(): Unit =
    UndoState.pop().foreach { prev =>
      projectState.now() match
        case ProjectState.Loaded(_, fileName) =>
          // Set state directly — no side effects inside Var.update, no undo-stack push
          projectState.set(ProjectState.Loaded(prev, fileName))
          saveWip(prev, fileName)
          triggerAutoSave()
          // Re-sync sub-states from the restored project
          UWertState.loadFromProject(prev)
          AreaState.loadFromProject(prev)
          WaermebrueckeState.loadFromProject(prev)
          EnergyState.loadFromProject(prev)
        case _ => ()
    }

  /** Enrich project with EBF plan images before export/file-write */
  def enrichProjectWithImages(project: GeakProject): GeakProject =
    val jsImages: js.Dictionary[String] =
      val fn = dom.window.asInstanceOf[js.Dynamic].getEbfPlanImages
      if !js.isUndefined(fn) && fn != null then fn().asInstanceOf[js.Dictionary[String]]
      else js.Dictionary.empty[String]
    project.copy(
      ebfPlans = project.ebfPlans.map { plans =>
        plans.copy(plans = plans.plans.map { plan =>
          if plan.imageDataUrl.isDefined then plan
          else
            val img = jsImages.get(plan.id)
              .orElse(Option(dom.window.localStorage.getItem(s"ebf_plan_image_${plan.id}")))
              .filter(_.nonEmpty)
            plan.copy(imageDataUrl = img)
        })
      }
    )

  /** Write JSON string directly to the stored FileSystemFileHandle (no dialog).
   *  Uses raw js.Function1 callbacks to avoid any js.Thenable / Future conversion issues. */
  private def writeToLocalFile(jsonStr: String): Unit =
    localFileHandle.now().foreach { handle =>
      val onError: js.Function1[js.Any, Unit] = err =>
        val msg = err.asInstanceOf[js.Dynamic].message.toString
        dom.console.error("Lokales Speichern fehlgeschlagen:", err)
        // Stale or invalid handle (e.g. file was moved) — surface error in UI
        val hint =
          if msg.contains("NotFound") || msg.contains("not found") then
            "Datei nicht gefunden – sie wurde möglicherweise verschoben. Bitte Datei neu öffnen."
          else if msg.contains("NotAllowed") || msg.contains("not allowed") then
            "Schreibberechtigung fehlt. Bitte Seite neu laden und Berechtigung erteilen."
          else
            s"Speichern fehlgeschlagen: $msg"
        localSaveError.set(Some(hint))
        // Clear stale handle so future changes don't silently fail
        localFileHandle.set(None)
        localFileName.set(None)

      val onCreate: js.Function1[js.Any, Unit] = writable =>
        val w = writable.asInstanceOf[js.Dynamic]
        // close() is async — must wait for it before reporting success
        val onClosed: js.Function1[js.Any, Unit] = _ =>
          localSaveError.set(None)
          localSaveSuccess.set(true)
          successClearTimer.foreach(dom.window.clearTimeout)
          val t = dom.window.setTimeout(() => localSaveSuccess.set(false), 2000)
          successClearTimer = Some(t)
          dom.console.log(s"💾 Gespeichert: ${localFileName.now().getOrElse("")}")
        val onWritten: js.Function1[js.Any, Unit] = _ =>
          w.close().asInstanceOf[js.Dynamic].`then`(onClosed, onError)
        w.write(jsonStr).asInstanceOf[js.Dynamic].`then`(onWritten, onError)

      handle.createWritable().asInstanceOf[js.Dynamic].`then`(onCreate, onError)
    }

  /** Immediately write the project to the local file (no debounce). */
  def saveNow(project: GeakProject): Unit =
    if localFileHandle.now().isDefined then
      val enriched = enrichProjectWithImages(project)
      writeToLocalFile(enriched.asJson.noSpaces)
    else
      dom.console.warn("saveNow: kein Dateihandle gesetzt")

  /** Schedule a debounced local file save (3 s after last change) */
  private def scheduleLocalFileSave(project: GeakProject): Unit =
    if localFileHandle.now().isEmpty then return
    localFileSaveTimer.foreach(dom.window.clearTimeout)
    val timerId = dom.window.setTimeout(() => {
      val enriched = enrichProjectWithImages(project)
      writeToLocalFile(enriched.asJson.noSpaces)
    }, LOCAL_SAVE_DELAY_MS)
    localFileSaveTimer = Some(timerId)

  /** Persist current project to localStorage so it survives a page reload.
    * Images are stripped before serialisation — they are already stored individually
    * under ebf_plan_image_<id> keys and would otherwise overflow the 5 MB quota.
    */
  private def saveWip(project: GeakProject, fileName: String): Unit =
    val stripped = project.copy(
      ebfPlans = project.ebfPlans.map(plans =>
        plans.copy(plans = plans.plans.map(_.copy(imageDataUrl = None)))
      )
    )
    try
      dom.window.localStorage.setItem(WIP_KEY,      stripped.asJson.noSpaces)
      dom.window.localStorage.setItem(WIP_FILE_KEY, fileName)
      scheduleLocalFileSave(project) // also auto-save to local file if handle is set (with images)
    catch case ex: Exception =>
      dom.console.error(s"WIP speichern fehlgeschlagen: ${ex.getMessage}")

  /** Remove the work-in-progress snapshot from localStorage. */
  def clearWip(): Unit =
    dom.window.localStorage.removeItem(WIP_KEY)
    dom.window.localStorage.removeItem(WIP_FILE_KEY)

  /** Save U-Wert calculations to current project */
  def saveUWertCalculations(): Unit =
    updateProject(project => UWertState.saveToProject(project))

  /** Save area calculations to current project */
  def saveAreaCalculations(): Unit =
    updateProject(project => AreaState.saveToProject(project))

  /** Save Wärmebrücken to current project */
  def saveWaermebruecken(): Unit =
    updateProject(project => WaermebrueckeState.saveToProject(project))

  /** Save EBF plans to current project */
  def saveEbfPlans(): Unit =
    updateProject(project => EbfState.saveToProject(project))

  /** Save energy consumption data to current project */
  def saveEnergyData(): Unit =
    updateProject(project => EnergyState.saveToProject(project))

  /** Pure helper: apply GIS data to a project and return the updated project */
  def saveGisDataToProject(project: GeakProject, gisData: pme123.geak4s.domain.gis.MaddResponse): GeakProject =
    val filledProject = fillProjectFromGis(project, gisData)
    filledProject.copy(gisData = Some(gisData))

  /** Save GIS data to current project and autofill project fields when empty */
  def saveGisData(gisData: pme123.geak4s.domain.gis.MaddResponse): Unit =
    updateProject(project => saveGisDataToProject(project, gisData))

  /** Fill project fields from extracted GIS data without overriding existing input when present */
  private def fillProjectFromGis(project: GeakProject, gisData: pme123.geak4s.domain.gis.MaddResponse): GeakProject =
    val firstBuildingOpt = gisData.buildingList.headOption
    val firstEntranceOpt = firstBuildingOpt.flatMap(_.buildingEntranceList.headOption)

    def preferExistingString(existing: Option[String], extracted: Option[String]): Option[String] =
      if existing.exists(_.trim.nonEmpty) then existing
      else extracted

    def preferExisting[T](existing: Option[T], extracted: Option[T]): Option[T] =
      existing.orElse(extracted)

    val extractedAddress = firstEntranceOpt.map { entrance =>
      Address(
        street = Some(entrance.buildingEntrance.street.streetName.descriptionLong).filter(_.trim.nonEmpty),
        houseNumber = Some(entrance.buildingEntrance.buildingEntranceNo).filter(_.trim.nonEmpty),
        zipCode = Some(entrance.buildingEntrance.locality.swissZipCode).filter(_.trim.nonEmpty),
        city = Some(entrance.buildingEntrance.locality.placeName).filter(_.trim.nonEmpty),
        country = Some("Schweiz"),
        lat = Some(entrance.buildingEntrance.coordinates.east),
        lon = Some(entrance.buildingEntrance.coordinates.north)
      )
    }

    val newAddress = if project.project.buildingLocation.address == Address.empty then extractedAddress.getOrElse(project.project.buildingLocation.address) else project.project.buildingLocation.address

    val firstBuilding = firstBuildingOpt

    val newBuildingLocation = project.project.buildingLocation.copy(
      address = newAddress,
      municipality = preferExisting(project.project.buildingLocation.municipality, firstBuilding.map(_.municipality.municipalityName)),
      buildingName = preferExisting(project.project.buildingLocation.buildingName, firstBuilding.flatMap(_.building.buildingClass)),
      parcelNumber = preferExisting(project.project.buildingLocation.parcelNumber, firstBuilding.flatMap(_.realestateIdentificationList.headOption.map(_.number)))
    )

    val buildingData = project.project.buildingData
    val newBuildingData = buildingData.copy(
      constructionYear = buildingData.constructionYear.orElse(firstBuilding.flatMap(_.building.dateOfConstruction.flatMap(_.dateOfConstruction).flatMap(_.toIntOption))),
      numberOfFloors = buildingData.numberOfFloors.orElse(firstBuilding.flatMap(_.building.numberOfFloors)),
      energyReferenceArea = buildingData.energyReferenceArea.orElse(firstBuilding.flatMap(_.building.surfaceAreaOfBuilding).map(_.toDouble))
    )

    val egid = firstBuilding.flatMap(b => Some(b.egid).filter(_.nonEmpty))
    val edid = firstEntranceOpt.map(_.edid).filter(_.nonEmpty)
    val egidEntryAddress = extractedAddress.getOrElse(Address.empty)

    val currentEntries = project.project.egidEdidGroup.entries
    val newEgidEntries = if currentEntries.isEmpty then
      List(EgidEdidEntry(egid = egid, edid = edid, address = egidEntryAddress))
    else
      currentEntries.zipWithIndex.map { (entry, idx) =>
        if idx == 0 then
          entry.copy(
            egid = preferExisting(entry.egid, egid),
            edid = preferExisting(entry.edid, edid),
            address = if entry.address == Address.empty then egidEntryAddress else entry.address
          )
        else entry
      }

    project.copy(
      project = project.project.copy(
        projectName = project.project.projectName,
        buildingLocation = newBuildingLocation,
        buildingData = newBuildingData,
        egidEdidGroup = project.project.egidEdidGroup.copy(entries = newEgidEntries)
      )
    )

  /** Initialize Google Drive integration */
  def initializeGoogleDrive(): Unit =
    GoogleDriveService.initialize()
    if GoogleDriveService.isSignedIn then
      driveConnected.set(true)

  /** Sign in to Google Drive */
  def signInToGoogleDrive(): Unit =
    GoogleDriveService.signIn().foreach { success =>
      driveConnected.set(success)
      if success then
        dom.console.log("Successfully connected to Google Drive")
        // Mark sync as initialized when manually connecting
        if getCurrentProject.isDefined && !syncInitialized.now() then
          syncInitialized.set(true)
        // Trigger initial save if project is loaded
        triggerAutoSave()
        // Start periodic sync
        startPeriodicSync()
    }

  /** Create project folder structure if not already created */
  private def ensureProjectFolderStructure(): Unit =
    getCurrentProject.foreach { project =>
      val projectName = project.project.projectName
      dom.console.log(s"🔍 ensureProjectFolderStructure called for: '$projectName'")
      dom.console.log(s"🔍 Already created projects: ${projectsWithFolderStructure.mkString(", ")}")

      if projectName.trim.nonEmpty && !projectsWithFolderStructure.contains(projectName) then
        dom.console.log(s"📁 Creating folder structure for project: $projectName")
        // Add to set IMMEDIATELY to prevent race conditions
        projectsWithFolderStructure = projectsWithFolderStructure + projectName
        dom.console.log(s"🔍 Updated tracking set: ${projectsWithFolderStructure.mkString(", ")}")

        GoogleDriveService.createProjectFolderStructure(projectName).foreach { folderSuccess =>
          if folderSuccess then
            dom.console.log("✅ Project folder structure created successfully")
          else
            dom.console.error("❌ Failed to create some project folders")
            // Remove from set if creation failed so it can be retried
            projectsWithFolderStructure = projectsWithFolderStructure - projectName
        }
      else if projectsWithFolderStructure.contains(projectName) then
        dom.console.log(s"⏭️  Folder structure already created for project: $projectName - skipping")
      else
        dom.console.log(s"⚠️  Project name is empty - skipping folder creation")
    }

  /** Auto-connect to Google Drive when creating or loading a project */
  private def autoConnectToGoogleDrive(): Unit =
    // Check if Google Drive is configured
    if !GoogleDriveService.isConfigured then
      dom.console.warn("Google Drive is not configured - skipping auto-connect")
      return

    // If already connected, just initialize sync
    if driveConnected.now() then
      dom.console.log("Already connected to Google Drive - initializing sync")
      if !syncInitialized.now() then
        syncInitialized.set(true)

      // Create project folder structure if needed
      ensureProjectFolderStructure()

      startPeriodicSync()
      return

    // Auto-connect to Google Drive
    dom.console.log("Auto-connecting to Google Drive...")
    GoogleDriveService.signIn().foreach { success =>
      driveConnected.set(success)
      if success then
        dom.console.log("Successfully auto-connected to Google Drive")
        // Mark sync as initialized for auto-connected projects
        syncInitialized.set(true)

        // Create project folder structure if needed
        ensureProjectFolderStructure()

        // Trigger initial save
        triggerAutoSave()
        // Start periodic sync
        startPeriodicSync()
      else
        dom.console.warn("Failed to auto-connect to Google Drive")
    }

  /** Sign out from Google Drive */
  def signOutFromGoogleDrive(): Unit =
    GoogleDriveService.signOut()
    driveConnected.set(false)
    lastSyncTime.set(None)
    stopPeriodicSync()

  /** Manually sync project to Google Drive (will prompt for login if needed) */
  def syncToGoogleDrive(): Unit =
    getCurrentProject match
      case Some(project) =>
        // Check if Google Drive is configured
        if !GoogleDriveService.isConfigured then
          driveError.set(Some("Google Drive ist nicht konfiguriert. Bitte aktualisieren Sie GoogleDriveConfig.scala mit Ihrer Client ID."))
          dom.console.warn("Google Drive is not configured. See documentation for setup instructions.")
          return

        driveSyncing.set(true)
        driveError.set(None)

        // Show login prompt if not connected
        if !GoogleDriveService.isSignedIn then
          driveLoginPrompt.set(true)

        val projectName = project.project.projectName match
          case name if name.nonEmpty => name
          case _ => "Unnamed_Project"

        // saveProjectState will automatically prompt for login if not signed in
        GoogleDriveService.saveProjectState(project, projectName).foreach { success =>
          driveSyncing.set(false)
          driveLoginPrompt.set(false)
          if success then
            // Update connection status if login was successful
            if !driveConnected.now() then
              driveConnected.set(true)
            lastSyncTime.set(Some(System.currentTimeMillis()))
            driveError.set(None)
            dom.console.log("Project synced to Google Drive")
          else
            if !GoogleDriveService.isConfigured then
              driveError.set(Some("Google Drive ist nicht konfiguriert"))
            else
              driveError.set(Some("Synchronisierung fehlgeschlagen"))
            dom.console.error("Failed to sync project to Google Drive")
        }
      case None =>
        dom.console.warn("No project loaded to sync")

  /** Trigger auto-save with debouncing */
  private def triggerAutoSave(): Unit =
    // Only auto-save if sync has been initialized
    if !syncInitialized.now() || !autoSaveEnabled.now() || !driveConnected.now() then
      return

    // Cancel existing timer
    autoSaveTimer.foreach(dom.window.clearTimeout)

    // Set new timer
    val timerId = dom.window.setTimeout(() => {
      syncToGoogleDrive()
    }, AUTO_SAVE_DELAY_MS)

    autoSaveTimer = Some(timerId)

  /** Toggle auto-save */
  def toggleAutoSave(): Unit =
    autoSaveEnabled.update(!_)
    if autoSaveEnabled.now() then
      triggerAutoSave()

  /** Initialize sync for a new project - connects to Google Drive and starts auto-save and periodic sync */
  def initializeSync(): Unit =
    // Check if project name is set
    getCurrentProject match
      case Some(project) if project.project.projectName.trim.isEmpty =>
        driveError.set(Some("Bitte geben Sie zuerst eine Projektbezeichnung ein."))
        dom.console.warn("Cannot initialize sync: project name is empty")
        return
      case None =>
        driveError.set(Some("Kein Projekt geladen."))
        dom.console.warn("Cannot initialize sync: no project loaded")
        return
      case _ => // Project name is set, continue

    dom.console.log("Initializing sync for project - connecting to Google Drive")
    syncInitialized.set(true)
    // Connect to Google Drive (folder structure will be created after successful sign-in)
    autoConnectToGoogleDrive()

  /** Start periodic sync (every 30 seconds) */
  private def startPeriodicSync(): Unit =
    // Only start if sync is initialized, connected and project is loaded
    if !syncInitialized.now() || !driveConnected.now() || getCurrentProject.isEmpty then
      return

    // Stop any existing timer
    stopPeriodicSync()

    // Set up periodic sync
    val timerId = dom.window.setInterval(() => {
      if driveConnected.now() && getCurrentProject.isDefined then
        syncToGoogleDrive()
    }, PERIODIC_SYNC_INTERVAL_MS)

    periodicSyncTimer = Some(timerId)
    dom.console.log("Started periodic sync (every 30 seconds)")

  /** Stop periodic sync */
  private def stopPeriodicSync(): Unit =
    periodicSyncTimer.foreach { timerId =>
      dom.window.clearInterval(timerId)
      dom.console.log("Stopped periodic sync")
    }
    periodicSyncTimer = None

end AppState

