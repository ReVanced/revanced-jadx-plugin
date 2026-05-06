package app.revanced.jadx.fingerprinting.ui.builder

import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JTextField

internal class ReturnPatchPanel(private val onChange: () -> Unit) : BuildModePanel() {
    private val varNameField = JTextField(DEFAULT_VAR_NAME)
    private val defClassField = JTextField()
    private val methodNameField = JTextField()
    private val returnTypeCombo = typeCombo(DEX_RETURN_TYPES).apply { selectedItem = DEX_RETURN_TYPES[0] }
    private val returnValueField = JTextField(DEFAULT_BOOLEAN_VALUE)
    private val lateCheck = JCheckBox("Return Late - execute all code but override the return value")

    init {
        layout = BorderLayout()
        add(buildForm(), BorderLayout.CENTER)
        wireListeners()
    }

    override fun buildScript(): String {
        val varName = varNameField.text.trim().ifEmpty { DEFAULT_VAR_NAME }
        val cls = defClassField.text.trim()
        val name = methodNameField.text.trim()
        val rt = comboValue(returnTypeCombo.selectedItem, "(any type)")
        val raw = returnValueField.text.trim()
        val fn = if (lateCheck.isSelected) "returnLate" else "returnEarly"
        val value = formatReturnValue(rt, raw)
        return buildString {
            appendLine("val $varName by gettingFirstMethodDeclaratively {")
            if (cls.isNotEmpty()) appendLine("    definingClass(\"$cls\")")
            if (name.isNotEmpty()) appendLine("    name(\"$name\")")
            if (rt.isNotEmpty()) appendLine("    returnType(\"$rt\")")
            appendLine("}")
            appendLine()
            appendLine("// In execute():")
            append("$varName.$fn($value)")
        }
    }

    override fun reset() {
        varNameField.text = DEFAULT_VAR_NAME
        defClassField.text = ""
        methodNameField.text = ""
        returnTypeCombo.selectedItem = DEX_RETURN_TYPES[0]
        returnValueField.text = DEFAULT_BOOLEAN_VALUE
        lateCheck.isSelected = false
    }

    private fun buildForm() = formPanel(
        "Generates a fingerprint (gettingFirstMethodDeclaratively) plus the execute() " +
        "snippet using returnEarly() or returnLate() from BytecodeUtils.\n\n" +
        "• returnEarly - inserts instructions at index 0, method body never runs.\n" +
        "• returnLate - replaces every RETURN instruction, all code still executes."
    ) {
        section("Property Variable Name", varNameField)
        section("Defining Class", defClassField)
        section("Method Name", methodNameField)
        section("Return Type", returnTypeCombo)
        section("Return Value", returnValueField)
        row(lateCheck)
    }

    private fun wireListeners() {
        val dl = docListener(onChange)
        listOf(varNameField, defClassField, methodNameField, returnValueField).forEach {
            it.document.addDocumentListener(dl)
        }
        returnTypeCombo.wireChange(onChange)
        lateCheck.addItemListener { onChange() }
    }

    /**
     * Formats [raw] as a Kotlin literal matching DEX type [rt].
     * Falls back to the raw string with appropriate suffix/sentinel when parsing fails.
     */
    private fun formatReturnValue(rt: String, raw: String): String = when (rt) {
        "Z" -> if (raw.lowercase() in TRUTHY_LITERALS) "true" else "false"
        "B", "S", "C", "I" -> raw.toLongOrNull()?.toString() ?: raw.ifEmpty { "0" }
        "J" -> raw.toLongOrNull()?.let { "${it}L" } ?: raw.ifEmpty { "0L" }
        "F" -> raw.toFloatOrNull()?.let { "${it}f" } ?: raw.ifEmpty { "0.0f" }
        "D" -> raw.toDoubleOrNull()?.toString() ?: raw.ifEmpty { "0.0" }
        "" -> raw.ifEmpty { "/* value */" }
        else -> "\"${raw.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private companion object {
        private const val DEFAULT_VAR_NAME = "targetMethod"
        private const val DEFAULT_BOOLEAN_VALUE = "false"
        private val TRUTHY_LITERALS = setOf("true", "1")
    }
}
