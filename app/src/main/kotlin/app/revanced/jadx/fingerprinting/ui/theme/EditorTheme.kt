package app.revanced.jadx.fingerprinting.ui.theme

import app.revanced.jadx.fingerprinting.ui.components.CodePanel
import io.github.oshai.kotlinlogging.KLogger
import jadx.api.plugins.gui.JadxGuiContext
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import java.awt.Container
import java.util.ArrayDeque
import javax.swing.UIManager

internal fun applyEditorTheme(codePanel: CodePanel, guiContext: JadxGuiContext, log: KLogger) {
    runCatching {
        guiContext.mainFrame.javaClass.getMethod("getEditorTheme")
            .invoke(guiContext.mainFrame) as? Theme
    }.onFailure { e ->
        log.warn { "JADX getEditorTheme unavailable: ${e.message}" }
    }.getOrNull()?.let { theme ->
        codePanel.setTheme(theme)
        return
    }

    runCatching {
        (guiContext.mainFrame as? Container)?.let { container ->
            findRSyntaxTextArea(container)?.let { source ->
                codePanel.cloneAppearanceFrom(source)
                return
            }
        }
    }.onFailure { e ->
        log.warn { "Could not clone theme from JADX editor: ${e.message}" }
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
        log.warn { "Could not auto-apply editor theme: ${e.message}" }
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