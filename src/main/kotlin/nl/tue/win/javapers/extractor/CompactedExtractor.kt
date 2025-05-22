package nl.tue.win.javapers.extractor

import nl.tue.win.codepers.GraphExtractor
import nl.tue.win.codepers.makeEdge
import nl.tue.win.codepers.makeNode
import nl.tue.win.codepers.primitiveTypes
import nl.tue.win.lpg.Graph
import spoon.reflect.CtModel
import spoon.reflect.code.*
import spoon.reflect.declaration.CtType
import spoon.reflect.visitor.filter.TypeFilter

class CompactedExtractor(private val projectName: String, val model: CtModel) : GraphExtractor {

    override fun extract(): Graph {
        return extract(false)
    }

    fun extract(extractFeatures: Boolean): Graph {
        return Graph(projectName).also { g ->

            // let's ignore void
            primitiveTypes
                .forEach { prim ->
                    // Primitives
                    makeNode(prim, "Primitive", simpleName = prim).let { node -> g.nodes.add(node) }
                }
            // Let's consider String a Primitive
            makeNode("java.lang.String", "Structure", simpleName = "String").let { node ->
                g.nodes.add(node)
            }

            model.allPackages.filter { !it.isUnnamedPackage }.forEach { pkg ->
                // Containers
                makeNode(pkg.qualifiedName, "Container", simpleName = pkg.simpleName).let { node ->
                    node["kind"] = "package"
                    g.nodes.add(node)
                }
            }

            model.allPackages.filter { !it.isUnnamedPackage }.forEach { pkg ->
                val pkgNode = g.nodes.findById(pkg.qualifiedName)!!
                g.nodes.findById(pkg.declaringPackage.qualifiedName)?.let { declPkgNode ->
                    // Container-contains-Container
                    g.edges.add(makeEdge(declPkgNode, pkgNode, 1, "contains"))
                }
            }

            val allTypes = model.allTypes.flatMap { allTypesForReal(it) }

            allTypes.forEach { type ->
                // Structure
                makeNode(type.qualifiedName, "Structure", simpleName = type.simpleName).let { node ->
                    node["kind"] = when {
                        type.isInterface -> "interface"
                        type.isEnum -> "enum"
                        type.isAbstract -> "abstract class"
                        else -> {
                            "class"
                        }
                    }
                    node["sourceText"] = type.toString()

                    if (extractFeatures) {
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
                        g.edges.add(makeEdge(pkgNode, node, 1, "contains").also { edge ->
                            edge["containmentType"] = "package"
                        })
                    }
                }
            }

            allTypes.forEach { type ->
                g.nodes.findById(type.qualifiedName)?.let { node ->

                    type.nestedTypes.forEach { nestedType ->

                        g.nodes.findById(nestedType.qualifiedName)?.let { nestedTypeNode ->
                            g.edges.add(makeEdge(nestedTypeNode, node, 1, "nests"))
                        }
                    }

                    if (type.superclass != null) {
                        g.nodes.findById(type.superclass.qualifiedName)?.let { ancestor ->
                            // specializes
                            g.edges.add(makeEdge(node, ancestor, 1, "specializes").also { edge ->
                                edge["specializationType"] = "extends"
                            })
                        }
                    }
                    type.superInterfaces.forEach {
                        g.nodes.findById(it.qualifiedName)?.let { ancestor ->
                            // specializes
                            g.edges.add(makeEdge(node, ancestor, 1, "specializes").also { edge ->
                                edge["specializationType"] = "implements"
                            })
                        }
                    }

                    val fieldTypes = type.fields.map { it.typeOrArrayType }
                    (fieldTypes + fieldTypes.flatMap { it?.actualTypeArguments ?: listOf() })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            otherType?.also {
                                g.nodes.findById(it.qualifiedName)?.let {
                                    // holds
                                    g.edges.add(makeEdge(node, it, count, "holds"))
                                }
                            }
                        }

                    val methodTypes = type.methods.map { it.typeOrArrayType }
                    (methodTypes + methodTypes.flatMap { it?.actualTypeArguments ?: listOf() })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            otherType?.also {
                                g.nodes.findById(it.qualifiedName)?.let {
                                    // returns
                                    g.edges.add(makeEdge(node, it, count, "returns"))
                                }
                            }
                        }

                    val paramTypes = type.methods.flatMap { it.parameters }.map { it.typeOrArrayType }
                    (paramTypes + paramTypes.flatMap { it?.actualTypeArguments ?: listOf() })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            otherType?.also {
                                g.nodes.findById(it.qualifiedName)?.let {
                                    // accepts
                                    g.edges.add(makeEdge(node, it, count, "accepts"))
                                }
                            }
                        }

                    val accessedTypes = type.typeMembers
                        .filterIsInstance<CtBodyHolder>()
                        .flatMap { it.body?.getElements(TypeFilter(CtFieldAccess::class.java))?.toList() ?: listOf() }
                        .asSequence()
                        .mapNotNull { it.target }
                        .map { if (it is CtTypeAccess<*>) it.accessedType else it.type }
                        .toList() +
                            type.fields
                                .mapNotNull { it.defaultExpression }
                                .flatMap {
                                    it.getElements(TypeFilter(CtFieldAccess::class.java))?.toList() ?: listOf()
                                }
                                .asSequence()
                                .mapNotNull { it.target }
                                .map { if (it is CtTypeAccess<*>) it.accessedType else it.type }
                                .toList()
                    (accessedTypes + accessedTypes.flatMap { it?.actualTypeArguments ?: listOf() })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            otherType?.also {
                                g.nodes.findById(it.qualifiedName)?.let {
                                    if (node.id != it.id) {
                                        // accesses
                                        g.edges.add(makeEdge(node, it, count, "accesses"))
                                    }
                                }
                            }
                        }

                    val calledTypes = type.typeMembers
                        .filterIsInstance<CtBodyHolder>()
                        .flatMap { it.body?.getElements(TypeFilter(CtInvocation::class.java))?.toList() ?: listOf() }
                        .asSequence()
                        .mapNotNull { it.target }
                        .map { if (it is CtTypeAccess<*>) it.accessedType else it.type }
                        .toList() +
                            type.fields
                                .mapNotNull { it.defaultExpression }
                                .flatMap { it.getElements(TypeFilter(CtInvocation::class.java))?.toList() ?: listOf() }
                                .asSequence()
                                .mapNotNull { it.target }
                                .map { if (it is CtTypeAccess<*>) it.accessedType else it.type }
                                .toList()
                                .toTypedArray()
                    (calledTypes + calledTypes.flatMap { it?.actualTypeArguments ?: listOf() })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            otherType?.also {
                                g.nodes.findById(it.qualifiedName)?.let {
                                    if (node.id != it.id) {
                                        // calls
                                        g.edges.add(makeEdge(node, it, count, "calls"))
                                    }
                                }
                            }
                        }

                    // constructs
                    val constructedTypes = type.typeMembers
                        .filterIsInstance<CtBodyHolder>()
                        .flatMap {
                            it.body?.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf()
                        }
                        .mapNotNull { it.typeOrArrayType } +
                            type.fields
                                .mapNotNull { it.defaultExpression }
                                .flatMap {
                                    it.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf()
                                }
                                .map { it.typeOrArrayType }
                    (constructedTypes + constructedTypes.flatMap { it?.actualTypeArguments ?: listOf() })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            otherType?.also {
                                g.nodes.findById(it.qualifiedName)?.let {
                                    g.edges.add(makeEdge(node, it, count, "constructs"))
                                }
                            }
                        }
                }

            }
        }
    }
}

fun allTypesForReal(type: CtType<*>): Set<CtType<*>> {
    return setOf(type, *type.nestedTypes.flatMap { allTypesForReal(it) }.toTypedArray())
}
