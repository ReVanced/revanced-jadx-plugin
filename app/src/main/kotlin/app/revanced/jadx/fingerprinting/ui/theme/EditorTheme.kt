package app.revanced.jadx.fingerprinting.ui.theme

import app.revanced.jadx.fingerprinting.ui.components.CodePanel
import io.github.oshai.kotlinlogging.KLogger
import jadx.api.plugins.gui.JadxGuiContext
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import java.awt.Container
import java.lang.reflect.Method
import java.util.ArrayDeque
import javax.swing.UIManager

private object EditorThemeMethodCache {
    @Volatile private var resolved = false
    @Volatile private var method: Method? = null

    fun get(mainFrame: Any, log: KLogger): Method? {
        if (resolved) return method
        synchronized(this) {
            if (resolved) return method
            method = runCatching { mainFrame.javaClass.getMethod("getEditorTheme") }
                .onFailure { e -> log.warn { "JADX getEditorTheme unavailable: ${e.message}" } }
                .getOrNull()
            resolved = true
            return method
        }
    }
}

@Volatile private var cloneFailureLogged = false
@Volatile private var autoApplyFailureLogged = false

internal fun applyEditorTheme(codePanel: CodePanel, guiContext: JadxGuiContext, log: KLogger) {
    EditorThemeMethodCache.get(guiContext.mainFrame, log)?.let { m ->
        runCatching { m.invoke(guiContext.mainFrame) as? Theme }
            .getOrNull()?.let { theme ->
                codePanel.setTheme(theme)
                return
            }
    }

    runCatching {
        (guiContext.mainFrame as? Container)?.let { container ->
            findRSyntaxTextArea(container)?.let { source ->
                codePanel.cloneAppearanceFrom(source)
                return
            }
        }
    }.onFailure { e ->
        if (!cloneFailureLogged) {
            cloneFailureLogged = true
            log.warn { "Could not clone theme from JADX editor: ${e.message}" }
        }
    }

    runCatching {
        val isDark = UIManager.getBoolean("laf.dark")
        val resource = if (isDark) {
            "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"
        } else {
            "/org/fife/ui/rsyntaxtextarea/themes/default.xml"
        }

        RSyntaxTextArea::class.java.getResourceAsStream(resource)?.use { stream ->
            Theme.load(stream)?.let { theme ->
                codePanel.setTheme(theme)
            }
        }
    }.onFailure { e ->
        if (!autoApplyFailureLogged) {
            autoApplyFailureLogged = true
            log.warn { "Could not auto-apply editor theme: ${e.message}" }
        }
    }
}

private fun findRSyntaxTextArea(container: Container): RSyntaxTextArea? {
    val queue = ArrayDeque<Container>()
    queue.add(container)

    while (queue.isNotEmpty()) {
        queue.poll()?.let { current ->
            for (comp in current.components) {
                if (comp is RSyntaxTextArea) return comp
                if (comp is Container) queue.add(comp)
            }
        }
    }
    return null
}