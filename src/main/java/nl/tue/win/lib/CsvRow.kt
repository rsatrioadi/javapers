package nl.tue.win.lib

import java.text.DecimalFormat


class CsvRow : HashMap<String, Any?>() {
    val preferredColumnOrder: MutableList<String> = mutableListOf()
        get() = field

    fun setPreferredColumnOrder(vararg columns: String) {
        preferredColumnOrder.clear()
        preferredColumnOrder.addAll(columns.asList())
    }

    fun toString(vararg prefColumnOrder: String): String {
        val sep = (DecimalFormat.getInstance() as DecimalFormat).decimalFormatSymbols.decimalSeparator
        val delim = if (sep == ',') ";" else ","
        val esc = "\""
        val end = System.lineSeparator()
        val header = prefColumnOrder.toList() + (this.keys.toList() - prefColumnOrder.toSet())
        return header.joinToString(delim) { escape(this.getOrDefault(it, "").toString(), delim, esc, end) }
    }

    override fun toString(): String {
        return this.toString(*preferredColumnOrder.toTypedArray())
    }
}

fun escape(token: String, delimiter: String, escape: String, lineEnd: String): String {
    return if (token.contains(delimiter) || token.contains(escape) || token.contains(lineEnd))
        escape + token.replace(escape.toRegex(), escape + escape) + escape
    else token
}
