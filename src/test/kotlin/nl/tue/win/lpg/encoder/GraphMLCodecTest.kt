package nl.tue.win.lpg.encoder

import nl.tue.win.lpg.*
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

internal class GraphMLCodecTest {

    @Test
    fun encodeGraph() {

        val doc = GraphMLCodec.encodeGraph(Graph("G",
            Nodes.of(
                Node("keanu", "ACTOR").apply {
                    set("name", "Keanu Reeves")
                },
                Node("matrix", "MOVIE").apply {
                    set("genre", "Action")
                }
            ),
            Edges.of(
                Edge("keanu", "matrix", "keanu-matrix", "ACTS_IN").apply {
                    set("role", "Neo")
                }
            )
        ))

        val transformerFactory = TransformerFactory.newInstance()
        val transformer: Transformer = transformerFactory.newTransformer()
        val source = DOMSource(doc)
        val result = StreamResult(System.out)

        transformer.transform(source, result)

    }
}