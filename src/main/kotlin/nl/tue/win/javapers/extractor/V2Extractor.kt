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
import spoon.reflect.visitor.filter.TypeFilter
import spoon.support.reflect.declaration.CtTypeParameterImpl
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class V2Extractor(
	private val projectName: String,
	private val model: CtModel,
	private val inputPaths: List<String>
) : GraphExtractor {

	override fun extract(): Graph {
		val g = Graph(projectName)

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
				simpleName = path.fileName.toString()
			)
			dirNode["qualifiedName"] = dirNode.id
			dirNode["kind"] = "folder"
			g.nodes.add(dirNode)
			folderNodes[path] = dirNode

			Files.list(path).use { stream ->
				stream.toList().forEach { child ->
					if (Files.isDirectory(child)) {
						processDir(child)
						g.edges.add(makeEdge(dirNode, folderNodes[child]!!, label = "contains"))
					} else {
						val fileNode = makeNode(
							id = child.toString(),
							labels = arrayOf("File"),
							simpleName = child.fileName.toString()
						)
						fileNode["qualifiedName"] = fileNode.id
						fileNode["kind"] = "file"
						g.nodes.add(fileNode)
						fileNodes[child] = fileNode
						g.edges.add(makeEdge(dirNode, fileNode, label = "contains"))
					}
				}
			}
		}

		// 1) Seed only the given inputPaths:
		inputPaths
			.map { Paths.get(it).toAbsolutePath().normalize() }
			.filter { Files.exists(it) }
			.forEach { root ->
				if (Files.isDirectory(root)) {
					processDir(root)
				} else {
					// only that single file
					val single = makeNode(
						id = root.toString(),
						labels = arrayOf("File"),
						simpleName = root.fileName.toString()
					)
					single["qualifiedName"] = single.id
					single["kind"] = "file"
					g.nodes.add(single)
					fileNodes[root] = single
				}
			}

		// 2) Project includes → exactly those roots (no children, no parents)
		inputPaths
			.map { Paths.get(it).toAbsolutePath().normalize() }
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

		val types = model
			.getElements(TypeFilter(CtType::class.java))
			.mapNotNull { ct ->
//				println(ct.simpleName + ", " + ct.reference)
				ct.position.file?.toURI()?.let { uri ->
					val p = Paths.get(uri).toAbsolutePath().normalize()
					val id = ct.qualifiedName  // unique
					val n = makeNode(id, labels = arrayOf("Type"), simpleName = ct.simpleName)
					val kind = when {
						ct is CtTypeParameterImpl -> "type parameter"
						ct.isInterface -> "interface"
						ct.isEnum      -> "enum"
						ct.isClass     -> if (ct.isAbstract) "abstract class" else "class"
						else           -> "class"
					}
					n["qualifiedName"] = ct.qualifiedName
					n["kind"] = kind
					n["docComment"] = ct.docComment ?: ""
					n["visibility"] = if (ct.isPublic) "public"
							else if (ct.isProtected) "protected "
							else if (ct.isPrivate) "private"
							else "default"
					g.nodes.add(n)
					typesMap[ct.reference] = n
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
				v["docComment"] = fld.declaration.docComment ?: ""
				v["visibility"] = if (fld.declaration.isPublic) "public"
						else if (fld.declaration.isProtected) "protected "
						else if (fld.declaration.isPrivate) "private"
						else "default"
				g.nodes.add(v)
				vars[fld] = v

				g.edges.add(makeEdge(ti.node, v, label = "encapsulates"))
				fld.type.let { typeRef ->
					typesMap[typeRef]?.let { g.edges.add(makeEdge(v, it, label = "typed")) }
				}
			}
		}

		fun simpleSig(exec: CtExecutableReference<*>): String {
			val sig = exec.signature
			val classFullName = exec.declaringType.qualifiedName
			val classSimpleName = exec.declaringType.simpleName
			return sig.replaceFirst(classFullName, classSimpleName)
		}

		// b) methods & ctors → Operation
		types.forEach { ti ->
			ti.ct.declaredExecutables.forEach { exec ->
				val sig = if (exec.isConstructor) simpleSig(exec) else exec.signature
				val opId = "${ti.ct.qualifiedName}#$sig"
				val o = makeNode(opId, labels = arrayOf("Operation"), simpleName = sig)
				val kind = if (exec.isConstructor) "constructor" else "method"
				o["qualifiedName"] = "${ti.ct.qualifiedName}#$sig"
				o["kind"] = kind
				o["sourceText"] = getSourceText(exec.executableDeclaration)
				o["docComment"] = exec.executableDeclaration.docComment ?: ""
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
				ops += OpInfo(o, exec.executableDeclaration)

				g.edges.add(makeEdge(ti.node, o, label = "encapsulates"))
				exec.type.qualifiedName?.let { rqn ->
					typesByQn[rqn]?.let { g.edges.add(makeEdge(o, it.node, label = "returns")) }
				}
				// parameters: invert
				exec.executableDeclaration.parameters.forEachIndexed { index,param ->
					val pid = "${ti.ct.qualifiedName}#$sig:param:${param.simpleName}"
					val p = makeNode(pid, labels = arrayOf("Variable"), simpleName = param.simpleName)
					p["qualifiedName"] = "${ti.ct.qualifiedName}#${sig}:param[${index}]:${param.simpleName}"
					p["kind"] = "parameter"
					p["parameterIndex"] = index
					g.nodes.add(p)
					vars[param.reference] = p
					g.edges.add(makeEdge(p, o, label = "parameterizes"))

					param.type.let { typeRef ->
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
							child.type.qualifiedName?.let { qn ->
								typesByQn[qn]?.let { g.edges.add(makeEdge(oNode, it.node, label = "instantiates")) }
							}
						}

						is CtFieldRead<*>, is CtFieldWrite<*> -> {
							val ref = (child as CtFieldAccess<*>).variable
							vars[ref]?.let { g.edges.add(makeEdge(oNode, it, label = "uses")) }
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
		// create one global Metric node
		val metricId = "Metrics#NumMethods"
		val numMethodsNode = makeNode(
			id         = metricId,
			labels     = arrayOf("Metric"),
			simpleName = "NumMethods"
		)
		numMethodsNode["qualifiedName"] = "Number of Methods"
		numMethodsNode["kind"] = "metric"
		g.nodes.add(numMethodsNode)

		// for each class, point to that one node and stash the count on the edge
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

		// attach per-operation edges
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
		val owners = mutableListOf<CtTypeReference<*>>()
		val declaringType = this.type
		declaringType.superclass?.let { owners += it }
		owners += declaringType.superInterfaces

		return owners.flatMap { owner ->
			owner.declaration
				?.declaredExecutables
				?.filter { it.signature == this.signature }
				?: emptyList()
		}
	}
}
