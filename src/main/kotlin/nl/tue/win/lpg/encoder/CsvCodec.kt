package nl.tue.win.lpg.encoder

import nl.tue.win.lib.CsvRow
import nl.tue.win.lib.CsvRows
import nl.tue.win.lpg.*
import java.io.File

object CsvCodec : GraphCodec<CsvRowsList, CsvRows, CsvRows, CsvRow, CsvRow> {
    override fun encodeGraph(graph: Graph): CsvRowsList {
        return CsvRowsList(encodeNodes(graph.nodes), encodeEdges(graph.edges))
    }

    override fun encodeNodes(nodes: Nodes): CsvRows {
        val nodeRows = CsvRows().also {
            nodes.asList().forEach { node -> it.add(encodeNode(node)) }
            it.setPreferredColumnOrder("id", "labels")
        }
        return nodeRows
    }

    override fun encodeEdges(edges: Edges): CsvRows {
        val edgeRows = CsvRows().also {
            edges.asList().forEach { edge -> it.add(encodeEdge(edge)) }
            it.setPreferredColumnOrder("id", "source", "target", "labels")
        }
        return edgeRows
    }

    override fun encodeNode(node: Node): CsvRow {
        val row = CsvRow().also {
            it["id"] = node.id
            it["labels"] = node.labels.joinToString(",")
            node.properties.keys.forEach { key -> it[key] = node.properties[key] }
            it.setPreferredColumnOrder("id", "labels")
        }
        return row
    }

    override fun encodeEdge(edge: Edge): CsvRow {
        val row = CsvRow().also {
            it["id"] = edge.id
            it["source"] = edge.sourceId
            it["target"] = edge.targetId
            it["label"] = edge.label
            edge.properties.keys.forEach { key -> it[key] = edge.properties[key] }
            it.setPreferredColumnOrder("id", "source", "target", "labels")
        }
        return row
    }

    override fun writeToFile(graph: Graph, directory: String, baseName: String) {
        val nodesPath =
            "${if (directory.endsWith(File.separator)) directory else "$directory${File.separator}"}${baseName}-nodes.csv"
        File(nodesPath).writeText(encodeNodes(graph.nodes).toString())
        val edgesPath =
            "${if (directory.endsWith(File.separator)) directory else "$directory${File.separator}"}${baseName}-edges.csv"
        File(edgesPath).writeText(encodeEdges(graph.edges).toString())
    }

    override fun decodeEdge(encodedEdge: CsvRow): Edge {
        TODO("Not yet implemented")
    }

    override fun decodeNode(encodedNode: CsvRow): Node {
        TODO("Not yet implemented")
    }

    override fun decodeEdges(encodedEdges: CsvRows): Edges {
        TODO("Not yet implemented")
    }

    override fun decodeNodes(encodedNodes: CsvRows): Nodes {
        TODO("Not yet implemented")
    }

    override fun decodeGraph(encodedGraph: CsvRowsList): Graph {
        TODO("Not yet implemented")
    }
}

class CsvRowsList(vararg rowsList: CsvRows) : ArrayList<CsvRows>() {
    init {
        this.addAll(rowsList.toList())
    }

    override fun toString(): String {
        return this.joinToString("${System.lineSeparator()}${System.lineSeparator()}")
    }
}
