package nl.tue.win.javapers.extractor

import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import spoon.reflect.CtModel

class ExpandedExtractor(private val projectName: String) : GraphExtractor {

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
            println(g)
        }
    }
}
