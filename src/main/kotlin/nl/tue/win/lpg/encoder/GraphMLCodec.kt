package nl.tue.win.lpg.encoder

import nl.tue.win.lpg.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


object GraphMLCodec : GraphCodec<Document, NodeList, NodeList, Element, Element> {
    override fun encodeGraph(graph: Graph): Document {
        val df = DocumentBuilderFactory.newInstance()
        val db = df.newDocumentBuilder()
        val doc: Document = db.newDocument()
        val root = doc.createElement("graphml")
        doc.appendChild(root)
        root.apply {
            setAttribute("xmlns", "http://graphml.graphdrawing.org/xmlns")
            setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
            setAttribute(
                "xsi:schemaLocation",
                "http://graphml.graphdrawing.org/xmlns http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd"
            )

            appendChild(doc.createElement("graph").apply {
                setAttribute("id", graph.id)
                setAttribute("edgedefault", "directed")

                encodeNodes(graph.nodes, doc, this)
                encodeEdges(graph.edges, doc, this)
            })

            listOf("node", "edge").forEach { e ->
                getElementsByTagName(e).asList()
                    .flatMap { node ->
                        node.childNodes.asList()
                            .filter { it.nodeName == "data" && it.attributes.getNamedItem("key").nodeValue != "labels" }
                    }
                    .map { it.attributes.getNamedItem("key").nodeValue }
                    .distinct()
                    .forEach { attrName ->
                        appendChild(doc.createElement("key").apply {
                            setAttribute("id", attrName)
                            setAttribute("for", e)
                            setAttribute("attr.name", attrName)
                        })
                    }
            }
        }
        return doc
    }

    private fun encodeNodes(nodes: Nodes, doc: Document, e: Element): NodeList {
        nodes.map { encodeNode(it, doc) }.forEach {
            e.appendChild(it)
        }
        return doc.getElementsByTagName("node")
    }

    override fun encodeNodes(nodes: Nodes): NodeList {
        val doc = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .newDocument()
        return encodeNodes(nodes, doc, doc.documentElement)
    }

    private fun encodeEdges(edges: Edges, doc: Document, e: Element): NodeList {
        edges.map { encodeEdge(it, doc) }.forEach { e.appendChild(it) }
        return doc.getElementsByTagName("edge")
    }

    override fun encodeEdges(edges: Edges): NodeList {
        val doc = DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .newDocument()
        return encodeEdges(edges, doc, doc.documentElement)
    }

    private fun encodeNode(node: Node, doc: Document): Element {
        return doc.createElement("node").apply {
            setAttribute("id", node.id)
            val labels = node.labels.joinToString(",") { ":$it" }
            setAttribute("labels", labels)

            appendChild(doc.createElement("data").apply {
                setAttribute("key", "labels")
                textContent = labels
            })

            node.properties.forEach { (key, value) ->
                appendChild(doc.createElement("data").apply {
                    setAttribute("key", key)
                    textContent =
                        if (value is Collection<*>) value.joinToString(",", "[", "]")
                        else value.toString()
                })
            }
        }
    }

    override fun encodeNode(node: Node): Element {
        return encodeNode(
            node, DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .newDocument()
        )
    }

    private fun encodeEdge(edge: Edge, doc: Document): Element {
        return doc.createElement("edge").apply {
            setAttribute("id", edge.id)
            val labels = edge.labels.joinToString(",")
            setAttribute("labels", labels)
            setAttribute("source", edge.sourceId)
            setAttribute("target", edge.targetId)

            appendChild(doc.createElement("data").apply {
                setAttribute("key", "labels")
                textContent = labels
            })

            edge.properties.forEach { (key, value) ->
                appendChild(doc.createElement("data").apply {
                    setAttribute("key", key)
                    textContent =
                        if (value is Collection<*>) value.joinToString(",", "[", "]")
                        else value.toString()
                })
            }
        }
    }

    override fun encodeEdge(edge: Edge): Element {
        return encodeEdge(
            edge, DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .newDocument()
        )
    }

    override fun writeToFile(graph: Graph, directory: String, baseName: String) {
        val path =
            "${if (directory.endsWith(File.separator)) directory else "$directory${File.separator}"}${baseName}.xml"

        try {
            FileOutputStream(path).use { out ->
                val transformerFactory = TransformerFactory.newInstance()
                val transformer: Transformer = transformerFactory.newTransformer()
                val source = DOMSource(encodeGraph(graph))
                val result = StreamResult(out)
                transformer.transform(source, result)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun decodeEdge(encodedEdge: Element): Edge {
        TODO("Not yet implemented")
    }

    override fun decodeNode(encodedNode: Element): Node {
        TODO("Not yet implemented")
    }

    override fun decodeEdges(encodedEdges: NodeList): Edges {
        TODO("Not yet implemented")
    }

    override fun decodeNodes(encodedNodes: NodeList): Nodes {
        TODO("Not yet implemented")
    }

    override fun decodeGraph(encodedGraph: Document): Graph {
        TODO("Not yet implemented")
    }
}

private fun NodeList.asList(): List<org.w3c.dom.Node> {
    return List<org.w3c.dom.Node>(this.length) {
        this.item(it)
    }
}
