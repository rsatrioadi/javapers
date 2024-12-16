package nl.tue.win.lpg.encoder

import io.mockk.every
import io.mockk.mockk
import nl.tue.win.lpg.Node
import nl.tue.win.lpg.Nodes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CyJsonCodecTest {

    @Test
    fun encodeGraph() {
    }

    @Test
    fun encodeNodes() {
        val n1 = mockk<Node>()
        every { n1.id } returns "node1"
        every { n1.labels } returns mutableSetOf("label1", "label2")
        every { n1.properties } returns hashMapOf(
            Pair("strProp", "value1"),
            Pair("numProp", 2),
            Pair("boolProp", true),
            Pair("listProp", listOf("a","b")))
        val n2 = mockk<Node>()
        every { n2.id } returns "node2"
        every { n2.labels } returns mutableSetOf()
        every { n2.properties } returns hashMapOf(
            Pair("strProp", "value2"),
            Pair("numProp", 5))
        val nodes = mockk<Nodes>()
        every { nodes.asList() } returns listOf(n1,n2)
        val jo = CyJsonCodec.encodeNodes(nodes)
        println(jo.toString(2))
        assertEquals(2, jo.length())
    }

    @Test
    fun encodeEdges() {
    }

    @Test
    fun encodeNode() {

        val n1 = mockk<Node>()
        every { n1.id } returns "node1"
        every { n1.labels } returns mutableSetOf("label1", "label2")
        every { n1.properties } returns hashMapOf(
            Pair("strProp", "value1"),
            Pair("numProp", 2),
            Pair("boolProp", true),
            Pair("listProp", listOf("a","b")))
        val jo = CyJsonCodec.encodeNode(n1)
        println(jo.toString(2))

        val data = jo["data"]
        assert(data is JSONObject)

        data as JSONObject
        assertEquals("node1", data["id"])
        val labels = data["labels"]
        assert(labels is JSONArray)

        labels as JSONArray
        assertEquals(2, labels.length())
        assert(labels.contains("label1"))
        assert(labels.contains("label2"))

        val properties = data["properties"]
        assert(properties is JSONObject)
        properties as JSONObject
        assertEquals(2, properties["numProp"])
    }

    @Test
    fun encodeEdge() {
    }

    @Test
    fun decodeGraph() {
    }

    @Test
    fun decodeNodes() {
    }

    @Test
    fun decodeEdges() {
    }

    @Test
    fun decodeNode() {

        val n1 = mockk<Node>()
        every { n1.id } returns "node1"
        every { n1.labels } returns mutableSetOf("label1", "label2")
        every { n1.properties } returns hashMapOf(
            Pair("strProp", "value1"),
            Pair("numProp", 2),
            Pair("boolProp", true),
            Pair("listProp", listOf("a","b")))
        val jo = CyJsonCodec.encodeNode(n1)

        val n2 = CyJsonCodec.decodeNode(jo)
        assertEquals(n1.id,n2.id)
        assertEquals(n1.labels,n2.labels)
        assertEquals(n1.properties,n2.properties)
    }

    @Test
    fun decodeEdge() {
    }
}
