package nl.tue.win.javapers.extractor

import nl.tue.win.lib.md5
import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import spoon.reflect.CtModel
import spoon.reflect.code.CtConstructorCall
import spoon.reflect.code.CtInvocation
import spoon.reflect.declaration.*
import spoon.reflect.reference.CtArrayTypeReference
import spoon.reflect.visitor.filter.TypeFilter


class ExpandedExtractor(private val projectName: String, val model: CtModel) : GraphExtractor {

	private var _extractFeatures: Boolean = false

	override fun extract(): Graph {
		return extract(false)
	}

	fun extract(extractFeatures: Boolean): Graph {
		return Graph(projectName).also { g ->

			addPrimitives(g)

			addPackages(g)

			val allTypes = model.allTypes.flatMap { allTypesForReal(it) }

			addClasses(g, allTypes)

			allTypes.forEach { type ->

				g.nodes.findById(type.qualifiedName)?.let { node ->

					addClassNestings(g, node, type)

					addInheritances(g, node, type)

					addFields(g, node, type)

					// Scripts
					type.typeMembers
						.filter { it is CtExecutable<*> }
						.map { it as CtExecutable<*> }
						.forEach { script ->
							val scriptDetails = extractSignatureLabelKind(script)
							val qualifiedName = "${node.id}.${scriptDetails.first}"

							// Script
							makeNode(
								qualifiedName,
								scriptDetails.second,
								simpleName = scriptDetails.first
							).let { scriptNode ->
								scriptNode["qualifiedName"] = qualifiedName
								addMethod(scriptNode, scriptDetails, script, g, node)

								addMethodReturnType(g, scriptNode, script)

								script.parameters.forEach { param ->
									val paramQualifiedName = "${node.id}.${scriptDetails.first}.${param.simpleName}"
									// Variable
									makeNode(
										paramQualifiedName,
										"Variable",
										simpleName = param.simpleName
									).let { paramNode ->
										paramNode["qualifiedName"] = paramQualifiedName
										paramNode["kind"] = "parameter"
										g.nodes.add(paramNode)

										addMethodParamTypes(g, paramNode, param)

										// hasParameter
										g.edges.add(makeEdge(scriptNode, paramNode, 1, "hasParameter"))
									}
								}

								addConstructorCalls(g, scriptNode, script)
							}
						}
				}
			}

			addMethodCalls(g, allTypes)
		}
	}

	private fun extractSignatureLabelKind(script: CtExecutable<*>) = when (script) {
		is CtConstructor<*> -> Triple(script.signature, "Constructor", "ctor")
		is CtMethod<*> -> Triple(script.signature, "Operation", "method")
		else -> {
			Triple(md5(script.toString()), "Script", "script")
		}
	}

	private fun addPrimitives(g: Graph) {
		// let's ignore void
		primitiveTypes
			.forEach { prim ->
				// Primitives
				makeNode(prim, "Primitive", simpleName = prim)
					.let { node ->
						node["qualifiedName"] = prim
						g.nodes.add(node)
					}
			}
		// Let's consider String a Primitive
		g.nodes.add(makeNode("java.lang.String", "Structure", simpleName = "String"))
	}

	private fun addPackages(g: Graph) {
		model.allPackages
			.filter { !it.isUnnamedPackage }
			.forEach { pkg ->
				// Containers
				makeNode(pkg.qualifiedName, "Container", simpleName = pkg.simpleName)
					.let { node ->
						node["qualifiedName"] = pkg.qualifiedName
						node["kind"] = "package"
						g.nodes.add(node)
					}
			}
		model.allPackages
			.filter { !it.isUnnamedPackage }
			.forEach { pkg ->
				val pkgNode = g.nodes.findById(pkg.qualifiedName)!!
				g.nodes.findById(pkg.declaringPackage.qualifiedName)?.let { declPkgNode ->
					// Container-contains-Container
					g.edges.add(makeEdge(declPkgNode, pkgNode, 1, "contains"))
				}
			}
	}

	private fun addClasses(g: Graph, allTypes: List<CtType<*>>) {
		allTypes.forEach { type ->
			// Structure
			makeNode(type.qualifiedName, "Structure", simpleName = type.simpleName)
				.let { node ->
					node["qualifiedName"] = type.qualifiedName
					node["kind"] = when {
						type.isInterface -> "interface"
						type.isEnum -> "enum"
						type.isAbstract -> "abstract"
						else -> {
							"class"
						}
					}
					node["docComment"] = type.docComment
					//                    node["sourceText"] = type.toString()


					if (this._extractFeatures) {
						val features = ClassFeatures(type, model)

						node["isPublic"] = features.isPublic

						node["isClass"] = features.isClass
						node["isInterface"] = features.isInterface
						node["isAbstract"] = features.isAbstract
						node["isEnum"] = features.isEnum
						node["isStatic"] = features.isStatic

						node["isSerializable"] = features.isSerializable
						node["isCollection"] = features.isCollection
						node["isIterable"] = features.isIterable
						node["isMap"] = features.isMap
						node["isAWTComponent"] = features.isAWTComponent

						node["namedController"] = features.namedController
						node["namedManager"] = features.namedManager
						node["namedListener"] = features.namedListener
						node["namedTest"] = features.namedTest

						node["numFields"] = features.numFields
						node["numPublicFields"] = features.numPublicFields
						node["numPrivateFields"] = features.numPrivateFields
						node["numPrimitiveFields"] = features.numPrimitiveFields
						node["numCollectionFields"] = features.numCollectionFields
						node["numIterableFields"] = features.numIterableFields
						node["numMapFields"] = features.numMapFields
						node["numAWTComponentFields"] = features.numAWTComponentFields

						node["ratioPublicFields"] = features.ratioPublicFields
						node["ratioPrivateFields"] = features.ratioPrivateFields

						node["numMethods"] = features.numMethods
						node["numPublicMethods"] = features.numPublicMethods
						node["numPrivateMethods"] = features.numPrivateMethods
						node["numAbstractMethods"] = features.numAbstractMethods
						node["numGetters"] = features.numGetters
						node["numSetters"] = features.numSetters

						node["ratioPublicMethods"] = features.ratioPublicMethods
						node["ratioPrivateMethods"] = features.ratioPrivateMethods
						node["ratioAbstractMethods"] = features.ratioAbstractMethods
						node["ratioGetters"] = features.ratioGetters
						node["ratioSetters"] = features.ratioSetters

						node["ratioGettersToFields"] = features.ratioGettersToFields
						node["ratioSettersToFields"] = features.ratioSettersToFields

						node["numStatementsInMethods"] = features.numStatementsInMethods
						node["averageStatementsPerMethod"] = features.averageStatementsPerMethod
						node["numParametersInMethods"] = features.numParametersInMethods
						node["averageParametersPerMethod"] = features.averageParametersPerMethod
						node["numBranchingInMethods"] = features.numBranchingInMethods
						node["averageBranchingPerMethod"] = features.averageBranchingPerMethod
						node["numLoopsInMethods"] = features.numLoopsInMethods
						node["averageLoopsPerMethod"] = features.averageLoopsPerMethod

						node["accessesIO"] = features.accessesIO

						node["maxLoopDepth"] = features.maxLoopDepth
					}


					g.nodes.add(node)
					// Container-contains-Structure
					g.nodes.findById(type.`package`.qualifiedName)?.let { pkgNode ->
						g.edges.add(makeEdge(pkgNode, node, 1, "contains"))
					}
				}
		}
	}

	private fun addClassNestings(g: Graph, node: Node, type: CtType<*>) {
		type.nestedTypes.forEach { nestedType ->

			g.nodes.findById(nestedType.qualifiedName)?.let { nestedTypeNode ->
				g.edges.add(makeEdge(node, nestedTypeNode, 1, "nests"))
			}
		}
	}

	private fun addInheritances(g: Graph, node: Node, type: CtType<*>) {
		type.ancestors.forEach {
			g.nodes.findById(it.qualifiedName)?.let { ancestor ->
				// specializes
				g.edges.add(makeEdge(node, ancestor, 1, "specializes"))
			}
		}
	}

	private fun addFields(g: Graph, node: Node, type: CtType<*>) {
		type.fields.forEach { field ->
			// Variable [kind=field]
			val qualifiedName = "${type.qualifiedName}.${field.simpleName}"
			makeNode(
				qualifiedName,
				"Variable",
				simpleName = field.simpleName
			).let<Node, Unit> { fieldNode ->
				fieldNode["qualifiedName"] = qualifiedName
				fieldNode["kind"] = "field"
				fieldNode["sourceText"] = field.toString()
				fieldNode["visibility"] = if (field.isPublic) {
					"public"
				} else if (field.isPrivate) {
					"private"
				} else if (field.isProtected) {
					"protected"
				} else {
					"default"
				}
				g.nodes.add(fieldNode)

				if (field.type.isArray) {

					g.nodes.findById((field.type as CtArrayTypeReference).arrayType.qualifiedName)
						?.let { fieldTypeNode ->
							// Variable-type-Type
							g.edges.add(makeEdge(fieldNode, fieldTypeNode, 1, "type")
								.also { edge ->
									edge["kind"] = "array"
								})
						}

				} else {

					g.nodes.findById(field.type.qualifiedName)?.let { fieldTypeNode ->
						// Variable-type-Type
						g.edges.add(makeEdge(fieldNode, fieldTypeNode, 1, "type")
							.also { edge ->
								edge["kind"] = "type"
							})
					}

					(field.type.actualTypeArguments ?: listOf())
						.groupingBy { it }
						.eachCount()
						.forEach { (typeArg, count) ->

							g.nodes.findById(typeArg.qualifiedName)?.let<Node, Unit> { fieldTypeNode ->
								// Variable-type-Type
								g.edges.add(makeEdge(fieldNode, fieldTypeNode, count, "type").also { edge ->
									edge["kind"] = "type argument"
								})
							}
						}
				}

				// Structure-hasVariable-Variable
				g.edges.add(makeEdge(node, fieldNode, 1, "hasVariable"))
			}
		}
	}

	private fun addMethod(
		scriptNode: Node,
		names: Triple<String, String, String>,
		script: CtExecutable<*>,
		g: Graph,
		node: Node
	) {
		scriptNode["kind"] = names.third
		scriptNode["sourceText"] = script.toString()
		scriptNode["docComment"] = script.docComment
		scriptNode["visibility"] = when (script) {
			is CtModifiable ->
				if (script.isPublic) {
					"public"
				} else if (script.isPrivate) {
					"private"
				} else if (script.isProtected) {
					"protected"
				} else {
					"default"
				}

			else -> {
				"unknown"
			}
		}
		g.nodes.add(scriptNode)

		// hasScript
		g.edges.add(makeEdge(node, scriptNode, 1, "hasScript"))
	}

	private fun addMethodReturnType(
		g: Graph,
		scriptNode: Node,
		script: CtExecutable<*>
	) {
		if (script.type.isArray) {

			g.nodes.findById((script.type as CtArrayTypeReference).arrayType.qualifiedName)
				?.let { scriptTypeNode ->
					// returnType
					g.edges.add(makeEdge(scriptNode, scriptTypeNode, 1, "returnType")
						.also { edge ->
							edge["kind"] = "array"
						})
				}

		} else {

			g.nodes.findById(script.type.qualifiedName)?.let { fieldTypeNode ->
				// Variable-type-Type
				g.edges.add(makeEdge(scriptNode, fieldTypeNode, 1, "returnType")
					.also { edge ->
						edge["kind"] = "type"
					})
			}

			(script.type.actualTypeArguments ?: listOf())
				.groupingBy { it }
				.eachCount()
				.forEach { (typeArg, count) ->

					g.nodes.findById(typeArg.qualifiedName)?.let { fieldTypeNode ->
						// Variable-type-Type
						g.edges.add(makeEdge(scriptNode, fieldTypeNode, count, "returnType")
							.also { edge ->
								edge["kind"] = "type argument"
							})
					}

				}

		}
	}

	private fun addMethodParamTypes(
		g: Graph,
		paramNode: Node,
		param: CtParameter<*>
	) {
		if (param.type.isArray) {

			g.nodes.findById((param.type as CtArrayTypeReference).arrayType.qualifiedName)
				?.let { paramTypeNode ->
					// Variable-type-Type
					g.edges.add(makeEdge(paramNode, paramTypeNode, 1, "type")
						.also { edge ->
							edge["kind"] = "array"
						})
				}

		} else {

			g.nodes.findById(param.type.qualifiedName)?.let { paramTypeNode ->
				// Variable-type-Type
				g.edges.add(makeEdge(paramNode, paramTypeNode, 1, "type")
					.also { edge ->
						edge["kind"] = "type"
					})
			}

			(param.type.actualTypeArguments ?: listOf())
				.groupingBy { it }
				.eachCount()
				.forEach { (typeArg, count) ->

					g.nodes.findById(typeArg.qualifiedName)?.let { paramTypeNode ->
						// Variable-type-Type
						g.edges.add(makeEdge(paramNode, paramTypeNode, count, "type")
							.also { edge ->
								edge["kind"] = "type argument"
							})
					}

				}

		}
	}

	private fun addConstructorCalls(
		g: Graph,
		scriptNode: Node,
		script: CtExecutable<*>
	) {
		val ctorCalls = (script.getElements(TypeFilter(CtConstructorCall::class.java))?.toList()
			?: listOf())
			.stream()
			.filter { (it != null) && (it.type != null) && !it.type.isArray }
			.toList()

		val constructedTypes = ctorCalls
			.map { it.type }
			.groupingBy { it }
			.eachCount()

		val constructedTypeArgs = ctorCalls
			.flatMap { it.actualTypeArguments ?: listOf() }
			.groupingBy { it }
			.eachCount()

		constructedTypes.forEach { (constructedType, count) ->
			constructedType?.also {
				g.nodes.findById(it.qualifiedName)?.let { constructedTypeNode ->
					// instantiates
					g.edges.add(makeEdge(scriptNode, constructedTypeNode, count, "instantiates"))
				}
			}
		}
	}

	private fun addMethodCalls(g: Graph, allTypes: List<CtType<*>>) {
		allTypes.forEach { type ->
			g.nodes.findById(type.qualifiedName)?.let { node ->

				type.typeMembers
					.filter { it is CtExecutable<*> }
					.map { it as CtExecutable<*> }
					.forEach { script ->
						val names = when (script) {
							is CtConstructor<*> -> Triple(script.signature, "Constructor", "ctor")
							is CtMethod<*> -> Triple(script.signature, "Operation", "method")
							else -> {
								Triple(script.simpleName, "Script", "script")
							}
						}
						g.nodes.findById("${node.id}.${names.first}")?.let { scriptNode ->
							val invokedMethods =
								script.getElements(TypeFilter(CtInvocation::class.java))?.toList() ?: listOf()
							invokedMethods
								.groupingBy { it.executable }
								.eachCount()
								.forEach { (method, count) ->
									g.nodes.findById("${method.declaringType?.qualifiedName}.${method.signature}")
										?.let {
											// invokes
											g.edges.add(makeEdge(scriptNode, it, count, "invokes"))
										}
								}
						}
					}
			}
		}
	}
}
