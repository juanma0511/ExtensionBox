package com.extensionbox.app.ui.screens

data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val category: AppCategory,
    val description: String = ""
)

enum class AppCategory {
    SAFE, CAUTION, EXTREME
}
