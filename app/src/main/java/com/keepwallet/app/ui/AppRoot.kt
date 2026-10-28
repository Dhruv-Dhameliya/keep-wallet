package com.keepwallet.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import com.keepwallet.app.ui.components.ConfettiBurst
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.ReceiptStore
import com.keepwallet.app.ui.components.AppBackdrop
import com.keepwallet.app.ui.theme.Motion
import androidx.compose.ui.unit.IntOffset
import com.keepwallet.app.ui.components.DetectedEntryCard
import com.keepwallet.app.ui.components.pressScale
import com.keepwallet.app.ui.screens.ActivityScreen
import com.keepwallet.app.ui.screens.BalanceScreen
import com.keepwallet.app.ui.screens.ConverterScreen
import com.keepwallet.app.ui.screens.CustomizeScreen
import com.keepwallet.app.ui.screens.DetectionScreen
import com.keepwallet.app.ui.screens.EntryDetailsScreen
import com.keepwallet.app.ui.screens.EntryEditorScreen
import com.keepwallet.app.ui.screens.HomeScreen
import com.keepwallet.app.ui.screens.LockScreen
import com.keepwallet.app.ui.screens.InsightsScreen
import com.keepwallet.app.ui.screens.ManageScreen
import com.keepwallet.app.ui.screens.NotificationsScreen
import com.keepwallet.app.ui.screens.MoveMoneySheet
import com.keepwallet.app.ui.screens.PlanScreen
import com.keepwallet.app.ui.screens.ReviewScreen
import com.keepwallet.app.ui.screens.SettingsScreen
import com.keepwallet.app.ui.screens.SplashCard
import com.keepwallet.app.ui.screens.WelcomeScreen
import kotlinx.coroutines.flow.collectLatest

/**
 * What is on screen: a tab, or a page opened over it. [depth] is how many pages are stacked (0 for a tab), which
 * is all the transition needs to know to slide forwards on the way in and backwards on the way out.
 */
private data class Screen(val key: String, val depth: Int)

/** Keys of the settings pages: "settings" is the index, "settings/<section>" one of its sections. */
private const val SETTINGS = "settings"
private const val SETTINGS_SECTION = "settings/"

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppRoot(viewModel: MainViewModel, receipts: ReceiptStore) {
  var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
  // The pages opened over the tab, oldest first, kept as one string so it survives rotation ("settings|settings/home").
  // Back always pops the last one, so it returns to the page you came from, never somewhere else.
  var pagesRaw by rememberSaveable { mutableStateOf("") }
  val pages = remember(pagesRaw) { if (pagesRaw.isEmpty()) emptyList() else pagesRaw.split('|') }
  fun push(key: String) {
    if (pagesRaw.substringAfterLast('|') != key) pagesRaw = if (pagesRaw.isEmpty()) key else "$pagesRaw|$key"
  }
  fun pop() { pagesRaw = pagesRaw.substringBeforeLast('|', "") }
  var settingsQuery by rememberSaveable { mutableStateOf("") }
  val tab = Tab.valueOf(tabName)

  // Scroll positions live here, above the page switch, so coming back to a screen finds it where it was left.
  val homeList = rememberLazyListState()
  val activityList = rememberLazyListState()
  val insightsList = rememberLazyListState()
  val planList = rememberLazyListState()
  val settingsList = rememberLazyListState()
  // Each settings section keeps its own place too, so coming back from a page opened inside it finds it where it was.
  val sectionLists = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }

  val data by viewModel.data.collectAsStateWithLifecycle()
  val editor by viewModel.editor.collectAsStateWithLifecycle()
  val viewingId by viewModel.viewingId.collectAsStateWithLifecycle()
  val banner by viewModel.banner.collectAsStateWithLifecycle()
  val moveMoney by viewModel.moveMoney.collectAsStateWithLifecycle()
  val onboarded by viewModel.onboarded.collectAsStateWithLifecycle()
  val splashPending by viewModel.splashPending.collectAsStateWithLifecycle()
  val ruleSuggestion by viewModel.ruleSuggestion.collectAsStateWithLifecycle()
  val locked by viewModel.locked.collectAsStateWithLifecycle()
  val snackbarHost = remember { SnackbarHostState() }

  fun closePages() { pagesRaw = "" }

  LaunchedEffect(viewModel) {
    viewModel.alertsRequests.collect {
      pagesRaw = "notifications"
    }
  }
  LaunchedEffect(viewModel) {
    viewModel.tabRequests.collect { requested ->
      tabName = requested.name
      closePages()
    }
  }
  // Goals reached and months finished under budget: a tap of haptic feedback and confetti.
  var confettiBurst by remember { mutableIntStateOf(0) }
  val haptics = LocalHapticFeedback.current
  val reduceMotion = LocalContext.current.let { context ->
    remember { android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
  }
  LaunchedEffect(viewModel) {
    viewModel.celebrations.collect {
      haptics.performHapticFeedback(HapticFeedbackType.Confirm)
      if (!reduceMotion) confettiBurst++
    }
  }
  LaunchedEffect(viewModel) {
    // collectLatest: a newer message replaces the one on screen instead of queueing behind it.
    viewModel.messages.collectLatest { message ->
      val result = snackbarHost.showSnackbar(
        message = message.text,
        actionLabel = message.actionLabel,
        duration = if (message.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
      )
      if (result == SnackbarResult.ActionPerformed) message.onAction?.invoke()
    }
  }

  val onSubScreen = pages.isNotEmpty()
  val viewingTransaction = viewingId?.let { id -> data.transactions.firstOrNull { it.id == id } }

  // The entry being viewed was deleted: close its page.
  LaunchedEffect(viewingId, data.transactions, data.loaded) {
    if (data.loaded && viewingId != null && viewingTransaction == null) viewModel.closeDetails()
  }

  // Back goes to the most recent page; handlers registered later win, so the entry pages are last.
  BackHandler(enabled = onSubScreen || tab != Tab.HOME) {
    if (onSubScreen) pop() else tabName = Tab.HOME.name
  }
  BackHandler(enabled = viewingTransaction != null) { viewModel.closeDetails() }
  BackHandler(enabled = editor != null) { viewModel.closeEditor() }

  AppBackdrop {
    Box(Modifier.fillMaxSize()) {
      Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) },
        // The tab screens draw their own top bars and the system insets are applied here, once.
        bottomBar = {
          if (!onSubScreen) AppNavigationBar(current = tab, onSelect = { tabName = it.name })
        },
        floatingActionButton = {
          if (!onSubScreen) {
            val listState = when (tab) {
              Tab.HOME -> homeList
              Tab.ACTIVITY -> activityList
              Tab.INSIGHTS -> insightsList
              Tab.PLAN -> planList
            }
            // Wide with a label at the top of a page, a plain round button once the page has been scrolled.
            val atTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 } }
            ExtendedFloatingActionButton(
              onClick = { viewModel.openNewEntry() },
              expanded = atTop,
              icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
              text = { Text("Add") },
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
              elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 2.dp),
              modifier = Modifier.testTag("tab_add").semantics { contentDescription = "Add transaction" }
            )
          }
        }
      ) { padding ->
        val screen = Screen(pages.lastOrNull() ?: tab.name, pages.size)
        val pageColor = MaterialTheme.colorScheme.background
        AnimatedContent(
          targetState = screen,
          // One motion language, like iOS: a page you open slides in from the right while the one beneath drifts
          // a little to the left; going back is the exact reverse. Tabs slide sideways in the direction you move,
          // so the page to your left arrives from the left. Pages are opaque and only move: no fades, so every
          // frame is a plain translation of two layers and nothing needs blending.
          transitionSpec = {
            val move = tween<IntOffset>(Motion.Page, easing = Motion.Standard)
            when {
              targetState.depth > initialState.depth -> (slideInHorizontally(move) { it } togetherWith
                slideOutHorizontally(move) { -it / 3 })
              targetState.depth < initialState.depth -> (slideInHorizontally(move) { -it / 3 } togetherWith
                slideOutHorizontally(move) { it }).apply { targetContentZIndex = -1f }
              else -> {
                val order = Tab.entries.map { it.name }
                val direction = if (order.indexOf(targetState.key) >= order.indexOf(initialState.key)) 1 else -1
                slideInHorizontally(move) { direction * it } togetherWith slideOutHorizontally(move) { -direction * it }
              }
            }.using(SizeTransform(clip = false))
          },
          modifier = Modifier.fillMaxSize().padding(padding),
          label = "screen"
        ) { target ->
          val key = target.key
          Box(Modifier.fillMaxSize().background(pageColor)) {
            when {
              key == "manage" -> ManageScreen(viewModel, onBack = ::pop)
              key == "detection" -> DetectionScreen(viewModel, onBack = ::pop)
              key == "customize" -> CustomizeScreen(viewModel, onBack = ::pop)
              key == "converter" -> ConverterScreen(viewModel, onBack = ::pop)
              key == "review" -> ReviewScreen(viewModel, onBack = ::pop)
              key == "notifications" -> NotificationsScreen(
                viewModel,
                onBack = ::pop,
                onOpenSettings = { pagesRaw = "$SETTINGS|${SETTINGS_SECTION}notifications" }
              )
              key == "balance" -> BalanceScreen(viewModel, onBack = ::pop)
              key == "plan_page" -> PlanScreen(viewModel, onManageCategories = { push("manage") }, onBack = ::pop)
              key == SETTINGS || key.startsWith(SETTINGS_SECTION) -> {
                val section = key.removePrefix(SETTINGS).removePrefix("/").ifEmpty { null }
                SettingsScreen(
                  viewModel,
                  onBack = ::pop,
                  onOpenCategories = { push("manage") },
                  onOpenBalance = { push("balance") },
                  onOpenCustomize = { push("customize") },
                  onOpenDetection = { push("detection") },
                  onOpenConverter = { push("converter") },
                  onOpenReview = { push("review") },
                  onOpenPlan = { planSection ->
                    viewModel.setPlanSection(planSection)
                    push("plan_page")
                  },
                  section = section,
                  onOpenSection = { push("$SETTINGS_SECTION$it") },
                  query = settingsQuery,
                  onQuery = { settingsQuery = it },
                  listState = if (section == null) settingsList else sectionLists.getOrPut(section) { androidx.compose.foundation.lazy.LazyListState() }
                )
              }
              key == Tab.HOME.name -> HomeScreen(
                viewModel,
                onOpenSettings = { push(SETTINGS) },
                onOpenBalance = { push("balance") },
                onOpenCustomize = { push("customize") },
                onOpenAlerts = { push("notifications") },
                onOpen = viewModel::openDetails,
                listState = homeList
              )
              key == Tab.ACTIVITY.name -> ActivityScreen(viewModel, onEdit = viewModel::openDetails, listState = activityList)
              key == Tab.INSIGHTS.name -> InsightsScreen(viewModel, onOpenConverter = { push("converter") }, onOpenReview = { push("review") }, listState = insightsList)
              else -> PlanScreen(viewModel, onManageCategories = { push("manage") }, listState = planList)
            }
          }
        }
      }

      ConfettiBurst(confettiBurst, Modifier.testTag("confetti"))

      // Floating prompt for a freshly detected payment. Home already shows the same card inline.
      val showBanner = banner != null && editor == null && viewingTransaction == null && !(tab == Tab.HOME && !onSubScreen)
      AnimatedVisibility(
        visible = showBanner,
        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).testTag("detected_banner"),
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut()
      ) {
        banner?.let { entry ->
          DetectedEntryCard(
            entry = entry,
            onAdd = { viewModel.reviewDetected(entry.id) },
            onDismiss = { viewModel.discardDetected(entry.id) }
          )
        }
      }

      // Entry details, then the entry form above it. Both are full pages that slide up.
      var lastViewing by remember { mutableStateOf(viewingTransaction) }
      LaunchedEffect(viewingTransaction) { if (viewingTransaction != null) lastViewing = viewingTransaction }
      val shownViewing = viewingTransaction ?: lastViewing
      AnimatedVisibility(
        visible = viewingTransaction != null,
        enter = slideInHorizontally(tween(Motion.Page, easing = Motion.Standard)) { it },
        exit = slideOutHorizontally(tween(Motion.Page, easing = Motion.Standard)) { it }
      ) {
        shownViewing?.let { EntryDetailsScreen(viewModel, it, data, receipts, onBack = viewModel::closeDetails) }
      }

      var lastEditor by remember { mutableStateOf(editor) }
      LaunchedEffect(editor) { if (editor != null) lastEditor = editor }
      val shownEditor = editor ?: lastEditor
      AnimatedVisibility(
        visible = editor != null,
        enter = slideInVertically(tween(Motion.Page, easing = Motion.Standard)) { it },
        exit = slideOutVertically(tween(Motion.Page, easing = Motion.Standard)) { it }
      ) {
        shownEditor?.let { EntryEditorScreen(viewModel, it, data, receipts) }
      }

      // First launch ever: the logo card, then a short welcome. Both are skipped on every later start.
      var splashDone by rememberSaveable { mutableStateOf(false) }
      AnimatedVisibility(visible = splashPending && !splashDone, enter = fadeIn(tween(1)), exit = fadeOut(tween(Motion.Medium, easing = Motion.Standard))) {
        SplashCard(onFinished = {
          splashDone = true
          viewModel.markSplashShown()
        })
      }
      AnimatedVisibility(
        visible = !onboarded && !(splashPending && !splashDone),
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(200))
      ) {
        WelcomeScreen(viewModel, data)
      }

      // The lock sits above everything, including pages and sheets that were open when the app was left.
      AnimatedVisibility(visible = locked, enter = fadeIn(tween(1)), exit = fadeOut(tween(220))) {
        LockScreen(viewModel)
      }
    }
  }

  moveMoney?.let { state ->
    MoveMoneySheet(viewModel, state, data.categories)
  }

  // After filing a detected payment by hand: offer to remember it as a rule.
  ruleSuggestion?.let { rule ->
    AlertDialog(
      onDismissRequest = viewModel::dismissRuleSuggestion,
      title = { Text("File these automatically?") },
      text = {
        Text(
          "Next time a payment with \"${rule.match}\" in its name is detected, Keep Wallet will put it under " +
            "${data.categories.firstOrNull { it.id == rule.categoryId }?.name ?: "this category"} for you. " +
            "You can change or remove rules in Settings under Detect payments."
        )
      },
      confirmButton = { TextButton(onClick = viewModel::acceptRuleSuggestion, modifier = Modifier.testTag("rule_yes")) { Text("Yes, remember") } },
      dismissButton = { TextButton(onClick = viewModel::dismissRuleSuggestion, modifier = Modifier.testTag("rule_no")) { Text("Not now") } }
    )
  }
}

/** The Material 3 navigation bar: four destinations with the standard indicator pill. */
@Composable
private fun AppNavigationBar(current: Tab, onSelect: (Tab) -> Unit) {
  // Labels would wrap letter by letter at big system font sizes; icons carry the meaning then.
  val showLabels = LocalDensity.current.fontScale <= 1.3f
  val hairline = MaterialTheme.colorScheme.outlineVariant
  NavigationBar(
    modifier = Modifier
      .testTag("bottom_bar")
      .drawBehind { drawLine(hairline, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) },
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    tonalElevation = 0.dp
  ) {
    listOf(
      Triple(Tab.HOME, "Home", Icons.Rounded.Home to Icons.Outlined.Home),
      Triple(Tab.ACTIVITY, "Activity", Icons.Rounded.Receipt to Icons.Outlined.Receipt),
      Triple(Tab.INSIGHTS, "Insights", Icons.Rounded.BarChart to Icons.Outlined.BarChart),
      Triple(Tab.PLAN, "Plan", Icons.Rounded.PieChart to Icons.Outlined.PieChart)
    ).forEach { (tab, label, icons) ->
      val selected = tab == current
      NavigationBarItem(
        selected = selected,
        onClick = { onSelect(tab) },
        icon = { Icon(if (selected) icons.first else icons.second, contentDescription = null) },
        label = if (showLabels) ({ Text(label, maxLines = 1, softWrap = false) }) else null,
        alwaysShowLabel = true,
        modifier = Modifier.semantics { contentDescription = label }.testTag("tab_${tab.name.lowercase()}")
      )
    }
  }
}
