package app.revanced.jadx.fingerprinting.core

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.Patcher
import app.revanced.patcher.PatcherConfig
import app.revanced.patcher.PatcherContext
import app.revanced.patcher.patch.Patch
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.Method
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
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

    @OptIn(DelicateCoroutinesApi::class)
    fun createPatcher(sourceApk: File, patcherTemporaryFilesPath: File) {
        this.sourceApk = sourceApk
        this.patcherTemporaryFilesPath = File(patcherTemporaryFilesPath, UUID.randomUUID().toString())
        log.info { "Called createPatcher with $sourceApk and ${this.patcherTemporaryFilesPath}" }
        GlobalScope.launch(Dispatchers.IO) {
            ScriptEvaluation.preload()
        }
    }

    fun searchFingerprint(fingerprint: Fingerprint): Method? {
        if (!::sourceApk.isInitialized || !::patcherTemporaryFilesPath.isInitialized) {
            log.error { "Patcher not initialized" }
            return null
        }

        val patcher = cachedPatcher ?: run {
            log.info { "Creating new cached Patcher for $sourceApk" }
            Patcher(
                PatcherConfig(
                    sourceApk,
                    patcherTemporaryFilesPath,
                    null,
                    patcherTemporaryFilesPath.absolutePath,
                ),
            ).also { cachedPatcher = it }
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
