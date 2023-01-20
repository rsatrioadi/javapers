package nl.tue.win.javapers.extractor

import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import spoon.reflect.CtModel
import spoon.reflect.code.CtBodyHolder
import spoon.reflect.code.CtConstructorCall
import spoon.reflect.code.CtFieldAccess
import spoon.reflect.code.CtInvocation
import spoon.reflect.visitor.filter.TypeFilter

class CompactedExtractor(private val projectName: String) : GraphExtractor {

    override fun extract(model: CtModel): Graph {
        return Graph(projectName).also { g ->

            listOf("byte", "char", "short", "int", "long", "float", "double", "boolean", "void")
                .forEach { prim ->
                    // Primitives
                    Node(prim, "Primitive").let { node ->
                        node["simpleName"] = prim
                        g.nodes.add(node)
                    }
                }
            // Let's consider String a Primitive
            Node("java.lang.String", "Structure").let { node ->
                node["simpleName"] = "String"
                g.nodes.add(node)
            }

            model.allPackages.filter { !it.isUnnamedPackage }.forEach { pkg ->
                // Containers
                Node(pkg.qualifiedName, "Container").let { node ->
                    node["simpleName"] = pkg.simpleName
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

            model.allTypes.forEach { type ->
                // Structure
                Node(type.qualifiedName, "Structure").let { node ->
                    node["simpleName"] = type.simpleName
                    node["kind"] = when {
                        type.isInterface -> "interface"
                        type.isEnum -> "enumeration"
                        type.isAbstract -> "abstract"
                        else -> {
                            "class"
                        }
                    }
                    g.nodes.add(node)
                    // Container-contains-Structure
                    g.nodes.findById(type.`package`.qualifiedName)?.let { pkgNode ->
                        g.edges.add(makeEdge(pkgNode, node, 1, "contains"))
                    }
                }
            }

            model.allTypes.forEach { type ->
                g.nodes.findById(type.qualifiedName)?.let { node ->

                    type.ancestors.forEach {
                        g.nodes.findById(it.qualifiedName)?.let { ancestor ->
                            // specializes
                            g.edges.add(makeEdge(node, ancestor, 1, "specializes"))
                        }
                    }

                    (type.fields.map { it.type } + type.fields.flatMap { it.type.actualTypeArguments })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            g.nodes.findById(otherType.qualifiedName)?.let {
                                // holds
                                g.edges.add(makeEdge(node, it, count, "holds"))
                            }
                        }

                    (type.methods.map { it.type } + type.methods.flatMap { it.type.actualTypeArguments })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            g.nodes.findById(otherType.qualifiedName)?.let {
                                // returns
                                g.edges.add(makeEdge(node, it, count, "returns"))
                            }
                        }

                    (type.methods.flatMap { it.parameters }.map { it.type }
                            + type.methods.flatMap { it.parameters }.flatMap { it.type.actualTypeArguments })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            g.nodes.findById(otherType.qualifiedName)?.let {
                                // accepts
                                g.edges.add(makeEdge(node, it, count, "accepts"))
                            }
                        }

                    val accessedTypes = type.typeMembers
                        .filterIsInstance<CtBodyHolder>()
                        .flatMap { it.body?.getElements(TypeFilter(CtFieldAccess::class.java))?.toList() ?: listOf() }
                        .map { it.type }
                    (accessedTypes + accessedTypes.flatMap { it.actualTypeArguments })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            g.nodes.findById(otherType.qualifiedName)?.let {
                                // accesses
                                g.edges.add(makeEdge(node, it, count, "accesses"))
                            }
                        }

                    val calledTypes = type.typeMembers
                        .filterIsInstance<CtBodyHolder>()
                        .flatMap { it.body?.getElements(TypeFilter(CtInvocation::class.java))?.toList() ?: listOf() }
                        .map { it.type } +
                            type.fields
                                .mapNotNull { it.defaultExpression }
                                .flatMap { it.getElements(TypeFilter(CtInvocation::class.java))?.toList() ?: listOf() }
                                .map { it.type }
                                .toTypedArray()
                    (calledTypes + calledTypes.flatMap { it.actualTypeArguments })
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            g.nodes.findById(otherType.qualifiedName)?.let {
                                // calls
                                g.edges.add(makeEdge(node, it, count, "calls"))
                            }
                        }

                    // constructs
                    val constructedTypes = type.typeMembers
                        .filterIsInstance<CtBodyHolder>()
                        .flatMap {
                            it.body?.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf()
                        }
                        .map { it.type } +
                            type.fields
                                .mapNotNull { it.defaultExpression }
                                .flatMap {
                                    it.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf()
                                }
                                .map { it.type }
                    (constructedTypes + constructedTypes.flatMap { it.actualTypeArguments }.toTypedArray())
                        .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                            g.nodes.findById(otherType.qualifiedName)?.let {
                                g.edges.add(makeEdge(node, it, count, "constructs"))
                            }
                        }
                }

            }
            println(g)
        }
    }
}