package nl.tue.win.javapers.extractor

import nl.tue.win.lpg.Edge
import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import spoon.reflect.CtModel
import spoon.reflect.declaration.CtType
import spoon.reflect.reference.CtTypeReference

interface GraphExtractor {
    fun extract(model: CtModel): Graph
}

val <T> CtType<T>.ancestors: List<CtTypeReference<*>>
    get() = listOfNotNull(
        this.superclass,
        *this.superInterfaces.toTypedArray()
    )

fun makeEdge(source: Node, target: Node, weight: Int = 1, vararg labels: String): Edge {
    val label = labels.joinToString(",")
    val id = "${source.id}-$label-${target.id}"
    return Edge(source, target, id, *labels)
        .also { it["weight"] = weight }
}