package app.revanced.jadx.fingerprinting.ui.components

import java.util.concurrent.CopyOnWriteArrayList

object EditorSettingsHolder {
    @Volatile
    private var currentSettings: CodePanel.Settings = CodePanel.Settings()

    private val listeners = CopyOnWriteArrayList<(CodePanel.Settings) -> Unit>()

    fun current(): CodePanel.Settings = currentSettings

    fun update(settings: CodePanel.Settings) {
        if (settings == currentSettings) return
        currentSettings = settings
        listeners.forEach { it(settings) }
    }

    fun subscribe(listener: (CodePanel.Settings) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}
