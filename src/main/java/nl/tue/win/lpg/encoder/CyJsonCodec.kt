package nl.tue.win.lpg.encoder

import nl.tue.win.lpg.*
import org.json.JSONArray
import org.json.JSONObject

object CyJsonCodec : GraphCodec<JSONObject, JSONArray, JSONArray, JSONObject, JSONObject> {
    override fun encodeGraph(graph: Graph): JSONObject {
        val obj = JSONObject()
        val elements = JSONObject()
        elements.put("nodes", this.encodeNodes(graph.nodes))
        elements.put("edges", this.encodeEdges(graph.edges))
        obj.put("elements", elements)
        return obj
    }

    override fun encodeNodes(nodes: Nodes): JSONArray {
        val nodeArray = JSONArray()
        nodes.asList().forEach { node -> nodeArray.put(this.encodeNode(node) as Any) }
        return nodeArray
    }

    override fun encodeEdges(edges: Edges): JSONArray {
        val nodeArray = JSONArray()
        edges.asList().forEach { edge -> nodeArray.put(this.encodeEdge(edge) as Any) }
        return nodeArray
    }

    override fun encodeNode(node: Node): JSONObject {
        val element = JSONObject()
        val data = JSONObject()
        data.put("id", node.id)
        data.put("labels", node.labels)
        data.put("properties", JSONObject(node.properties))
        element.put("data", data)
        return element
    }

    override fun encodeEdge(edge: Edge): JSONObject {
        val element = JSONObject()
        val data = JSONObject()
        data.put("id", edge.id)
        data.put("source", edge.source.id)
        data.put("target", edge.target.id)
        data.put("labels", edge.labels)
        data.put("properties", JSONObject(edge.properties))
        element.put("data", data)
        return element
    }

    override fun decodeGraph(encodedGraph: JSONObject): Graph {
        TODO("Not yet implemented")
    }

    override fun decodeNodes(encodedNodes: JSONArray): Nodes {
        TODO("Not yet implemented")
    }

    override fun decodeEdges(encodedEdges: JSONArray): Edges {
        TODO("Not yet implemented")
    }

    override fun decodeNode(encodedNode: JSONObject): Node {
        val data = encodedNode["data"] as JSONObject
        val node = Node(
            data["id"] as String,
            *(data["labels"] as JSONArray).toSet().map { it.toString() }.toTypedArray()
        )
        val properties = data["properties"] as JSONObject
        properties.keys().forEach {
            val prop = properties[it]
            if (prop is JSONArray) {
                node[it] = prop.toList()
            } else {
                node[it] = properties[it]
            }
        }
        return node
    }

    override fun decodeEdge(encodedEdge: JSONObject): Edge {
        TODO("Not yet implemented")
    }
}
