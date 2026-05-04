package app.revanced.jadx.fingerprinting.ui.components

import jadx.gui.utils.ui.MousePressedHandler
import org.fife.ui.autocomplete.AutoCompletion
import org.fife.ui.autocomplete.CompletionProvider
import org.fife.ui.autocomplete.DefaultCompletionProvider
import org.fife.ui.autocomplete.TemplateCompletion
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.LineNumberFormatter
import org.fife.ui.rtextarea.LineNumberList
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.math.log10

class CodePanel : JPanel() {
    private val codeArea: RSyntaxTextArea = RSyntaxTextArea()
    private val codeScrollPane: RTextScrollPane

    private var useSourceLines = false

    init {
        this.codeArea.setSyntaxEditingStyle(RSyntaxTextArea.SYNTAX_STYLE_KOTLIN)
        RSyntaxTextArea.setTemplatesEnabled(true)
        this.codeArea.setAntiAliasingEnabled(true)
        this.codeScrollPane = RTextScrollPane(codeArea)
        setLayout(BorderLayout())
        setBorder(EmptyBorder(0, 0, 0, 0))
        add(codeScrollPane, BorderLayout.CENTER)
        initLinesModeSwitch()
        val ac = AutoCompletion(createCompletionProvider())
        ac.isParameterAssistanceEnabled = true
        ac.install(codeArea)
    }

    var text: String
        get() = codeArea.text
        set(text) {
            codeArea.text = text
            codeArea.caretPosition = 0
        }

    fun setEditable(editable: Boolean) {
        codeArea.isEditable = editable
        codeArea.highlightCurrentLine = editable
    }

    fun setTheme(theme: Theme) {
        theme.apply(codeArea)
    }

    fun cloneAppearanceFrom(source: RSyntaxTextArea) {
        codeArea.background = source.background
        codeArea.foreground = source.foreground
        codeArea.caretColor = source.caretColor
        codeArea.selectionColor = source.selectionColor
        codeArea.selectedTextColor = source.selectedTextColor
        codeArea.currentLineHighlightColor = source.currentLineHighlightColor
        codeArea.syntaxScheme = source.syntaxScheme.clone() as org.fife.ui.rsyntaxtextarea.SyntaxScheme
        codeScrollPane.gutter.background = source.background
        codeArea.revalidate()
        codeArea.repaint()
    }

    @Synchronized
    private fun applyLineFormatter() {
        codeScrollPane.gutter.lineNumberFormatter = SIMPLE_LINE_FORMATTER
    }

    private fun initLinesModeSwitch() {
        val lineModeSwitch = MousePressedHandler { _: MouseEvent? ->
            useSourceLines = !useSourceLines
            applyLineFormatter()
        }
        for (gutterComp in codeScrollPane.gutter.components) {
            if (gutterComp is LineNumberList) {
                gutterComp.addMouseListener(lineModeSwitch)
            }
        }
    }

    fun createCompletionProvider(): CompletionProvider {
        val provider = DefaultCompletionProvider()

        provider.addCompletion(TemplateCompletion(provider,
            "fingerprint", "fingerprint { … }",
            "fingerprint {\n\t\${cursor}\n}"))
        provider.addCompletion(TemplateCompletion(provider,
            "gettingFirstMethodDeclaratively", "gettingFirstMethodDeclaratively { … }",
            "gettingFirstMethodDeclaratively {\n\t\${cursor}\n}"))
        provider.addCompletion(TemplateCompletion(provider,
            "definingClass", "definingClass(\"…\")",
            "definingClass(\"\${cursor}\")"))
        provider.addCompletion(TemplateCompletion(provider,
            "name", "name(\"…\")",
            "name(\"\${cursor}\")"))
        provider.addCompletion(TemplateCompletion(provider,
            "returnType", "returnType(\"…\")",
            "returnType(\"\${cursor}\")"))
        provider.addCompletion(TemplateCompletion(provider,
            "parameterTypes", "parameterTypes(\"…\")",
            "parameterTypes(\"\${cursor}\")"))
        provider.addCompletion(TemplateCompletion(provider,
            "returns", "returns(\"…\") - return type",
            "returns(\"\${cursor}\")"))
        provider.addCompletion(TemplateCompletion(provider,
            "parameters", "parameters(\"…\") - parameter types",
            "parameters(\"\${cursor}\")"))
        provider.addCompletion(TemplateCompletion(provider,
            "accessFlags", "accessFlags(AccessFlags.…)",
            "accessFlags(AccessFlags.\${cursor})"))
        provider.addCompletion(TemplateCompletion(provider,
            "opcodes", "opcodes(Opcode.…)",
            "opcodes(Opcode.\${cursor})"))
        provider.addCompletion(TemplateCompletion(provider,
            "strings", "strings(\"…\")",
            "strings(\"\${cursor}\")"))
        provider.addCompletion(TemplateCompletion(provider,
            "custom", "custom { method, classDef -> … }",
            "custom { method, classDef ->\n\t\${cursor}\n}"))

        return provider
    }

    companion object {
        private val SIMPLE_LINE_FORMATTER: LineNumberFormatter = object : LineNumberFormatter {
            override fun format(lineNumber: Int): String = lineNumber.toString()
            override fun getMaxLength(maxLineNumber: Int): Int =
                if (maxLineNumber < 10) 1 else 1 + log10(maxLineNumber.toDouble()).toInt()
        }
    }
}
