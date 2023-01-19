package nl.tue.win.lpg.encoder

import nl.tue.win.lpg.*

object CsvCodec : GraphCodec<Pair<String, String>, String, String, String, String> {
    override fun encodeGraph(graph: Graph): Pair<String, String> {
        TODO("Not yet implemented")
    }

    override fun encodeNodes(nodes: Nodes): String {
        TODO("Not yet implemented")
    }

    override fun encodeEdges(edges: Edges): String {
        TODO("Not yet implemented")
    }

    override fun encodeNode(node: Node): String {
        TODO("Not yet implemented")
    }

    override fun encodeEdge(edge: Edge): String {
        TODO("Not yet implemented")
    }

    override fun decodeEdge(encodedEdge: String): Edge {
        TODO("Not yet implemented")
    }

    override fun decodeNode(encodedNode: String): Node {
        TODO("Not yet implemented")
    }

    override fun decodeEdges(encodedEdges: String): Edges {
        TODO("Not yet implemented")
    }

    override fun decodeNodes(encodedNodes: String): Nodes {
        TODO("Not yet implemented")
    }

    override fun decodeGraph(encodedGraph: Pair<String, String>): Graph {
        TODO("Not yet implemented")
    }
}