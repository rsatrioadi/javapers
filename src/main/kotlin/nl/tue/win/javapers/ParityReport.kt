package nl.tue.win.javapers

import nl.tue.win.lpg.Graph

object ParityReport {

    fun compare(graphA: Graph, graphB: Graph, nameA: String = "A", nameB: String = "B"): String {
        val sb = StringBuilder()

        val allNodeLabels = (graphA.nodes.flatMap { it.labels } + graphB.nodes.flatMap { it.labels }).toSortedSet()
        sb.appendLine("=== Node counts per label ===")
        sb.appendLine("%-30s %10s %10s".format("Label", nameA, nameB))
        allNodeLabels.forEach { label ->
            sb.appendLine(
                "%-30s %10d %10d".format(
                    label,
                    graphA.nodes.findNodesWithLabel(label).size,
                    graphB.nodes.findNodesWithLabel(label).size
                )
            )
        }

        val allEdgeLabels = (graphA.edgeList.map { it.label } + graphB.edgeList.map { it.label }).toSortedSet()
        sb.appendLine("\n=== Edge counts per label ===")
        sb.appendLine("%-30s %10s %10s".format("Label", nameA, nameB))
        allEdgeLabels.forEach { label ->
            sb.appendLine(
                "%-30s %10d %10d".format(
                    label,
                    graphA.edgeList.count { it.label == label },
                    graphB.edgeList.count { it.label == label }
                )
            )
        }

        val keysA = graphA.edgeList.map { "${it.sourceId}--[${it.label}]-->${it.targetId}" }.toSet()
        val keysB = graphB.edgeList.map { "${it.sourceId}--[${it.label}]-->${it.targetId}" }.toSet()
        val overlap = keysA intersect keysB
        val union = keysA union keysB
        val pct = if (union.isEmpty()) 100.0 else overlap.size * 100.0 / union.size

        sb.appendLine("\n=== Edge overlap (source + label + target) ===")
        sb.appendLine("Overlap: ${overlap.size}/${union.size} (${"%.1f".format(pct)}%)")
        sb.appendLine("Only in $nameA: ${(keysA - keysB).size}")
        sb.appendLine("Only in $nameB: ${(keysB - keysA).size}")

        val onlyA = keysA - keysB
        val onlyB = keysB - keysA
        if (onlyA.isNotEmpty()) {
            sb.appendLine("\n-- Sample edges only in $nameA (up to 20) --")
            onlyA.take(20).forEach { sb.appendLine("  $it") }
        }
        if (onlyB.isNotEmpty()) {
            sb.appendLine("\n-- Sample edges only in $nameB (up to 20) --")
            onlyB.take(20).forEach { sb.appendLine("  $it") }
        }

        return sb.toString()
    }
}
