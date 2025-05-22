package nl.tue.win.codepers

import nl.tue.win.lib.md5
import nl.tue.win.lpg.Edge
import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spoon.reflect.declaration.CtType
import spoon.reflect.reference.CtTypeReference

interface GraphExtractor {
    fun extract(): Graph

    companion object {
        val logger: Logger = LoggerFactory.getLogger(GraphExtractor::class.java)
    }
}

val primitiveTypes = setOf("byte", "char", "short", "int", "long", "float", "double", "boolean")

val <T> CtType<T>.ancestors: Set<CtTypeReference<*>>
    get() = setOfNotNull(
        this.superclass,
        *this.superInterfaces.toTypedArray()
    )

fun makeNode(id: String, vararg labels: String, simpleName: String): Node {
    GraphExtractor.logger.atInfo()
        .setMessage("- Creating node.")
//        .addKeyValue("id", id)
        .addKeyValue("simpleName", simpleName)
        .addKeyValue("labels", labels.joinToString { it })
        .log()
    return Node(id, *labels)
        .also {
            it["simpleName"] = simpleName
            it["metaSrc"] = "source code"
        }
}

fun makeEdge(source: Node, target: Node, weight: Int = 1, label: String): Edge {
    GraphExtractor.logger.atInfo()
        .setMessage("- Creating edge.")
        .addKeyValue("source", source.properties["simpleName"])
        .addKeyValue("target", target.properties["simpleName"])
        .addKeyValue("label", label)
        .log()
    val id = md5("${source.id}-$label-${target.id}")
    return Edge(source.id, target.id, id, label)
        .also {
            it["weight"] = weight
            it["metaSrc"] = "source code"
        }
}

