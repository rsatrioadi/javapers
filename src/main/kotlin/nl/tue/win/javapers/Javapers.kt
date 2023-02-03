package nl.tue.win.javapers

import nl.tue.win.javapers.extractor.CompactedExtractor
import nl.tue.win.javapers.extractor.ExpandedExtractor
import nl.tue.win.lib.Either
import nl.tue.win.lpg.encoder.Codecs
import nl.tue.win.lpg.encoder.CsvCodec
import org.kohsuke.args4j.CmdLineParser
import spoon.Launcher
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

open class Javapers {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val parseResult = Options.tryParse(args)
            if (parseResult is Either.Right) {
                println(parseResult.right.second)
                printInstructions(parseResult.right.first)
                exitProcess(-1)
            }
            val options = (parseResult as Either.Left).left
            val model = Launcher().run {
                addInputResource(options.inputPath)
                buildModel()
            }
            val graph =
                (if (options.compacted) CompactedExtractor(options.baseName)
                else ExpandedExtractor(options.baseName))
                    .extract(model)
            val graphCodec = Codecs[options.format] ?: CsvCodec
            println(graphCodec.javaClass)
            val directory = makeDir(options.outputPath)
            graphCodec.writeToFile(graph, directory, options.baseName)
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
