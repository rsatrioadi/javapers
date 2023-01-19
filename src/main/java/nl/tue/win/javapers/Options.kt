package nl.tue.win.javapers

import nl.tue.win.lib.Either
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option

class Options {
    @Option(name = "-i", aliases = ["--input"], usage = "Input path", metaVar = "INFILE")
    var inputPath = "."

    @Option(name = "-o", aliases = ["--output"], usage = "Output directory", metaVar = "OUTFILE")
    var outputPath = "."

    @Option(name = "-f", aliases = ["--format"], usage = "Output format: json, xml, or csv", metaVar = "FORMAT")
    var format = "json"

    @Option(name = "-p", aliases = ["--project-name"], usage = "Project name", metaVar = "PROJECT_NAME")
    var projectName = "JavaProject"

    @Option(name = "-c", aliases = ["--compacted"], usage = "Generate compacted graph")
    var compacted = false

    companion object {
        fun tryParse(args: Array<String>): Either<Options, Pair<CmdLineParser, String>> =
            try {
                val options = Options()
                val parser = CmdLineParser(options)
                parser.parseArgument(*args)
                Either.Left(options)
            } catch (e: CmdLineException) {
                Either.Right(Pair(e.parser, e.message ?: "Unspecified cause."))
            }
    }
}