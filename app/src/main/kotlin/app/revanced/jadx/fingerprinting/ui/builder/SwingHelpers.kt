package app.revanced.jadx.fingerprinting.ui.builder

import java.awt.Font
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

// Materializes a [DefaultListModel] into a regular [List] snapshot.
internal fun <T> DefaultListModel<T>.asList(): List<T> = (0 until size()).map { getElementAt(it) }

// [DocumentListener] that fires [action] for any document change.
internal fun docListener(action: () -> Unit): DocumentListener = object : DocumentListener {
    override fun insertUpdate(e: DocumentEvent) = action()
    override fun removeUpdate(e: DocumentEvent) = action()
    override fun changedUpdate(e: DocumentEvent) = action()
}

// Monospaced [JTextArea] suited for Smali / Kotlin snippet input.
internal fun monospaceTextArea(rows: Int, cols: Int): JTextArea = JTextArea(rows, cols).apply {
    font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    lineWrap = false
    tabSize = 4
}

/**
 * Wires both selection changes and free-text edits of an editable combo to [onChange].
 * Eliminates the verbose `addActionListener + (editor.editorComponent as? JTextField)` pair.
 */
internal fun JComboBox<*>.wireChange(onChange: () -> Unit) {
    addActionListener { onChange() }
    (editor.editorComponent as? JTextField)?.document?.addDocumentListener(docListener(onChange))
}
