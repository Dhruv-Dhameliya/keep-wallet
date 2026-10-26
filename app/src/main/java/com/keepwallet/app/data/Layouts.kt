package com.keepwallet.app.data

import org.json.JSONArray
import org.json.JSONObject

/** One Home arrangement: the sections in order, each switched on or off. */
data class SavedLayout(val name: String, val items: List<Pair<WidgetKind, Boolean>>) {
  fun toJson(): JSONObject = JSONObject().put("name", name).put(
    "items",
    JSONArray().also { array -> items.forEach { (kind, on) -> array.put(JSONObject().put("kind", kind.name).put("on", on)) } }
  )

  companion object {
    fun fromJson(o: JSONObject): SavedLayout? {
      val items = o.optJSONArray("items") ?: return null
      val list = (0 until items.length()).mapNotNull { i ->
        val item = items.optJSONObject(i) ?: return@mapNotNull null
        val kind = WidgetKind.entries.firstOrNull { it.name == item.optString("kind") } ?: return@mapNotNull null
        kind to item.optBoolean("on", true)
      }
      val name = o.optString("name").trim()
      return if (name.isEmpty() || list.isEmpty()) null else SavedLayout(name, list)
    }
  }
}

/** The ready-made arrangements offered next to the user's own. */
object BuiltInLayouts {
  private fun layout(name: String, on: List<WidgetKind>): SavedLayout =
    SavedLayout(name, on.map { it to true } + WidgetKind.entries.filter { it !in on }.map { it to false })

  val minimal = layout("Minimal", listOf(WidgetKind.BALANCE_CARD, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY))

  val detailed = layout(
    "Detailed",
    listOf(
      WidgetKind.BALANCE_CARD, WidgetKind.MONTH_OVERVIEW, WidgetKind.QUICK_STATS, WidgetKind.SPENDING_CHART,
      WidgetKind.HIGHLIGHTS, WidgetKind.POCKETS_GRID, WidgetKind.RECENT_ACTIVITY
    )
  )

  val billsFocus = layout(
    "Bills focus",
    listOf(WidgetKind.BALANCE_CARD, WidgetKind.UPCOMING_BILLS, WidgetKind.MONTH_OVERVIEW, WidgetKind.SAVINGS_GOALS, WidgetKind.RECENT_ACTIVITY)
  )

  val all = listOf(minimal, detailed, billsFocus)
}
