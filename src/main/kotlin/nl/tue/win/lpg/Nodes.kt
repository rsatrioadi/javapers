package nl.tue.win.lpg

import nl.tue.win.lpg.encoder.CyJsonCodec
import nl.tue.win.lpg.encoder.GraphCodec

class Nodes : HashSet<Node>() {

    companion object {
        fun of(vararg nodes: Node): Nodes {
            val result = Nodes()
            nodes.forEach { result.add(it) }
            return result
        }
    }

    fun asList(): List<Node> {
        return listOf(*this.toTypedArray())
    }

    fun findById(id: String): Node? {
        return this.firstOrNull { it.id == id }
    }

    fun findNodesWithLabel(label: String): List<Node> {
        return this.filter { it.labels.contains(label) }
    }

    override fun toString(): String {
        return toString(CyJsonCodec)
    }

    fun <GraphType, NodesType, EdgesType, NodeType, EdgeType> toString(encoder: GraphCodec<GraphType, NodesType, EdgesType, NodeType, EdgeType>): String {
        return encoder.encodeNodes(this).toString()
    }
}