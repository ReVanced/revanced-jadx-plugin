package app.revanced.jadx.fingerprinting.core

import com.android.tools.smali.dexlib2.iface.Method

fun Method.getShortId(): String {
    return "${this.name}(${this.parameterTypes.joinToString(separator = "") { it.toString() }})${this.returnType}"
}
