package nl.tue.win.lpg

import nl.tue.win.lpg.encoder.CyJsonCodec
import nl.tue.win.lpg.encoder.GraphCodec

class Edge(val sourceId: String, val targetId: String, val id: String, vararg labels: String = arrayOf()) {

    val labels: Set<String>
    val properties: HashMap<String, Any> = HashMap()

    init {
        this.labels = setOf(*labels)
    }

    operator fun get(property: String): Any? {
        return properties[property]
    }

    operator fun set(property: String, value: Any) {
        properties[property] = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Edge

        if (sourceId != other.sourceId) return false
        if (targetId != other.targetId) return false
        if (labels != other.labels) return false
        if (properties != other.properties) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceId.hashCode()
        result = 31 * result + targetId.hashCode()
        result = 31 * result + labels.hashCode()
        result = 31 * result + properties.hashCode()
        return result
    }

    override fun toString(): String {
        return toString(CyJsonCodec)
    }

    fun <GraphType, NodesType, EdgesType, NodeType, EdgeType> toString(encoder: GraphCodec<GraphType, NodesType, EdgesType, NodeType, EdgeType>): String {
        return encoder.encodeEdge(this).toString()
    }
}