package nl.tue.win.javapers.extractor

import nl.tue.win.codepers.GraphExtractor
import nl.tue.win.codepers.makeEdge
import nl.tue.win.codepers.makeNode
import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import spoon.reflect.CtModel
import spoon.reflect.code.*
import spoon.reflect.declaration.*
import spoon.reflect.reference.CtExecutableReference
import spoon.reflect.reference.CtTypeReference
import spoon.reflect.reference.CtVariableReference
import spoon.support.reflect.declaration.CtTypeParameterImpl
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class V2Extractor(
	private val projectName: String,
	private val model: CtModel,
	private val inputPaths: List<String>,
	private val excludeGlobs: List<String> = emptyList()
) : GraphExtractor {

	override fun extract(): Graph = runV2Pipeline(projectName, model, inputPaths, excludeGlobs)
}

fun runV2Pipeline(
	projectName: String,
	model: CtModel,
	inputPaths: List<String>,
	excludeGlobs: List<String> = emptyList()
): Graph {
	val g = Graph(projectName)

	// compile exclude matchers once; match against the filename segment only
	val fs = FileSystems.getDefault()
	val matchers = excludeGlobs.map { fs.getPathMatcher("glob:$it") }
	fun Path.isExcluded() = matchers.any { it.matches(this.fileName) }

	// Resolve symlinks so paths from the filesystem walk and from Spoon's
	// ct.position.file both point to the same canonical location.
	// Falls back to toAbsolutePath().normalize() if resolution fails.
	fun Path.canonical(): Path = try { toRealPath() } catch (_: Exception) { toAbsolutePath().normalize() }

	// ─────────────────────────────────────────────────────────────────────
	// 1) PROJECT node
	// ─────────────────────────────────────────────────────────────────────
	val projectNode = makeNode(
		id = projectName,
		labels = arrayOf("Project"),
		simpleName = projectName
	)
	projectNode["qualifiedName"] = projectNode.id
	projectNode["kind"] = "project"
	g.nodes.add(projectNode)

	// ─────────────────────────────────────────────────────────────────────
	// 2) FILESYSTEM: Folder ↔ File (only input roots and their descendants)
	// ─────────────────────────────────────────────────────────────────────
	val folderNodes = mutableMapOf<Path, Node>()
	val fileNodes = mutableMapOf<Path, Node>()

	fun processDir(path: Path) {
		if (folderNodes.containsKey(path)) return
		val dirNode = makeNode(
			id = path.toString(),
			labels = arrayOf("Folder"),
			simpleName = path.fileName?.toString() ?: path.toString()
		)
		dirNode["qualifiedName"] = dirNode.id
		dirNode["kind"] = "folder"
		g.nodes.add(dirNode)
		folderNodes[path] = dirNode

		Files.list(path).use { stream ->
			stream.toList().forEach { child ->
				val c = child.canonical()
				if (c.isExcluded()) return@forEach
				if (Files.isDirectory(c)) {
					processDir(c)
					val childNode = folderNodes[c]
					if (childNode != null) g.edges.add(makeEdge(dirNode, childNode, label = "contains"))
				} else {
					val fileNode = makeNode(
						id = c.toString(),
						labels = arrayOf("File"),
						simpleName = c.fileName?.toString() ?: c.toString()
					)
					fileNode["qualifiedName"] = fileNode.id
					fileNode["kind"] = "file"
					g.nodes.add(fileNode)
					fileNodes[c] = fileNode
					g.edges.add(makeEdge(dirNode, fileNode, label = "contains"))
				}
			}
		}
	}

	// 1) Seed only the given inputPaths:
	inputPaths
		.map { Paths.get(it).canonical() }
		.filter { Files.exists(it) }
		.forEach { root ->
			if (Files.isDirectory(root)) {
				processDir(root)
			} else {
				// only that single file
				val single = makeNode(
					id = root.toString(),
					labels = arrayOf("File"),
					simpleName = root.fileName?.toString() ?: root.toString()
				)
				single["qualifiedName"] = single.id
				single["kind"] = "file"
				g.nodes.add(single)
				fileNodes[root] = single
			}
		}

	// 2) Project includes → exactly those roots (no children, no parents)
	inputPaths
		.map { Paths.get(it).canonical() }
		.forEach { root ->
			val node = if (Files.isDirectory(root)) folderNodes[root] else fileNodes[root]
			node?.let { g.edges.add(makeEdge(projectNode, it, label = "includes")) }
		}

	// ─────────────────────────────────────────────────────────────────────
	// 3) SCOPES (packages)
	// ─────────────────────────────────────────────────────────────────────
	val scopeNodes = mutableMapOf<CtPackage, Node>()
	model.allPackages
		.filter { !it.isUnnamedPackage }
		.forEach { pkg ->
			val id = pkg.qualifiedName
			val n = makeNode(id, labels = arrayOf("Scope"), simpleName = pkg.simpleName)
			n["qualifiedName"] = pkg.qualifiedName
			n["kind"] = "package"
			g.nodes.add(n)
			scopeNodes[pkg] = n

			// nested packages
			pkg.declaringPackage
				?.takeIf { !it.isUnnamedPackage }
				?.let { parent ->
					scopeNodes[parent]?.let { g.edges.add(makeEdge(it, n, label = "encloses")) }
				}
		}


	// ─────────────────────────────────────────────────────────────────────
	// 4) TYPES (classes/interfaces)
	// ─────────────────────────────────────────────────────────────────────
	data class TypeInfo(val node: Node, val ct: CtType<*>, val file: Path)

	val typesMap = mutableMapOf<CtTypeReference<*>, Node>()
	// keyed by qualifiedName so we can detect same-QN declarations across source roots
	val nodesByQn = mutableMapOf<String, Node>()

	val types: List<TypeInfo> = model
		.allTypes
		.flatMap { allTypesForReal(it) }
		.mapNotNull { ct ->
			ct.position.file?.toURI()?.let { uri ->
				val p = Paths.get(uri).canonical()
				// skip types whose source file was excluded from the filesystem walk
				if (!fileNodes.containsKey(p)) return@mapNotNull null
				val qn = ct.qualifiedName

				// If two source roots declare the same qualified name (e.g. src/main vs src/test),
				// reuse the existing node rather than creating a duplicate. Both files still get
				// a  File -declares-> Type  edge below, so neither declaration is invisible.
				val existing = nodesByQn[qn]
				if (existing != null) {
					GraphExtractor.logger.atWarn()
						.setMessage("Duplicate type declaration — reusing existing node")
						.addKeyValue("qualifiedName", qn)
						.addKeyValue("file", p.toString())
						.log()
					typesMap[ct.reference] = existing
					return@mapNotNull TypeInfo(existing, ct, p)
				}

				val n = makeNode(qn, labels = arrayOf("Type"), simpleName = ct.simpleName)
				val kind = when {
					ct is CtTypeParameterImpl -> "type parameter"
					ct.isInterface -> "interface"
					ct.isEnum      -> "enum"
					ct.isClass     -> if (ct.isAbstract) "abstract class" else "class"
					else           -> "class"
				}
				n["qualifiedName"] = qn
				n["kind"] = kind
				n["docComment"] = ct.docComment ?: ""
				n["visibility"] = if (ct.isPublic) "public"
						else if (ct.isProtected) "protected "
						else if (ct.isPrivate) "private"
						else "default"
				g.nodes.add(n)
				typesMap[ct.reference] = n
				nodesByQn[qn] = n
				TypeInfo(n, ct, p)
			}
		}

	val typesByQn = types.associateBy { it.ct.qualifiedName }
	val typeInfoByCt = types.associateBy { it.ct }

	// File → declares → Type
	types.groupBy { it.file }.forEach { (path, infos) ->
		val fNode = fileNodes[path] ?: return@forEach

		infos.forEach { g.edges.add(makeEdge(fNode, it.node, label = "declares")) }

		// File → declares → Scope
		infos.first().ct.`package`?.let { pkg ->
			scopeNodes[pkg]?.let { g.edges.add(makeEdge(fNode, it, label = "declares")) }
		}

		// import → requires
		try {
			Files.readAllLines(path)
				.asSequence()
				.map(String::trim)
				.filter { it.startsWith("import ") }
				.map { it.removePrefix("import ").removeSuffix(";") }
				.distinct()
				.forEach { imp ->
					typesByQn[imp]?.file?.let { req ->
						fileNodes[req]?.let { g.edges.add(makeEdge(fNode, it, label = "requires")) }
					}
				}
		} catch (e: Exception) {
			GraphExtractor.logger.atWarn().setMessage(e.message ?: "unknown error")
		}
	}

	// Scope → encloses → Type
	types.forEach { type ->
		type.ct.`package`?.let { pkg ->
			scopeNodes[pkg]?.let { g.edges.add(makeEdge(it, type.node, label = "encloses")) }
		}
	}


	// ─────────────────────────────────────────────────────────────────────
	// 5) NESTED TYPES & INHERITANCE
	// ─────────────────────────────────────────────────────────────────────
	types.forEach { ti ->
		// nested classes
		ti.ct.nestedTypes.forEach { nested ->
			typeInfoByCt[nested]?.let { g.edges.add(makeEdge(ti.node, it.node, label = "encloses")) }
		}
		// extends
		ti.ct.superclass?.qualifiedName?.let { sqn ->
			typesByQn[sqn]?.let { g.edges.add(makeEdge(ti.node, it.node, label = "specializes")) }
		}
		// implements
		ti.ct.superInterfaces.forEach { iface ->
			iface.qualifiedName?.let { iqn ->
				typesByQn[iqn]?.let { g.edges.add(makeEdge(ti.node, it.node, label = "specializes")) }
			}
		}
	}


	// ─────────────────────────────────────────────────────────────────────
	// 6) OPERATIONS & VARIABLES
	// ─────────────────────────────────────────────────────────────────────
	data class OpInfo(val node: Node, val exec: CtExecutable<*>)

	val ops = mutableListOf<OpInfo>()
	val vars = mutableMapOf<CtVariableReference<*>, Node>()

	// a) fields → Variable
	types.forEach { ti ->
		ti.ct.declaredFields.forEach { fld ->
			val varId = "${ti.ct.qualifiedName}.${fld.simpleName}"
			val v = makeNode(varId, labels = arrayOf("Variable"), simpleName = fld.simpleName)
			v["qualifiedName"] = "${ti.ct.qualifiedName}.${fld.simpleName}"
			v["kind"] = "field"
			v["sourceText"] = getSourceText(fld.declaration)
			v["docComment"] = fld.declaration?.docComment ?: ""
			v["visibility"] = if (fld.declaration?.isPublic ?: false) "public"
					else if (fld.declaration?.isProtected ?: false) "protected "
					else if (fld.declaration?.isPrivate ?: false) "private"
					else "default"
			g.nodes.add(v)
			vars[fld] = v

			g.edges.add(makeEdge(ti.node, v, label = "encapsulates"))
			fld.type?.let { typeRef ->
				typesMap[typeRef]?.let { g.edges.add(makeEdge(v, it, label = "typed")) }
			}
		}
	}

	fun simpleSig(exec: CtExecutableReference<*>): String {
		val sig = exec.signature ?: return ""
		val declaringType = exec.declaringType ?: return sig
		val classFullName = declaringType.qualifiedName ?: return sig
		val classSimpleName = declaringType.simpleName ?: return sig
		return sig.replaceFirst(classFullName, classSimpleName)
	}

	// b) methods & ctors → Operation
	types.forEach { ti ->
		ti.ct.declaredExecutables.forEach { exec ->
			val sig = if (exec.isConstructor) simpleSig(exec) else (exec.signature ?: "")
			val opId = "${ti.ct.qualifiedName}#$sig"
			val o = makeNode(opId, labels = arrayOf("Operation"), simpleName = sig)
			val kind = if (exec.isConstructor) "constructor" else "method"
			o["qualifiedName"] = "${ti.ct.qualifiedName}#$sig"
			o["kind"] = kind
			val execDecl = exec.executableDeclaration
			o["sourceText"] = getSourceText(execDecl)
			o["docComment"] = execDecl?.docComment ?: ""
			o["visibility"] = when (exec.declaration) {
				is CtModifiable -> {
					val mod = exec.declaration as CtModifiable
					if (mod.isPublic) {
						"public"
					} else if (mod.isPrivate) {
						"private"
					} else if (mod.isProtected) {
						"protected"
					} else {
						"default"
					}
				}

				else -> {
					"unknown"
				}
			}
			g.nodes.add(o)
			if (execDecl != null) {
				ops += OpInfo(o, execDecl)
			}

			g.edges.add(makeEdge(ti.node, o, label = "encapsulates"))
			exec.type?.qualifiedName?.let { rqn ->
				typesByQn[rqn]?.let { g.edges.add(makeEdge(o, it.node, label = "returns")) }
			}
			// parameters: invert
			execDecl?.parameters?.forEachIndexed { index, param ->
				val pid = "${ti.ct.qualifiedName}#${sig}:param[${index}]:${param.simpleName}"
				val p = makeNode(pid, labels = arrayOf("Variable"), simpleName = param.simpleName)
				p["qualifiedName"] = pid
				p["kind"] = "parameter"
				p["parameterIndex"] = index
				g.nodes.add(p)
				vars[param.reference] = p
				g.edges.add(makeEdge(p, o, label = "parameterizes"))

				param.type?.let { typeRef ->
					typesMap[typeRef]?.let { g.edges.add(makeEdge(p, it, label = "typed")) }
				}
			}
		}
	}

	// c) calls / instantiates / uses / overrides
	ops.forEach { (oNode, exec) ->
		exec.body
			?.filterChildren(fun(it: CtElement): Boolean {
				return it is CtInvocation<*> ||
						it is CtConstructorCall<*> ||
						it is CtFieldRead<*> ||
						it is CtFieldWrite<*>
			})
			?.forEach { child: CtElement ->
				when (child) {
					is CtInvocation<*> -> {
						ops.find { it.exec.reference == child.executable }
							?.let { g.edges.add(makeEdge(oNode, it.node, label = "invokes")) }
					}

					is CtConstructorCall<*> -> {
						child.type?.qualifiedName?.let { qn ->
							typesByQn[qn]?.let { g.edges.add(makeEdge(oNode, it.node, label = "instantiates")) }
						}
					}

					is CtFieldRead<*>, is CtFieldWrite<*> -> {
						val ref = (child as? CtVariableAccess<*>)?.variable
						ref?.let { vars[it]?.let { vn -> g.edges.add(makeEdge(oNode, vn, label = "uses")) } }
					}
				}
			}

		// overrides
		exec.overriddenExecutables().forEach { oref ->
			ops.find { it.exec.reference == oref }
				?.let { g.edges.add(makeEdge(oNode, it.node, label = "overrides")) }
		}
	}


	// ─────────────────────────────────────────────────────────────────────
	// 7) NUM METHODS METRIC (single Metric node, per‐class edges)
	// ─────────────────────────────────────────────────────────────────────
	val metricId = "Metrics#NumMethods"
	val numMethodsNode = makeNode(
		id         = metricId,
		labels     = arrayOf("Metric"),
		simpleName = "NumMethods"
	)
	numMethodsNode["qualifiedName"] = "Number of Methods"
	numMethodsNode["kind"] = "metric"
	g.nodes.add(numMethodsNode)

	types.forEach { ti ->
		val count = ti.ct.declaredExecutables.size
		val edge  = makeEdge(ti.node, numMethodsNode, label = "measures")
		edge["value"] = count
		g.edges.add(edge)
	}

	// ─────────────────────────────────────────────────────────────────────
	// 8) NUM STATEMENTS METRIC (single Metric node, per‐operation edges)
	// ─────────────────────────────────────────────────────────────────────
	val numStmtsNode = makeNode(
		id = "Metrics#NumStatements",
		labels = arrayOf("Metric"),
		simpleName = "NumStatements"
	)
	numStmtsNode["qualifiedName"] = "Number of Statements"
	numStmtsNode["kind"] = "metric"
	g.nodes.add(numStmtsNode)

	ops.forEach { (opNode, exec) ->
		val stmtCount = exec.body?.statements?.size ?: 0
		val edge = makeEdge(opNode, numStmtsNode, label = "measures")
		edge["value"] = stmtCount
		g.edges.add(edge)
	}

	return g
}


// helper to find overridden in super‐classes and interfaces
private fun CtExecutable<*>.overriddenExecutables(): List<CtExecutableReference<*>> {
	val ref = this.reference ?: return emptyList()
	val declaringTypeRef = ref.declaringType ?: return emptyList()
	val owners = mutableListOf<CtTypeReference<*>>()
	declaringTypeRef.superclass?.let { owners += it }
	owners += declaringTypeRef.superInterfaces

	return owners.flatMap { owner ->
		owner.declaration
			?.declaredExecutables
			?.filter { it.signature == this.signature }
			?: emptyList()
	}
}
