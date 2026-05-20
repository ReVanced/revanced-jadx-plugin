package app.revanced.jadx.fingerprinting.ui.builder

import javax.swing.JComboBox

/**
 * DEX type descriptor catalogs for combo box population.
 *
 * Each entry uses the format `"<descriptor> - <human label>"` so users can read
 * the friendly name while [comboValue] extracts only the descriptor portion.
 */
internal val DEX_PRIMITIVE_TYPES: Array<String> = arrayOf(
    "V - void", "Z - boolean", "B - byte", "S - short",
    "C - char", "I - int", "J - long", "F - float", "D - double",
    "Ljava/lang/String; - String",
    "Ljava/lang/CharSequence; - CharSequence",
    "Ljava/lang/Object; - Object",
    "Ljava/lang/Integer; - Integer",
    "Ljava/lang/Long; - Long",
    "Ljava/lang/Boolean; - Boolean",
    "Ljava/lang/Runnable; - Runnable",
    "Ljava/util/List; - List",
    "Ljava/util/Map; - Map",
    "Ljava/util/Set; - Set",
    "[B - byte[]",
    "[I - int[]",
    "[Z - boolean[]",
    "[Ljava/lang/String; - String[]",
    "[Ljava/lang/Object; - Object[]",
)

// Method return types: primitives plus a leading "(any type)" sentinel that maps to no constraint.
internal val DEX_RETURN_TYPES: Array<String> = arrayOf("(any type)") + DEX_PRIMITIVE_TYPES

// Field types: no `void`, with rare reference types omitted (Map/Set/Runnable/etc.).
internal val DEX_FIELD_TYPES: Array<String> = arrayOf(
    "Z - boolean", "I - int", "B - byte", "S - short",
    "C - char", "J - long", "F - float", "D - double",
    "Ljava/lang/String; - String",
    "Ljava/lang/CharSequence; - CharSequence",
    "Ljava/lang/Object; - Object",
    "Ljava/util/List; - List",
    "[B - byte[]",
    "[I - int[]",
    "[Ljava/lang/String; - String[]",
    "[Ljava/lang/Object; - Object[]",
)

/**
 * Extracts the DEX descriptor from a combo entry like `"Z - boolean"` → `"Z"`.
 * Returns `""` when [item] is null or matches [skip] (sentinel for "no constraint").
 */
internal fun comboValue(item: Any?, skip: String = ""): String {
    val raw = item?.toString()
        ?.let { if (it.contains(" - ")) it.substringBefore(" - ").trim() else it }
        ?: ""
    return if (raw == skip) "" else raw
}

// Editable combo populated from a DEX type catalog. Defaults to [DEX_PRIMITIVE_TYPES].
internal fun typeCombo(items: Array<String> = DEX_PRIMITIVE_TYPES): JComboBox<String> =
    JComboBox(items).apply { isEditable = true }
