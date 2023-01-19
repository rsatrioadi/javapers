package nl.tue.win.lpg.encoder

import nl.tue.win.lpg.*

interface GraphCodec<GraphType, NodesType, EdgesType, NodeType, EdgeType> {
    fun encodeGraph(graph: Graph): GraphType
    fun encodeNodes(nodes: Nodes): NodesType
    fun encodeEdges(edges: Edges): EdgesType
    fun encodeNode(node: Node): NodeType
    fun encodeEdge(edge: Edge): EdgeType

    fun decodeGraph(encodedGraph: GraphType): Graph
    fun decodeNodes(encodedNodes: NodesType): Nodes
    fun decodeEdges(encodedEdges: EdgesType): Edges
    fun decodeNode(encodedNode: NodeType): Node
    fun decodeEdge(encodedEdge: EdgeType): Edge
}

