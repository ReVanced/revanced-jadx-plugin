package app.revanced.jadx.fingerprinting.ui

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.ui.ReVancedJadxPluginUi.copyWithTimeout
import app.revanced.jadx.fingerprinting.ui.components.CodePanel
import app.revanced.jadx.fingerprinting.ui.theme.applyEditorTheme
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.gui.JadxGuiContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.io.File
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

private val CODE_DIALOG_LOG = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/code-dialog")

internal fun JComponent.setFixedSize(sz: Int) {
    val d = Dimension(sz, sz)
    preferredSize = d; maximumSize = d; minimumSize = d
}

internal fun GridBagConstraints.atRow(r: Int): GridBagConstraints =
    (clone() as GridBagConstraints).also { it.gridy = r }

internal fun readOnlyCodePanel(text: String, guiContext: JadxGuiContext, log: KLogger): CodePanel =
    CodePanel().also {
        it.setEditable(false)
        it.text = text
        applyEditorTheme(it, guiContext, log)
    }

internal fun showError(
    guiContext: JadxGuiContext,
    msg: String,
    title: String = "Error",
    parent: Component? = null,
) {
    JOptionPane.showMessageDialog(
        parent ?: guiContext.mainFrame, msg, title, JOptionPane.ERROR_MESSAGE
    )
}

internal fun ReVancedJadxPluginUi.showError(
    msg: String,
    title: String = "Error",
    parent: Component? = null,
) = showError(guiContext, msg, title, parent)

internal fun showCodeDialog(
    guiContext: JadxGuiContext,
    title: String,
    code: String,
    subtitle: String = "",
    parent: Component? = null,
) {
    SwingUtilities.invokeLater {
        val dialog = JDialog(guiContext.mainFrame, title, false)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE

        val lines = code.lines()
        val longestLine = lines.maxOfOrNull { it.length } ?: 60
        val dialogW = (longestLine * 10 + 120).coerceIn(620, 1200)
        val dialogH = (lines.size * 20 + 140).coerceIn(300, 700)
        dialog.setSize(dialogW, dialogH)
        dialog.setLocationRelativeTo(parent ?: guiContext.mainFrame)

        val panel = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        if (subtitle.isNotEmpty()) {
            panel.add(JLabel("<html>$subtitle</html>").apply {
                border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
            }, BorderLayout.NORTH)
        }

        panel.add(readOnlyCodePanel(code, guiContext, CODE_DIALOG_LOG), BorderLayout.CENTER)

        val defaultFileName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trimEnd('_')
        val copyBtn = JButton("Copy to Clipboard").also { copyWithTimeout(it, code, dialog) }
        val saveBtn = JButton("Save…").apply {
            addActionListener { saveToFile(code, defaultFileName, dialog) }
        }
        val closeBtn = JButton("Close").apply { addActionListener { dialog.dispose() } }
        val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        btnRow.add(copyBtn); btnRow.add(saveBtn); btnRow.add(closeBtn)
        panel.add(btnRow, BorderLayout.SOUTH)

        dialog.contentPane.add(panel)
        dialog.isVisible = true
    }
}

internal fun ReVancedJadxPluginUi.showCodeDialog(
    title: String,
    code: String,
    subtitle: String = "",
    parent: Component? = null,
) = showCodeDialog(guiContext, title, code, subtitle, parent)

internal fun saveToFile(code: String, defaultName: String, parent: Component? = null) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save Script"
        selectedFile = File("$defaultName.kt")
        fileFilter = FileNameExtensionFilter("Kotlin source files (*.kt)", "kt")
    }
    if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return
    val target = chooser.selectedFile.let {
        if (it.name.contains('.')) it else File("${it.absolutePath}.kt")
    }
    runCatching { target.writeText(code) }
        .onFailure {
            JOptionPane.showMessageDialog(
                parent, "Save failed: ${it.message}", "Save Error", JOptionPane.ERROR_MESSAGE
            )
        }
}