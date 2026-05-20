package app.revanced.jadx.fingerprinting.ui.builder

import java.awt.*
import javax.swing.*

internal class ClassPatchPanel(private val onChange: () -> Unit) : BuildModePanel() {
    private val typeField = JTextField()
    private val nullableCheck = JCheckBox("OrNull - return null instead of throwing")

    init {
        layout = BorderLayout()
        add(buildForm(), BorderLayout.CENTER)
        wireListeners()
    }

    override fun buildScript(): String {
        val cls = typeField.text.trim()
        val fn = if (nullableCheck.isSelected) "gettingFirstImmutableClassDefOrNull"
                 else "gettingFirstImmutableClassDef"
        return if (cls.isNotEmpty()) "$fn(\"$cls\")" else "$fn(/* Lcom/example/Foo; */)"
    }

    override fun reset() {
        typeField.text = ""
        nullableCheck.isSelected = false
    }

    private fun buildForm() = formPanel(
        "Generates a cached delegate property that resolves a ClassDef by its exact " +
        "type descriptor. Use this to inspect or mutate class fields without needing " +
        "a method fingerprint."
    ) {
        section("Class Descriptor (e.g. Lcom/example/Foo;)", typeField)
        row(nullableCheck)
    }

    private fun wireListeners() {
        typeField.document.addDocumentListener(docListener(onChange))
        nullableCheck.addItemListener { onChange() }
    }
}
