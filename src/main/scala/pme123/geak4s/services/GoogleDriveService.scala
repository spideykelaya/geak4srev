package pme123.geak4s.services

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import scala.scalajs.js.timers
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import pme123.geak4s.config.GoogleDriveConfig
import pme123.geak4s.domain.GeakProject
import pme123.geak4s.domain.JsonCodecs.given
import io.circe.syntax.*
import io.circe.parser.*

/** Service for Google Drive integration using Google Drive API v3
  *
  * Features:
  *   - Authentication via Google OAuth 2.0
  *   - Create folders in Google Drive
  *   - Upload files (Excel, JSON state)
  *   - Download files
  *   - Persist project state automatically
  */
object GoogleDriveService:

  private var gapiInited              = false
  private var gisInited               = false
  private var tokenClient: js.Dynamic = null
  private var accessToken: String     = ""

  // LocalStorage keys
  private val STORAGE_KEY_ACCESS_TOKEN = "geak4s_google_drive_access_token"
  private val STORAGE_KEY_TOKEN_EXPIRY = "geak4s_google_drive_token_expiry"

  /** Initialize Google Drive API Call this once when the app starts
    */
  def initialize(): Unit =
    dom.console.log("=== Google Drive initialize() called ===")
    dom.console.log(s"Google Drive configured: ${GoogleDriveConfig.isConfigured}")

    if !GoogleDriveConfig.isConfigured then
      dom.console.warn("⚠️ Google Drive is not configured. Please check .env file")
      return

    try
      // Wait for Google API libraries to load
      waitForGoogleAPIs()
    catch
      case ex: Exception =>
        dom.console.error(s"❌ Failed to initialize Google Drive service: ${ex.getMessage}")
    end try
  end initialize

  /** Save access token to localStorage */
  private def saveTokenToStorage(token: String, expiresIn: Int = 3600): Unit =
    try
      val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)
      dom.window.localStorage.setItem(STORAGE_KEY_ACCESS_TOKEN, token)
      dom.window.localStorage.setItem(STORAGE_KEY_TOKEN_EXPIRY, expiryTime.toString)
      dom.console.log(s"✅ Access token saved to localStorage (expires in ${expiresIn}s)")
    catch
      case ex: Exception =>
        dom.console.error(s"Failed to save token to localStorage: ${ex.getMessage}")

  /** Load access token from localStorage */
  private def loadTokenFromStorage(): Option[String] =
    try
      val token     = dom.window.localStorage.getItem(STORAGE_KEY_ACCESS_TOKEN)
      val expiryStr = dom.window.localStorage.getItem(STORAGE_KEY_TOKEN_EXPIRY)

      if token != null && expiryStr != null then
        val expiryTime = expiryStr.toLong
        val now        = System.currentTimeMillis()

        if now < expiryTime then
          dom.console.log("✅ Valid access token found in localStorage")
          Some(token)
        else
          dom.console.log("⚠️ Access token in localStorage has expired")
          clearTokenFromStorage()
          None
        end if
      else
        None
      end if
    catch
      case ex: Exception =>
        dom.console.error(s"Failed to load token from localStorage: ${ex.getMessage}")
        None

  /** Clear access token from localStorage */
  private def clearTokenFromStorage(): Unit =
    try
      dom.window.localStorage.removeItem(STORAGE_KEY_ACCESS_TOKEN)
      dom.window.localStorage.removeItem(STORAGE_KEY_TOKEN_EXPIRY)
      dom.console.log("🗑️ Access token cleared from localStorage")
    catch
      case ex: Exception =>
        dom.console.error(s"Failed to clear token from localStorage: ${ex.getMessage}")

  /** Wait for Google API libraries to load, then initialize
    */
  private def waitForGoogleAPIs(): Unit =
    val checkInterval = 100 // ms
    var attempts      = 0
    val maxAttempts   = 50  // 5 seconds total

    var intervalId: js.timers.SetIntervalHandle = null
    intervalId = js.timers.setInterval(checkInterval) {
      attempts += 1

      val gapi   = js.Dynamic.global.gapi
      val google = js.Dynamic.global.google

      if !js.isUndefined(gapi) && !js.isUndefined(google) then
        js.timers.clearInterval(intervalId)
        dom.console.log("✅ Google API libraries loaded")
        initializeGapi()
        initializeGis()
      else if attempts >= maxAttempts then
        js.timers.clearInterval(intervalId)
        dom.console.error("❌ Timeout waiting for Google API libraries to load")
      end if
    }
  end waitForGoogleAPIs

  /** Initialize Google API client (gapi)
    */
  private def initializeGapi(): Unit =
    val gapi = js.Dynamic.global.gapi

    dom.console.log("🔧 Initializing Google API client...")

    gapi.load(
      "client",
      { () =>
        // Note: We don't use apiKey with the new GIS approach
        // The access token from GIS is used instead
        gapi.client.init(js.Dynamic.literal(
          discoveryDocs = js.Array("https://www.googleapis.com/discovery/v1/apis/drive/v3/rest")
        )).`then`(
          { () =>
            dom.console.log("✅ Google API client initialized")
            gapiInited = true

            // Try to restore token from localStorage
            loadTokenFromStorage().foreach { token =>
              accessToken = token
              gapi.client.setToken(js.Dynamic.literal(access_token = token))
              dom.console.log("✅ Restored access token from localStorage")
            }
          }: js.Function0[Unit],
          { (error: js.Dynamic) =>
            dom.console.error("❌ Failed to initialize Google API client:")
            dom.console.error(error)
          }: js.Function1[js.Dynamic, Unit]
        )
      }: js.Function0[Unit]
    )
  end initializeGapi

  /** Initialize Google Identity Services (GIS)
    */
  private def initializeGis(): Unit =
    val google = js.Dynamic.global.google

    dom.console.log("🔧 Initializing Google Identity Services...")
    dom.console.log(s"Scopes: ${GoogleDriveConfig.scopes}")

    tokenClient = google.accounts.oauth2.initTokenClient(js.Dynamic.literal(
      client_id = GoogleDriveConfig.clientId,
      scope = GoogleDriveConfig.scopes,
      callback = { (response: js.Dynamic) =>
        if !js.isUndefined(response.error) then
          dom.console.error(s"❌ Token error: ${response.error}")
        else
          accessToken = response.access_token.toString

          // Save token to localStorage
          val expiresIn = if !js.isUndefined(response.expires_in) then
            response.expires_in.toString.toInt
          else
            3600 // Default to 1 hour
          saveTokenToStorage(accessToken, expiresIn)

          dom.console.log("✅ Access token received")
      }: js.Function1[js.Dynamic, Unit]
    ))

    gisInited = true
    dom.console.log("✅ Google Identity Services initialized")
  end initializeGis

  /** Sign in to Google Drive
    */
  def signIn(): Future[Boolean] =
    dom.console.log("=== Google Drive signIn() called ===")
    dom.console.log(s"isConfigured: ${GoogleDriveConfig.isConfigured}")
    dom.console.log(s"gapiInited: $gapiInited, gisInited: $gisInited")

    if !GoogleDriveConfig.isConfigured then
      dom.console.error("Cannot sign in - Google Drive is not configured!")
      dom.console.error("Please check your .env file")
      return Future.successful(false)
    end if

    if !gapiInited || !gisInited then
      dom.console.error("Cannot sign in - Google Drive not initialized!")
      return Future.successful(false)

    try
      dom.console.log("Opening Google sign-in popup...")

      val promise = Promise[Boolean]()

      // Set callback for this specific sign-in request
      tokenClient.callback = { (response: js.Dynamic) =>
        if !js.isUndefined(response.error) then
          dom.console.error(s"❌ Sign in failed: ${response.error}")
          promise.success(false)
        else
          accessToken = response.access_token.toString

          // Save token to localStorage
          val expiresIn = if !js.isUndefined(response.expires_in) then
            response.expires_in.toString.toInt
          else
            3600 // Default to 1 hour
          saveTokenToStorage(accessToken, expiresIn)

          dom.console.log("✅ Signed in to Google Drive")

          // Set the access token for gapi client
          val gapi = js.Dynamic.global.gapi
          gapi.client.setToken(js.Dynamic.literal(access_token = accessToken))

          promise.success(true)
      }: js.Function1[js.Dynamic, Unit]

      // Request access token
      val gapi = js.Dynamic.global.gapi
      if js.isUndefined(gapi.client.getToken()) || gapi.client.getToken() == null then
        // Prompt for consent
        tokenClient.requestAccessToken(js.Dynamic.literal(prompt = "consent"))
      else
        // Skip consent if already granted
        tokenClient.requestAccessToken(js.Dynamic.literal(prompt = ""))
      end if

      promise.future
    catch
      case ex: Exception =>
        dom.console.error(s"❌ Sign in error: ${ex.getMessage}")
        Future.successful(false)
    end try
  end signIn

  /** Sign out from Google Drive
    */
  def signOut(): Unit =
    val gapi  = js.Dynamic.global.gapi
    val token = gapi.client.getToken()

    if !js.isUndefined(token) && token != null then
      val google = js.Dynamic.global.google
      google.accounts.oauth2.revoke(token.access_token)
      gapi.client.setToken(null)
      accessToken = ""

      // Clear token from localStorage
      clearTokenFromStorage()

      dom.console.log("✅ Signed out from Google Drive")
    end if
  end signOut

  /** Check if user is signed in
    */
  def isSignedIn: Boolean =
    val gapi = js.Dynamic.global.gapi
    if js.isUndefined(gapi) || js.isUndefined(gapi.client) then
      false
    else
      val token = gapi.client.getToken()
      !js.isUndefined(token) && token != null && accessToken.nonEmpty
    end if
  end isSignedIn

  /** Check if Google Drive is configured
    */
  def isConfigured: Boolean = GoogleDriveConfig.isConfigured

  /** Get current user's display name or email Note: With the new GIS API, we don't have direct
    * access to user profile You would need to use the People API or userinfo endpoint for this
    */
  def getCurrentUserName: Option[String] =
    if !isSignedIn then None
    else Some("Google Drive User") // Simplified for now

  /** Find or create a folder by path
    * @param folderPath
    *   Path like "folder1/folder2/folder3"
    * @return
    *   Future with folder ID
    */
  def findOrCreateFolder(folderPath: String): Future[Option[String]] =
    if folderPath.isEmpty then
      Future.successful(Some("root"))
    else
      val segments = folderPath.split("/").filter(_.nonEmpty)
      findOrCreateFolderRecursive(segments.toList, "root")

  /** Recursively find or create folders
    */
  private def findOrCreateFolderRecursive(
      segments: List[String],
      parentId: String
  ): Future[Option[String]] =
    segments match
    case Nil                => Future.successful(Some(parentId))
    case folderName :: rest =>
      findFolder(folderName, parentId).flatMap {
        case Some(folderId) =>
          // Folder exists, continue with next segment
          findOrCreateFolderRecursive(rest, folderId)
        case None           =>
          // Folder doesn't exist, create it
          createFolder(folderName, parentId).flatMap {
            case Some(folderId) =>
              findOrCreateFolderRecursive(rest, folderId)
            case None           =>
              Future.successful(None)
          }
      }

  /** Find a folder by name in a parent folder
    */
  private def findFolder(folderName: String, parentId: String): Future[Option[String]] =
    val promise = Promise[Option[String]]()

    try
      val gapi  = js.Dynamic.global.gapi
      val query =
        s"name='$folderName' and '$parentId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"

      gapi.client.drive.files.list(js.Dynamic.literal(
        q = query,
        fields = "files(id, name)",
        spaces = "drive"
      )).`then`(
        { (response: js.Dynamic) =>
          val files = response.result.files.asInstanceOf[js.Array[js.Dynamic]]
          if files.length > 0 then
            promise.success(Some(files(0).id.toString))
          else
            promise.success(None)
        }: js.Function1[js.Dynamic, Unit],
        { (error: js.Dynamic) =>
          dom.console.error(s"Error finding folder: ${error.result.error.message}")
          promise.success(None)
        }: js.Function1[js.Dynamic, Unit]
      )
    catch
      case ex: Exception =>
        dom.console.error(s"Error finding folder: ${ex.getMessage}")
        promise.success(None)
    end try

    promise.future
  end findFolder

  /** Create a folder in Google Drive
    */
  private def createFolder(folderName: String, parentId: String): Future[Option[String]] =
    val promise = Promise[Option[String]]()

    try
      val gapi = js.Dynamic.global.gapi

      val fileMetadata = js.Dynamic.literal(
        name = folderName,
        mimeType = "application/vnd.google-apps.folder",
        parents = js.Array(parentId)
      )

      gapi.client.drive.files.create(js.Dynamic.literal(
        resource = fileMetadata,
        fields = "id"
      )).`then`(
        { (response: js.Dynamic) =>
          val folderId = response.result.id.toString
          dom.console.log(s"✅ Created folder: $folderName (ID: $folderId)")
          promise.success(Some(folderId))
        }: js.Function1[js.Dynamic, Unit],
        { (error: js.Dynamic) =>
          dom.console.error(s"Error creating folder: ${error.result.error.message}")
          promise.success(None)
        }: js.Function1[js.Dynamic, Unit]
      )
    catch
      case ex: Exception =>
        dom.console.error(s"Error creating folder: ${ex.getMessage}")
        promise.success(None)
    end try

    promise.future
  end createFolder

  /** Upload a file to Google Drive
    * @param folderPath
    *   Folder path (e.g., "GEAK_Projects/Project1")
    * @param fileName
    *   File name
    * @param content
    *   File content as ArrayBuffer
    * @param mimeType
    *   MIME type of the file
    * @return
    *   Future with upload result
    */
  def uploadFile(
      folderPath: String,
      fileName: String,
      content: js.typedarray.ArrayBuffer,
      mimeType: String
  ): Future[Boolean] =
    findOrCreateFolder(folderPath).flatMap {
      case Some(folderId) =>
        uploadFileToFolder(fileName, content, mimeType, folderId)
      case None           =>
        dom.console.error(s"Failed to find or create folder: $folderPath")
        Future.successful(false)
    }

  /** Find a file by name in a specific folder
    */
  private def findFile(fileName: String, folderId: String): Future[Option[String]] =
    val promise = Promise[Option[String]]()

    try
      val gapi  = js.Dynamic.global.gapi
      val query = s"name='$fileName' and '$folderId' in parents and trashed=false"

      gapi.client.drive.files.list(js.Dynamic.literal(
        q = query,
        fields = "files(id, name)",
        spaces = "drive"
      )).`then`(
        { (response: js.Dynamic) =>
          val files = response.result.files.asInstanceOf[js.Array[js.Dynamic]]
          if files.length > 0 then
            promise.success(Some(files(0).id.toString))
          else
            promise.success(None)
        }: js.Function1[js.Dynamic, Unit],
        { (error: js.Dynamic) =>
          dom.console.error(s"Error finding file: ${error.result.error.message}")
          promise.success(None)
        }: js.Function1[js.Dynamic, Unit]
      )
    catch
      case ex: Exception =>
        dom.console.error(s"Error finding file: ${ex.getMessage}")
        promise.success(None)
    end try

    promise.future
  end findFile

  /** Upload file to a specific folder (creates new or updates existing)
    */
  private def uploadFileToFolder(
      fileName: String,
      content: js.typedarray.ArrayBuffer,
      mimeType: String,
      folderId: String
  ): Future[Boolean] =
    // First check if file already exists
    findFile(fileName, folderId).flatMap {
      case Some(fileId) =>
        // File exists, update it
        updateExistingFile(fileId, fileName, content, mimeType)
      case None         =>
        // File doesn't exist, create new
        createNewFile(fileName, content, mimeType, folderId)
    }

  /** Create a new file in Google Drive
    */
  private def createNewFile(
      fileName: String,
      content: js.typedarray.ArrayBuffer,
      mimeType: String,
      folderId: String
  ): Future[Boolean] =
    val promise = Promise[Boolean]()

    try
      val gapi = js.Dynamic.global.gapi

      // Convert ArrayBuffer to base64
      val uint8Array = new js.typedarray.Uint8Array(content)
      val binary     = uint8Array.foldLeft("")((acc, byte) => acc + byte.toChar)
      val base64     = dom.window.btoa(binary)

      val fileMetadata = js.Dynamic.literal(
        name = fileName,
        parents = js.Array(folderId)
      )

      val multipartBody =
        s"""--boundary
           |Content-Type: application/json; charset=UTF-8
           |
           |${js.JSON.stringify(fileMetadata)}
           |--boundary
           |Content-Type: $mimeType
           |Content-Transfer-Encoding: base64
           |
           |$base64
           |--boundary--""".stripMargin

      val request = gapi.client.request(js.Dynamic.literal(
        path = "/upload/drive/v3/files",
        method = "POST",
        params = js.Dynamic.literal(uploadType = "multipart"),
        headers = js.Dynamic.literal(
          `Content-Type` = "multipart/related; boundary=boundary"
        ),
        body = multipartBody
      ))

      request.`then`(
        { (response: js.Dynamic) =>
          dom.console.log(s"✅ Created new file: $fileName")
          promise.success(true)
        }: js.Function1[js.Dynamic, Unit],
        { (error: js.Dynamic) =>
          dom.console.error(s"Error creating file: ${error.result.error.message}")
          promise.success(false)
        }: js.Function1[js.Dynamic, Unit]
      )
    catch
      case ex: Exception =>
        dom.console.error(s"Error creating file: ${ex.getMessage}")
        promise.success(false)
    end try

    promise.future
  end createNewFile

  /** Update an existing file in Google Drive
    */
  private def updateExistingFile(
      fileId: String,
      fileName: String,
      content: js.typedarray.ArrayBuffer,
      mimeType: String
  ): Future[Boolean] =
    val promise = Promise[Boolean]()

    try
      val gapi = js.Dynamic.global.gapi

      // Convert ArrayBuffer to base64
      val uint8Array = new js.typedarray.Uint8Array(content)
      val binary     = uint8Array.foldLeft("")((acc, byte) => acc + byte.toChar)
      val base64     = dom.window.btoa(binary)

      val multipartBody =
        s"""--boundary
           |Content-Type: application/json; charset=UTF-8
           |
           |{}
           |--boundary
           |Content-Type: $mimeType
           |Content-Transfer-Encoding: base64
           |
           |$base64
           |--boundary--""".stripMargin

      val request = gapi.client.request(js.Dynamic.literal(
        path = s"/upload/drive/v3/files/$fileId",
        method = "PATCH",
        params = js.Dynamic.literal(uploadType = "multipart"),
        headers = js.Dynamic.literal(
          `Content-Type` = "multipart/related; boundary=boundary"
        ),
        body = multipartBody
      ))

      request.`then`(
        { (response: js.Dynamic) =>
          dom.console.log(s"✅ Updated existing file: $fileName")
          promise.success(true)
        }: js.Function1[js.Dynamic, Unit],
        { (error: js.Dynamic) =>
          dom.console.error(s"Error updating file: ${error.result.error.message}")
          promise.success(false)
        }: js.Function1[js.Dynamic, Unit]
      )
    catch
      case ex: Exception =>
        dom.console.error(s"Error updating file: ${ex.getMessage}")
        promise.success(false)
    end try

    promise.future
  end updateExistingFile

  /** Create standard project folder structure Creates subdirectories: 08_Fotos, 07_Unterlagen,
    * 05_Gesuche, 04_Ausschreibung, 03_Berichte, 02_Berechnungen
    */
  def createProjectFolderStructure(projectName: String): Future[Boolean] =
    val sanitizedName = projectName.replaceAll("[^a-zA-Z0-9-_]", "_")
    val projectFolder = s"${GoogleDriveConfig.rootFolder}/$sanitizedName"

    val subfolders = List(
      "01_AN_AB_RE_Kor",
      "08_Fotos",
      "07_Unterlagen",
      "05_Gesuche",
      "04_Ausschreibung",
      "03_Berichte",
      "02_Berechnungen"
    )

    // First, ensure the base project folder exists
    findOrCreateFolder(projectFolder).flatMap:
      case Some(baseFolderId) =>
        dom.console.log(
          s"✅ Base project folder created/verified: $projectFolder (ID: $baseFolderId)"
        )

        // Now create all subfolders in parallel
        val folderFutures = subfolders.map { subfolder =>
          val folderPath = s"$projectFolder/$subfolder"
          findOrCreateFolder(folderPath).map {
            case Some(_) =>
              dom.console.log(s"✅ Created/verified folder: $subfolder")
              true
            case None    =>
              dom.console.error(s"❌ Failed to create folder: $subfolder")
              false
          }
        }

        // Wait for all subfolders to be created
        Future.sequence(folderFutures).map(results => results.forall(identity))

      case None =>
        dom.console.error(s"❌ Failed to create base project folder: $projectFolder")
        Future.successful(false)

  end createProjectFolderStructure

  /** Save project state to Google Drive Automatically prompts for login if not signed in
    */
  def saveProjectState(project: GeakProject, projectName: String): Future[Boolean] =
    dom.console.log("=== saveProjectState() called ===")
    dom.console.log(s"Project name: $projectName")
    dom.console.log(s"isConfigured: $isConfigured")
    dom.console.log(s"isSignedIn: $isSignedIn")

    if !isConfigured then
      dom.console.warn("⚠️ Google Drive is not configured...")
      Future.successful(false)
    else if !isSignedIn then
      dom.console.log("🔐 Not signed in to Google Drive - prompting for login")
      signIn().flatMap { success =>
        if success then saveProjectStateInternal(project, projectName)
        else Future.successful(false)
      }
    else
      saveProjectStateInternal(project, projectName)
    end if
  end saveProjectState

  /** Internal method to save project state (assumes already signed in)
    */
  private def saveProjectStateInternal(project: GeakProject, projectName: String): Future[Boolean] =
    try
      val sanitizedName = projectName.replaceAll("[^a-zA-Z0-9-_]", "_")
      val projectFolder = s"${GoogleDriveConfig.rootFolder}/$sanitizedName"
      val fileName      = "project_state.json"

      // Convert project to JSON
      val jsonString  = project.asJson.noSpaces
      val jsonBytes   = jsonString.getBytes("UTF-8")
      val arrayBuffer = js.typedarray.ArrayBuffer(jsonBytes.length)
      val uint8Array  = new js.typedarray.Uint8Array(arrayBuffer)
      jsonBytes.zipWithIndex.foreach { case (byte, i) => uint8Array(i) = byte }

      uploadFile(projectFolder, fileName, arrayBuffer, "application/json")
    catch
      case ex: Exception =>
        dom.console.error(s"Failed to save project state: ${ex.getMessage}")
        Future.successful(false)

  /** Upload Excel file to Google Drive Automatically prompts for login if not signed in
    */
  def uploadExcelFile(
      project: GeakProject,
      projectName: String,
      excelBuffer: js.typedarray.ArrayBuffer
  ): Future[Boolean] =
    if !isSignedIn then
      dom.console.log("Not signed in to Google Drive - prompting for login")
      signIn().flatMap { success =>
        if success then uploadExcelFileInternal(project, projectName, excelBuffer)
        else Future.successful(false)
      }
    else
      uploadExcelFileInternal(project, projectName, excelBuffer)

  /** Internal method to upload Excel file (assumes already signed in)
    */
  private def uploadExcelFileInternal(
      project: GeakProject,
      projectName: String,
      excelBuffer: js.typedarray.ArrayBuffer
  ): Future[Boolean] =
    try
      val sanitizedName = projectName.replaceAll("[^a-zA-Z0-9-_]", "_")
      val projectFolder = s"${GoogleDriveConfig.rootFolder}/$sanitizedName"
      val fileName      = s"${sanitizedName}_${System.currentTimeMillis()}.xlsx"

      uploadFile(
        projectFolder,
        fileName,
        excelBuffer,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      )
    catch
      case ex: Exception =>
        dom.console.error(s"Failed to upload Excel file: ${ex.getMessage}")
        Future.successful(false)

  /** List all project folders in the GEAK4S root folder Returns a list of project folder names
    * Automatically prompts for login if not signed in
    */
  def listProjects(): Future[List[String]] =
    if !isConfigured then
      dom.console.warn("⚠️ Google Drive is not configured")
      Future.successful(List.empty)
    else if !isSignedIn then
      dom.console.log("🔐 Not signed in to Google Drive - prompting for login")
      signIn().flatMap { success =>
        if success then listProjectsInternal()
        else Future.successful(List.empty)
      }
    else
      listProjectsInternal()

  /** Internal method to list projects (assumes already signed in)
    */
  private def listProjectsInternal(): Future[List[String]] =
    val promise = Promise[List[String]]()

    try
      // First find the GEAK4S root folder
      findFolder(GoogleDriveConfig.rootFolder, "root").flatMap {
        case Some(rootFolderId) =>
          // List all folders in the GEAK4S folder
          val gapi  = js.Dynamic.global.gapi
          val query =
            s"'$rootFolderId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"

          gapi.client.drive.files.list(js.Dynamic.literal(
            q = query,
            fields = "files(id, name, modifiedTime)",
            spaces = "drive",
            orderBy = "modifiedTime desc"
          )).`then`(
            { (response: js.Dynamic) =>
              val files        = response.result.files.asInstanceOf[js.Array[js.Dynamic]]
              val projectNames = files.map(_.name.toString).toList
              dom.console.log(s"✅ Found ${projectNames.length} projects")
              promise.success(projectNames)
            }: js.Function1[js.Dynamic, Unit],
            { (error: js.Dynamic) =>
              dom.console.error(s"Error listing projects: ${error.result.error.message}")
              promise.success(List.empty)
            }: js.Function1[js.Dynamic, Unit]
          )

          promise.future

        case None =>
          dom.console.log(s"GEAK4S root folder not found")
          Future.successful(List.empty)
      }
    catch
      case ex: Exception =>
        dom.console.error(s"Error listing projects: ${ex.getMessage}")
        promise.success(List.empty)
    end try

    promise.future
  end listProjectsInternal

  /** Load project state from Google Drive Automatically prompts for login if not signed in
    */
  def loadProjectState(projectName: String): Future[Option[GeakProject]] =
    if !isConfigured then
      dom.console.warn("⚠️ Google Drive is not configured")
      Future.successful(None)
    else if !isSignedIn then
      dom.console.log("🔐 Not signed in to Google Drive - prompting for login")
      signIn().flatMap { success =>
        if success then loadProjectStateInternal(projectName)
        else Future.successful(None)
      }
    else
      loadProjectStateInternal(projectName)

  /** Internal method to load project state (assumes already signed in)
    */
  private def loadProjectStateInternal(projectName: String): Future[Option[GeakProject]] =
    val promise = Promise[Option[GeakProject]]()

    try
      val sanitizedName = projectName.replaceAll("[^a-zA-Z0-9-_]", "_")
      val projectFolder = s"${GoogleDriveConfig.rootFolder}/$sanitizedName"
      val fileName      = "project_state.json"

      // Find the project folder
      findOrCreateFolder(projectFolder).flatMap {
        case Some(folderId) =>
          // Find the project_state.json file
          val gapi  = js.Dynamic.global.gapi
          val query = s"name='$fileName' and '$folderId' in parents and trashed=false"

          gapi.client.drive.files.list(js.Dynamic.literal(
            q = query,
            fields = "files(id, name)",
            spaces = "drive"
          )).`then`(
            { (response: js.Dynamic) =>
              val files = response.result.files.asInstanceOf[js.Array[js.Dynamic]]
              if files.length > 0 then
                val fileId = files(0).id.toString
                // Download the file content
                gapi.client.drive.files.get(js.Dynamic.literal(
                  fileId = fileId,
                  alt = "media"
                )).`then`(
                  { (response: js.Dynamic) =>
                    val jsonString = response.body.toString
                    // Parse JSON to GeakProject
                    decode[GeakProject](jsonString) match
                    case Right(project) =>
                      dom.console.log(s"✅ Loaded project: $projectName")
                      promise.success(Some(project))
                    case Left(error)    =>
                      dom.console.error(s"Failed to parse project JSON: ${error.getMessage}")
                      promise.success(None)
                    end match
                  }: js.Function1[js.Dynamic, Unit],
                  { (error: js.Dynamic) =>
                    dom.console.error(s"Error downloading file: ${error.result.error.message}")
                    promise.success(None)
                  }: js.Function1[js.Dynamic, Unit]
                )
              else
                dom.console.log(s"Project state file not found for: $projectName")
                promise.success(None)
              end if
            }: js.Function1[js.Dynamic, Unit],
            { (error: js.Dynamic) =>
              dom.console.error(s"Error finding file: ${error.result.error.message}")
              promise.success(None)
            }: js.Function1[js.Dynamic, Unit]
          )

          promise.future

        case None =>
          dom.console.error(s"Failed to find project folder: $projectFolder")
          Future.successful(None)
      }
    catch
      case ex: Exception =>
        dom.console.error(s"Failed to load project state: ${ex.getMessage}")
        promise.success(None)
    end try

    promise.future
  end loadProjectStateInternal

end GoogleDriveService
