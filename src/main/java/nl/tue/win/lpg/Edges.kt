package nl.tue.win.lpg

import nl.tue.win.lpg.encoder.CyJsonCodec
import nl.tue.win.lpg.encoder.GraphCodec

class Edges : HashSet<Edge>() {

    companion object {
        fun of(vararg edges: Edge): Edges {
            val result = Edges()
            edges.forEach { result.add(it) }
            return result
        }
    }

    fun asList(): List<Edge> {
        return listOf(*this.toTypedArray())
    }

    override fun toString(): String {
        return toString(CyJsonCodec)
    }

    fun <GraphType, NodesType, EdgesType, NodeType, EdgeType> toString(encoder: GraphCodec<GraphType, NodesType, EdgesType, NodeType, EdgeType>): String {
        return encoder.encodeEdges(this).toString()
    }
}