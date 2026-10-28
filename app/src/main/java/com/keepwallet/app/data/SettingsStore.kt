package com.keepwallet.app.data

import android.content.Context
import android.content.SharedPreferences
import com.keepwallet.app.domain.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Accent colour choices. [argb] is the colour used for buttons, the balance card and highlights. */
enum class AccentColor(val label: String, val argb: Long) {
  TEAL("Teal", 0xFF16655E),
  BLUE("Blue", 0xFF2563EB),
  VIOLET("Violet", 0xFF6D4FD6),
  ROSE("Rose", 0xFFBE185D),
  TERRACOTTA("Terracotta", 0xFFC25B42),
  AMBER("Amber", 0xFFB45309),
  GREEN("Green", 0xFF15803D),
  GRAPHITE("Graphite", 0xFF2B2B2D)
}

/** One line of "what the detector did with a notification": the app, when, and a plain-words outcome. No message text is kept. */
data class DetectionLogEntry(val at: Long, val app: String, val outcome: String, val captured: Boolean)

/** Which background the app uses, independent of the accent colour. */
/** How strongly the seed colour tints the surfaces; all follow Google's Material colour-science rules. */
enum class ColorStyle(val label: String, val hint: String) {
  BALANCED("Balanced", "Soft, calm tones (the Material default)"),
  VIBRANT("Vibrant", "Rich, saturated colour"),
  FAITHFUL("Faithful", "Stays closest to your colour"),
  NEUTRAL("Neutral", "Almost grey with a hint of colour")
}

enum class BackgroundMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
  BLACK("Pure black"),
  WHITE("Pure white")
}

/**
 * Small preference store. Lives outside Room so the notification listener can read the
 * on/off switch without touching the database.
 */
class SettingsStore(context: Context, private val firstRunEnabled: Boolean = true, hideOnOpenDefault: Boolean = true) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  init {
    // The black-and-white look is the default: new installs start there, and an existing install is moved to it
    // once (everything can be changed again in Settings afterwards).
    if (prefs.getInt(KEY_LOOK_VERSION, 0) < LOOK_VERSION) {
      prefs.edit()
        .putInt(KEY_LOOK_VERSION, LOOK_VERSION)
        .putString(KEY_ACCENT, AppPalette.MONO.accent.name)
        .remove(KEY_CUSTOM_ACCENT)
        .putInt(KEY_HIGHLIGHT, AppPalette.MONO.tertiary.toInt())
        .putString(KEY_BACKGROUND, AppPalette.MONO.background.name)
        .putString(KEY_PALETTE, AppPalette.MONO.name)
        .apply()
    }
  }

  private val _accent = MutableStateFlow(readAccent())
  val accent: StateFlow<AccentColor> = _accent.asStateFlow()

  private val _backgroundMode = MutableStateFlow(readBackground())
  val backgroundMode: StateFlow<BackgroundMode> = _backgroundMode.asStateFlow()

  private val _colorStyle = MutableStateFlow(readEnum(KEY_COLOR_STYLE, ColorStyle.BALANCED))
  val colorStyle: StateFlow<ColorStyle> = _colorStyle.asStateFlow()

  /** Material You: take the colours from the wallpaper (Android 12+) instead of the chosen accent. */
  private val _systemColors = MutableStateFlow(prefs.getBoolean(KEY_SYSTEM_COLORS, false))
  val systemColors: StateFlow<Boolean> = _systemColors.asStateFlow()

  /** Keys of the alerts the user has already seen on the notifications page; the bell counts only the rest. */
  private val _seenAlerts = MutableStateFlow(prefs.getStringSet(KEY_SEEN_ALERTS, emptySet()).orEmpty().toSet())
  val seenAlerts: StateFlow<Set<String>> = _seenAlerts.asStateFlow()

  fun markAlertsSeen(keys: Collection<String>) {
    if (keys.all { it in _seenAlerts.value }) return
    // Only recent keys matter: keep the newest few hundred.
    val next = (_seenAlerts.value + keys).toList().takeLast(SEEN_ALERTS_MAX).toSet()
    _seenAlerts.value = next
    prefs.edit().putStringSet(KEY_SEEN_ALERTS, next).apply()
  }

  /** The last few things the detector looked at, newest first, so a miss can be explained. */
  private val _detectionLog = MutableStateFlow(readDetectionLog())
  val detectionLog: StateFlow<List<DetectionLogEntry>> = _detectionLog.asStateFlow()

  fun addDetectionLog(entry: DetectionLogEntry) {
    val next = (listOf(entry) + _detectionLog.value).take(DETECTION_LOG_SIZE)
    _detectionLog.value = next
    prefs.edit().putString(KEY_DETECTION_LOG, writeDetectionLog(next)).apply()
  }

  fun clearDetectionLog() {
    _detectionLog.value = emptyList()
    prefs.edit().remove(KEY_DETECTION_LOG).apply()
  }

  private fun readDetectionLog(): List<DetectionLogEntry> = try {
    val array = org.json.JSONArray(prefs.getString(KEY_DETECTION_LOG, "[]"))
    (0 until array.length()).map { i ->
      val o = array.getJSONObject(i)
      DetectionLogEntry(o.getLong("at"), o.getString("app"), o.getString("outcome"), o.getBoolean("captured"))
    }
  } catch (_: Exception) {
    emptyList()
  }

  private fun writeDetectionLog(list: List<DetectionLogEntry>): String = org.json.JSONArray().also { array ->
    list.forEach { array.put(org.json.JSONObject().put("at", it.at).put("app", it.app).put("outcome", it.outcome).put("captured", it.captured)) }
  }.toString()

  /** Exchange rates for the converter: the ones last downloaded, or the approximate built-in table. */
  private val _rates = MutableStateFlow(CurrencyRates.decode(prefs.getString(KEY_RATES, null)) ?: RateTable(CurrencyRates.builtIn, null, builtIn = true))
  val rates: StateFlow<RateTable> = _rates.asStateFlow()

  /** The last finished month that was already looked at for the "under budget" celebration (e.g. "2026-09"). */
  var celebratedMonth: String?
    get() = prefs.getString(KEY_CELEBRATED, null)
    set(value) {
      prefs.edit().putString(KEY_CELEBRATED, value).apply()
    }

  /** An accent colour of the user's own making; when set it is used instead of [accent]. */
  private val _customAccent = MutableStateFlow(if (prefs.contains(KEY_CUSTOM_ACCENT)) prefs.getInt(KEY_CUSTOM_ACCENT, 0) else null)
  val customAccent: StateFlow<Int?> = _customAccent.asStateFlow()

  /** The second, highlight colour of the current palette; null lets the theme derive one from the accent. */
  private val _highlight = MutableStateFlow(if (prefs.contains(KEY_HIGHLIGHT)) prefs.getInt(KEY_HIGHLIGHT, 0) else null)
  val highlight: StateFlow<Int?> = _highlight.asStateFlow()

  /** Name of the palette that was applied last, while nothing has been changed by hand since. */
  private val _palette = MutableStateFlow(readEnumOrNull<AppPalette>(KEY_PALETTE))
  val palette: StateFlow<AppPalette?> = _palette.asStateFlow()

  /** The welcome screen shows once, on a fresh install; existing installs are treated as done. */
  private val _onboarded = MutableStateFlow(!firstRunEnabled || prefs.getBoolean(KEY_ONBOARDED, prefs.getBoolean(KEY_SEEDED, false)))
  val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

  /** The animated logo card plays on the very first launch only, so every later start is immediate. */
  private val _splashPending = MutableStateFlow(firstRunEnabled && !prefs.getBoolean(KEY_SPLASH_SHOWN, prefs.getBoolean(KEY_SEEDED, false)))
  val splashPending: StateFlow<Boolean> = _splashPending.asStateFlow()


  private val _chartType = MutableStateFlow(readEnum(KEY_CHART, ChartType.BAR))
  val chartType: StateFlow<ChartType> = _chartType.asStateFlow()

  /** The accounts whose balances add up on the home card; null means the simple choice made by [showTotalOnHero]. */
  private val _heroAccounts = MutableStateFlow(
    prefs.getString(KEY_HERO_ACCOUNTS, null)?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.toSet()?.takeIf { it.isNotEmpty() }
  )
  val heroAccounts: StateFlow<Set<Long>?> = _heroAccounts.asStateFlow()

  fun setHeroAccounts(ids: Set<Long>?) {
    _heroAccounts.value = ids?.takeIf { it.isNotEmpty() }
    prefs.edit().apply {
      if (ids.isNullOrEmpty()) remove(KEY_HERO_ACCOUNTS) else putString(KEY_HERO_ACCOUNTS, ids.joinToString(","))
    }.apply()
  }

  private val _showTotalOnHero = MutableStateFlow(prefs.getBoolean(KEY_HERO_TOTAL, false))
  val showTotalOnHero: StateFlow<Boolean> = _showTotalOnHero.asStateFlow()

  /** Home arrangements the user saved under a name. */
  private val _layouts = MutableStateFlow(readLayouts())
  val layouts: StateFlow<List<SavedLayout>> = _layouts.asStateFlow()

  // ---- App lock ----
  private val _lockEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false) && prefs.contains(KEY_PIN_HASH))
  val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

  private val _pinLength = MutableStateFlow(prefs.getInt(KEY_PIN_LENGTH, 4))
  val pinLength: StateFlow<Int> = _pinLength.asStateFlow()

  private val _biometric = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC, false))
  val biometric: StateFlow<Boolean> = _biometric.asStateFlow()

  /** Seconds away from the app before the PIN is asked for again; 0 asks every time. */
  private val _lockTimeout = MutableStateFlow(prefs.getInt(KEY_LOCK_TIMEOUT, 60))
  val lockTimeout: StateFlow<Int> = _lockTimeout.asStateFlow()

  /** Hides the app in the recent-apps list (and blocks screenshots) while the lock is on. */
  private val _hideInRecents = MutableStateFlow(prefs.getBoolean(KEY_HIDE_RECENTS, true))
  val hideInRecents: StateFlow<Boolean> = _hideInRecents.asStateFlow()

  /** Optional reminders, all off until switched on in Settings. */
  private val _remindBills = MutableStateFlow(prefs.getBoolean(KEY_REMIND_BILLS, false))
  val remindBills: StateFlow<Boolean> = _remindBills.asStateFlow()
  private val _budgetAlerts = MutableStateFlow(prefs.getBoolean(KEY_BUDGET_ALERTS, false))
  val budgetAlerts: StateFlow<Boolean> = _budgetAlerts.asStateFlow()
  private val _dailyNudge = MutableStateFlow(prefs.getBoolean(KEY_DAILY_NUDGE, false))
  val dailyNudge: StateFlow<Boolean> = _dailyNudge.asStateFlow()
  private val _nudgeHour = MutableStateFlow(prefs.getInt(KEY_NUDGE_HOUR, 20))
  val nudgeHour: StateFlow<Int> = _nudgeHour.asStateFlow()

  /** When on (the default), the balance starts hidden every time the app is opened; tapping the eye shows it for now. */
  private val _hideOnOpen = MutableStateFlow(prefs.getBoolean(KEY_HIDE_ON_OPEN, hideOnOpenDefault))
  val hideOnOpen: StateFlow<Boolean> = _hideOnOpen.asStateFlow()

  private val _hideBalance = MutableStateFlow(prefs.getBoolean(KEY_HIDE_BALANCE, false) || _hideOnOpen.value)
  val hideBalance: StateFlow<Boolean> = _hideBalance.asStateFlow()

  /** Screens whose list/grid switch is set to grid; the rest show a list. */
  private val _gridViews = MutableStateFlow(prefs.getStringSet(KEY_GRID_VIEWS, emptySet()).orEmpty().toSet())
  val gridViews: StateFlow<Set<String>> = _gridViews.asStateFlow()

  private val _currencySymbol = MutableStateFlow(prefs.getString(KEY_CURRENCY, null) ?: Money.defaultSymbol())
  val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

  private val _captureEnabled = MutableStateFlow(prefs.getBoolean(KEY_CAPTURE, false))
  val captureEnabled: StateFlow<Boolean> = _captureEnabled.asStateFlow()

  var isSeeded: Boolean
    get() = prefs.getBoolean(KEY_SEEDED, false)
    set(value) = prefs.edit().putBoolean(KEY_SEEDED, value).apply()

  /** Bumped when the built-in categories, accounts or home layout change, so existing installs are upgraded once. */
  var defaultsVersion: Int
    get() = prefs.getInt(KEY_DEFAULTS_VERSION, 1)
    set(value) = prefs.edit().putInt(KEY_DEFAULTS_VERSION, value).apply()

  /** Picking a ready-made accent clears any custom colour and the palette name. */
  fun setAccent(value: AccentColor) {
    _accent.value = value
    _customAccent.value = null
    _palette.value = null
    prefs.edit().putString(KEY_ACCENT, value.name).remove(KEY_CUSTOM_ACCENT).remove(KEY_PALETTE).apply()
  }

  fun setCustomAccent(argb: Int) {
    _customAccent.value = argb
    _palette.value = null
    prefs.edit().putInt(KEY_CUSTOM_ACCENT, argb).remove(KEY_PALETTE).apply()
  }

  fun setColorStyle(value: ColorStyle) {
    _colorStyle.value = value
    prefs.edit().putString(KEY_COLOR_STYLE, value.name).apply()
  }

  fun setSystemColors(value: Boolean) {
    _systemColors.value = value
    prefs.edit().putBoolean(KEY_SYSTEM_COLORS, value).apply()
  }

  fun setRates(table: RateTable) {
    _rates.value = table
    prefs.edit().putString(KEY_RATES, CurrencyRates.encode(table)).apply()
  }

  fun setBackgroundMode(value: BackgroundMode) {
    _backgroundMode.value = value
    _palette.value = null
    prefs.edit().putString(KEY_BACKGROUND, value.name).remove(KEY_PALETTE).apply()
  }

  fun setHighlight(argb: Int) {
    _highlight.value = argb
    prefs.edit().putInt(KEY_HIGHLIGHT, argb).apply()
  }

  /** Applies a whole palette at once: accent, highlight colour and background. */
  fun applyPalette(palette: AppPalette) {
    _accent.value = palette.accent
    _customAccent.value = palette.seed?.toInt()
    _highlight.value = palette.tertiary.toInt()
    _backgroundMode.value = palette.background
    _palette.value = palette
    prefs.edit()
      .putString(KEY_ACCENT, palette.accent.name)
      .apply { if (palette.seed != null) putInt(KEY_CUSTOM_ACCENT, palette.seed.toInt()) else remove(KEY_CUSTOM_ACCENT) }
      .putInt(KEY_HIGHLIGHT, palette.tertiary.toInt())
      .putString(KEY_BACKGROUND, palette.background.name)
      .putString(KEY_PALETTE, palette.name)
      .apply()
  }

  fun finishOnboarding() {
    _onboarded.value = true
    prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
  }

  /** For tests and for a "show the welcome again" action. */
  fun resetOnboarding() {
    _onboarded.value = false
    prefs.edit().putBoolean(KEY_ONBOARDED, false).apply()
  }

  fun markSplashShown() {
    _splashPending.value = false
    prefs.edit().putBoolean(KEY_SPLASH_SHOWN, true).apply()
  }


  fun setChartType(type: ChartType) {
    _chartType.value = type
    prefs.edit().putString(KEY_CHART, type.name).apply()
  }

  fun setShowTotalOnHero(enabled: Boolean) {
    _showTotalOnHero.value = enabled
    prefs.edit().putBoolean(KEY_HERO_TOTAL, enabled).apply()
    // The simple switch and a hand-picked set are two ways of saying the same thing; the latest one wins.
    setHeroAccounts(null)
  }

  /** Saves (or replaces, by name) a Home arrangement. */
  fun saveLayout(layout: SavedLayout) {
    val updated = _layouts.value.filter { !it.name.equals(layout.name, ignoreCase = true) } + layout
    _layouts.value = updated
    prefs.edit().putString(KEY_LAYOUTS, org.json.JSONArray().also { a -> updated.forEach { a.put(it.toJson()) } }.toString()).apply()
  }

  fun deleteLayout(name: String) {
    val updated = _layouts.value.filter { !it.name.equals(name, ignoreCase = true) }
    _layouts.value = updated
    prefs.edit().putString(KEY_LAYOUTS, org.json.JSONArray().also { a -> updated.forEach { a.put(it.toJson()) } }.toString()).apply()
  }

  private fun readLayouts(): List<SavedLayout> = try {
    val array = org.json.JSONArray(prefs.getString(KEY_LAYOUTS, "[]"))
    (0 until array.length()).mapNotNull { SavedLayout.fromJson(array.getJSONObject(it)) }
  } catch (_: Exception) {
    emptyList()
  }

  /** Turns the lock on with a new PIN. Only a salted hash is stored. */
  fun enableLock(pin: String, useBiometric: Boolean) {
    val salt = com.keepwallet.app.domain.PinHasher.newSalt()
    prefs.edit()
      .putString(KEY_PIN_SALT, salt).putString(KEY_PIN_HASH, com.keepwallet.app.domain.PinHasher.hash(pin, salt))
      .putInt(KEY_PIN_LENGTH, pin.length).putBoolean(KEY_LOCK, true).putBoolean(KEY_BIOMETRIC, useBiometric)
      .apply()
    _pinLength.value = pin.length
    _biometric.value = useBiometric
    _lockEnabled.value = true
  }

  fun disableLock() {
    prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).remove(KEY_PIN_LENGTH).putBoolean(KEY_LOCK, false).putBoolean(KEY_BIOMETRIC, false).apply()
    _lockEnabled.value = false
    _biometric.value = false
  }

  fun verifyPin(pin: String): Boolean {
    val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
    val hash = prefs.getString(KEY_PIN_HASH, null) ?: return false
    return com.keepwallet.app.domain.PinHasher.matches(pin, salt, hash)
  }

  fun setBiometric(enabled: Boolean) {
    _biometric.value = enabled
    prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
  }

  fun setLockTimeout(seconds: Int) {
    _lockTimeout.value = seconds
    prefs.edit().putInt(KEY_LOCK_TIMEOUT, seconds).apply()
  }

  fun setHideInRecents(hide: Boolean) {
    _hideInRecents.value = hide
    prefs.edit().putBoolean(KEY_HIDE_RECENTS, hide).apply()
  }

  /** The "always hide balances" switch in Settings: remembered. */
  fun setHideBalance(hidden: Boolean) {
    _hideBalance.value = hidden
    prefs.edit().putBoolean(KEY_HIDE_BALANCE, hidden).apply()
  }

  fun setRemindBills(value: Boolean) {
    _remindBills.value = value
    prefs.edit().putBoolean(KEY_REMIND_BILLS, value).apply()
  }

  fun setBudgetAlerts(value: Boolean) {
    _budgetAlerts.value = value
    prefs.edit().putBoolean(KEY_BUDGET_ALERTS, value).apply()
  }

  fun setDailyNudge(value: Boolean) {
    _dailyNudge.value = value
    prefs.edit().putBoolean(KEY_DAILY_NUDGE, value).apply()
  }

  fun setNudgeHour(hour: Int) {
    _nudgeHour.value = hour.coerceIn(0, 23)
    prefs.edit().putInt(KEY_NUDGE_HOUR, _nudgeHour.value).apply()
  }

  fun setHideOnOpen(value: Boolean) {
    _hideOnOpen.value = value
    prefs.edit().putBoolean(KEY_HIDE_ON_OPEN, value).apply()
  }

  /** The eye on the balance card. With "hide on open" it only lasts until the app is left; otherwise it is remembered. */
  fun toggleBalanceVisibility() {
    if (_hideOnOpen.value) _hideBalance.value = !_hideBalance.value else setHideBalance(!_hideBalance.value)
  }

  /** Called when the app goes to the background: the next time it is opened the balance is hidden again. */
  fun hideAgainIfNeeded() {
    if (_hideOnOpen.value) _hideBalance.value = true
  }

  fun setGridView(screen: String, grid: Boolean) {
    val updated = if (grid) _gridViews.value + screen else _gridViews.value - screen
    _gridViews.value = updated
    prefs.edit().putStringSet(KEY_GRID_VIEWS, updated).apply()
  }

  fun setCurrencySymbol(symbol: String) {
    val clean = symbol.trim().take(4).ifEmpty { Money.defaultSymbol() }
    _currencySymbol.value = clean
    prefs.edit().putString(KEY_CURRENCY, clean).apply()
  }

  fun setCaptureEnabled(enabled: Boolean) {
    _captureEnabled.value = enabled
    prefs.edit().putBoolean(KEY_CAPTURE, enabled).apply()
  }

  /** Read straight from disk so the listener service always sees the latest switch position. */
  fun isCaptureEnabledNow(): Boolean = prefs.getBoolean(KEY_CAPTURE, false)

  private fun readAccent(): AccentColor {
    readEnumOrNull<AccentColor>(KEY_ACCENT)?.let { return it }
    // Older versions had three fixed styles; carry the choice over.
    return when (prefs.getString(KEY_OLD_THEME, null)) {
      "POCKETS" -> AccentColor.VIOLET
      "RHYTHM" -> AccentColor.TERRACOTTA
      else -> AccentColor.GRAPHITE
    }
  }

  private fun readBackground(): BackgroundMode {
    readEnumOrNull<BackgroundMode>(KEY_BACKGROUND)?.let { return it }
    return when (prefs.getString(KEY_OLD_DARK, null)) {
      "LIGHT" -> BackgroundMode.LIGHT
      "DARK" -> BackgroundMode.DARK
      else -> BackgroundMode.SYSTEM
    }
  }

  private inline fun <reified E : Enum<E>> readEnumOrNull(key: String): E? =
    prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

  private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E = readEnumOrNull<E>(key) ?: default

  private companion object {
    const val PREFS_NAME = "keep_wallet_settings"
    const val KEY_ACCENT = "accent_color"
    const val KEY_BACKGROUND = "background_mode"
    const val KEY_CHART = "chart_type"
    const val KEY_HERO_TOTAL = "hero_total"
    const val KEY_HERO_ACCOUNTS = "hero_accounts"
    const val KEY_CUSTOM_ACCENT = "custom_accent"
    const val KEY_HIGHLIGHT = "highlight_color"
    const val KEY_COLOR_STYLE = "color_style"
    const val KEY_SYSTEM_COLORS = "system_colors"
    const val KEY_RATES = "currency_rates"
    const val KEY_DETECTION_LOG = "detection_log"
    const val KEY_SEEN_ALERTS = "seen_alerts"
    const val SEEN_ALERTS_MAX = 300
    const val DETECTION_LOG_SIZE = 25
    const val KEY_CELEBRATED = "celebrated_month"
    const val KEY_PALETTE = "palette"
    const val KEY_ONBOARDED = "onboarded"
    const val KEY_SPLASH_SHOWN = "splash_shown"
    const val KEY_HIDE_BALANCE = "hide_balance"
    const val KEY_HIDE_ON_OPEN = "hide_balance_on_open"
    const val KEY_REMIND_BILLS = "remind_bills"
    const val KEY_BUDGET_ALERTS = "budget_alerts"
    const val KEY_DAILY_NUDGE = "daily_nudge"
    const val KEY_NUDGE_HOUR = "nudge_hour"
    const val KEY_LOOK_VERSION = "look_version"
    const val LOOK_VERSION = 2
    const val KEY_LOCK = "lock_enabled"
    const val KEY_PIN_SALT = "pin_salt"
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_PIN_LENGTH = "pin_length"
    const val KEY_BIOMETRIC = "lock_biometric"
    const val KEY_LOCK_TIMEOUT = "lock_timeout"
    const val KEY_HIDE_RECENTS = "hide_in_recents"
    const val KEY_LAYOUTS = "saved_layouts"
    const val KEY_GRID_VIEWS = "grid_views"
    const val KEY_OLD_THEME = "theme_style"
    const val KEY_OLD_DARK = "dark_mode"
    const val KEY_CURRENCY = "currency_symbol"
    const val KEY_CAPTURE = "notification_capture_enabled"
    const val KEY_SEEDED = "seeded"
    const val KEY_DEFAULTS_VERSION = "defaults_version"
  }
}
