package com.dayline.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons users can pick for projects, lists and habits; persisted by key. */
object IconCatalog {
    val icons: List<Pair<String, ImageVector>> = listOf(
        "folder" to Icons.Rounded.Folder,
        "work" to Icons.Rounded.Work,
        "home" to Icons.Rounded.Home,
        "school" to Icons.Rounded.School,
        "book" to Icons.AutoMirrored.Rounded.MenuBook,
        "code" to Icons.Rounded.Code,
        "idea" to Icons.Rounded.Lightbulb,
        "rocket" to Icons.Rounded.RocketLaunch,
        "star" to Icons.Rounded.Star,
        "heart" to Icons.Rounded.Favorite,
        "fitness" to Icons.Rounded.FitnessCenter,
        "hike" to Icons.Rounded.Hiking,
        "spa" to Icons.Rounded.Spa,
        "meditate" to Icons.Rounded.SelfImprovement,
        "mind" to Icons.Rounded.Psychology,
        "water" to Icons.Rounded.WaterDrop,
        "sleep" to Icons.Rounded.Bedtime,
        "food" to Icons.Rounded.Restaurant,
        "coffee" to Icons.Rounded.Coffee,
        "shopping" to Icons.Rounded.ShoppingCart,
        "savings" to Icons.Rounded.Savings,
        "travel" to Icons.Rounded.Flight,
        "music" to Icons.Rounded.MusicNote,
        "art" to Icons.Rounded.Brush,
        "palette" to Icons.Rounded.Palette,
        "movie" to Icons.Rounded.Movie,
        "language" to Icons.Rounded.Language,
        "garden" to Icons.Rounded.LocalFlorist,
        "pets" to Icons.Rounded.Pets,
    )
    private val byKey = icons.toMap()

    fun get(key: String): ImageVector = byKey[key] ?: Icons.Rounded.Folder
}
