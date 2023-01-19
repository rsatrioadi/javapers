package nl.tue.win.lpg

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class NodeTest {

    @Test
    fun getId() {
        val id1 = "node1"
        val n1 = Node(id1)
        assertEquals(id1, n1.id)

        val id2 = "node2"
        val n2 = Node(id2, "label1")
        assertEquals(id2, n2.id)

        val id3 = "node3"
        val n3 = Node(id3, "label1", "label2")
        assertEquals(id3, n3.id)
    }

    @Test
    fun getLabels() {

        val id1 = "node1"
        val n1 = Node(id1)
        assert(n1.labels.isEmpty())
        assertEquals(setOf<String>(), n1.labels)

        val id2 = "node2"
        val n2 = Node(id2, "label1")
        assert(n2.labels.contains("label1"))
        assert(!n2.labels.contains("label2"))
        assertEquals(setOf("label1"), n2.labels)

        val id3 = "node3"
        val n3 = Node(id3, "label1", "label2")
        assert(n3.labels.contains("label1"))
        assert(n3.labels.contains("label2"))
        assertEquals(setOf("label2", "label1"), n3.labels)
    }

    @Test
    fun get() {
    }

    @Test
    fun set() {
    }

    @Test
    fun testEquals() {
    }
}