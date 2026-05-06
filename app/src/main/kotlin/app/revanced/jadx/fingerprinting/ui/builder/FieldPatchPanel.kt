package app.revanced.jadx.fingerprinting.ui.builder

import app.revanced.jadx.fingerprinting.ui.fingerprints.enumName
import app.revanced.jadx.fingerprinting.ui.fingerprints.fieldSetOpcode
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JTextField

internal class FieldPatchPanel(private val onChange: () -> Unit) : BuildModePanel() {
    private val isStaticCheck = JCheckBox("Static field (sput in <clinit>)").apply { isSelected = true }
    private val classField = JTextField()
    private val nameField = JTextField()
    private val fieldTypeCombo = typeCombo(DEX_FIELD_TYPES).apply { selectedIndex = 0 }
    private val methodField = JTextField(initMethodName(isStatic = true)).apply {
        toolTipText = "Auto-set to <clinit>/<init> when toggling static. Manual edits override auto-flip."
    }

    init {
        layout = BorderLayout()
        add(buildForm(), BorderLayout.CENTER)
        wireListeners()
    }

    override fun buildScript(): String {
        val cls = classField.text.trim()
        val fieldName = nameField.text.trim()
        val fieldType = comboValue(fieldTypeCombo.selectedItem)
        val isStatic = isStaticCheck.isSelected
        val methodName = methodField.text.trim().ifEmpty { initMethodName(isStatic) }
        val putOpcode = fieldType.takeIf { it.isNotEmpty() }?.let { fieldSetOpcode(it, isStatic) }
        val fieldDesc = buildString {
            append(fieldName.ifEmpty { "[field]" })
            if (fieldType.isNotEmpty()) append(":$fieldType")
        }
        val classDesc = cls.ifEmpty { "[class]" }
        return buildString {
            appendLine("// Field Patch: $fieldDesc in $classDesc")
            appendLine("//")
            appendLine("// Finds the method that sets the field so you can patch its value.")
            if (putOpcode != null && fieldName.isNotEmpty()) {
                appendLine("// In execute(), navigate to Opcode.${putOpcode.enumName()} that references")
                appendLine("// $classDesc->$fieldDesc and replace the preceding const.")
            }
            appendLine("gettingFirstMethodDeclaratively {")
            if (cls.isNotEmpty()) appendLine("    definingClass(\"$cls\")")
            appendLine("    name(\"$methodName\")")
            if (putOpcode != null) appendLine("    opcodes(Opcode.${putOpcode.enumName()})")
            append("}")
        }
    }

    override fun reset() {
        classField.text = ""
        nameField.text = ""
        fieldTypeCombo.selectedIndex = 0
        isStaticCheck.isSelected = true
        methodField.text = initMethodName(isStatic = true)
    }

    private fun buildForm() = formPanel(
        "Generates a fingerprint targeting the method that sets the specified field.\n\n" +
        "• Static field → SPUT instruction in <clinit>\n" +
        "• Instance field → IPUT instruction in <init>\n\n" +
        "In execute(), navigate to the matching opcode and replace the preceding const.\n" +
        "To nullify instance object fields (iput-object → null), use Field Nullifier mode."
    ) {
        section("Defining Class (e.g. Lcom/example/Foo;)", classField)
        section("Field Name", nameField)
        section("Field Type", fieldTypeCombo)
        row(isStaticCheck)
        section("Method to Search", methodField)
    }

    private fun wireListeners() {
        val dl = docListener(onChange)
        listOf(classField, nameField, methodField).forEach { it.document.addDocumentListener(dl) }
        fieldTypeCombo.wireChange(onChange)
        isStaticCheck.addItemListener {
            autoFlipMethodField()
            onChange()
        }
    }

    /**
     * When the user toggles static and the method field still holds the previous default,
     * flip it to the matching default. Manual edits (any other text) are preserved.
     */
    private fun autoFlipMethodField() {
        val current = methodField.text.trim()
        val newDefault = initMethodName(isStaticCheck.isSelected)
        val previousDefault = initMethodName(!isStaticCheck.isSelected)
        if (current == previousDefault) methodField.text = newDefault
    }
}
