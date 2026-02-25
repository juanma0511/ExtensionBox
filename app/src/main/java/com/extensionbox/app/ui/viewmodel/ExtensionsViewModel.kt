package com.extensionbox.app.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExtensionsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _moduleStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val moduleStates: StateFlow<Map<String, Boolean>> = _moduleStates.asStateFlow()

    init {
        loadModuleStates()
    }

    private fun loadModuleStates() {
        val states = mutableMapOf<String, Boolean>()
        for (i in 0 until ModuleRegistry.count()) {
            val key = ModuleRegistry.keyAt(i)
            states[key] = Prefs.isModuleEnabled(context, key, ModuleRegistry.defAt(i))
        }
        _moduleStates.value = states
    }

    fun toggleModule(key: String, enabled: Boolean) {
        Prefs.setModuleEnabled(context, key, enabled)
        val current = _moduleStates.value.toMutableMap()
        current[key] = enabled
        _moduleStates.value = current
    }
}
