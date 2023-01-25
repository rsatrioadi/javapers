package nl.tue.win.lpg

import nl.tue.win.lpg.encoder.CyJsonCodec
import nl.tue.win.lpg.encoder.GraphCodec

class Graph(
    val id: String,
    val nodes: Nodes = Nodes(),
    val edges: Edges = Edges(),
    vararg labels: String = arrayOf()
) {

    val labels: Set<String>
    val properties: HashMap<String, Any> = HashMap()

    val nodeList: List<Node> get() = nodes.asList()
    val edgeList: List<Edge> get() = edges.asList()

    init {
        this.labels = setOf(*labels)
    }

    operator fun get(property: String): Any? {
        return properties[property]
    }

    operator fun set(property: String, value: Any) {
        properties[property] = value
    }

    override fun toString(): String {
        return toString(CyJsonCodec)
    }

    fun <GraphType, NodesType, EdgesType, NodeType, EdgeType> toString(encoder: GraphCodec<GraphType, NodesType, EdgesType, NodeType, EdgeType>): String {
        return encoder.encodeGraph(this).toString()
    }
}