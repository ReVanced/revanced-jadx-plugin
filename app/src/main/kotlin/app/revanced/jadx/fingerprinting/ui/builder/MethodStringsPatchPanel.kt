package app.revanced.jadx.fingerprinting.ui.builder

import java.awt.*
import javax.swing.*

internal class MethodStringsPatchPanel(private val onChange: () -> Unit) : BuildModePanel() {
    private val stringsModel = DefaultListModel<String>()
    private val stringInput = JTextField(12)
    private val stringsList = JList(stringsModel)
    private val defClassField = JTextField()
    private val returnCombo = typeCombo(DEX_RETURN_TYPES).apply { selectedItem = DEX_RETURN_TYPES[0] }
    private val nullableCheck = JCheckBox("OrNull - return null instead of throwing")
    private val mutableCheck = JCheckBox("Mutable - gettingFirstMethod (for mutations)")

    init {
        layout = BorderLayout()
        add(buildForm(), BorderLayout.CENTER)
        wireListeners()
    }

    override fun buildScript(): String {
        val strings = stringsModel.asList()
        val rt = comboValue(returnCombo.selectedItem, "(any type)")
        val dc = defClassField.text.trim()
        val fnName = when {
            mutableCheck.isSelected && nullableCheck.isSelected -> "gettingFirstMethodOrNull"
            mutableCheck.isSelected -> "gettingFirstMethod"
            nullableCheck.isSelected -> "gettingFirstImmutableMethodOrNull"
            else -> "gettingFirstImmutableMethod"
        }
        val args = strings.joinToString(", ") { "\"$it\"" }
        val predicates = buildList {
            if (dc.isNotEmpty()) add("    definingClass(\"$dc\")")
            if (rt.isNotEmpty()) add("    returnType(\"$rt\")")
        }
        return if (predicates.isEmpty()) "$fnName($args)"
        else "$fnName($args) {\n${predicates.joinToString("\n")}\n}"
    }

    override fun reset() {
        stringsModel.clear()
        stringInput.text = ""
        defClassField.text = ""
        returnCombo.selectedItem = DEX_RETURN_TYPES[0]
        nullableCheck.isSelected = false
        mutableCheck.isSelected = false
    }

    private fun buildForm() = formPanel(
        "Searches for a method that contains all of the given string literals in its " +
        "bytecode. This strategy is often the most version-stable: obfuscated class/method " +
        "names change but string constants rarely do.\n\n" +
        "Generates: gettingFirstImmutableMethod(\"str1\", \"str2\")"
    ) {
        section("String Literals (bytecode constants)", buildStringListPanel(stringsModel, stringInput, stringsList, onChange))
        section("Defining Class (e.g. Lcom/example/Foo;) (optional filter)", defClassField)
        section("Return Type (optional filter)", returnCombo)
        row(nullableCheck)
        row(mutableCheck)
    }

    private fun wireListeners() {
        defClassField.document.addDocumentListener(docListener(onChange))
        returnCombo.wireChange(onChange)
        listOf(nullableCheck, mutableCheck).forEach { cb -> cb.addItemListener { onChange() } }
    }
}
