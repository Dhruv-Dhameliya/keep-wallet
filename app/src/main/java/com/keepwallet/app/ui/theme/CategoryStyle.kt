package com.keepwallet.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons a category can use, keyed by the string stored in the database. */
object CategoryIcons {
  private val icons: LinkedHashMap<String, ImageVector> = linkedMapOf(
    "housing" to Icons.Rounded.Home,
    "groceries" to Icons.Rounded.ShoppingBasket,
    "transport" to Icons.Rounded.DirectionsBus,
    "dining" to Icons.Rounded.Restaurant,
    "subscriptions" to Icons.Rounded.PlayCircle,
    "shopping" to Icons.Rounded.ShoppingBag,
    "person" to Icons.Rounded.Person,
    "transfer" to Icons.Rounded.SwapHoriz,
    "other" to Icons.Rounded.Category,
    "cashback" to Icons.Rounded.Redeem,
    "salary" to Icons.Rounded.Payments,
    "gift" to Icons.Rounded.CardGiftcard,
    "health" to Icons.Rounded.MedicalServices,
    "education" to Icons.Rounded.School,
    "entertainment" to Icons.Rounded.Movie,
    "travel" to Icons.Rounded.Flight,
    "bills" to Icons.Rounded.Receipt,
    "fitness" to Icons.Rounded.FitnessCenter,
    "pets" to Icons.Rounded.Pets,
    "savings" to Icons.Rounded.Savings,
    "work" to Icons.Rounded.Work
  )

  val keys: List<String> get() = icons.keys.toList()

  fun get(key: String): ImageVector = icons[key] ?: Icons.Rounded.Category
}

/** Colours offered when creating or editing a category. */
val CategoryPalette: List<Color> = listOf(
  Color(0xFFD9532F), Color(0xFFF59E0B), Color(0xFF2E9E5B), Color(0xFF0D9488),
  Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899),
  Color(0xFFE11D48), Color(0xFF64748B)
)
