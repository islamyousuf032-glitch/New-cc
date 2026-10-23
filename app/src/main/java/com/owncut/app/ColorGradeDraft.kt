package com.owncut.app

/** Text transport for exactly English/Bengali entry; native engine owns value validity.
 * A draft is not a history mutation and must successfully render before UI Apply.
 */
internal object ColorGradeDraft {
    fun parse(values: List<String>, enabled: Boolean): ColorGrade = ColorGrade.fromParametersV1(
        values.map { text -> text.trim().map { c -> when(c) {
            in '\u09e6'..'\u09ef' -> ('0'.code+c.code-'\u09e6'.code).toChar()
            '\u2212' -> '-'
            else -> c
        } }.joinToString("").toDouble() }.toDoubleArray(),enabled)
}
