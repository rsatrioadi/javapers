package nl.tue.win.lib

import java.text.DecimalFormat

class CsvRows : ArrayList<CsvRow>() {
    val preferredColumnOrder: MutableList<String> = mutableListOf()

    fun setPreferredColumnOrder(vararg columns: String) {
        preferredColumnOrder.clear()
        preferredColumnOrder.addAll(columns.asList())
    }

    override fun toString(): String {
        val sep = (DecimalFormat.getInstance() as DecimalFormat).decimalFormatSymbols.decimalSeparator
        val delim = if (sep == ',') ";" else ","
        val end = System.lineSeparator()
        val allHeaders = this.flatMap { it.keys }.distinct().sorted()
        val orderedHeaders = preferredColumnOrder + (allHeaders - preferredColumnOrder.toSet())
        val headerStr = orderedHeaders.joinToString(delim)
        return (listOf(headerStr) + this.map { it.toString(*orderedHeaders.toTypedArray()) })
            .joinToString(end)
    }
}