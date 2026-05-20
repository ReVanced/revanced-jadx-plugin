package app.revanced.jadx.fingerprinting.ui

internal object Icons {
    val revanced = """
        <svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" id="revanced" height="16" width="16">
            <path d="M5.1 0a0.28 0.28 0 0 0 -0.23 0.42l6.88 11.93a0.28 0.28 0 0 0 0.48 0L19.13 0.42A0.28 0.28 0 0 0 18.9 0ZM0.5 0a0.33 0.33 0 0 0 -0.3 0.46L10.43 23.8c0.05 0.12 0.17 0.2 0.3 0.2h2.54c0.13 0 0.25 -0.08 0.3 -0.2L23.8 0.46a0.33 0.33 0 0 0 -0.3 -0.46h-2.32a0.24 0.24 0 0 0 -0.21 0.14L12.2 20.08a0.23 0.23 0 0 1 -0.42 0L3.03 0.14A0.23 0.23 0 0 0 2.82 0Z" fill="currentColor" stroke-width="1"></path>
        </svg>
    """.trimIndent()

    val playArrow = """
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><!-- Icon from Material Symbols by Google - https://github.com/google/material-design-icons/blob/master/LICENSE --><path fill="currentColor" d="M8 19V5l11 7z"/></svg>
    """.trimIndent()

    val nextArrow = """
        <svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px"><!-- Icon from Material Symbols by Google - https://github.com/google/material-design-icons/blob/master/LICENSE --><path fill="currentColor" d="M647-440H160v-80h487L423-744l57-56 320 320-320 320-57-56 224-224Z"/></svg>
    """.trimIndent()

    val previousArrow = """
        <svg xmlns="http://www.w3.org/2000/svg" height="24px" viewBox="0 -960 960 960" width="24px"><!-- Icon from Material Symbols by Google - https://github.com/google/material-design-icons/blob/master/LICENSE --><path fill="currentColor" d="m313-440 224 224-57 56-320-320 320-320 57 56-224 224h487v80H313Z"/></svg>
    """.trimIndent()

    val clear = """
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><!-- Icon from Material Symbols by Google - https://github.com/google/material-design-icons/blob/master/LICENSE --><path fill="currentColor" d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
    """.trimIndent()

    fun copy(size: Int) = """
        <svg xmlns="http://www.w3.org/2000/svg" width="$size" height="$size" viewBox="0 0 24 24"><!-- Icon from Material Symbols by Google - https://github.com/google/material-design-icons/blob/master/LICENSE --><path fill="currentColor" d="M9 18q-.825 0-1.412-.587T7 16V4q0-.825.588-1.412T9 2h9q.825 0 1.413.588T20 4v12q0 .825-.587 1.413T18 18zm-4 4q-.825 0-1.412-.587T3 20V6h2v14h11v2z"/></svg>
    """.trimIndent()
}
