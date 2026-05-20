package app.revanced.jadx.fingerprinting.ui.builder

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

internal class ClassDeclarativePanel(private val onChange: () -> Unit) : BuildModePanel() {
    private val typeField = JTextField()
    private val nullableCheck = JCheckBox("OrNull - return null instead of throwing")
    private val mutableCheck = JCheckBox("Mutable - gettingFirstClassDef (for mutations)")
    private val predicatesArea = monospaceTextArea(rows = 6, cols = 20)

    init {
        layout = BorderLayout()
        add(buildForm(), BorderLayout.CENTER)
        wireListeners()
    }

    override fun buildScript(): String {
        val cls = typeField.text.trim()
        val preds = predicatesArea.text.trim()
        val fnName = chooseFunctionName()
        val typeArg = if (cls.isNotEmpty()) "\"$cls\"" else ""
        return if (preds.isEmpty()) {
            if (typeArg.isEmpty()) "$fnName()" else "$fnName($typeArg)"
        } else {
            val indented = preds.lines().joinToString("\n") { "    $it" }
            if (typeArg.isEmpty()) "$fnName {\n$indented\n}"
            else "$fnName($typeArg) {\n$indented\n}"
        }
    }

    override fun reset() {
        typeField.text = ""
        predicatesArea.text = ""
        nullableCheck.isSelected = false
        mutableCheck.isSelected = false
    }

    private fun chooseFunctionName(): String = when {
        mutableCheck.isSelected && nullableCheck.isSelected -> "gettingFirstClassDefDeclarativelyOrNull"
        mutableCheck.isSelected -> "gettingFirstClassDefDeclaratively"
        nullableCheck.isSelected -> "gettingFirstImmutableClassDefDeclarativelyOrNull"
        else -> "gettingFirstImmutableClassDefDeclaratively"
    }

    private fun buildForm() = formPanel(
        "Find a class using structural predicates. Useful when the class descriptor " +
        "is obfuscated but the class has identifiable methods or fields.\n\n" +
        "Available predicates inside the block:\n" +
        "  predicate { anyMethod { name == \"foo\" } }\n" +
        "  predicate { anyStaticField { type == \"Z\" } }\n" +
        "  predicate { anyInterface { startsWith(\"Landroid/\") } }\n" +
        "  predicate { superclass == \"Ljava/lang/Object;\" }"
    ) {
        section("Class Descriptor (optional - leave blank to search all classes)", typeField)
        row(buildSnippetBar())
        section("Predicates", JScrollPane(predicatesArea).apply { preferredSize = Dimension(0, 130) })
        row(nullableCheck)
        row(mutableCheck)
    }

    private fun buildSnippetBar(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        add(JLabel("Insert:"))
        SNIPPETS.forEach { (label, snippet) -> add(snippetButton(label, snippet)) }
    }

    private fun snippetButton(label: String, snippet: String): JButton = JButton(label).apply {
        font = font.deriveFont(font.size2D - 1f)
        toolTipText = snippet
        addActionListener { predicatesArea.insert("$snippet\n", predicatesArea.caretPosition) }
    }

    private fun wireListeners() {
        val dl = docListener(onChange)
        typeField.document.addDocumentListener(dl)
        predicatesArea.document.addDocumentListener(dl)
        listOf(nullableCheck, mutableCheck).forEach { cb -> cb.addItemListener { onChange() } }
    }

    private companion object {
        private val SNIPPETS = listOf(
            "anyMethod" to "predicate { anyMethod { name == \"\" } }",
            "anyStaticField" to "predicate { anyStaticField { type == \"\" } }",
            "anyInterface" to "predicate { anyInterface { startsWith(\"\") } }",
        )
    }
}
