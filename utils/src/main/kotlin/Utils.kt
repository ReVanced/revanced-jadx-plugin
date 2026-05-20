package app.revanced.utils

import app.revanced.patcher.extensions.InstructionExtensions.instructionsOrNull
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import lanchon.multidexlib2.BasicDexFileNamer
import lanchon.multidexlib2.MultiDexIO
import java.io.File

fun dumpMethodFingerprintsToJson() {
    val apkFile = File("test.apk")
    val opcodes: Opcodes
    val classes = MultiDexIO.readDexFile(
        true,
        apkFile,
        BasicDexFileNamer(),
        null,
        null,
    ).also { opcodes = it.opcodes }.classes
    val classFingerprints: List<Pair<ClassDef, List<MethodFingerprint>>> = classes.map { classDef ->
        val methods = classDef.methods.map { method ->
            MethodFingerprint(
                accessFlags = method.accessFlags,
                returnType = method.returnType,
                parameters = method.parameters.map { it.type },
                opcodes = method.instructionsOrNull?.map { it.opcode } ?: emptyList(),
                id = method.getShortId(),
                strings = method.instructionsOrNull?.mapNotNull { instruction ->
                    if (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) {
                        (instruction as ReferenceInstruction).reference as? StringReference
                    } else {
                        null
                    }
                }?.map { it.string } ?: emptyList(),
            )
        }
        classDef to methods
    }
    val module = SerializersModule {
        contextual(
            // You need to mention the exact Kotlin type here:
            OpcodeListSerializer(opcodes)
        )
    }
    val outputJson = File("methods.json")
    val json = Json {
        serializersModule = module
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
    }
    outputJson.bufferedWriter().use { writer ->
        json.encodeToString(
            classFingerprints.flatMap { (_, methods) ->
                methods.map { methodFingerprint ->
                    methodFingerprint
                }
            }
        ).let { writer.write(it) }
    }


}

fun main(){
    val apkFile = File("test.apk")
    val classes = MultiDexIO.readDexFile(
        true,
        apkFile,
        BasicDexFileNamer(),
        null,
        null,
    ).classes
    val allMethods = classes.flatMap { classDef ->
        classDef.methods
    }
    val solver = Solver(allMethods)
    val targetMethodId = "Ltech/httptoolkit/pinning_demo/MainActivity;doesCertMatchPin(Ljava/lang/String;Ljava/security/cert/Certificate;)Z"
    val uniqueFeatures = solver.getMinimalDistinguishingFeatures(targetMethodId)
    if (uniqueFeatures.isEmpty()) {
        println("No unique feature set found for method: $targetMethodId")
        return
    }
    println("Unique feature set for method $targetMethodId:")
    uniqueFeatures.forEach { feature ->
        println("Feature: $feature")
    }
    val mutableFeatures = uniqueFeatures.toMutableList()

    mutableFeatures.add("parameter_2|test")
    mutableFeatures.add("accessFlags|18")
    mutableFeatures.add("strings|teststring")
    mutableFeatures.add("strings|thingy")

    val stringFingerprint = Solver.featuresToFingerprint(mutableFeatures)
    println("String fingerprint:\n$stringFingerprint")

}