package nl.tue.win.javapers.extractor

import nl.tue.win.lib.md5
import nl.tue.win.lpg.Edge
import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import spoon.reflect.declaration.CtType
import spoon.reflect.declaration.CtTypedElement
import spoon.reflect.reference.CtArrayTypeReference
import spoon.reflect.reference.CtTypeReference

interface GraphExtractor {
    fun extract(): Graph
}

val primitiveTypes = setOf("byte", "char", "short", "int", "long", "float", "double", "boolean")

val <T> CtType<T>.ancestors: Set<CtTypeReference<*>>
    get() = setOfNotNull(
        this.superclass,
        *this.superInterfaces.toTypedArray()
    )

fun makeNode(id: String, vararg labels: String, simpleName: String): Node {
    return Node(id, *labels)
        .also {
            it["simpleName"] = simpleName
            it["metaSrc"] = "source code"
        }
}

fun makeEdge(source: Node, target: Node, weight: Int = 1, label: String): Edge {
    val id = md5("${source.id}-$label-${target.id}")
    return Edge(source.id, target.id, id, label)
        .also {
            it["weight"] = weight
            it["metaSrc"] = "source code"
        }
}

val CtTypedElement<*>.typeOrArrayType: CtTypeReference<*>?
    get() = if (this.type.isArray) (this.type as CtArrayTypeReference).arrayType else this.type