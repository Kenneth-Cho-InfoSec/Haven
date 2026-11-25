package com.arcturus.haven.launcher.search

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.InputType
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isEmpty
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.arcturus.haven.core.HavenModePreferences
import com.arcturus.haven.launcher.core.data.AndroidSettingsSearch
import com.arcturus.haven.launcher.core.data.AndroidSettingsSearchEntry
import com.arcturus.haven.launcher.core.data.LauncherSearch
import com.arcturus.haven.launcher.core.data.SearchStartAppHistory
import com.arcturus.haven.launcher.core.settings.LauncherAppearance
import com.arcturus.haven.launcher.core.settings.LauncherSettings
import com.arcturus.haven.launcher.core.settings.LauncherTypography
import com.arcturus.haven.launcher.core.settings.hasMediaSearchPermission
import com.arcturus.haven.launcher.core.settings.launcherPrefs
import com.arcturus.haven.launcher.core.settings.mediaSearchPermissions
import com.arcturus.haven.launcher.core.ui.dp
import com.arcturus.haven.launcher.core.ui.roundedDrawable
import com.arcturus.haven.launcher.core.ui.selectableBackground
import com.arcturus.haven.launcher.core.ui.toast
import com.arcturus.haven.launcher.launcher.CircularAppIconDrawable
import com.arcturus.haven.launcher.launcher.HomeRoleManager
import com.arcturus.haven.launcher.launcher.LaunchableApp
import com.arcturus.haven.launcher.launcher.LauncherCatalog
import com.arcturus.haven.launcher.launcher.SearchableShortcut
import com.arcturus.haven.launcher.launcher.WorkProfileAppLoadState
import com.arcturus.haven.launcher.settings.LauncherSettingsActivity
import com.arcturus.haven.ui.DummyActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class LauncherSearchActivity : ComponentActivity() {
    private val catalog by lazy { LauncherCatalog(this) }
    private val roleManager by lazy { HomeRoleManager(this) }
    private val preferences by lazy { launcherPrefs() }
    private val searchDataWorker = Executors.newSingleThreadExecutor { Thread(it, "Teres-search-data").apply { isDaemon = true } }
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var root: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var clearButton: TextView
    private lateinit var resultList: LinearLayout
    private var apps: List<LaunchableApp> = emptyList()
    private var vaultApps: List<LaunchableApp> = emptyList()
    private var contactQueryVersion = 0
    private var shortcutQueryVersion = 0
    private var currentQuery = ""
    private var supportedSettingsActions = emptySet<String>()
    private val searchColors by lazy { LauncherAppearance.resolve(this) }

    private val queryText: String get() = searchField.text?.toString().orEmpty()
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshResults() }
    private val contactsPermissionRequest = permissionLauncher(LauncherSettings.KEY_SEARCH_CONTACTS)
    private val calendarPermissionRequest = permissionLauncher(LauncherSettings.KEY_SEARCH_CALENDAR)
    private val mediaPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        preferences.edit { putBoolean(LauncherSettings.KEY_SEARCH_MEDIA, hasMediaSearchPermission()) }
        refreshResults()
    }

    private fun permissionLauncher(key: String) =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            preferences.edit { putBoolean(key, granted) }
            refreshResults()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        LauncherTypography.applyTheme(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        buildInterface()
        loadSupportedSettingsActions()
        catalog.loadAsync { loaded ->
            if (isFinishing || isDestroyed) return@loadAsync
            apps = loaded
            refreshResults()
        }
        if (HavenModePreferences.isIsolationEnabled(this) &&
            preferences.getBoolean(LauncherSettings.KEY_SEARCH_VAULT_APPS, false)
        ) {
            catalog.loadManagedWorkProfileAppsAsync { result ->
                if (isFinishing || isDestroyed) return@loadManagedWorkProfileAppsAsync
                if (result.state == WorkProfileAppLoadState.READY) {
                    vaultApps = result.apps
                    refreshResults()
                }
            }
        }
        searchField.post {
            searchField.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showSoftInput(searchField, 0)
        }
    }

    override fun onDestroy() {
        contactQueryVersion++
        shortcutQueryVersion++
        searchDataWorker.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        LauncherTypography.applyTheme(this)
        if (::root.isInitialized) LauncherTypography.applyToViewTree(this, root)
        if (::resultList.isInitialized) refreshResults()
    }

    private fun refreshResults() = renderResults(queryText)

    private fun buildInterface() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xE633354B.toInt(), 0xF025202D.toInt(), 0xF4162024.toInt()),
            )
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(10), dp(14), dp(10))
        }
        val searchCapsule = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(0xDD41465C.toInt(), dp(32))
            setPadding(dp(20), 0, dp(12), 0)
            minimumHeight = dp(48)
        }
        searchField = EditText(this).apply {
            hint = "Search contacts and apps"
            setHintTextColor(0xFFC6C5CE.toInt())
            setTextColor(Color.WHITE)
            textSize = 17f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = Color.TRANSPARENT.toDrawable()
            contentDescription = "Search contacts and apps"
            doOnTextChanged { s, _, _, _ ->
                currentQuery = s?.toString().orEmpty()
                clearButton.isVisible = currentQuery.isNotBlank()
                renderResults(currentQuery)
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    launchWebSearch(queryText)
                    true
                } else false
            }
        }
        searchCapsule.addView(searchField, LinearLayout.LayoutParams(0, dp(48), 1f))
        clearButton = TextView(this).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "Cancel"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                searchField.text?.clear()
                isVisible = false
                searchField.requestFocus()
            }
        }
        searchCapsule.addView(clearButton, LinearLayout.LayoutParams(dp(40), dp(40)))
        header.addView(searchCapsule, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "⋮"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xFFE7E5EE.toInt())
            contentDescription = "Search options"
            isClickable = true
            isFocusable = true
            background = selectableBackground(0x18FFFFFF)
            setOnClickListener { showSearchMenu(this) }
        }, LinearLayout.LayoutParams(dp(44), dp(56)).apply { marginStart = dp(8) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        resultList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(34))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(resultList)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        LauncherTypography.applyToViewTree(this, root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.setPadding(dp(22), bars.top + dp(4), dp(14), dp(10))
            scroll.setPadding(0, 0, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }
        renderResults("")
    }

    private fun renderResults(rawQuery: String) {
        if (!::resultList.isInitialized) return
        val query = rawQuery.trim()
        currentQuery = query
        contactQueryVersion++
        shortcutQueryVersion++
        resultList.removeAllViews()
        val showHidden = preferences.getBoolean(LauncherSettings.KEY_SEARCH_SHOW_HIDDEN_APPS, false)
        val hidden = preferences.getStringSet(LauncherSettings.KEY_HIDDEN_APPS, emptySet()).orEmpty()
        val eligibleApps = apps.filter { showHidden || it.entry.componentKey !in hidden }
        val appEntries = LauncherSearch.filter(eligibleApps.map { it.entry }, query)
        val appMap = eligibleApps.associateBy { it.entry.componentKey }
        val appLimit = if (query.isBlank()) preferences.getInt(LauncherSettings.KEY_SEARCH_START_APPS_COUNT, 4).coerceIn(0, 12) else MAX_APP_RESULTS
        val rankedApps = if (query.isBlank()) {
            SearchStartAppHistory.order(
                appEntries,
                SearchStartAppHistory.parse(preferences.getString(LauncherSettings.KEY_SEARCH_START_APP_HISTORY, "[]")),
            )
        } else appEntries
        val appResults = rankedApps.take(appLimit).mapNotNull { appMap[it.componentKey] }
        if (appResults.isNotEmpty()) {
            addSection("ALL APPS")
            if (query.isBlank()) addStartAppGrid(appResults) else appResults.forEach(::addAppRow)
        }

        if (query.isNotBlank() &&
            HavenModePreferences.isIsolationEnabled(this) &&
            preferences.getBoolean(LauncherSettings.KEY_SEARCH_VAULT_APPS, false) &&
            vaultApps.isNotEmpty()
        ) {
            val vaultMap = vaultApps.associateBy { it.entry.componentKey }
            val matchedVaultApps = LauncherSearch.filter(vaultApps.map { it.entry }, query)
                .take(MAX_APP_RESULTS)
                .mapNotNull { vaultMap[it.componentKey] }
            if (matchedVaultApps.isNotEmpty()) {
                addSection("HAVEN VAULT")
                matchedVaultApps.forEach(::addVaultAppRow)
            }
        }

        val settingsResults = AndroidSettingsSearch.filter(query, Build.VERSION.SDK_INT)
            .filter { it.intentAction in supportedSettingsActions }
        if (settingsResults.isNotEmpty()) {
            addSection("SETTINGS")
            settingsResults.forEach(::addSettingsResult)
        }

        val contactsEnabled = preferences.getBoolean(LauncherSettings.KEY_SEARCH_CONTACTS, false)
        val mediaEnabled = preferences.getBoolean(LauncherSettings.KEY_SEARCH_MEDIA, false)
        val calendarEnabled = preferences.getBoolean(LauncherSettings.KEY_SEARCH_CALENDAR, false)
        val hasCalendarPermission = hasPermission(Manifest.permission.READ_CALENDAR)
        val contactLimit = if (query.isBlank()) preferences.getInt(LauncherSettings.KEY_SEARCH_START_CONTACTS_COUNT, 2).coerceIn(0, 8) else MAX_CONTACT_RESULTS
        val searchContacts = contactsEnabled && hasPermission(Manifest.permission.READ_CONTACTS) && (query.isNotBlank() || contactLimit > 0)
        val searchMedia = mediaEnabled && hasMediaSearchPermission() && query.isNotBlank()
        val searchCalendar = calendarEnabled && hasCalendarPermission && query.isNotBlank()
        if (searchContacts || searchMedia || searchCalendar) {
            scheduleSearchData(query, contactLimit, searchContacts, searchMedia, searchCalendar)
        } else if (contactsEnabled && query.isNotBlank()) {
            addSection("CONTACTS")
            addPermissionResultCard(
                "Search in contacts", "Find contacts in your address book",
                letterGlyph("Contact", "?"), LauncherSettings.KEY_SEARCH_CONTACTS,
            ) { contactsPermissionRequest.launch(Manifest.permission.READ_CONTACTS) }
        }
        if (mediaEnabled && query.isNotBlank() && !hasMediaSearchPermission()) {
            addSection("MEDIA")
            addPermissionResultCard(
                "Files", "Find files on your device",
                letterGlyph("Media"), LauncherSettings.KEY_SEARCH_MEDIA,
            ) { mediaPermissionRequest.launch(mediaSearchPermissions()) }
        }
        if (calendarEnabled && query.isNotBlank() && !hasCalendarPermission) {
            addSection("CALENDAR")
            addPermissionResultCard(
                "Calendar", "Find events in your calendar",
                letterGlyph("Calendar"), LauncherSettings.KEY_SEARCH_CALENDAR,
            ) { calendarPermissionRequest.launch(Manifest.permission.READ_CALENDAR) }
        }

        if (preferences.getBoolean(LauncherSettings.KEY_SEARCH_SHORTCUTS, true) && query.isNotBlank()) {
            if (catalog.hasShortcutHostPermission()) {
                val version = shortcutQueryVersion
                catalog.loadSearchShortcutsAsync(query) { shortcuts ->
                    if (version != shortcutQueryVersion || isFinishing || isDestroyed) return@loadSearchShortcutsAsync
                    insertShortcutResults(shortcuts.take(MAX_SHORTCUT_RESULTS))
                }
            } else {
                addRankedResultSection("SHORTCUTS", TAG_SHORTCUT_RESULTS, 4) {
                    addInformationRow("Select Teres as the Home app to search app shortcuts.") {
                        val intent = roleManager.requestIntent()
                        if (intent != null) homeRoleRequest.launch(intent) else toast("Home app settings are unavailable")
                    }
                }
            }
        }

        if (query.isNotBlank() && preferences.getBoolean(LauncherSettings.KEY_SEARCH_WEB, true)) {
            val title = when (preferences.getString(LauncherSettings.KEY_SEARCH_PROVIDER, "google")) {
                "duckduckgo" -> "Search on DuckDuckGo"
                "bing" -> "Search on Bing"
                else -> "Search on Google"
            }
            addSection("WEB")
            addSuggestion(title, "com.google.android.googlequicksearchbox") { launchWebSearch(query) }
            if (preferences.getBoolean(LauncherSettings.KEY_SEARCH_PLAY_STORE, true)) {
                addSuggestion("Search on Play Store", "com.android.vending") { launchPlayStoreSearch(query) }
            }
            addSuggestion("Search on YouTube", "com.google.android.youtube") { launchUrl("https://www.youtube.com/results?search_query=${Uri.encode(query)}") }
            addSuggestion("Search on Google Maps", "com.google.android.apps.maps") { launchUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}") }
        }

        if (resultList.isEmpty()) {
            addInformationRow(if (apps.isEmpty()) "Loading installed apps…" else "No results found.", TAG_EMPTY_MESSAGE)
        }
        LauncherTypography.applyToViewTree(this, root)
    }

    private fun scheduleSearchData(
        query: String,
        contactLimit: Int,
        searchContacts: Boolean,
        searchMedia: Boolean,
        searchCalendar: Boolean,
    ) {
        val version = contactQueryVersion
        searchDataWorker.execute {
            val contacts = if (searchContacts) queryContacts(query, contactLimit) else emptyList()
            val media = if (searchMedia) queryMedia(query, MAX_MEDIA_RESULTS) else emptyList()
            val events = if (searchCalendar) queryCalendarEvents(query, MAX_CALENDAR_RESULTS) else emptyList()
            mainHandler.post {
                if (version != contactQueryVersion || isFinishing || isDestroyed) return@post
                insertContactResults(contacts)
                insertMediaResults(media)
                insertCalendarResults(events)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun permittedMediaTypes(): Set<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                setOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO)
            } else emptySet()
        }
        val visualSelected = Build.VERSION.SDK_INT >= 34 && hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return buildSet {
            if (hasPermission(Manifest.permission.READ_MEDIA_IMAGES) || visualSelected) add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
            if (hasPermission(Manifest.permission.READ_MEDIA_VIDEO) || visualSelected) add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            if (hasPermission(Manifest.permission.READ_MEDIA_AUDIO)) add(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO)
        }
    }

    private fun queryContacts(query: String, limit: Int): List<ContactResult> {
        if (limit <= 0) return emptyList()
        val found = LinkedHashMap<String, ContactResult>()
        runCatching {
            contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                if (query.isBlank()) null else "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                if (query.isBlank()) null else arrayOf("%$query%"),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                while (cursor.moveToNext() && found.size < limit) {
                    val id = cursor.getString(idColumn) ?: continue
                    val name = cursor.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                    found.putIfAbsent(id, ContactResult(id, name, ""))
                }
            }
        }
        if (found.size < limit) {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            )
            runCatching {
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    if (query.isBlank()) null else "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                    if (query.isBlank()) null else arrayOf("%$query%", "%$query%"),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                    val numberColumn = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (cursor.moveToNext() && found.size < limit) {
                        val id = cursor.getString(idColumn) ?: continue
                        val name = cursor.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                        val number = cursor.getString(numberColumn).orEmpty()
                        val previous = found[id]
                        found[id] = previous?.copy(number = previous.number.ifBlank { number }) ?: ContactResult(id, name, number)
                    }
                }
            }
        }
        return found.values.toList()
    }

    private fun queryMedia(query: String, limit: Int): List<MediaResult> {
        if (limit <= 0) return emptyList()
        val mediaTypes = permittedMediaTypes().ifEmpty { return emptyList() }
        val typeArgs = mediaTypes.map(Int::toString)
        val typeClause = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${typeArgs.joinToString(",") { "?" }})"
        val selection = if (query.isBlank()) typeClause else "($typeClause) AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = (if (query.isBlank()) typeArgs else typeArgs + "%$query%").toTypedArray()
        val externalUri = MediaStore.Files.getContentUri("external")
        val results = mutableListOf<MediaResult>()
        runCatching {
            contentResolver.query(
                externalUri,
                arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE),
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                    val mime = cursor.getString(mimeColumn).orEmpty()
                    val size = cursor.getLong(sizeColumn).coerceAtLeast(0L)
                    results += MediaResult(name, mediaSubtitle(mime, size), ContentUris.withAppendedId(externalUri, id), mime.ifBlank { "*/*" })
                }
            }
        }
        return results
    }

    private fun queryCalendarEvents(query: String, limit: Int): List<CalendarResult> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val allDayFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        val timedFormat = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault())
        val results = mutableListOf<CalendarResult>()
        runCatching {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID, CalendarContract.Events.TITLE,
                    CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY, CalendarContract.Events.EVENT_LOCATION,
                ),
                "${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.EVENT_LOCATION} LIKE ?",
                arrayOf("%$query%", "%$query%"),
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val startColumn = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endColumn = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val allDayColumn = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val locationColumn = cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)?.takeIf(String::isNotBlank) ?: continue
                    val start = cursor.getLong(startColumn)
                    val end = cursor.getLong(endColumn).takeIf { it > start } ?: start
                    val allDay = cursor.getInt(allDayColumn) != 0
                    val location = cursor.getString(locationColumn).orEmpty()
                    val date = (if (allDay) allDayFormat else timedFormat).format(Date(start))
                    results += CalendarResult(title, location.ifBlank { date }, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), start, end)
                }
            }
        }
        return results
    }

    private fun mediaSubtitle(mime: String, size: Long): String {
        val kind = when {
            mime.startsWith("image/") -> "Photo"
            mime.startsWith("video/") -> "Video"
            mime.startsWith("audio/") -> "Audio"
            else -> "Media"
        }
        return "$kind · ${Formatter.formatShortFileSize(this, size)}"
    }

    private fun insertContactResults(contacts: List<ContactResult>) {
        if (contacts.isEmpty()) return
        addRankedResultSection("CONTACTS", TAG_CONTACT_RESULTS, 1) {
            contacts.forEach { contact ->
                addView(resultRow(contact.name, contact.number, letterGlyph(contact.name, "?")) {
                    startActivityOrToast(
                        Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contact.id)),
                        "Contact could not be opened",
                    )
                })
            }
        }
    }

    private fun insertMediaResults(media: List<MediaResult>) {
        if (media.isEmpty()) return
        addRankedResultSection("MEDIA", TAG_MEDIA_RESULTS, 2) {
            media.forEach { item ->
                addView(resultRow(item.name, item.subtitle, letterGlyph(item.name)) {
                    startActivityOrToast(
                        Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "Media could not be opened",
                    )
                })
            }
        }
    }

    private fun insertCalendarResults(events: List<CalendarResult>) {
        if (events.isEmpty()) return
        addRankedResultSection("CALENDAR", TAG_CALENDAR_RESULTS, 3) {
            events.forEach { event ->
                addView(resultRow(event.title, event.subtitle, letterGlyph(event.title)) {
                    startActivityOrToast(
                        Intent(Intent.ACTION_VIEW, event.uri)
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis),
                        "Calendar event could not be opened",
                    )
                })
            }
        }
    }

    private fun addRankedResultSection(title: String, tag: String, rank: Int, rows: LinearLayout.() -> Unit) {
        resultList.findViewWithTag<View>(TAG_EMPTY_MESSAGE)?.let(resultList::removeView)
        resultList.findViewWithTag<View>(tag)?.let(resultList::removeView)
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.tag = tag
            setPadding(0, dp(8), 0, 0)
            addView(dividerView(), LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(8) })
            addView(sectionHeader(title))
            rows()
        }
        val higherSection = (0 until resultList.childCount).firstOrNull { index ->
            resultSectionRank(resultList.getChildAt(index))?.let { it > rank } == true
        }
        val insertAt = if (higherSection != null &&
            (resultList.getChildAt(higherSection) as? TextView)?.text == "WEB" &&
            higherSection > 0 && resultList.getChildAt(higherSection - 1) !is LinearLayout
        ) higherSection - 1 else higherSection
        resultList.addView(section, insertAt ?: resultList.childCount)
        LauncherTypography.applyToViewTree(this, root)
    }

    private fun resultSectionRank(view: View): Int? = when (view.tag) {
        TAG_CONTACT_RESULTS -> 1
        TAG_MEDIA_RESULTS -> 2
        TAG_CALENDAR_RESULTS -> 3
        TAG_SHORTCUT_RESULTS -> 4
        else -> if ((view as? TextView)?.text == "WEB") 5 else null
    }

    private fun addPermissionResultCard(
        title: String,
        summary: String,
        icon: Drawable,
        key: String,
        permissionRequest: () -> Unit,
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(10))
        }
        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(ImageView(this@LauncherSearchActivity).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = roundedDrawable(0xFF666A79.toInt(), dp(14))
                val pad = dp(12)
                setPadding(pad, pad, pad, pad)
            }, LinearLayout.LayoutParams(dp(84), dp(84)).apply { marginEnd = dp(20) })
            addView(LinearLayout(this@LauncherSearchActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(labelView(title, 16f, Color.WHITE, 500))
                addView(labelView(summary, 13f, 0xFFD0CED7.toInt()).apply { setPadding(0, dp(2), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        card.addView(detail)
        fun actionButton(label: String, enabled: Boolean, onClick: () -> Unit) =
            labelView(label, 15f, if (enabled) Color.WHITE else Color.BLACK, 500).apply {
                gravity = Gravity.CENTER
                background = roundedDrawable(if (enabled) 0xFF3F4558.toInt() else 0xFFB6B0CE.toInt(), dp(18))
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(actionButton("Disable", true) {
                preferences.edit { putBoolean(key, false) }
                renderResults(currentQuery)
            }, LinearLayout.LayoutParams(0, dp(62), 1f).apply { marginEnd = dp(10) })
            addView(actionButton("Allow", false, permissionRequest), LinearLayout.LayoutParams(0, dp(62), 1f))
        }
        card.addView(actions)
        resultList.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    private fun insertShortcutResults(shortcuts: List<SearchableShortcut>) {
        if (shortcuts.isEmpty()) return
        addRankedResultSection("SHORTCUTS", TAG_SHORTCUT_RESULTS, 4) {
            shortcuts.forEach { result ->
                val icon = result.icon?.let { CircularAppIconDrawable(it, searchColors.accent) } ?: letterGlyph(result.label)
                addView(resultRow(result.label, "", icon) {
                    runCatching { catalog.start(result.shortcut, null) }.onFailure { toast("Shortcut is unavailable") }
                })
            }
        }
    }

    private fun addAppRow(app: LaunchableApp) {
        resultList.addView(resultRow(app.entry.label, "", appIcon(app)) { startApp(app) })
    }

    private fun addVaultAppRow(app: LaunchableApp) {
        resultList.addView(resultRow(app.entry.label, "Haven Vault", appIcon(app)) {
            val intent = Intent(DummyActivity.UNFREEZE_AND_LAUNCH).apply {
                component = ComponentName(this@LauncherSearchActivity, DummyActivity::class.java)
                putExtra("packageName", app.entry.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            DummyActivity.registerSameProcessRequest(intent)
            runCatching { startActivity(intent) }.onFailure {
                runCatching { catalog.start(app) }.onFailure { toast("Vault app could not be opened") }
            }
        })
    }

    private fun addStartAppGrid(appResults: List<LaunchableApp>) {
        val grid = GridLayout(this).apply {
            columnCount = START_APP_COLUMNS
            useDefaultMargins = false
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        appResults.forEachIndexed { index, app ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(2), 0, dp(7))
                background = selectableBackground(0x18FFFFFF)
                isClickable = true
                isFocusable = true
                contentDescription = "Launch ${app.entry.label}"
                setOnClickListener { startApp(app) }
                addView(ImageView(this@LauncherSearchActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(appIcon(app))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(62), dp(62)))
                addView(labelView(app.entry.label, 12f, Color.WHITE, 500).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    includeFontPadding = false
                    letterSpacing = 0.01f
                    setLineSpacing(dp(4).toFloat(), 1f)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            }
            grid.addView(tile, GridLayout.LayoutParams(
                GridLayout.spec(index / START_APP_COLUMNS),
                GridLayout.spec(index % START_APP_COLUMNS, GridLayout.FILL, 1f),
            ).apply {
                width = 0
                height = -2
                setGravity(Gravity.FILL_HORIZONTAL)
            })
        }
        resultList.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
    }

    private fun addSettingsResult(entry: AndroidSettingsSearchEntry) {
        val artwork = runCatching { packageManager.getApplicationIcon("com.android.settings") }
            .getOrDefault(packageManager.defaultActivityIcon)
        resultList.addView(resultRow(entry.title, "", CircularAppIconDrawable(artwork, searchColors.accent), iconSizeDp = 26, minimumHeightDp = 62) {
            startActivityOrToast(Intent(entry.intentAction), "${entry.title} settings are unavailable on this device")
        })
    }

    private fun loadSupportedSettingsActions() {
        searchDataWorker.execute {
            val supported = AndroidSettingsSearch.entries.asSequence()
                .filter { it.minimumApi <= Build.VERSION.SDK_INT }
                .filter { entry -> runCatching { Intent(entry.intentAction).resolveActivity(packageManager) != null }.getOrDefault(false) }
                .mapTo(mutableSetOf()) { it.intentAction }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                supportedSettingsActions = supported
                refreshResults()
            }
        }
    }

    private fun addSection(title: String) {
        if (resultList.isNotEmpty()) {
            resultList.addView(dividerView(), LinearLayout.LayoutParams(-1, dp(1)).apply {
                topMargin = dp(12)
                bottomMargin = dp(10)
            })
        }
        resultList.addView(sectionHeader(title))
    }

    private fun dividerView() = View(this).apply { setBackgroundColor(0x55FFFFFF) }

    private fun sectionHeader(title: String) = TextView(this).apply {
        text = title
        tag = title
        textSize = 12f
        letterSpacing = 0.12f
        includeFontPadding = false
        setTextColor(0xFFCBC9D2.toInt())
        setPadding(dp(8), 0, dp(2), 0)
        contentDescription = "$title results"
    }

    private fun addSuggestion(title: String, iconPackage: String, action: () -> Unit) {
        val icon = apps.firstOrNull { it.entry.packageName == iconPackage }?.icon
            ?.let { CircularAppIconDrawable(it, searchColors.accent) }
            ?: letterGlyph(title.firstOrNull()?.toString().orEmpty())
        resultList.addView(resultRow(title, "", icon, iconSizeDp = 30, minimumHeightDp = 62, action = action))
    }

    private fun addInformationRow(message: String, rowTag: String? = null, action: (() -> Unit)? = null) {
        val row = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(0xFFD4D2DC.toInt())
            background = roundedDrawable(0x44252938, dp(14))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = action != null
            isFocusable = action != null
            tag = rowTag
            action?.let { click -> setOnClickListener { click() } }
        }
        resultList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
    }

    private fun resultRow(
        title: String,
        subtitle: String,
        icon: Drawable,
        iconSizeDp: Int = 50,
        minimumHeightDp: Int = 68,
        action: () -> Unit,
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(minimumHeightDp)
        setPadding(dp(2), dp(5), dp(6), dp(5))
        background = selectableBackground(0x18FFFFFF)
        isClickable = true
        isFocusable = true
        contentDescription = listOf(title, subtitle).filter(String::isNotBlank).joinToString(". ")
        setOnClickListener { action() }
        addView(ImageView(this@LauncherSearchActivity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(icon)
        }, LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)).apply { marginEnd = dp(16) })
        val labels = LinearLayout(this@LauncherSearchActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(labelView(title, 16f, Color.WHITE, 500).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            if (subtitle.isNotBlank()) addView(labelView(subtitle, 13f, 0xFFD0CED7.toInt()).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            })
        }
        addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@LauncherSearchActivity).apply {
            text = "›"
            textSize = 28f
            setTextColor(0xFFCBC6D4.toInt())
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(24), dp(46)))
    }

    private fun labelView(value: String, sizeSp: Float, color: Int, weight: Int? = null) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        weight?.let { typeface = LauncherTypography.typeface(this@LauncherSearchActivity, it) }
    }

    private fun appIcon(app: LaunchableApp) =
        CircularAppIconDrawable(app.icon ?: packageManager.defaultActivityIcon, searchColors.accent)

    private fun startApp(app: LaunchableApp) {
        runCatching { catalog.start(app) }.onFailure { toast("App could not be opened") }
    }

    private fun startActivityOrToast(intent: Intent, errorMessage: String) {
        runCatching { startActivity(intent) }.onFailure { toast(errorMessage) }
    }

    private fun letterGlyph(seed: String, fallback: String = "•"): Drawable =
        TextGlyphDrawable(seed.trim().firstOrNull()?.uppercaseChar()?.toString()?.ifBlank { fallback } ?: fallback, searchColors.accent)

    private fun showSearchMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Search preferences").setOnMenuItemClickListener { openSearchSettings(); true }
            menu.add("Clear query").setOnMenuItemClickListener { searchField.text?.clear(); true }
        }.show()
    }

    private fun openSearchSettings() {
        startActivity(Intent(this, LauncherSettingsActivity::class.java).putExtra(LauncherSettingsActivity.EXTRA_PAGE, "search"))
    }

    private fun launchWebSearch(query: String) {
        if (query.isBlank()) return
        val encoded = Uri.encode(query)
        val base = when (preferences.getString(LauncherSettings.KEY_SEARCH_PROVIDER, "google")) {
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "bing" -> "https://www.bing.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
        launchUrl(base)
    }

    private fun launchPlayStoreSearch(query: String) {
        if (query.isBlank()) return
        startActivityOrToast(Intent(Intent.ACTION_VIEW, "market://search?q=${Uri.encode(query)}".toUri()), "Play Store is unavailable")
    }

    private fun launchUrl(url: String) =
        startActivityOrToast(Intent(Intent.ACTION_VIEW, url.toUri()), "No app can open this search")

    private data class ContactResult(val id: String, val name: String, val number: String)
    private data class MediaResult(val name: String, val subtitle: String, val uri: Uri, val mimeType: String)
    private data class CalendarResult(val title: String, val subtitle: String, val uri: Uri, val startMillis: Long, val endMillis: Long)

    private class TextGlyphDrawable(label: String, color: Int) : Drawable() {
        private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 40f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private val value = label.take(1)
        override fun draw(canvas: Canvas) {
            val side = minOf(bounds.width(), bounds.height()).toFloat()
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            canvas.drawCircle(cx, cy, side / 2f, background)
            canvas.drawText(value, cx, cy - (text.ascent() + text.descent()) / 2f, text)
        }
        override fun setAlpha(alpha: Int) { background.alpha = alpha; text.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { background.colorFilter = colorFilter; text.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val START_APP_COLUMNS = 4
        const val MAX_APP_RESULTS = 12
        const val MAX_CONTACT_RESULTS = 8
        const val MAX_MEDIA_RESULTS = 8
        const val MAX_CALENDAR_RESULTS = 8
        const val MAX_SHORTCUT_RESULTS = 8
        const val TAG_EMPTY_MESSAGE = "search-empty-message"
        const val TAG_CONTACT_RESULTS = "contact-search-results"
        const val TAG_MEDIA_RESULTS = "media-search-results"
        const val TAG_CALENDAR_RESULTS = "calendar-search-results"
        const val TAG_SHORTCUT_RESULTS = "shortcut-search-results"
    }
}
