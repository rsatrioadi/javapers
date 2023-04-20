package nl.tue.win.lpg.encoder

import nl.tue.win.lpg.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CyJsonCodec : GraphCodec<JSONObject, JSONArray, JSONArray, JSONObject, JSONObject> {
    override fun encodeGraph(graph: Graph): JSONObject {
        val elements = JSONObject()
        elements.put("nodes", this.encodeNodes(graph.nodes))
        elements.put("edges", this.encodeEdges(graph.edges))
        val obj = JSONObject()
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
        val data = JSONObject().run {
            put("id", node.id)
            put("labels", node.labels)
            put("properties", JSONObject(node.properties))
        }
        val element = JSONObject()
        element.put("data", data)
        return element
    }

    override fun encodeEdge(edge: Edge): JSONObject {
        val data = JSONObject().run {
            put("id", edge.id)
            put("source", edge.sourceId)
            put("target", edge.targetId)
            put("label", edge.label)
            put("properties", JSONObject(edge.properties))
        }
        val element = JSONObject()
        element.put("data", data)
        return element
    }

    override fun writeToFile(graph: Graph, directory: String, baseName: String) {
        val path =
            "${if (directory.endsWith(File.separator)) directory else "$directory${File.separator}"}${baseName}.json"
        File(path).writeText(encodeGraph(graph).toString())
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
            *(data["labels"] as JSONArray).toSet()
                .map { it.toString() }
                .toTypedArray()
        )
        val properties = data["properties"] as JSONObject
        properties.keys().forEach {
            val prop = properties[it]
            node[it] = if (prop is JSONArray) prop.toList() else properties[it]
        }
        return node
    }

    override fun decodeEdge(encodedEdge: JSONObject): Edge {
        val data = encodedEdge["data"] as JSONObject
        val edge = Edge(
            data["source"] as String,
            data["target"] as String,
            data["id"] as String,
            data["label"] as String
        )
        val properties = data["properties"] as JSONObject
        properties.keys().forEach {
            val prop = properties[it]
            edge[it] = if (prop is JSONArray) prop.toList() else properties[it]
        }
        return edge
    }
}
