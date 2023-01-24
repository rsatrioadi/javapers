package nl.tue.win.lib

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class CsvRowsTest {

    @Test
    fun testToString() {
        val row1 = CsvRow().also {
            it["col2"] = "Hello \"world\""
            it["col1"] = 123
            it["id"] = 1
            it["name"] = "One"
        }
        val row2 = CsvRow().also {
            it["col1"] = 12.3
            it["name"] = "Two"
            it["col3"] = true
            it["col4"] = "this contains, comma"
            it["id"] = 2
        }
        val rows = CsvRows().also {
            it.add(row1)
            it.add(row2)
            it.setPreferredColumnOrder("id", "name")
        }
        println(rows)
    }
}