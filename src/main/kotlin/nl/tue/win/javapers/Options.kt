package nl.tue.win.javapers

import nl.tue.win.lib.Either
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option

class Options {
    @Option(name = "-i", aliases = ["--input"], usage = "Input path(s); set separator with -s (default: plus '+')", metaVar = "INFILE")
    var inputPath = "."

    @Option(name = "-o", aliases = ["--output"], usage = "Output directory", metaVar = "OUTFILE")
    var outputPath = "."

    @Option(name = "-f", aliases = ["--format"], usage = "Output format: json or csv", metaVar = "FORMAT")
    var format = "csv"

    @Option(name = "-n", aliases = ["--name"], usage = "Base file name", metaVar = "BASE_NAME")
    var baseName = "JavaProject"

    @Option(name = "-1", aliases = ["--version-1"], usage = "Use old graph schema (Containers and Structures instead of Scopes and Types")
    var useOldSchema = false

    @Option(name = "-a", aliases = ["--stdout"], usage = "Write output to stdout instead of file(s)")
    var stdout = false

    @Option(name = "-s", aliases = ["--separator"], usage = "Separator for splitting input paths", metaVar = "SEPARATOR")
    var separator = "+"

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
