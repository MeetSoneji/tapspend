package com.meetsoneji.tapspend.core

/** CSV writer with spreadsheet formula-injection protection (cells starting with = + - @ tab CR get a leading '). */
object CsvExport {
    private val DANGEROUS = setOf('=', '+', '-', '@', '\t', '\r')

    fun cell(raw: String): String {
        var v = raw
        if (v.isNotEmpty() && v[0] in DANGEROUS) v = "'$v"
        val needsQuotes = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }

    fun row(cells: List<String>): String = cells.joinToString(",") { cell(it) }
}
