package nl.tue.win.javapers

import nl.tue.win.javapers.extractor.CompactedExtractor
import nl.tue.win.javapers.extractor.ExpandedExtractor
import nl.tue.win.lib.Either
import nl.tue.win.lpg.encoder.CsvCodec
import nl.tue.win.lpg.encoder.CyJsonCodec
import org.kohsuke.args4j.CmdLineParser
import spoon.Launcher
import kotlin.system.exitProcess

object Javapers {

    @JvmStatic
    fun main(args: Array<String>) {
        val parseResult = Options.tryParse(args)
        if (parseResult is Either.Right) {
            printInstructions(parseResult.right.first)
            exitProcess(-1)
        }
        val options = (parseResult as Either.Left).left
        val model = Launcher().run {
            addInputResource(options.inputPath)
            buildModel()
        }
        val graph =
            run { if (options.compacted) CompactedExtractor(options.projectName) else ExpandedExtractor(options.projectName) }.extract(
                model
            )
        val graphCodec = when (options.format) {
            "json" -> CyJsonCodec
            "csv" -> CsvCodec
            else -> {
                // let's default to CyJson
                CyJsonCodec
            }
        }
        val decoded = graphCodec.encodeGraph(graph)
    }

    private fun printInstructions(parser: CmdLineParser) {
        print("javapers")
        parser.printSingleLineUsage(System.out)
        println()
        parser.printUsage(System.out)
    }
}
