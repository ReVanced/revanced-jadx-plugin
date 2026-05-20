package app.revanced.jadx.fingerprinting.ui.components

import app.revanced.jadx.fingerprinting.core.SCRIPT_PRELUDE_LINE_COUNT
import app.revanced.jadx.fingerprinting.core.ScriptEvaluation
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

class CodePanel(enableLinting: Boolean = false) : JPanel() {
    private val codeArea: RSyntaxTextArea = RSyntaxTextArea()
    private val codeScrollPane: RTextScrollPane

    private var useSourceLines = false

    private val lintParser: KotlinScriptParser? = if (enableLinting) {
        KotlinScriptParser(
            compileAsync = { ScriptEvaluation.compileDiagnostics(it) },
            preludeLineOffset = SCRIPT_PRELUDE_LINE_COUNT,
            textArea = codeArea,
        )
    } else {
        null
    }

    init {
        this.codeArea.setSyntaxEditingStyle(RSyntaxTextArea.SYNTAX_STYLE_KOTLIN)
        RSyntaxTextArea.setTemplatesEnabled(true)
        this.codeArea.setAntiAliasingEnabled(true)
        lintParser?.let {
            // reparse delay after the last keystroke
            // todo: make these user editable settings
            this.codeArea.parserDelay = 800
            this.codeArea.addParser(it)
        }
        this.codeScrollPane = RTextScrollPane(codeArea)
        setLayout(BorderLayout())
        setBorder(EmptyBorder(0, 0, 0, 0))
        add(codeScrollPane, BorderLayout.CENTER)
        initLinesModeSwitch()
        val ac = AutoCompletion(createCompletionProvider())
        // todo: make these user editable settings
        ac.isParameterAssistanceEnabled = true
        ac.isAutoActivationEnabled = true
        ac.autoActivationDelay = 300
        ac.install(codeArea)
    }

    override fun removeNotify() {
        super.removeNotify()
        lintParser?.dispose()
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

        fun tpl(input: String, shortDesc: String, template: String) {
            provider.addCompletion(TemplateCompletion(provider, input, shortDesc, template))
        }

        tpl("fingerprint", "fingerprint { … }", "fingerprint {\n\t\${cursor}\n}")
        
        listOf(
            "gettingFirstMethodDeclaratively",
            "gettingFirstMethodDeclarativelyOrNull",
            "gettingFirstImmutableMethodDeclaratively",
            "gettingFirstImmutableMethodDeclarativelyOrNull",
            "gettingFirstMethod",
            "gettingFirstMethodOrNull",
            "gettingFirstImmutableMethod",
            "gettingFirstImmutableMethodOrNull",
            "composingFirstMethod",
        ).forEach { fn ->
            tpl(fn, "$fn { … }", "$fn {\n\t\${cursor}\n}")
            tpl(fn, "$fn(\"…\") { … }", "$fn(\"\${str}\") {\n\t\${cursor}\n}")
        }

        listOf(
            "gettingFirstClassDef",
            "gettingFirstClassDefOrNull",
            "gettingFirstImmutableClassDef",
            "gettingFirstImmutableClassDefOrNull",
            "gettingFirstClassDefDeclaratively",
            "gettingFirstClassDefDeclarativelyOrNull",
            "gettingFirstImmutableClassDefDeclaratively",
            "gettingFirstImmutableClassDefDeclarativelyOrNull",
        ).forEach { fn ->
            tpl(fn, "$fn(\"L…;\")", "$fn(\"\${cursor}\")")
            tpl(fn, "$fn(\"L…;\") { … }", "$fn(\"\${descriptor}\") {\n\t\${cursor}\n}")
        }

        tpl("definingClass", "definingClass(\"L…;\")", "definingClass(\"\${cursor}\")")
        tpl("name", "name(\"…\")", "name(\"\${cursor}\")")
        tpl("returnType", "returnType(\"…\")", "returnType(\"\${cursor}\")")
        tpl("parameterTypes", "parameterTypes(\"…\", …)", "parameterTypes(\"\${cursor}\")")
        tpl("accessFlags", "accessFlags(AccessFlags.…)", "accessFlags(AccessFlags.\${cursor})")
        tpl("opcodes", "opcodes(Opcode.…)", "opcodes(Opcode.\${cursor})")
        tpl("opcodesPattern", "opcodesPattern(\"smali …\")", "opcodesPattern(\"\"\"\n\t\${cursor}\n\"\"\")")
        tpl("strings", "strings(\"…\")", "strings(\"\${cursor}\")")
        tpl("custom", "custom { method, classDef -> … }", "custom { method, classDef ->\n\t\${cursor}\n}")
        tpl("returns", "returns(\"…\") - return type", "returns(\"\${cursor}\")")
        tpl("parameters", "parameters(\"…\") - parameter types", "parameters(\"\${cursor}\")")

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
