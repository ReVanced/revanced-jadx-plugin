package app.revanced.jadx.fingerprinting.ui.components

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice
import org.fife.ui.rsyntaxtextarea.parser.ParseResult
import org.fife.ui.rsyntaxtextarea.parser.ParserNotice
import javax.swing.text.BadLocationException
import kotlin.script.experimental.api.ScriptDiagnostic

/**
 * RSyntaxTextArea parser that compiles the user fingerprint script via Kotlin scripting
 * and exposes compiler diagnostics as inline error squigglies + margin markers.
 *
 * The compile call is asynchronous: [parse] returns the most-recent cached notices
 * immediately so RSTA doesn't block the EDT, then kicks off a background compile.
 * When the compile completes, the parser forces RSTA to reparse so the freshly
 * computed notices get painted.
 *
 * Successive edits cancel any in-flight compile so we never paint stale results.
 *
 * @param compileAsync suspending compile function returning the raw [ScriptDiagnostic] list
 * @param preludeLineOffset number of prelude lines prepended before the user script -
 *   subtracted from diagnostic line numbers so markers land on the user's source line
 * @param textArea owning text area; needed to call [RSyntaxTextArea.forceReparsing]
 *   when async work completes
 */
class KotlinScriptParser(
    private val compileAsync: suspend (String) -> List<ScriptDiagnostic>,
    private val preludeLineOffset: Int,
    private val textArea: RSyntaxTextArea,
) : AbstractParser() {
    private val log = KotlinLogging.logger("revanced-jadx-plugin/script-lint")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var cached: List<ScriptDiagnostic> = emptyList()
    @Volatile private var inflight: Job? = null
    private var lastText: String = ""

    override fun parse(doc: RSyntaxDocument, style: String): ParseResult {
        val text = try {
            doc.getText(0, doc.length)
        } catch (e: BadLocationException) {
            log.warn(e) { "BadLocationException reading document" }
            return emptyResult()
        }

        if (text != lastText) {
            lastText = text
            inflight?.cancel()
            if (text.isNotBlank()) {
                inflight = scope.launch {
                    val notices = runCatching { compileAsync(text) }
                        .onFailure { log.warn(it) { "Script lint compile threw" } }
                        .getOrDefault(emptyList())
                    cached = notices
                    withContext(Dispatchers.Swing) {
                        textArea.forceReparsing(this@KotlinScriptParser)
                    }
                }
            } else {
                cached = emptyList()
            }
        }

        return buildResult(doc)
    }

    private fun emptyResult(): ParseResult = DefaultParseResult(this)

    private fun buildResult(doc: RSyntaxDocument): ParseResult {
        val result = DefaultParseResult(this)
        val rootElement = doc.defaultRootElement
        val lineCount = rootElement.elementCount

        cached.forEach { report ->
            val rawLine = report.location?.start?.line ?: return@forEach
            val userLine = rawLine - preludeLineOffset
            if (userLine !in 1..lineCount) return@forEach

            val element = rootElement.getElement(userLine - 1)
            val offset = element.startOffset
            val length = (element.endOffset - offset).coerceAtLeast(1)

            val notice = DefaultParserNotice(this, report.message, userLine - 1, offset, length)
            notice.level = report.severity.toNoticeLevel()
            result.addNotice(notice)
        }
        return result
    }

    // Cancel any pending compilations
    fun dispose() {
        scope.cancel()
    }

    private fun ScriptDiagnostic.Severity.toNoticeLevel(): ParserNotice.Level = when (this) {
        ScriptDiagnostic.Severity.FATAL,
        ScriptDiagnostic.Severity.ERROR -> ParserNotice.Level.ERROR
        ScriptDiagnostic.Severity.WARNING -> ParserNotice.Level.WARNING
        ScriptDiagnostic.Severity.INFO,
        ScriptDiagnostic.Severity.DEBUG -> ParserNotice.Level.INFO
    }
}
