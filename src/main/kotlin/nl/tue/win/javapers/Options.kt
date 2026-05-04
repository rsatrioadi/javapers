package nl.tue.win.javapers

import nl.tue.win.lib.Either
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import org.kohsuke.args4j.spi.StringArrayOptionHandler

class Options {
    @Option(name = "-i", aliases = ["--input"], usage = "Root directory (or file) to analyse. Use --exclude to skip subdirs like target/ or build/.", metaVar = "DIR")
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

    @Option(name = "-s", aliases = ["--separator"], usage = "Separator for splitting multiple input paths (legacy; prefer a single root with --exclude)", metaVar = "SEPARATOR")
    var separator = "+"

    @Option(
        name = "-x", aliases = ["--exclude"],
        usage = "Glob pattern to exclude (repeatable), appended to built-in defaults; e.g. --exclude generated",
        metaVar = "GLOB",
        handler = StringArrayOptionHandler::class
    )
    var excludeGlobs: List<String> = emptyList()

    @Option(
        name = "--cp", aliases = ["--classpath"],
        usage = "Colon-separated extra JAR paths added to the source classpath for symbol resolution",
        metaVar = "JARS"
    )
    var classpath: String = ""

    @Option(
        name = "--pom",
        usage = "Path to pom.xml (or its directory); triggers Maven build-tool mode. Mutually exclusive with --input.",
        metaVar = "POM"
    )
    var pomPath: String = ""

    companion object {
        fun tryParse(args: Array<String>): Either<Options, Pair<CmdLineParser, String>> =
            try {
                val options = Options()
                val parser = CmdLineParser(options)
                parser.parseArgument(*args)
                if (options.pomPath.isNotEmpty() && options.inputPath != ".") {
                    Either.Right(Pair(parser, "--pom and --input are mutually exclusive"))
                } else {
                    Either.Left(options)
                }
            } catch (e: CmdLineException) {
                Either.Right(Pair(e.parser, e.message ?: "Unspecified cause."))
            }
    }
}
