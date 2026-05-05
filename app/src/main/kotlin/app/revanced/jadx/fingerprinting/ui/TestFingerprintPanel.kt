package app.revanced.jadx.fingerprinting.ui

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.ui.components.CodePanel
import app.revanced.jadx.fingerprinting.ui.theme.applyEditorTheme
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.gui.JadxGuiContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

private const val HISTORY_MAX = 30

class TestFingerprintPanel(
    private val context: JadxPluginContext,
    private val guiContext: JadxGuiContext,
) : JPanel(BorderLayout()) {

    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/test-panel")
    private val codePanel = CodePanel()
    private val history = ArrayDeque<String>(HISTORY_MAX)
    private var historyIndex = -1
    private val backBtn = JButton("◀").apply { toolTipText = "Previous script"; isEnabled = false }
    private val fwdBtn = JButton("▶").apply { toolTipText = "Next script"; isEnabled = false }
    private val histLabel = JLabel("")

    init {
        applyEditorTheme(codePanel, guiContext, log)

        val resultPanel = FingerprintResultPanel(
            context = context,
            guiContext = guiContext,
            scriptProvider = {
                val script = codePanel.text
                saveToHistory(script)
                script
            },
            clearAction = { clearEditor() },
        )

        val historyBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(JLabel("History:"))
            add(backBtn)
            add(fwdBtn)
            add(histLabel)
        }

        val leftPanel = JPanel(BorderLayout()).apply {
            add(historyBar, BorderLayout.NORTH)
            add(codePanel, BorderLayout.CENTER)
        }
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, resultPanel).apply {
            resizeWeight = 0.45
            dividerSize = 5
        }
        add(splitPane, BorderLayout.CENTER)
        wireHistoryListeners()
    }

    private fun wireHistoryListeners() {
        backBtn.addActionListener { navigateHistory(-1) }
        fwdBtn.addActionListener { navigateHistory(+1) }
    }

    fun saveToHistory(script: String) {
        if (script.isBlank()) return
        if (history.isNotEmpty() && history.last() == script) return
        if (history.size >= HISTORY_MAX) history.removeFirst()
        history.addLast(script)
        historyIndex = -1
        updateHistoryButtons()
    }

    private fun clearEditor() {
        val current = codePanel.text
        if (current.isNotBlank()) saveToHistory(current)
        codePanel.text = ""
        historyIndex = -1
        updateHistoryButtons()
    }

    private fun navigateHistory(delta: Int) {
        if (history.isEmpty()) return
        val lastIdx = history.size - 1
        val newIdx = when {
            historyIndex == -1 && delta < 0 -> {
                val live = codePanel.text
                if (live.isNotBlank() && (history.isEmpty() || history.last() != live)) {
                    if (history.size >= HISTORY_MAX) history.removeFirst()
                    history.addLast(live)
                }
                lastIdx
            }
            historyIndex == -1 -> return
            else -> (historyIndex + delta).coerceIn(0, lastIdx)
        }
        historyIndex = newIdx
        SwingUtilities.invokeLater {
            codePanel.text = history[newIdx]
            updateHistoryButtons()
        }
    }

    private fun updateHistoryButtons() {
        val hasHistory = history.isNotEmpty()
        backBtn.isEnabled = hasHistory && historyIndex != 0
        fwdBtn.isEnabled = hasHistory && historyIndex != -1
        histLabel.text = when {
            !hasHistory -> ""
            historyIndex == -1 -> "${history.size} saved"
            else -> "${historyIndex + 1} / ${history.size}"
        }
    }
}
