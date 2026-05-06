package app.revanced.jadx.fingerprinting.ui.builder

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.border.TitledBorder

// Wraps [content] in a panel with an etched + titled border.
internal fun titledSection(title: String, content: JComponent): JPanel =
    JPanel(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP
        )
        add(content, BorderLayout.CENTER)
    }

// Read-only multi-line caption painted to look like a label, used for form descriptions.
internal fun infoArea(text: String): JTextArea = JTextArea(text).apply {
    isEditable = false; lineWrap = true; wrapStyleWord = true
    isOpaque = false; background = null
    border = BorderFactory.createEmptyBorder(0, 2, 4, 2)
}

/**
 * Vertical-stacking form helper. Use [section] for titled fields and [row] for raw components.
 * Constructed by [formPanel]; not intended to be instantiated directly.
 */
internal class FormBuilder(private val panel: JPanel, private val gbc: GridBagConstraints) {
    fun section(title: String, content: JComponent) {
        panel.add(titledSection(title, content), gbc); gbc.gridy++
    }

    fun row(component: JComponent) {
        panel.add(component, gbc); gbc.gridy++
    }
}

/**
 * Builds a vertically-stacked form panel.
 * Optional [info] header renders a wrapped description above the fields.
 */
internal fun formPanel(info: String? = null, block: FormBuilder.() -> Unit): JPanel {
    val panel = JPanel(GridBagLayout()).apply {
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }
    val gbc = GridBagConstraints().apply {
        gridx = 0; gridy = 0
        weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
        insets = Insets(0, 0, 8, 0)
    }
    if (info != null) {
        panel.add(infoArea(info), gbc); gbc.gridy++
    }
    with(FormBuilder(panel, gbc), block)
    gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
    panel.add(JPanel(), gbc)
    return panel
}

// Standard layout for an input bar (input + Add + Remove) above a scrollable list.
internal fun inputListPanel(
    inputBar: JComponent,
    addBtn: JButton,
    removeBtn: JButton,
    list: JList<*>,
): JPanel = JPanel(BorderLayout(4, 4)).apply {
    add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        add(inputBar); add(addBtn); add(removeBtn)
    }, BorderLayout.NORTH)
    add(JScrollPane(list).apply { preferredSize = Dimension(0, 80) }, BorderLayout.CENTER)
}

/**
 * Editable list backed by a text field. Pressing Enter or clicking Add appends the trimmed value.
 * Empty values are ignored. Each mutation triggers [onChange].
 */
internal fun buildStringListPanel(
    model: DefaultListModel<String>,
    input: JTextField,
    list: JList<String>,
    onChange: () -> Unit,
): JPanel {
    val addBtn = JButton("Add")
    val removeBtn = JButton("Remove")
    list.selectionMode = ListSelectionModel.SINGLE_SELECTION
    addBtn.addActionListener {
        val v = input.text.trim()
        if (v.isNotEmpty()) {
            model.addElement(v); input.text = ""; onChange()
        }
    }
    input.addActionListener { addBtn.doClick() }
    removeBtn.addActionListener {
        val idx = list.selectedIndex
        if (idx >= 0) {
            model.remove(idx); onChange()
        }
    }
    return inputListPanel(input, addBtn, removeBtn, list)
}
