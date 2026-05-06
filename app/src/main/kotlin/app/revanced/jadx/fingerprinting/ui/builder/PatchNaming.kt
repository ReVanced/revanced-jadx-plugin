package app.revanced.jadx.fingerprinting.ui.builder

/**
 * DEX init method name for static (`<clinit>`) or instance (`<init>`) contexts.
 * Centralizes the convention to avoid stringly-typed duplication across patch builders.
 */
internal fun initMethodName(isStatic: Boolean): String =
    if (isStatic) "<clinit>" else "<init>"

// Default fingerprint property name for nullifier patches.
internal fun defaultNullifyVarName(isStatic: Boolean): String =
    if (isStatic) "staticInitFingerprint" else "constructorFingerprint"
