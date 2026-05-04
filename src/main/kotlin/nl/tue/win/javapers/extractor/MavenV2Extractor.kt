package nl.tue.win.javapers.extractor

import nl.tue.win.codepers.GraphExtractor
import nl.tue.win.javapers.buildSpoonModel
import nl.tue.win.javapers.collectJavaFiles
import nl.tue.win.lpg.Graph
import spoon.Launcher
import spoon.MavenLauncher
import spoon.reflect.CtModel
import java.io.File

class MavenV2Extractor(
    private val projectName: String,
    private val pomPath: String,
    private val excludeGlobs: List<String> = emptyList(),
    private val extraClasspath: List<String> = emptyList()
) : GraphExtractor {

    // Typed as Launcher (base class) because we build the model with a plain
    // Launcher; MavenLauncher is used only for classpath resolution.
    lateinit var launcher: Launcher
        private set
    lateinit var model: CtModel
        private set

    override fun extract(): Graph {
        val projectDir = File(pomPath).let { if (it.isDirectory) it else it.parentFile }.absolutePath

        // Phase 1: use MavenLauncher to resolve the dependency classpath from
        // the pom.xml.  We do NOT call buildModel() on it — we only want the
        // resolved sourceClasspath (all transitive compile-scope JARs).
        val mavenClasspath: Array<String> =
            MavenLauncher(projectDir, MavenLauncher.SOURCE_TYPE.APP_SOURCE)
                .environment.sourceClasspath ?: emptyArray()

        // Phase 2: collect deduplicated .java files (same logic as source-only
        // mode) so that two source roots declaring the same type don't cause
        // Spoon's ModelBuildingException.
        val javaFiles = collectJavaFiles(listOf(projectDir), excludeGlobs)

        // Phase 3: build model with a plain Launcher carrying the Maven classpath.
        // buildSpoonModel retries after each "already defined" error so duplicate
        // types that slipped past collectJavaFiles don't crash the tool.
        val cp = mavenClasspath + extraClasspath.toTypedArray()
        val (l, m) = buildSpoonModel(javaFiles, complianceLevel = 17, extraClasspath = cp)
        launcher = l
        model = m

        return runV2Pipeline(projectName, model, listOf(projectDir), excludeGlobs)
    }
}
