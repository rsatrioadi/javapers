package nl.tue.win.javapers

import nl.tue.win.codepers.makeEdge
import nl.tue.win.codepers.makeNode
import nl.tue.win.javapers.extractor.*
import nl.tue.win.jhalstead.HalsteadMetrics
import nl.tue.win.jhalstead.HalsteadMetricsCalculator
import nl.tue.win.lib.Either
import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.encoder.Codecs
import org.kohsuke.args4j.CmdLineParser
import spoon.Launcher
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

class Javapers {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parseResult = Options.tryParse(args)
            if (args.isEmpty()) {
                printInstructions(CmdLineParser(Options()))
                exitProcess(-1)
            }
            if (parseResult is Either.Right) {
                println(parseResult.right.second)
                printInstructions(parseResult.right.first)
                exitProcess(-1)
            }
            val options = (parseResult as Either.Left).left
            val paths = options.inputPath.split(options.separator)
            val launcher = Launcher().apply {
                paths.forEach { addInputResource(it) }
                environment.complianceLevel = 17
                environment.ignoreSyntaxErrors = true
            }
            val model = launcher.buildModel()
            val graph: Graph

            if (options.useOldSchema) {
                graph = V1Extractor(options.baseName, model).extract()
                graph["schemaVersion"] = "1.2.0"
            } else {
                graph = V2Extractor(options.baseName, model, paths).extract()
                graph["schemaVersion"] = "2.0"

                val calculator = HalsteadMetricsCalculator()
                val halsteadMetrics: List<HalsteadMetrics> = calculator.analyzeProject(launcher, model)

                val halsteadNode = makeNode(
                    id = "Metrics#HalsteadMetrics",
                    labels = arrayOf("Metric"),
                    simpleName = "HalsteadMetrics"
                )
                halsteadNode["qualifiedName"] = "Halstead Complexity Metrics"
                halsteadNode["kind"] = "metric"
                graph.nodes.add(halsteadNode)

                for (metric in halsteadMetrics) {
                    val sourceId = metric.elementID
                    val sourceNode = graph.nodes.find { it["qualifiedName"] == sourceId }
                        ?: continue // skip if not found

                    val edge = makeEdge(sourceNode, halsteadNode, label = "measures")
                    metric.toMap().forEach { (k, v) ->
                        if (k != "id" && k != "kind") edge[k] = v
                    }
                    graph.edges.add(edge)
                }
            }

            val graphCodec = Codecs[options.format]
            val directory = makeDir(options.outputPath)
            graphCodec?.writeToFile(graph, directory, options.baseName)
            if (options.stdout) {
                val printStreamUtf8 = PrintStream(System.out, true, StandardCharsets.UTF_8)
                printStreamUtf8.println(graphCodec?.encodeGraph(graph).toString())
            }
        }
    }
}

private fun makeDir(path: String): String {
    val p = Paths.get(path)
    if (!Files.isDirectory(p)) {
        Files.createDirectories(p)
    }
    return p.toAbsolutePath().normalize().toString()
}

private fun printInstructions(parser: CmdLineParser) {
    print("javapers")
    parser.printSingleLineUsage(System.out)
    println()
    parser.printUsage(System.out)
}
