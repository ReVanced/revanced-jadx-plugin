package app.revanced.jadx.fingerprinting.ui.components

import app.revanced.jadx.fingerprinting.core.SCRIPT_PRELUDE_LINE_COUNT
import app.revanced.jadx.fingerprinting.core.ScriptEvaluation
import jadx.gui.utils.ui.MousePressedHandler
import org.fife.ui.autocomplete.AutoCompletion
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Theme
import org.fife.ui.rtextarea.IconRowHeader
import org.fife.ui.rtextarea.LineNumberFormatter
import org.fife.ui.rtextarea.LineNumberList
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import kotlin.math.log10

class CodePanel(
    initialSettings: Settings = EditorSettingsHolder.current(),
) : JPanel(BorderLayout()) {

    data class Settings(
        // Enable Kotlin lint.
        val enableLinting: Boolean = true,
        // Milliseconds of inactivity before [KotlinScriptParser] re-compiles.
        val lintDebounceMs: Int = 800,
        // Prevent hanging by adding a timeout for a single compile attempt.
        val lintTimeoutMs: Int = 10000,
        // Show autocomplete popup as user types (vs. only on Ctrl + Space).
        val autoCompleteEnabled: Boolean = true,
        // Milliseconds of inactivity before autocomplete popup appears.
        val autoCompleteDelayMs: Int = 300,
        // Show param-hint popups after `(`.
        val parameterAssistance: Boolean = true,
        // Tab-line guides painted.
        val paintTabLines: Boolean = true,
        // Text anti-aliasing.
        val antiAliasing: Boolean = true,
        // Auto-indent on newline.
        val enableAutoIndent: Boolean = true,
        // Visible whitespace markers.
        val whitespaceCharacters: Boolean = false,
    )

    private var settings: Settings = initialSettings

    private val codeArea: RSyntaxTextArea = RSyntaxTextArea()
    private val codeScrollPane: RTextScrollPane

    private var useSourceLines = false

    private var lintParser: KotlinScriptParser? = null
    private var autoCompletion: AutoCompletion? = null
    private var settingsSubscription: AutoCloseable? = null

    init {
        codeArea.syntaxEditingStyle = RSyntaxTextArea.SYNTAX_STYLE_KOTLIN
        RSyntaxTextArea.setTemplatesEnabled(true)
        applyTextAreaFlags()
        applyLintingState()
        codeScrollPane = RTextScrollPane(codeArea)
        border = EmptyBorder(0, 0, 0, 0)
        add(codeScrollPane, BorderLayout.CENTER)
        initLinesModeSwitch()
        initMarkerJumpHandler()
        applyAutoCompleteState()
        openSettingsSubscription()
    }

    override fun removeNotify() {
        super.removeNotify()
        settingsSubscription?.close()
        settingsSubscription = null
        lintParser?.let {
            codeArea.removeParser(it)
            it.dispose()
        }
        lintParser = null
        autoCompletion?.uninstall()
        autoCompletion = null
    }

    fun applySettings(new: Settings) {
        val old = settings
        if (old == new) return
        settings = new

        // RSyntaxTextArea flags
        if (old.antiAliasing != new.antiAliasing) codeArea.antiAliasingEnabled = new.antiAliasing
        if (old.paintTabLines != new.paintTabLines) codeArea.paintTabLines = new.paintTabLines
        if (old.enableAutoIndent != new.enableAutoIndent) codeArea.isAutoIndentEnabled = new.enableAutoIndent
        if (old.whitespaceCharacters != new.whitespaceCharacters) codeArea.isWhitespaceVisible = new.whitespaceCharacters

        // Kotlin lint
        if (old.lintDebounceMs != new.lintDebounceMs) codeArea.parserDelay = new.lintDebounceMs

        // Lint enable/disable handler
        if (old.enableLinting != new.enableLinting) applyLintingState()

        // Autocomplete toggle
        if (old.autoCompleteEnabled != new.autoCompleteEnabled) {
            applyAutoCompleteState()
        } else {
            autoCompletion?.let { ac ->
                if (old.autoCompleteDelayMs != new.autoCompleteDelayMs) {
                    ac.autoActivationDelay = new.autoCompleteDelayMs
                }
                if (old.parameterAssistance != new.parameterAssistance) {
                    ac.isParameterAssistanceEnabled = new.parameterAssistance
                }
            }
        }
    }

    var text: String
        get() = codeArea.text
        set(text) {
            codeArea.text = text
            codeArea.caretPosition = 0
        }

    fun insertAtCaret(text: String) {
        codeArea.replaceSelection(text)
        codeArea.requestFocusInWindow()
    }

    fun setEditable(editable: Boolean) {
        codeArea.isEditable = editable
        codeArea.highlightCurrentLine = editable
        if (editable) {
            if (settingsSubscription == null) {
                applySettings(EditorSettingsHolder.current())
                openSettingsSubscription()
            }
        } else {
            settingsSubscription?.close()
            settingsSubscription = null
            applySettings(settings.copy(enableLinting = false))
        }
    }

    private fun openSettingsSubscription() {
        settingsSubscription = EditorSettingsHolder.subscribe { new ->
            SwingUtilities.invokeLater { applySettings(new) }
        }
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
    
    private fun applyTextAreaFlags() {
        codeArea.antiAliasingEnabled = settings.antiAliasing
        codeArea.paintTabLines = settings.paintTabLines
        codeArea.isAutoIndentEnabled = settings.enableAutoIndent
        codeArea.isWhitespaceVisible = settings.whitespaceCharacters
    }

    private fun applyLintingState() {
        val want = settings.enableLinting
        val have = lintParser != null
        when {
            want && !have -> {
                lintParser = KotlinScriptParser(
                    compileAsync = { ScriptEvaluation.compileDiagnostics(it, settings.lintTimeoutMs.toLong()) },
                    preludeLineOffset = SCRIPT_PRELUDE_LINE_COUNT,
                    textArea = codeArea,
                ).also {
                    codeArea.parserDelay = settings.lintDebounceMs
                    codeArea.addParser(it)
                }
            }
            !want && have -> {
                lintParser?.let {
                    codeArea.removeParser(it)
                    it.dispose()
                }
                lintParser = null
            }
        }
    }

    private fun applyAutoCompleteState() {
        val want = settings.autoCompleteEnabled
        val have = autoCompletion != null
        when {
            want && !have -> {
                autoCompletion = AutoCompletion(KotlinScriptCompletions.create()).apply {
                    isParameterAssistanceEnabled = settings.parameterAssistance
                    isAutoActivationEnabled = true
                    autoActivationDelay = settings.autoCompleteDelayMs
                    install(codeArea)
                }
            }
            !want && have -> {
                autoCompletion?.uninstall()
                autoCompletion = null
            }
        }
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

    private fun initMarkerJumpHandler() {
        for (gutterComp in codeScrollPane.gutter.components) {
            if (gutterComp !is IconRowHeader) continue
            gutterComp.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1) return
                    val areaPoint = SwingUtilities.convertPoint(gutterComp, e.point, codeArea)
                    val offset = codeArea.viewToModel2D(areaPoint).takeIf { it >= 0 } ?: return
                    val line = codeArea.getLineOfOffset(offset)
                    codeArea.caretPosition = codeArea.getLineStartOffset(line)
                    codeArea.requestFocusInWindow()
                }
            })
            return
        }
    }

    companion object {
        private val SIMPLE_LINE_FORMATTER: LineNumberFormatter = object : LineNumberFormatter {
            override fun format(lineNumber: Int): String = lineNumber.toString()
            override fun getMaxLength(maxLineNumber: Int): Int =
                if (maxLineNumber < 10) 1 else 1 + log10(maxLineNumber.toDouble()).toInt()
        }
    }
}
