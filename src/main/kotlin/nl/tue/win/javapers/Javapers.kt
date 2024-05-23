package nl.tue.win.javapers

import nl.tue.win.javapers.extractor.CompactedExtractor
import nl.tue.win.javapers.extractor.ExpandedExtractor
import nl.tue.win.lib.Either
import nl.tue.win.lpg.encoder.Codecs
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
            val model = Launcher().run {
                addInputResource(options.inputPath)
                environment.complianceLevel = 17
                buildModel()
            }
            val graph =
                (if (options.compacted) CompactedExtractor(options.baseName, model)
                else ExpandedExtractor(options.baseName, model))
                    .extract()
            val graphCodec = Codecs[options.format]
            val directory = makeDir(options.outputPath)
            graphCodec?.writeToFile(graph, directory, options.baseName)
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
