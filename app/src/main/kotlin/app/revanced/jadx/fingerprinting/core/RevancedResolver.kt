package app.revanced.jadx.fingerprinting.core

import app.revanced.jadx.fingerprinting.ReVancedFingerprintPlugin
import app.revanced.patcher.Fingerprint
import app.revanced.patcher.Patcher
import app.revanced.patcher.PatcherConfig
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

class ReVancedResolver {
    private val log = KotlinLogging.logger("${ReVancedFingerprintPlugin.ID}/resolver")
    private lateinit var sourceApk: File
    private lateinit var patcherTemporaryFilesPath: File

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
        val patcher = Patcher(
            PatcherConfig(
                this.sourceApk,
                this.patcherTemporaryFilesPath,
                null,
                this.patcherTemporaryFilesPath.absolutePath,
            ),
        )
        var searchResult: Method? = null

        val tempPatch = bytecodePatch(name = "Temporary patch for searching fingerprint") {
            execute {
                log.info { "Inside execute" }
                searchResult = fingerprint.originalMethodOrNull
                log.info { "Fingerprint found: $searchResult" }
            }
        }

        patcher.use { p ->
            p += setOf(tempPatch)
            runBlocking {
                p().collect { result ->
                    val exception = result.exception
                        ?: return@collect log.info { "\"${result.patch}\" succeeded" }
                    log.error(exception) { "\"${result.patch}\" failed:\n" }
                }
            }
        }
        log.info { "Outside of block $searchResult" }

        return searchResult
    }
}
