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

            // Containers
            model.allPackages.filter { !it.isUnnamedPackage }.forEach { pkg ->
                Node(pkg.qualifiedName, "Container").let { node ->
                    node["simpleName"] = pkg.simpleName
                    node["kind"] = "package"
                    g.nodes.add(node)
                }
            }

            // Container-contains-Container
            model.allPackages.filter { !it.isUnnamedPackage }.forEach { pkg ->
                val pkgNode = g.nodes.findById(pkg.qualifiedName)!!
                g.nodes.findById(pkg.declaringPackage.qualifiedName)?.let { declPkgNode ->
                    g.edges.add(makeEdge(declPkgNode, pkgNode, 1, "contains"))
                }
            }

            // Structure, Container-contains-Structure
            model.allTypes.forEach { type ->
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
                    g.nodes.findById(type.`package`.qualifiedName)?.let { pkgNode ->
                        g.edges.add(makeEdge(pkgNode, node, 1, "contains"))
                    }
                }
            }

            model.allTypes.forEach { type ->
                val node = g.nodes.findById(type.qualifiedName)!!

                // specializes
                type.ancestors.forEach {
                    g.nodes.findById(it.qualifiedName)?.let { ancestor ->
                        g.edges.add(makeEdge(node, ancestor, 1, "specializes"))
                    }
                }

                // holds
                listOf(
                    *type.fields.map { it.type }.toTypedArray(),
                    *type.fields.flatMap { it.type.actualTypeArguments }.toTypedArray()
                ).groupingBy { it }.eachCount().forEach { (otherType, count) ->
                    g.nodes.findById(otherType.qualifiedName)?.let {
                        g.edges.add(makeEdge(node, it, count, "holds"))
                    }
                }

                // returns
                listOf(
                    *type.methods.map { it.type }.toTypedArray(),
                    *type.methods.flatMap { it.type.actualTypeArguments }.toTypedArray()
                ).groupingBy { it }.eachCount().forEach { (otherType, count) ->
                    g.nodes.findById(otherType.qualifiedName)?.let {
                        g.edges.add(makeEdge(node, it, count, "returns"))
                    }
                }

                // accepts
                listOf(
                    *type.methods.flatMap { it.parameters }.map { it.type }.toTypedArray(),
                    *type.methods.flatMap { it.parameters }.flatMap { it.type.actualTypeArguments }.toTypedArray()
                ).groupingBy { it }.eachCount().forEach { (otherType, count) ->
                    g.nodes.findById(otherType.qualifiedName)?.let {
                        g.edges.add(makeEdge(node, it, count, "accepts"))
                    }
                }

                // accesses
                val accessedTypes = type.typeMembers
                    .filter { it is CtBodyHolder }
                    .map { it as CtBodyHolder }
                    .flatMap { it.body?.getElements(TypeFilter(CtFieldAccess::class.java))?.toList() ?: listOf() }
                    .map { it.type }
                    .toTypedArray()
                listOf(
                    *accessedTypes,
                    *accessedTypes.flatMap { it.actualTypeArguments }.toTypedArray()
                ).groupingBy { it }.eachCount().forEach { (otherType, count) ->
                    g.nodes.findById(otherType.qualifiedName)?.let {
                        g.edges.add(makeEdge(node, it, count, "accesses"))
                    }
                }

                // calls
                val calledTypes = type.typeMembers
                    .filter { it is CtBodyHolder }
                    .map { it as CtBodyHolder }
                    .flatMap { it.body?.getElements(TypeFilter(CtInvocation::class.java))?.toList() ?: listOf() }
                    .map { it.type }
                    .toTypedArray()
                listOf(
                    *calledTypes,
                    *calledTypes.flatMap { it.actualTypeArguments }.toTypedArray()
                ).groupingBy { it }.eachCount().forEach { (otherType, count) ->
                    g.nodes.findById(otherType.qualifiedName)?.let {
                        g.edges.add(makeEdge(node, it, count, "calls"))
                    }
                }

                // constructs
                val constructedTypes = type.typeMembers
                    .filter { it is CtBodyHolder }
                    .map { it as CtBodyHolder }
                    .flatMap { it.body?.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf() }
                    .map { it.type }
                    .toTypedArray()
                listOf(
                    *constructedTypes,
                    *constructedTypes.flatMap { it.actualTypeArguments }.toTypedArray()
                ).groupingBy { it }.eachCount().forEach { (otherType, count) ->
                    g.nodes.findById(otherType.qualifiedName)?.let {
                        g.edges.add(makeEdge(node, it, count, "constructs"))
                    }
                }

            }
            println(g)
        }
    }
}