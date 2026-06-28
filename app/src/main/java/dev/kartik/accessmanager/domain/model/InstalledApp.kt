package dev.kartik.accessmanager.domain.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed application on the device.
 *
 * Contains only metadata required for display and identification.
 * Internet access state is NOT part of this model — that belongs to the Rule Engine.
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val uid: Int,
    val isSystemApp: Boolean,
)
