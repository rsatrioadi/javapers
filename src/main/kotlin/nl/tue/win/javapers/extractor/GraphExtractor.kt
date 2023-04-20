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

val <T> CtType<T>.ancestors: Set<CtTypeReference<*>>
    get() = setOfNotNull(
        this.superclass,
        *this.superInterfaces.toTypedArray()
    )

fun makeEdge(source: Node, target: Node, weight: Int = 1, label: String): Edge {
    val id = md5("${source.id}-$label-${target.id}")
    return Edge(source.id, target.id, id, label)
        .also { it["weight"] = weight }
}

val CtTypedElement<*>.typeOrArrayType: CtTypeReference<*>?
    get() = if (this.type.isArray) (this.type as CtArrayTypeReference).arrayType else this.type