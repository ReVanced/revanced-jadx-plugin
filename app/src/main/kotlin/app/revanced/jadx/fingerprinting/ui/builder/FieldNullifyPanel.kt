package app.revanced.jadx.fingerprinting.ui.builder

import app.revanced.jadx.fingerprinting.ui.fingerprints.buildFieldNullifyCode
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

internal class FieldNullifyPanel(
    private val isStatic: Boolean,
    private val onChange: () -> Unit,
) : BuildModePanel() {
    private val classField = JTextField()
    private val fieldNamesArea = monospaceTextArea(rows = 4, cols = 20).apply {
        toolTipText = "One field name per line"
    }
    private val varNameField = JTextField(defaultNullifyVarName(isStatic))

    init {
        layout = BorderLayout()
        add(buildForm(), BorderLayout.CENTER)
        wireListeners()
    }

    override fun buildScript(): String {
        val cls = classField.text.trim()
        val varName = varNameField.text.trim().ifEmpty { defaultNullifyVarName(isStatic) }
        val fieldNames = fieldNamesArea.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return buildFieldNullifyCode(cls, fieldNames, varName, isStatic)
    }

    override fun reset() {
        classField.text = ""
        fieldNamesArea.text = ""
        varNameField.text = defaultNullifyVarName(isStatic)
    }

    private fun buildForm(): JPanel = formPanel(buildDescription()) {
        section("Defining Class (e.g. Lcom/example/Foo;)", classField)
        section("Field Names to Nullify (one per line)", JScrollPane(fieldNamesArea).apply {
            preferredSize = Dimension(0, 90)
        })
        section("Fingerprint Variable Name", varNameField)
    }

    private fun buildDescription(): String {
        val opcode = if (isStatic) "SPUT_OBJECT" else "IPUT_OBJECT"
        val container = if (isStatic) "static initializer" else "constructor"
        val smaliInject = if (isStatic) "sput-object v0" else "iput-object v0"
        val fieldKind = if (isStatic) "static" else "instance"
        return "Generates the complete execute() snippet for nullifying $fieldKind object fields.\n\n" +
            "Pattern: finds $opcode instructions in the $container matching the given " +
            "field names, then injects const/4 v0, 0x0 + $smaliInject immediately after each to " +
            "overwrite with null.\n\n" +
            "Field names are matched via contains() - stable unobfuscated names survive ProGuard.\n" +
            "Indices are patched bottom-up to avoid index-shift corruption."
    }

    private fun wireListeners() {
        val dl = docListener(onChange)
        classField.document.addDocumentListener(dl)
        varNameField.document.addDocumentListener(dl)
        fieldNamesArea.document.addDocumentListener(dl)
    }
}
