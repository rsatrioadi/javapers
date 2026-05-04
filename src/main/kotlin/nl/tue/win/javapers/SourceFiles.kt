package nl.tue.win.javapers

import nl.tue.win.codepers.GraphExtractor
import spoon.Launcher
import spoon.compiler.ModelBuildingException
import spoon.reflect.CtModel
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Walk [roots] recursively, applying [excludeGlobs] against each path segment,
 * and return the deduplicated list of .java files to feed to Spoon.
 *
 * Proactive deduplication: when two files resolve to the same qualified name
 * (package + filename stem), the first-seen file is kept and the duplicate is
 * skipped with a warning.  This prevents most ModelBuildingException crashes.
 *
 * Any cases that slip through proactive dedup (e.g. a type declared in a file
 * whose name doesn't match the class name) are handled at build time by
 * [buildSpoonModel], which retries after each "already defined" error.
 */
fun collectJavaFiles(roots: List<String>, excludeGlobs: List<String>): List<Path> {
    val fs = FileSystems.getDefault()
    val matchers = excludeGlobs.map { fs.getPathMatcher("glob:$it") }
    fun Path.isExcluded() = matchers.any { it.matches(this.fileName) }

    val seenQNames = mutableMapOf<String, Path>()
    val result = mutableListOf<Path>()

    fun visit(path: Path) {
        if (path.isExcluded()) return
        when {
            Files.isDirectory(path) ->
                Files.list(path).use { stream ->
                    stream.sorted().forEach { visit(it) }
                }
            path.fileName.toString().endsWith(".java") -> {
                val qn = qualifiedName(path)
                val prior = seenQNames[qn]
                if (prior != null) {
                    GraphExtractor.logger.atWarn()
                        .setMessage("Skipping duplicate type declaration (proactive dedup) — keeping first-seen file")
                        .addKeyValue("qualifiedName", qn)
                        .addKeyValue("skipped", path.toString())
                        .addKeyValue("kept", prior.toString())
                        .log()
                } else {
                    seenQNames[qn] = path
                    result.add(path)
                }
            }
        }
    }

    roots.map { Paths.get(it).toAbsolutePath().normalize() }
        .filter { Files.exists(it) }
        .forEach { root ->
            if (Files.isDirectory(root)) visit(root)
            else if (root.fileName.toString().endsWith(".java")) result.add(root)
        }

    return result
}

/**
 * Build a Spoon [Launcher] + [CtModel] from [javaFiles], applying [extraClasspath].
 *
 * If Spoon throws [ModelBuildingException] for a "type already defined" conflict
 * (cases that slipped past [collectJavaFiles]'s proactive dedup — typically a
 * type declared in a file whose name doesn't match the class name), the offending
 * file is removed and the build is retried.  Repeats up to [maxRetries] times.
 *
 * This makes the tool resilient to all forms of cross-module type duplication
 * without requiring perfect file-content parsing upfront.
 */
fun buildSpoonModel(
    javaFiles: List<Path>,
    complianceLevel: Int = 17,
    extraClasspath: Array<String> = emptyArray(),
    maxRetries: Int = 50
): Pair<Launcher, CtModel> {
    val files = javaFiles.toMutableList()
    val pattern = Regex("""The type (\S+) is already defined""")

    repeat(maxRetries) {
        val launcher = Launcher().apply {
            files.forEach { addInputResource(it.toString()) }
            environment.complianceLevel = complianceLevel
            environment.ignoreSyntaxErrors = true
            if (extraClasspath.isNotEmpty()) environment.setSourceClasspath(extraClasspath)
        }
        try {
            return Pair(launcher, launcher.buildModel())
        } catch (e: ModelBuildingException) {
            val msg = e.message ?: throw e
            if ("is already defined" !in msg) throw e

            // Extract the simple class name Spoon reported
            val simpleName = pattern.find(msg)?.groupValues?.get(1) ?: throw e

            // Find ALL files declaring this type:
            //   Fast path — filename matches <simpleName>.java
            //   Slow path — scan file contents (handles type in a non-eponymous file)
            val byName = files.filter { it.fileName.toString() == "$simpleName.java" }
            val declaringFiles = if (byName.size >= 2) {
                byName
            } else {
                files.filter { f ->
                    f.fileName.toString() == "$simpleName.java" ||
                    try {
                        Files.newBufferedReader(f).useLines { lines ->
                            lines.any { line ->
                                val t = line.trim()
                                "class $simpleName" in t || "interface $simpleName" in t ||
                                "enum $simpleName" in t || "@interface $simpleName" in t
                            }
                        }
                    } catch (_: Exception) { false }
                }
            }
            if (declaringFiles.isEmpty()) throw e  // can't identify the culprit — rethrow

            // Keep the first-seen file, drop ALL the rest in one shot so a type
            // with N copies only needs 1 retry instead of N-1.
            val toKeep      = declaringFiles.first()
            val toRemoveAll = declaringFiles.drop(1)

            toRemoveAll.forEach { files.remove(it) }
            GraphExtractor.logger.atWarn()
                .setMessage("Retrying Spoon build after removing ${toRemoveAll.size} duplicate declaration(s)")
                .addKeyValue("type", simpleName)
                .addKeyValue("kept", toKeep.toString())
                .addKeyValue("removed", toRemoveAll.map { it.toString() }.toString())
                .log()
        }
    }
    throw IllegalStateException(
        "Could not build Spoon model after $maxRetries retries to resolve duplicate type declarations."
    )
}

/**
 * Derive the qualified name of a .java file by reading its package declaration.
 *
 * Uses proper stateful block-comment tracking (no arbitrary line limit) so that
 * files with non-standard generated headers (lines inside /* */ that don't start
 * with '*') are handled correctly.  Stops early at the first import or type
 * declaration, which proves the file has no package statement (default package).
 *
 * Falls back to the bare filename stem on any I/O or parse failure.
 */
private fun qualifiedName(file: Path): String {
    val stem = file.fileName.toString().removeSuffix(".java")
    return try {
        var inBlockComment = false
        var pkg: String? = null

        outer@ for (rawLine in Files.newBufferedReader(file).lineSequence()) {
            val line = rawLine.trim()
            when {
                inBlockComment -> {
                    if ("*/" in line) inBlockComment = false
                }
                line.startsWith("/*") -> {
                    // single-line /* ... */ or opening of a multi-line block
                    if ("*/" !in line) inBlockComment = true
                }
                line.startsWith("//") || line.isEmpty() -> { /* skip */ }
                line.startsWith("package ") -> {
                    pkg = line.removePrefix("package ").trimEnd(';').trim()
                    break@outer
                }
                // Anything else that's a real statement (import, annotation, declaration)
                // appearing before a package keyword means this is the default package.
                line.startsWith("import ") ||
                line.startsWith("@") ||
                "class " in line || "interface " in line ||
                "enum " in line || "@interface " in line -> break@outer
            }
        }

        if (pkg.isNullOrEmpty()) stem else "$pkg.$stem"
    } catch (_: Exception) {
        stem
    }
}
