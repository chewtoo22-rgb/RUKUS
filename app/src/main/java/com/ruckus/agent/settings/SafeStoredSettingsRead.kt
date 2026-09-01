package com.ruckus.agent.settings

internal object SafeStoredSettingsRead {
    fun load(valuesProvider: () -> Map<String, *>): RukusSettings = try {
        StoredSettingsDecoder.decode(valuesProvider())
    } catch (_: RuntimeException) {
        RukusSettings()
    }
}
