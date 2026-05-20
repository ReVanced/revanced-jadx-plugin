package app.revanced.jadx.fingerprinting.ui

import app.revanced.patcher.Fingerprint
import com.android.tools.smali.dexlib2.analysis.reflection.util.ReflectionUtils
import com.android.tools.smali.dexlib2.iface.Method
import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.core.SCRIPT_PRELUDE_LINE_COUNT
import app.revanced.jadx.fingerprinting.core.ScriptEvaluation
import app.revanced.jadx.fingerprinting.core.getShortId
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.gui.JadxGuiContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.regex.Pattern
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

private const val PAGE_SIZE = 10

class FingerprintResultPanel(
    private val context: JadxPluginContext,
    private val guiContext: JadxGuiContext,
    private val scriptProvider: () -> String,
    private val clearAction: () -> Unit = {},
) : JPanel(BorderLayout()) {
    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/result-panel")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var matchedMethods = linkedSetOf<Method>()
    @Volatile private var cachedFingerprint: Fingerprint? = null
    private var currentPage = 0
    private var exhausted = false
    private val copyIcon = ReVancedJadxPluginUi.inlineSvgIcon(Icons.copy(16))

    private val runButton = iconButton(
        "Run the script",
        ReVancedJadxPluginUi.inlineSvgIcon(Icons.playArrow),
    )
    private val clearButton = iconButton(
        "Clear editor",
        ReVancedJadxPluginUi.inlineSvgIcon(Icons.clear),
    )
    private val prevPageButton = iconButton(
        "Previous page",
        ReVancedJadxPluginUi.inlineSvgIcon(Icons.previousArrow),
    ).apply { isEnabled = false }
    private val nextPageButton = iconButton(
        "Next page / load more",
        ReVancedJadxPluginUi.inlineSvgIcon(Icons.nextArrow),
    ).apply { isEnabled = false }

    private val resultLabel = JLabel("Fingerprint result").apply {
        border = BorderFactory.createEmptyBorder(0, 10, 0, 0)
    }
    private val matchCountLabel = JLabel("").apply {
        border = BorderFactory.createEmptyBorder(0, 10, 0, 0)
    }

    private val resultContentBox = Box.createVerticalBox()
    private val resultScrollPane: JScrollPane

    init {
        val upPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(runButton)
            add(clearButton)
            add(resultLabel)
        }
        val downPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(prevPageButton)
            add(nextPageButton)
            add(matchCountLabel)
        }
        val resultHeaderPanel = JPanel(GridLayout(2, 1, 10, 10)).apply {
            border = BorderFactory.createEmptyBorder(0, 10, 10, 0)
            add(upPanel)
            add(downPanel)
        }
        add(resultHeaderPanel, BorderLayout.NORTH)

        val resultContentPanel = JPanel(BorderLayout()).apply {
            add(resultContentBox, BorderLayout.PAGE_START)
        }
        resultScrollPane = JScrollPane(resultContentPanel)
        add(resultScrollPane, BorderLayout.CENTER)

        runButton.addActionListener {
            matchedMethods = linkedSetOf()
            cachedFingerprint = null
            currentPage = 0
            exhausted = false
            matchCountLabel.text = ""
            fetchAndRender(statusText = "Evaluating…", isFirstRun = true)
        }
        clearButton.addActionListener { clearAction() }

        prevPageButton.addActionListener {
            currentPage--
            renderCurrentPage()
        }
        nextPageButton.addActionListener {
            currentPage++
            val needed = (currentPage + 1) * PAGE_SIZE
            if (!exhausted && matchedMethods.size < needed) {
                fetchAndRender(statusText = "Searching…", isFirstRun = false)
            } else {
                renderCurrentPage()
            }
        }
    }

    override fun removeNotify() {
        super.removeNotify()
        scope.cancel()
    }

    private fun fetchAndRender(statusText: String, isFirstRun: Boolean) {
        setControlsEnabled(false)
        showStatusText(statusText)

        val script = scriptProvider()
        scope.launch {
            val executionTime = measureTime {
                if (isFirstRun) {
                    var evalResult: ResultWithDiagnostics<EvaluationResult>? = null
                    var evalError: Throwable? = null
                    try {
                        evalResult = ScriptEvaluation.rawEvaluate(script)
                    } catch (t: Throwable) {
                        evalError = t
                        log.error(t) { "Exception during script evaluation" }
                    }

                    if (evalError != null) {
                        withContext(Dispatchers.Swing) {
                            showStatusText("Evaluation failed: ${evalError.message}")
                            setControlsEnabled(true)
                        }
                        return@launch
                    }

                    val fp = extractFingerprint(evalResult!!) { msg ->
                        withContext(Dispatchers.Swing) {
                            showStatusText(msg)
                            setControlsEnabled(true)
                        }
                    }
                    if (fp == null) return@launch
                    cachedFingerprint = fp
                }

                val fp = cachedFingerprint
                if (fp == null) {
                    withContext(Dispatchers.Swing) {
                        showStatusText("No fingerprint - please run the script first.")
                        setControlsEnabled(true)
                    }
                    return@launch
                }

                val needed = (currentPage + 1) * PAGE_SIZE
                while (!exhausted && matchedMethods.size < needed) {
                    fp.ignoreSet = matchedMethods.toSet()
                    val next = ReVancedJadxPluginUi.resolver.searchFingerprint(fp)
                    if (next != null) matchedMethods.add(next)
                    else exhausted = true
                }
            }

            withContext(Dispatchers.Swing) {
                resultLabel.text = "Executed in ${executionTime.inWholeMilliseconds.milliseconds}"
                renderCurrentPage()
            }
        }
    }

    private fun renderCurrentPage() {
        val allResults = matchedMethods.toList()
        val startIdx = currentPage * PAGE_SIZE
        val pageSlice = allResults.drop(startIdx).take(PAGE_SIZE)

        resultContentBox.removeAll()

        if (pageSlice.isEmpty()) {
            val msg = if (allResults.isEmpty()) "Fingerprint not found in the APK." else "No more results."
            resultContentBox.add(ReVancedJadxPluginUi.createWrappedTextArea(msg).apply {
                alignmentX = LEFT_ALIGNMENT
            })
        } else {
            pageSlice.forEachIndexed { i, method ->
                if (i > 0) resultContentBox.add(Box.createVerticalStrut(10))
                resultContentBox.add(resultCard(startIdx + i + 1, method))
            }
        }

        val total = allResults.size
        val firstShown = if (total == 0) 0 else startIdx + 1
        val lastShown = minOf(startIdx + PAGE_SIZE, total)
        matchCountLabel.text = when {
            total == 0 -> "No match"
            exhausted -> "Showing $firstShown-$lastShown of $total"
            else -> "Showing $firstShown-$lastShown of $total+"
        }
        prevPageButton.isEnabled = currentPage > 0
        nextPageButton.isEnabled = !exhausted || startIdx + PAGE_SIZE < total

        setControlsEnabled(true)
        resultContentBox.revalidate()
        resultContentBox.repaint()
        resultScrollPane.verticalScrollBar.value = resultScrollPane.verticalScrollBar.minimum
    }

    private fun resultCard(displayIndex: Int, method: Method): JComponent {
        val dexClass = method.definingClass
        val javaName = ReflectionUtils.dexToJavaName(dexClass).replace("$", ".")
        val shortId = method.getShortId()
        val accentColor: Color = UIManager.getColor("Component.accentColor") ?: Color(0x4d9fec)

        val grid = JPanel(GridBagLayout()).apply {
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, accentColor),
                BorderFactory.createEmptyBorder(6, 8, 6, 4),
            )
        }

        val labelGbc = GridBagConstraints().apply {
            gridx = 0
            anchor = GridBagConstraints.EAST
            insets = Insets(1, 0, 1, 8)
        }
        val valueGbc = GridBagConstraints().apply {
            gridx = 1
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(1, 0, 1, 2)
        }
        val copyGbc = GridBagConstraints().apply {
            gridx = 2
            anchor = GridBagConstraints.CENTER
            insets = Insets(1, 0, 1, 0)
        }

        grid.add(JLabel("#$displayIndex").apply { font = font.deriveFont(Font.BOLD) },
            GridBagConstraints().apply {
                gridx = 0; gridwidth = 3; gridy = 0
                anchor = GridBagConstraints.WEST
                insets = Insets(0, 0, 6, 0)
            })

        var row = 1
        fun addRow(label: String, value: String) {
            grid.add(JLabel("$label:"), labelGbc.atRow(row))
            grid.add(JTextField(value).apply {
                isEditable = false
                border = null
                isOpaque = false
            }, valueGbc.atRow(row))
            grid.add(copyButton(value), copyGbc.atRow(row))
            row++
        }

        addRow("Class (Java)", javaName)
        addRow("Class (DEX)", dexClass)
        addRow("Short ID", shortId)

        val javaKlass = context.decompiler.searchJavaClassByOrigFullName(javaName)
        val fgMethod = javaKlass?.searchMethodByShortId(shortId)
        val actionRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        fgMethod?.let { sourceMethod ->
            addRow("Method", sourceMethod.fullName)
            actionRow.add(JButton("Jump to method").apply {
                addActionListener {
                    if (!guiContext.open(sourceMethod.codeNodeRef))
                        log.error { "Failed to jump to method: ${sourceMethod.fullName}" }
                }
            })
        }
        actionRow.add(JButton("Find usages").apply {
            toolTipText = "Scan APK for methods that call this method"
            addActionListener { showUsagesDialog(method) }
        })
        grid.add(actionRow, GridBagConstraints().apply {
            gridx = 1; gridwidth = 2; gridy = row
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 0, 0, 0)
        })

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            alignmentX = LEFT_ALIGNMENT
            add(grid, BorderLayout.CENTER)
        }
    }

    private fun showUsagesDialog(target: Method) {
        val dialog = JDialog(guiContext.mainFrame, "Find usages - ${target.name}", false)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.setSize(900, 520)
        dialog.setLocationRelativeTo(guiContext.mainFrame)

        val targetJavaName = ReflectionUtils.dexToJavaName(target.definingClass).replace("$", ".")
        val targetSig = "${target.name}(${target.parameterTypes.joinToString(", ")})${target.returnType}"
        val headerLabel = JLabel(
            "<html><b>$targetSig</b><br><small>in <tt>$targetJavaName</tt></small></html>"
        ).apply { border = BorderFactory.createEmptyBorder(0, 0, 8, 0) }

        val statusLabel = JLabel("Scanning APK…")
        val filterField = JTextField(20).apply { toolTipText = "Substring filter (case-insensitive); matches class or method" }
        val filterRow = JPanel(BorderLayout(8, 0)).apply {
            add(statusLabel, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
                add(JLabel("Filter:"))
                add(filterField)
            }, BorderLayout.EAST)
        }
        val callerHolder = mutableListOf<Method>()
        val model = object : DefaultTableModel(arrayOf("Class", "Method"), 0) {
            override fun isCellEditable(row: Int, col: Int) = false
            override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
        }
        val sorter = TableRowSorter(model)
        val table = JTable(model).apply {
            rowSorter = sorter
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
            setShowGrid(false)
            rowHeight = 22
            intercellSpacing = Dimension(0, 0)
        }
        table.columnModel.getColumn(0).preferredWidth = 480
        table.columnModel.getColumn(1).preferredWidth = 360

        fun jumpToSelected() {
            val viewRow = table.selectedRow.takeIf { it >= 0 } ?: return
            val modelRow = table.convertRowIndexToModel(viewRow)
            val caller = callerHolder.getOrNull(modelRow) ?: return
            val javaName = ReflectionUtils.dexToJavaName(caller.definingClass).replace("$", ".")
            val shortId = caller.getShortId()
            val sourceMethod = context.decompiler.searchJavaClassByOrigFullName(javaName)
                ?.searchMethodByShortId(shortId)
            if (sourceMethod != null) {
                if (!guiContext.open(sourceMethod.codeNodeRef))
                    log.error { "Failed to jump to caller: ${sourceMethod.fullName}" }
            } else {
                log.warn { "No JADX source for caller: $javaName.$shortId" }
            }
        }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && table.selectedRow >= 0) jumpToSelected()
            }
        })

        filterField.document.addDocumentListener(object : DocumentListener {
            private fun applyFilter() {
                val text = filterField.text
                sorter.rowFilter = if (text.isBlank()) null
                else RowFilter.regexFilter("(?i)" + Pattern.quote(text))
            }
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
        })

        val openBtn = JButton("Open Selected").apply {
            isEnabled = false
            addActionListener { jumpToSelected() }
        }
        table.selectionModel.addListSelectionListener { openBtn.isEnabled = table.selectedRow >= 0 }
        val closeBtn = JButton("Close").apply { addActionListener { dialog.dispose() } }
        val southPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply {
            add(openBtn); add(closeBtn)
        }

        val northPanel = JPanel(BorderLayout()).apply {
            add(headerLabel, BorderLayout.NORTH)
            add(filterRow, BorderLayout.CENTER)
            border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        }
        dialog.contentPane.add(JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(northPanel, BorderLayout.NORTH)
            add(JScrollPane(table), BorderLayout.CENTER)
            add(southPanel, BorderLayout.SOUTH)
        })
        dialog.isVisible = true

        scope.launch {
            val callers: List<Method>
            val scanTime = measureTime {
                callers = runCatching { ReVancedJadxPluginUi.resolver.findCallers(target) }
                    .onFailure { log.error(it) { "findCallers failed" } }
                    .getOrDefault(emptyList())
            }
            val sortedCallers = callers.sortedWith(compareBy({ it.definingClass }, { it.name }))
            withContext(Dispatchers.Swing) {
                statusLabel.text = "Found ${callers.size} usage(s) in ${scanTime.inWholeMilliseconds.milliseconds}"
                callerHolder.clear()
                callerHolder.addAll(sortedCallers)
                sortedCallers.forEach { caller ->
                    val javaName = ReflectionUtils.dexToJavaName(caller.definingClass).replace("$", ".")
                    model.addRow(arrayOf(javaName, caller.getShortId()))
                }
            }
        }
    }

    private fun iconButton(tooltipText: String, icon: Icon): JButton =
        JButton(null, icon).apply {
            toolTipText = tooltipText
            margin = Insets(3, 3, 3, 3)
            preferredSize = Dimension(icon.iconWidth, icon.iconHeight)
            maximumSize = preferredSize
            border = BorderFactory.createEmptyBorder(3, 3, 3, 3)
        }

    private fun copyButton(valueToCopy: String): JButton =
        JButton(null, copyIcon).apply {
            toolTipText = "Copy to Clipboard"
            border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
            isContentAreaFilled = false
            setFixedSize(20)
            addActionListener { guiContext.copyToClipboard(valueToCopy) }
        }

    private fun showStatusText(text: String) {
        resultContentBox.removeAll()
        resultContentBox.add(ReVancedJadxPluginUi.createWrappedTextArea(text).apply {
            alignmentX = LEFT_ALIGNMENT
        })
        resultContentBox.revalidate()
        resultContentBox.repaint()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        runButton.isEnabled = enabled
        clearButton.isEnabled = enabled
        if (!enabled) {
            prevPageButton.isEnabled = false
            nextPageButton.isEnabled = false
        }
    }

    private suspend fun extractFingerprint(
        evalResult: ResultWithDiagnostics<EvaluationResult>,
        onError: suspend (String) -> Unit,
    ): Fingerprint? = when (evalResult) {
        is ResultWithDiagnostics.Failure -> {
            val msgs = buildString {
                appendLine("Script evaluation failed:")
                evalResult.reports.forEach { report ->
                    val userLine = report.location?.start?.line
                        ?.minus(SCRIPT_PRELUDE_LINE_COUNT)
                        ?.takeIf { it >= 1 }
                    val locPart = userLine?.let { " (line $it)" } ?: ""
                    appendLine("  ${report.severity}: ${report.message}$locPart")
                    log.error { "  ${report.severity}: ${report.message}$locPart" }
                }
            }
            onError(msgs.trim())
            null
        }
        is ResultWithDiagnostics.Success -> when (val rv = evalResult.value.returnValue) {
            ResultValue.NotEvaluated -> { onError("Script was not evaluated."); null }
            is ResultValue.Error -> {
                log.error(rv.error) { "Script execution error" }
                onError("Script execution error: ${rv.error.message}")
                null
            }
            is ResultValue.Unit -> { onError("Script did not produce a value."); null }
            is ResultValue.Value -> when (val v = rv.value) {
                null -> { onError("Script returned null."); null }
                !is Fingerprint -> {
                    log.error { "Actual value classloader: ${v.javaClass.classLoader}" }
                    log.error { "Expected Fingerprint classloader: ${Fingerprint::class.java.classLoader}" }
                    onError("Script returned unexpected type: ${rv.type}")
                    null
                }
                else -> v
            }
        }
    }
}
