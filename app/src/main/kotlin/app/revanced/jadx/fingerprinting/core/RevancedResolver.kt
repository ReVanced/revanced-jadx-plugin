package app.revanced.jadx.fingerprinting.core

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.Patcher
import app.revanced.patcher.PatcherConfig
import app.revanced.patcher.PatcherContext
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.lang.reflect.Field
import java.util.UUID

class ReVancedResolver : AutoCloseable {
    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/resolver")
    private lateinit var sourceApk: File
    private lateinit var patcherTemporaryFilesPath: File

    @Volatile private var cachedPatcher: Patcher? = null
    private val executablePatchesField by lazy {
        PatcherContext::class.java.getDeclaredField("executablePatches").also { it.isAccessible = true }
    }
    private val allPatchesField by lazy {
        PatcherContext::class.java.getDeclaredField("allPatches").also { it.isAccessible = true }
    }
    private val bytecodeContextField by lazy {
        PatcherContext::class.java.getDeclaredField("bytecodeContext").also { it.isAccessible = true }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun createPatcher(
        sourceApk: File,
        patcherTemporaryFilesPath: File,
        onMethodsLoaded: (List<Method>) -> Unit = {},
    ) {
        this.sourceApk = sourceApk
        this.patcherTemporaryFilesPath = File(patcherTemporaryFilesPath, UUID.randomUUID().toString())
        log.info { "Called createPatcher with $sourceApk and ${this.patcherTemporaryFilesPath}" }
        GlobalScope.launch(Dispatchers.IO) {
            ScriptEvaluation.preload()
            try {
                log.info { "Eagerly initializing Patcher for $sourceApk" }
                val patcher = buildPatcher().also { cachedPatcher = it }
                val methods = extractMethodsFromPatcher(patcher)
                log.info { "Extracted ${methods.size} methods from Patcher context" }
                onMethodsLoaded(methods)
            } catch (e: Exception) {
                log.error(e) { "Failed to eagerly initialize Patcher or extract methods" }
            }
        }
    }

    private fun buildPatcher() = Patcher(
        PatcherConfig(
            sourceApk,
            patcherTemporaryFilesPath,
            null,
            patcherTemporaryFilesPath.absolutePath,
        ),
    )

    private fun extractMethodsFromPatcher(patcher: Patcher): List<Method> {
        val bytecodeCtx = bytecodeContextField.get(patcher.context)
        val classesField = findFieldByName(bytecodeCtx.javaClass, "classes")
            ?: throw NoSuchFieldException("'classes' field not found in ${bytecodeCtx.javaClass.name}")
        @Suppress("UNCHECKED_CAST")
        return (classesField.get(bytecodeCtx) as Iterable<ClassDef>).flatMap { it.methods }
    }

    private fun findFieldByName(cls: Class<*>, name: String): Field? {
        var c: Class<*>? = cls
        while (c != null) {
            try { return c.getDeclaredField(name).also { it.isAccessible = true } }
            catch (_: NoSuchFieldException) { c = c.superclass }
        }
        return null
    }

    fun searchFingerprint(fingerprint: Fingerprint): Method? {
        if (!::sourceApk.isInitialized || !::patcherTemporaryFilesPath.isInitialized) {
            log.error { "Patcher not initialized" }
            return null
        }

        val patcher = cachedPatcher ?: run {
            log.warn { "Patcher not yet ready; creating synchronously for $sourceApk" }
            buildPatcher().also { cachedPatcher = it }
        }

        var searchResult: Method? = null

        val tempPatch = bytecodePatch(name = "Temporary patch for searching fingerprint") {
            execute {
                log.info { "Inside execute" }
                searchResult = fingerprint.originalMethodOrNull
                log.info { "Fingerprint found: $searchResult" }
            }
        }

        patcher += setOf(tempPatch)
        runBlocking {
            patcher().collect { result ->
                val exception = result.exception
                    ?: return@collect log.info { "\"${result.patch}\" succeeded" }
                log.error(exception) { "\"${result.patch}\" failed:\n" }
            }
        }
        clearPatches(patcher.context)

        log.info { "Search result: $searchResult" }
        return searchResult
    }

    @Suppress("UNCHECKED_CAST")
    private fun clearPatches(context: PatcherContext) {
        (executablePatchesField.get(context) as MutableSet<Patch<*>>).clear()
        (allPatchesField.get(context) as MutableSet<Patch<*>>).clear()
    }

    override fun close() {
        cachedPatcher?.close()
        cachedPatcher = null
    }
}
