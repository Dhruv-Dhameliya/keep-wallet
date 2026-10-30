package com.keepwallet.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TableChart
import android.content.Intent
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Sync
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.keepwallet.app.ui.components.SegmentedToggle
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.BuildConfig
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.AppPalette
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.BackupManager
import com.keepwallet.app.data.ChartType
import com.keepwallet.app.domain.Money
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.PlanSection
import com.keepwallet.app.ui.components.AmountText
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.ConfirmDialog
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.KeepWalletMark
import com.keepwallet.app.ui.components.SubScreenHeader
import com.keepwallet.app.ui.theme.extra
import java.time.LocalDate

// ---- Building blocks --------------------------------------------------------------------------------

/** A section name: plain text above its settings, nothing around it. */
@Composable
fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
  Text(
    title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp)
  )
}

@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  AppCard(modifier.fillMaxWidth(), content = content)
}

/** One row: optional icon, title, subtitle and a trailing control; clickable when [onClick] is given. */
@Composable
fun SettingsRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  titleColor: Color = MaterialTheme.colorScheme.onSurface,
  subtitleTag: String? = null,
  enabled: Boolean = true,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null
) {
  val click = if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(click)
      .then(if (subtitleTag != null) Modifier.testTag(subtitleTag) else Modifier)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      IconTile(icon, 32.dp)
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = if (enabled) titleColor else MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (trailing != null) {
      Spacer(Modifier.width(8.dp))
      trailing()
    } else if (onClick != null) {
      Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
  }
}

@Composable
private fun IconTile(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 34.dp) {
  val tint = MaterialTheme.colorScheme.primary
  Box(
    Modifier.size(size).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.18f)),
    contentAlignment = Alignment.Center
  ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f)) }
}

@Composable
private fun RowDivider() {
  HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** A row's place in the search: what it's called and which words should find it. */
private class Setting(val keywords: String, val heading: String? = null, val content: @Composable () -> Unit)

private class Group(val id: String, val title: String, val icon: ImageVector, val summary: String, val settings: List<Setting>)

/** The settings index reads top to bottom in this order; each heading gathers the sections that belong together. */
private val IndexOrder = listOf(
  "Make it yours" to listOf("appearance", "home"),
  "Money" to listOf("money"),
  "Automatic" to listOf("capture", "notifications"),
  "Safety and data" to listOf("privacy", "backup", "data"),
  "About" to listOf("about")
)

/** A found setting in the search results: its section name as plain text, then its rows in one card. */
@Composable
private fun SettingsSection(group: Group, items: List<Setting>) {
  Column(Modifier.fillMaxWidth().testTag("group_${group.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    SettingsSectionTitle(group.title, Modifier.testTag("section_${group.id}"))
    AppCard(Modifier.fillMaxWidth()) {
      items.forEachIndexed { index, setting ->
        if (index > 0) RowDivider()
        setting.content()
      }
    }
  }
}

/** The settings of one section, in clusters: each cluster has a plain heading and its own card. */
private fun androidx.compose.foundation.lazy.LazyListScope.sectionClusters(group: Group) {
  val clusters = mutableListOf<MutableList<Setting>>()
  group.settings.forEach { setting ->
    if (setting.heading != null || clusters.isEmpty()) clusters += mutableListOf(setting) else clusters.last() += setting
  }
  clusters.forEachIndexed { index, cluster ->
    item(key = "cluster_$index") {
      Column(Modifier.fillMaxWidth().testTag("cluster_${group.id}_$index"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cluster.first().heading?.let { SettingsSectionTitle(it) }
        AppCard(Modifier.fillMaxWidth()) {
          cluster.forEachIndexed { row, setting ->
            if (row > 0) RowDivider()
            setting.content()
          }
        }
      }
    }
  }
}

// ---- The screen -------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
  onOpenCategories: () -> Unit,
  onOpenBalance: () -> Unit,
  onOpenCustomize: () -> Unit,
  onOpenDetection: () -> Unit,
  onOpenConverter: () -> Unit = {},
  onOpenReview: () -> Unit = {},
  onOpenPlan: (PlanSection) -> Unit = {},
  /** Which section page this is; null is the index. */
  section: String? = null,
  onOpenSection: (String) -> Unit = {},
  query: String = "",
  onQuery: (String) -> Unit = {},
  modifier: Modifier = Modifier,
  listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val accent by viewModel.accent.collectAsStateWithLifecycle()
  val background by viewModel.backgroundMode.collectAsStateWithLifecycle()
  val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
  val colorStyle by viewModel.colorStyle.collectAsStateWithLifecycle()
  val systemColors by viewModel.systemColors.collectAsStateWithLifecycle()
  val palette by viewModel.palette.collectAsStateWithLifecycle()
  val chartType by viewModel.chartType.collectAsStateWithLifecycle()
  val showTotal by viewModel.showTotalOnHero.collectAsStateWithLifecycle()
  val heroAccountsPicked by viewModel.heroAccounts.collectAsStateWithLifecycle()
  val hideBalance by viewModel.hideBalance.collectAsStateWithLifecycle()
  val hideOnOpen by viewModel.hideOnOpen.collectAsStateWithLifecycle()
  val remindBills by viewModel.remindBills.collectAsStateWithLifecycle()
  val budgetAlerts by viewModel.budgetAlerts.collectAsStateWithLifecycle()
  val dailyNudge by viewModel.dailyNudge.collectAsStateWithLifecycle()
  val nudgeHour by viewModel.nudgeHour.collectAsStateWithLifecycle()
  // Android 13 and later ask before an app may show notifications.
  val context = androidx.compose.ui.platform.LocalContext.current
  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (!granted) viewModel.showMessage("Notifications are blocked for Keep Wallet in the phone's settings")
  }
  fun askForNotifications(turningOn: Boolean) {
    if (turningOn && android.os.Build.VERSION.SDK_INT >= 33 &&
      androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
  }
  val currency by viewModel.currencySymbol.collectAsStateWithLifecycle()
  val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()

  var confirmSample by remember { mutableStateOf(false) }
  var confirmErase by remember { mutableStateOf(false) }
  var pendingImport by remember { mutableStateOf<Uri?>(null) }
  var showPinSetup by remember { mutableStateOf(false) }
  var showDisableLock by remember { mutableStateOf(false) }
  var showChangePin by remember { mutableStateOf(false) }
  val lockEnabled by viewModel.lockEnabled.collectAsStateWithLifecycle()
  val biometricOn by viewModel.biometricEnabled.collectAsStateWithLifecycle()
  val lockTimeout by viewModel.lockTimeout.collectAsStateWithLifecycle()
  val hideRecents by viewModel.hideInRecents.collectAsStateWithLifecycle()
  val biometricAvailable = LocalBiometric.current?.available() == true

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
    if (uri != null) viewModel.exportBackup(uri)
  }
  val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
    if (uri != null) viewModel.exportCsv(uri)
  }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) pendingImport = uri
  }

  val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
  val autoBackupUri by viewModel.autoBackupUri.collectAsStateWithLifecycle()
  val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsStateWithLifecycle()
  val lastAutoBackupTime by viewModel.lastAutoBackupTime.collectAsStateWithLifecycle()


  val folderTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) {
      try {
        context.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
      } catch (_: Exception) {}
      viewModel.setAutoBackupUri(uri.toString())
    }
  }

  val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) viewModel.importSpreadsheet(uri)
  }

  val groups = listOf(
    Group(
      "appearance", "Appearance", Icons.Rounded.Palette, "${palette?.label ?: "Custom"} · colours, background, currency",
      listOf(
        Setting("preview theme look", heading = "Theme") { AppearancePreview(accent) },
        Setting("palette palettes theme ready made colours preset") {
          Column(Modifier.padding(vertical = 14.dp)) {
            Text("Palettes", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 14.dp))
            Text(
              "One tap sets the accent, highlight and background together.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(
              Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).testTag("palette_row"),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              AppPalette.classic.forEach { option ->
                PaletteCard(option, selected = palette == option) { viewModel.applyPalette(option) }
              }
            }
            Spacer(Modifier.height(14.dp))
            val thisMonth = remember { java.time.LocalDate.now().monthValue }
            val inSeason = remember(thisMonth) { AppPalette.suggestedFor(thisMonth) }
            Text("Seasonal packs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 14.dp))
            Text(
              "Themed looks for the time of year. ${inSeason.label} fits this month.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 14.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(
              Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp).testTag("palette_row_seasonal"),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // The pack for this month comes first.
              (listOf(inSeason) + AppPalette.seasonal.filter { it != inSeason }).forEach { option ->
                PaletteCard(option, selected = palette == option, badge = if (option == inSeason) "Now" else null) { viewModel.applyPalette(option) }
              }
            }
          }
        },
        Setting("accent colour color theme custom hue slider", heading = "Colours") {
          Column(Modifier.padding(14.dp)) {
            Text("Accent colour", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              AccentColor.entries.forEach { option ->
                val selected = option == accent && customAccent == null
                Box(
                  Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(option.argb))
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape))
                    .clickable(role = Role.RadioButton) { viewModel.setAccent(option) }
                    .semantics { contentDescription = option.label + if (selected) ", selected" else "" }
                    .testTag("accent_${option.name}"),
                  contentAlignment = Alignment.Center
                ) {
                  if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                }
              }
            }
            Spacer(Modifier.height(14.dp))
            // Any colour of your own: slide through the hues; it is used as the accent straight away.
            val hsv = remember(customAccent, accent) {
              FloatArray(3).also { android.graphics.Color.colorToHSV(customAccent ?: accent.argb.toInt(), it) }
            }
            var hue by remember(customAccent) { mutableStateOf(hsv[0]) }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Custom colour", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(112.dp))
              Box(
                Modifier
                  .size(26.dp)
                  .clip(CircleShape)
                  .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.72f, 0.62f))))
                  .then(if (customAccent != null) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
              )
            }
            androidx.compose.material3.Slider(
              value = hue,
              onValueChange = { hue = it },
              onValueChangeFinished = { viewModel.setCustomAccent(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.72f, 0.62f))) },
              valueRange = 0f..359f,
              modifier = Modifier.fillMaxWidth().testTag("custom_hue")
            )
          }
        },
        Setting("background light dark black white pure mode") {
          Column(Modifier.padding(14.dp)) {
            Text("Background", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              BackgroundMode.entries.forEach { mode ->
                BackgroundTile(mode, mode == background, Modifier.weight(1f)) { viewModel.setBackgroundMode(mode) }
              }
            }
          }
        },
        Setting("colour style vibrant balanced faithful neutral tonal material you wallpaper dynamic system colours") {
          Column(Modifier.padding(14.dp)) {
            Text("Colour style", style = MaterialTheme.typography.titleSmall)
            Text(
              colorStyle.hint,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              com.keepwallet.app.data.ColorStyle.entries.forEach { option ->
                FilterChip(
                  selected = colorStyle == option && !systemColors,
                  onClick = { viewModel.setColorStyle(option) },
                  label = { Text(option.label) },
                  enabled = !systemColors,
                  modifier = Modifier.testTag("color_style_${option.name}")
                )
              }
            }
            if (android.os.Build.VERSION.SDK_INT >= 31) {
              Spacer(Modifier.height(6.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                  Text("Use wallpaper colours", style = MaterialTheme.typography.titleSmall)
                  Text(
                    "Material You: take the colours from your wallpaper",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(checked = systemColors, onCheckedChange = viewModel::setSystemColors, modifier = Modifier.testTag("system_colors_switch"))
              }
            }
          }
        },
        Setting("currency symbol money", heading = "Currency") {
          Column(Modifier.padding(14.dp)) {
            Text("Currency symbol", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Money.presetSymbols.forEach { symbol ->
                FilterChip(
                  selected = currency == symbol,
                  onClick = { viewModel.setCurrency(symbol) },
                  label = { Text(symbol) },
                  modifier = Modifier.testTag("currency_$symbol")
                )
              }
            }
          }
        }
      )
    ),
    Group(
      "home", "Home", Icons.Rounded.Dashboard, "Layout, graph and the balance card",
      listOf(
        Setting("customize home sections order reorder add remove", heading = "Layout") {
          SettingsRow(
            title = "Customize home",
            subtitle = "Choose, reorder and add sections",
            icon = Icons.Rounded.Dashboard,
            onClick = onOpenCustomize,
            modifier = Modifier.testTag("settings_customize_home")
          )
        },
        Setting("graph chart style bars line donut") {
          Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconTile(Icons.Rounded.BarChart)
              Spacer(Modifier.width(12.dp))
              Column {
                Text("Spending graph style", style = MaterialTheme.typography.titleSmall)
                Text("How the home graph is drawn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(ChartType.BAR to "Bars", ChartType.LINE to "Line", ChartType.DONUT to "Donut").forEach { (type, label) ->
                FilterChip(
                  selected = chartType == type,
                  onClick = { viewModel.setChartType(type) },
                  label = { Text(label) },
                  modifier = Modifier.testTag("chart_style_${type.name}")
                )
              }
            }
          }
        },
        Setting("total balance card hero accounts", heading = "Balance card") {
          SettingsRow(
            title = "Show total on balance card",
            subtitle = if (heroAccountsPicked != null) "Custom: the accounts ticked on the Balance page" else if (showTotal) "Card shows bank, wallets and cash together" else "Card shows your bank balance only",
            icon = Icons.Rounded.Visibility,
            trailing = { Switch(checked = showTotal && heroAccountsPicked == null, onCheckedChange = viewModel::setShowTotalOnHero, modifier = Modifier.testTag("hero_total_switch")) }
          )
        },
        Setting("hide balance privacy mask amounts") {
          SettingsRow(
            title = "Hide balances",
            subtitle = "Always mask the figures on the card",
            icon = Icons.Rounded.VisibilityOff,
            trailing = { Switch(checked = hideBalance, onCheckedChange = viewModel::setHideBalance, modifier = Modifier.testTag("hide_balance_switch")) }
          )
        },
        Setting("hide balance on open start hidden privacy every time") {
          SettingsRow(
            title = "Hide balance on open",
            subtitle = "The card starts hidden each time you open the app",
            icon = Icons.Rounded.VisibilityOff,
            trailing = { Switch(checked = hideOnOpen, onCheckedChange = viewModel::setHideOnOpen, modifier = Modifier.testTag("hide_on_open_switch")) }
          )
        }
      )
    ),
    Group(
      "money", "Money", Icons.Rounded.AccountBalanceWallet, "${data.accounts.size} accounts · ${data.categories.size} categories",
      listOf(
        Setting("accounts balances bank wallet cash", heading = "Accounts") {
          SettingsRow(
            title = "Accounts & balances",
            subtitle = "Bank accounts, wallets and cash",
            icon = Icons.Rounded.AccountBalanceWallet,
            onClick = onOpenBalance,
            modifier = Modifier.testTag("settings_balance")
          )
        },
        Setting("categories names icons colours limits", heading = "Plan") {
          SettingsRow(
            title = "Categories",
            subtitle = "Names, icons, colours and limits",
            icon = Icons.Rounded.Category,
            onClick = onOpenCategories,
            modifier = Modifier.testTag("settings_manage")
          )
        },
        Setting("bills recurring subscriptions rent salary repeat") {
          SettingsRow(
            title = "Bills & subscriptions",
            subtitle = if (data.recurring.isEmpty()) "Rent, subscriptions and salary that repeat" else "${data.recurring.count { it.active }} active",
            icon = Icons.Rounded.Event,
            onClick = { onOpenPlan(PlanSection.BILLS) },
            modifier = Modifier.testTag("settings_bills")
          )
        },
        Setting("savings goals targets") {
          SettingsRow(
            title = "Savings goals",
            subtitle = if (data.goals.isEmpty()) "Save towards a trip, a laptop or a rainy day" else "${data.goals.size} ${if (data.goals.size == 1) "goal" else "goals"}",
            icon = Icons.Rounded.Savings,
            onClick = { onOpenPlan(PlanSection.GOALS) },
            modifier = Modifier.testTag("settings_goals")
          )
        },
        Setting("review report year month summary savings net worth forecast pdf", heading = "Tools") {
          SettingsRow(
            title = "Monthly & yearly review",
            subtitle = "Savings, net worth, forecast and a PDF",
            icon = Icons.Rounded.Assessment,
            onClick = onOpenReview,
            modifier = Modifier.testTag("settings_review")
          )
        },
        Setting("currency converter exchange rates foreign money travel") {
          SettingsRow(
            title = "Currency converter",
            subtitle = "Convert between currencies, works offline",
            icon = Icons.Rounded.CurrencyExchange,
            onClick = onOpenConverter,
            modifier = Modifier.testTag("settings_converter")
          )
        }
      )
    ),
    Group(
      "capture", "Smart capture", Icons.Rounded.Notifications, if (captureEnabled) "On · reads payment alerts" else "Off",
      listOf(
        Setting("detect payments notifications capture sms upi") {
          SettingsRow(
            title = "Detect payments",
            subtitle = if (captureEnabled) "On · reads payment alerts and asks before saving" else "Off",
            icon = Icons.Rounded.Notifications,
            onClick = onOpenDetection,
            modifier = Modifier.testTag("settings_detection")
          )
        }
      )
    ),
    Group(
      "notifications", "Notifications", Icons.Rounded.NotificationsActive,
      listOf(remindBills, budgetAlerts, dailyNudge).count { it }.let { if (it == 0) "All off" else "$it of 3 on" },
      buildList {
        add(
          Setting("bill reminders due subscription rent notify") {
            SettingsRow(
              title = "Bill reminders",
              subtitle = "The day before a bill is due and on the day",
              icon = Icons.Rounded.EventRepeat,
              trailing = { Switch(checked = remindBills, onCheckedChange = { askForNotifications(it); viewModel.setRemindBills(it) }, modifier = Modifier.testTag("remind_bills_switch")) }
            )
          }
        )
        add(
          Setting("budget alerts limit 80 percent over notify") {
            SettingsRow(
              title = "Budget alerts",
              subtitle = "When a category reaches 80% and when it goes over",
              icon = Icons.Rounded.PieChart,
              trailing = { Switch(checked = budgetAlerts, onCheckedChange = { askForNotifications(it); viewModel.setBudgetAlerts(it) }, modifier = Modifier.testTag("budget_alerts_switch")) }
            )
          }
        )
        add(
          Setting("daily nudge reminder evening log spending notify") {
            Column {
              SettingsRow(
                title = "Evening nudge",
                subtitle = "A gentle reminder if nothing was added today",
                icon = Icons.Rounded.NotificationsActive,
                trailing = { Switch(checked = dailyNudge, onCheckedChange = { askForNotifications(it); viewModel.setDailyNudge(it) }, modifier = Modifier.testTag("daily_nudge_switch")) }
              )
              if (dailyNudge) {
                Row(Modifier.padding(start = 66.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf(18, 20, 22).forEach { hour ->
                    FilterChip(
                      selected = nudgeHour == hour,
                      onClick = { viewModel.setNudgeHour(hour) },
                      label = { Text("%d:00".format(hour)) },
                      modifier = Modifier.testTag("nudge_hour_$hour")
                    )
                  }
                }
              }
            }
          }
        )
      }
    ),
    Group(
      "privacy", "Privacy & security", Icons.Rounded.Lock, if (lockEnabled) "App lock is on" else "App lock is off",
      buildList {
        add(
          Setting("app lock pin password fingerprint biometric security privacy") {
            SettingsRow(
              title = "App lock",
              subtitle = if (lockEnabled) "On · asks for your PIN${if (biometricOn) " or fingerprint" else ""}" else "Ask for a PIN before the app opens",
              icon = Icons.Rounded.Lock,
              trailing = {
                Switch(
                  checked = lockEnabled,
                  onCheckedChange = { if (it) showPinSetup = true else showDisableLock = true },
                  modifier = Modifier.testTag("lock_switch")
                )
              }
            )
          }
        )
        if (lockEnabled) {
          add(
            Setting("lock now") {
              SettingsRow(
                title = "Lock now",
                subtitle = "Close the app behind the PIN screen",
                icon = Icons.Rounded.LockClock,
                onClick = {
                  viewModel.lockNow()
                },
                modifier = Modifier.testTag("lock_now")
              )
            }
          )
          add(
            Setting("lock timeout ask for pin after minutes immediately") {
              Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text("Ask for the PIN", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf(0 to "Every time", 60 to "After 1 min", 300 to "After 5 min").forEach { (seconds, label) ->
                    FilterChip(
                      selected = lockTimeout == seconds,
                      onClick = { viewModel.setLockTimeout(seconds) },
                      label = { Text(label) },
                      modifier = Modifier.testTag("lock_timeout_$seconds")
                    )
                  }
                }
              }
            }
          )
          if (biometricAvailable) {
            add(
              Setting("fingerprint face biometric unlock") {
                SettingsRow(
                  title = "Unlock with fingerprint",
                  subtitle = "The PIN always works as a fallback",
                  icon = Icons.Rounded.Fingerprint,
                  trailing = { Switch(checked = biometricOn, onCheckedChange = viewModel::setBiometric, modifier = Modifier.testTag("biometric_switch")) }
                )
              }
            )
          }
          add(
            Setting("hide recent apps preview screenshots") {
              SettingsRow(
                title = "Hide in recent apps",
                subtitle = "Blanks the app's preview and blocks screenshots while the lock is on",
                icon = Icons.Rounded.VisibilityOff,
                trailing = { Switch(checked = hideRecents, onCheckedChange = viewModel::setHideInRecents, modifier = Modifier.testTag("hide_recents_switch")) }
              )
            }
          )
          add(
            Setting("change pin") {
              SettingsRow(title = "Change PIN", icon = Icons.Rounded.Password, onClick = { showChangePin = true }, modifier = Modifier.testTag("change_pin"))
            }
          )
        }
      }
    ),
    Group(
      "backup", "Backup & sync", Icons.Rounded.Cloud, "Export, import and spreadsheet",
      listOf(
        Setting("export backup save file", heading = "Backup") {
          SettingsRow(
            title = "Export backup",
            subtitle = "Save everything, receipts included, to one file",
            icon = Icons.Rounded.Upload,
            onClick = { exportLauncher.launch(BackupManager.suggestedFileName(LocalDate.now())) },
            modifier = Modifier.testTag("export_backup")
          )
        },
        Setting("import restore backup file new phone") {
          SettingsRow(
            title = "Import backup",
            subtitle = "Restore from a backup file, e.g. on a new phone",
            icon = Icons.Rounded.Download,
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.testTag("import_backup")
          )
        },
        Setting("import csv spreadsheet bank statement", heading = "Import CSV") {
          SettingsRow(
            title = "Import CSV statement",
            subtitle = "Import entries from a bank or spreadsheet CSV file",
            icon = Icons.Rounded.Description,
            onClick = { csvImportLauncher.launch(arrayOf("*/*", "text/*")) },
            modifier = Modifier.testTag("import_csv")
          )
        },
        Setting("auto automated local backup folder", heading = "Automated Backups") {
          SettingsRow(
            title = "Automated local backup",
            subtitle = "Periodically back up to a folder on your device",
            icon = Icons.Rounded.Backup,
            trailing = {
              Switch(
                checked = autoBackupEnabled,
                onCheckedChange = viewModel::setAutoBackupEnabled,
                modifier = Modifier.testTag("auto_backup_switch")
              )
            }
          )
        },
        Setting("auto backup folder destination location") {
          SettingsRow(
            title = "Backup folder",
            subtitle = if (autoBackupUri != null) "Folder chosen (tap to change)" else "Tap to choose a folder",
            icon = Icons.Rounded.FolderOpen,
            onClick = { folderTreeLauncher.launch(null) },
            modifier = Modifier.testTag("auto_backup_folder")
          )
        },
        Setting("auto backup frequency daily weekly") {
          SettingsRow(
            title = "Frequency",
            subtitle = if (autoBackupFrequency == "DAILY") "Daily backup" else "Weekly backup",
            icon = Icons.Rounded.EventRepeat,
            trailing = {
              SegmentedToggle(
                options = listOf("WEEKLY" to "Weekly", "DAILY" to "Daily"),
                selected = autoBackupFrequency,
                onSelect = viewModel::setAutoBackupFrequency,
                modifier = Modifier.width(160.dp).testTag("auto_backup_freq_toggle")
              )
            }
          )
        },
        Setting("auto backup now run trigger") {
          val lastBackupStr = if (lastAutoBackupTime > 0) {
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").format(
              Instant.ofEpochMilli(lastAutoBackupTime).atZone(ZoneId.systemDefault())
            )
          } else "Never"
          SettingsRow(
            title = "Back up now",
            subtitle = "Last backup: $lastBackupStr",
            icon = Icons.Rounded.Sync,
            onClick = viewModel::runAutoBackupNow,
            modifier = Modifier.testTag("auto_backup_run_now")
          )
        },
        Setting("export csv spreadsheet excel sheets", heading = "Spreadsheets") {
          SettingsRow(
            title = "Export to spreadsheet",
            subtitle = "Every entry as a CSV file for Excel or Sheets",
            icon = Icons.Rounded.TableChart,
            onClick = { csvLauncher.launch(BackupManager.suggestedCsvName(LocalDate.now())) },
            modifier = Modifier.testTag("export_csv")
          )
        },
        Setting("google sync cloud") {
          SettingsRow(
            title = "Google sync",
            subtitle = "Coming soon · keep devices in step automatically",
            icon = Icons.Rounded.Cloud,
            onClick = viewModel::googleSyncInfo,
            modifier = Modifier.testTag("google_sync"),
            trailing = { Text("Soon", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
          )
        }
      )
    ),
    Group(
      "data", "Data", Icons.Rounded.Science, "Sample data and erase",
      listOf(
        Setting("sample demo data load") {
          SettingsRow(
            title = "Load sample data",
            subtitle = "Replace everything with a demo set",
            icon = Icons.Rounded.Science,
            onClick = { confirmSample = true },
            modifier = Modifier.testTag("load_sample")
          )
        },
        Setting("erase delete reset all data") {
          SettingsRow(
            title = "Erase all data",
            subtitle = "Delete every entry, account and receipt",
            icon = Icons.Rounded.DeleteForever,
            titleColor = MaterialTheme.colorScheme.error,
            onClick = { confirmErase = true },
            modifier = Modifier.testTag("erase_all")
          )
        }
      )
    ),
    Group(
      "about", "About", Icons.Rounded.Info, "Version ${BuildConfig.VERSION_NAME}",
      listOf(
        Setting("version about build") {
          SettingsRow(
            title = "Keep Wallet",
            subtitle = "Version ${BuildConfig.VERSION_NAME}",
            icon = Icons.Rounded.Info,
            subtitleTag = "app_version"
          )
        },
        Setting("privacy offline no account ads analytics internet") {
          SettingsRow(
            title = "Private by design",
            subtitle = "Everything stays on this phone. No account, ads, analytics or internet access.",
            icon = Icons.Rounded.Lock
          )
        }
      )
    )
  )

  val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
  val searching = words.isNotEmpty()
  val visible = groups.map { group ->
    group to if (searching) group.settings.filter { s -> words.all { it in s.keywords || it in group.title.lowercase() } } else group.settings
  }.filter { it.second.isNotEmpty() }

  val current = section?.let { id -> groups.firstOrNull { it.id == id } }
  if (current != null) {
    // One section on its own page: a plain header, then its settings in clusters.
    Column(modifier.fillMaxSize().testTag("settings_page_${current.id}")) {
      SubScreenHeader(current.title, onBack = onBack)
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("settings_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        sectionClusters(current)
      }
    }
  } else {
    val barBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(modifier.fillMaxSize().nestedScroll(barBehavior.nestedScrollConnection).testTag("settings_page_index")) {
      MediumTopAppBar(
        title = { Text("Settings", maxLines = 1) },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        scrollBehavior = barBehavior
      )
      LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("settings_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item(key = "summary") {
          AppCard(Modifier.fillMaxWidth().testTag("settings_summary")) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.extra.cardStart),
                contentAlignment = Alignment.Center
              ) { KeepWalletMark(size = 30.dp, knockout = MaterialTheme.extra.cardStart) }
              Spacer(Modifier.width(14.dp))
              Column(Modifier.weight(1f)) {
                Text("Keep Wallet", style = MaterialTheme.typography.titleMedium)
                Text(
                  "${data.transactions.size} entries · ${data.accounts.size} accounts · ${data.categories.size} categories",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Stored only on this phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
        item(key = "search") {
          OutlinedTextField(
            value = query,
            onValueChange = { onQuery(it.take(40)) },
            modifier = Modifier.fillMaxWidth().testTag("settings_search"),
            placeholder = { Text("Search settings") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
              if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear search") }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
              unfocusedBorderColor = Color.Transparent,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
          )
        }
        if (searching) {
          if (visible.isEmpty()) {
            item(key = "nothing") {
              Text(
                "No setting matches \"${query.trim()}\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp).testTag("settings_no_match")
              )
            }
          }
          visible.forEach { (group, items) ->
            item(key = group.id) { SettingsSection(group, items) }
          }
        } else {
          // The index: headings in the order you would look for things, each section a row that opens its own page.
          IndexOrder.forEach { (heading, ids) ->
            val rows = ids.mapNotNull { id -> groups.firstOrNull { it.id == id } }
            item(key = "index_$heading") {
              Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SettingsSectionTitle(heading, Modifier.testTag("heading_${heading.lowercase().replace(' ', '_')}"))
                AppCard(Modifier.fillMaxWidth()) {
                  rows.forEachIndexed { index, group ->
                    if (index > 0) RowDivider()
                    SettingsRow(
                      title = group.title,
                      subtitle = group.summary,
                      icon = group.icon,
                      onClick = { onOpenSection(group.id) },
                      modifier = Modifier.testTag("settings_section_${group.id}")
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (confirmSample) {
    ConfirmDialog(
      title = "Replace everything with sample data?",
      body = "Your current categories, accounts and transactions will be replaced by a demo set.",
      confirmLabel = "Load sample data",
      destructive = true,
      onConfirm = {
        confirmSample = false
        viewModel.loadSampleData()
      },
      onDismiss = { confirmSample = false }
    )
  }
  if (confirmErase) {
    ConfirmDialog(
      title = "Erase all data?",
      body = "All transactions, accounts, budgets and receipts will be deleted from this phone. This can't be undone.",
      confirmLabel = "Erase everything",
      destructive = true,
      onConfirm = {
        confirmErase = false
        viewModel.eraseAllData()
      },
      onDismiss = { confirmErase = false }
    )
  }
  if (showPinSetup) {
    PinSetupSheet(
      biometricAvailable = biometricAvailable,
      onSave = { pin, biometric ->
        viewModel.enableLock(pin, biometric)
        showPinSetup = false
      },
      onDismiss = { showPinSetup = false }
    )
  }
  if (showDisableLock) {
    PinPromptSheet(
      title = "Turn off app lock",
      confirmLabel = "Turn off",
      onConfirm = { pin ->
        val ok = viewModel.disableLock(pin)
        if (ok) showDisableLock = false
        ok
      },
      onDismiss = { showDisableLock = false }
    )
  }
  if (showChangePin) {
    PinChangeSheet(
      onChange = { current, new ->
        val ok = viewModel.changePin(current, new)
        if (ok) showChangePin = false
        ok
      },
      onDismiss = { showChangePin = false }
    )
  }
  pendingImport?.let { uri ->
    ConfirmDialog(
      title = "Restore this backup?",
      body = "Everything currently in Keep Wallet will be replaced by what's in the backup file. This can't be undone, so export a backup first if you might want the current data.",
      confirmLabel = "Restore",
      destructive = true,
      onConfirm = {
        pendingImport = null
        viewModel.importBackup(uri)
      },
      onDismiss = { pendingImport = null }
    )
  }
}

/** A small card and a small pane that look exactly like the real ones with the choices made so far. */
@Composable
private fun AppearancePreview(accent: AccentColor) {
  val extra = MaterialTheme.extra
  Column(Modifier.fillMaxWidth().padding(14.dp).testTag("appearance_preview"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(extra.cardStart, extra.cardEnd)))
        .padding(14.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          KeepWalletMark(size = 18.dp, knockout = extra.cardStart)
          Spacer(Modifier.width(6.dp))
          Text("${accent.label.uppercase()} · PREVIEW", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = Color.White.copy(alpha = 0.85f))
        }
        AmountText("${"$"}1,234.56", MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), color = Color.White)
      }
    }
    AppCard(Modifier.fillMaxWidth()) {
      Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
          Icon(Icons.Rounded.Category, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Groceries", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text("-${"$"}42.10", style = MaterialTheme.typography.titleSmall)
      }
    }
  }
}

/** A swatch of a background choice: what the page will look like, with its name underneath. */
@Composable
private fun BackgroundTile(mode: BackgroundMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val swatch: Brush = when (mode) {
    BackgroundMode.SYSTEM -> Brush.linearGradient(listOf(Color(0xFFF6F7F7), Color(0xFFF6F7F7), Color(0xFF0C1010), Color(0xFF0C1010)))
    BackgroundMode.LIGHT -> Brush.linearGradient(listOf(Color(0xFFF6F7F7), Color(0xFFF6F7F7)))
    BackgroundMode.DARK -> Brush.linearGradient(listOf(Color(0xFF0C1010), Color(0xFF151A1A)))
    BackgroundMode.BLACK -> Brush.linearGradient(listOf(Color.Black, Color.Black))
    BackgroundMode.WHITE -> Brush.linearGradient(listOf(Color.White, Color.White))
  }
  val ink = if (mode == BackgroundMode.DARK || mode == BackgroundMode.BLACK) Color.White else Color(0xFF121616)
  Column(
    modifier
      .clip(RoundedCornerShape(14.dp))
      .clickable(role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = mode.label + if (selected) ", selected" else "" }
      .testTag("background_${mode.name}"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Box(
      Modifier
        .fillMaxWidth()
        .height(46.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(swatch)
        .border(if (selected) 2.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center
    ) {
      if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = if (mode == BackgroundMode.SYSTEM) MaterialTheme.colorScheme.primary else ink, modifier = Modifier.size(18.dp))
      else Text("Aa", color = ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
    Text(mode.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** A ready-made palette: a swatch showing its accent, highlight and background, and its name. */
@Composable
private fun PaletteCard(palette: AppPalette, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
  val accent = Color(palette.seedArgb)
  val highlight = Color(palette.tertiary)
  val dark = palette.background == BackgroundMode.DARK || palette.background == BackgroundMode.BLACK
  val page = when (palette.background) {
    BackgroundMode.BLACK -> Color.Black
    BackgroundMode.DARK -> Color(0xFF14191A)
    BackgroundMode.WHITE -> Color.White
    else -> Color(0xFFF1F4F4)
  }
  val card = if (dark) Color(0xFF22292A) else Color.White
  Column(
    Modifier
      .width(112.dp)
      .clip(RoundedCornerShape(18.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .then(if (selected) Modifier.border(2.5.dp, accent, RoundedCornerShape(18.dp)) else Modifier)
      .clickable(role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = palette.label + if (selected) ", selected" else "" }
      .padding(8.dp)
      .testTag("palette_${palette.name}"),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(Modifier.fillMaxWidth().height(74.dp).clip(RoundedCornerShape(12.dp)).background(page).padding(7.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(accent, androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.45f)))))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(6.dp)).background(card))
          Box(Modifier.size(22.dp).clip(CircleShape).background(highlight))
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(palette.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
      if (badge != null && !selected) Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
      if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
    }
  }
}
