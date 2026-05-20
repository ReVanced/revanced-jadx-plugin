package app.revanced.utils

import app.revanced.patcher.extensions.InstructionExtensions.instructionsOrNull
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
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

fun Method.getShortId(): String {
    return "${this.definingClass}${this.name}(${this.parameterTypes.joinToString(separator = "") { it.toString() }})${this.returnType}"
}
class OpcodeListSerializer(
    private val opcodesParent: Opcodes
) : KSerializer<List<Opcode>> {
    override val descriptor: SerialDescriptor =
        ListSerializer(Int.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<Opcode>) {
        val intList = value.map {
            val opcodeValue = opcodesParent.getOpcodeValue(it)
            opcodeValue ?: throw IllegalArgumentException("Opcode value is null for $it")
            "$opcodeValue@$it"
        }
        encoder.encodeSerializableValue(ListSerializer(String.serializer()), intList)
    }

    override fun deserialize(decoder: Decoder): List<Opcode> {
        return listOf(Opcode.CONST_STRING)
    }
}
object AccessFlagSerializer: KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        Int.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Int) {
        val formattedFlags = AccessFlags.formatAccessFlagsForMethod(value)
        encoder.encodeSerializableValue(Int.serializer(), value)
    }

    override fun deserialize(decoder: Decoder): Int {
        return decoder.decodeInt()
    }
}
@Serializable
data class MethodFingerprint(
    val id: String,
    @Serializable(with = AccessFlagSerializer::class)
    val accessFlags: Int,
    val returnType: String,
    val parameters: List<String>,
    @Contextual
    val opcodes: List<Opcode>,
    val strings: List<String>,
)

fun Method.toMethodFingerprint(): MethodFingerprint {
    return MethodFingerprint(
        accessFlags = this.accessFlags,
        returnType = this.returnType,
        parameters = this.parameters.map { it.type },
        opcodes = this.instructionsOrNull?.map { it.opcode } ?: emptyList(),
        id = this.getShortId(),
        strings = this.instructionsOrNull?.mapNotNull { instruction ->
            if (instruction.opcode == Opcode.CONST_STRING || instruction.opcode == Opcode.CONST_STRING_JUMBO) {
                (instruction as ReferenceInstruction).reference as? StringReference
            } else {
                null
            }
        }?.map { it.string } ?: emptyList(),
    )
}