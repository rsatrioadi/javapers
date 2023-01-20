package nl.tue.win.javapers.extractor

import nl.tue.win.lpg.Graph
import nl.tue.win.lpg.Node
import spoon.reflect.CtModel
import spoon.reflect.code.CtConstructorCall
import spoon.reflect.declaration.CtConstructor
import spoon.reflect.declaration.CtExecutable
import spoon.reflect.declaration.CtMethod
import spoon.reflect.reference.CtArrayTypeReference
import spoon.reflect.visitor.filter.TypeFilter

class ExpandedExtractor(private val projectName: String) : GraphExtractor {

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

                    type.fields.forEach { field ->
                        // Variable [kind=field]
                        Node("${type.qualifiedName}.${field.simpleName}", "Variable").let { fieldNode ->
                            fieldNode["simpleName"] = field.simpleName
                            fieldNode["kind"] = "field"
                            g.nodes.add(fieldNode)

                            g.nodes.findById(field.type.qualifiedName)?.let { fieldTypeNode ->
                                // Variable-type-Type
                                g.edges.add(makeEdge(fieldNode, fieldTypeNode, 1, "type"))
                            }

                            // Structure-hasVariable-Variable
                            g.edges.add(makeEdge(node, fieldNode, 1, "hasVariable"))
                        }
                    }

                    type.typeMembers.filter { it is CtExecutable<*> }.map { it as CtExecutable<*> }.forEach { script ->
                        val names = when (script) {
                            is CtConstructor<*> -> Triple(script.signature, "Constructor", "ctor")
                            is CtMethod<*> -> Triple(script.signature, "Operation", "method")
                            else -> {
                                Triple("script${script.hashCode()}", "Script", "script")
                            }
                        }
                        // Script
                        Node("${node.id}.${names.first}", names.second).let { scriptNode ->
                            scriptNode["simpleName"] = names.first
                            scriptNode["kind"] = names.third
                            g.nodes.add(scriptNode)

                            // hasScript
                            g.edges.add(makeEdge(node, scriptNode, 1, "hasScript"))

                            g.nodes.findById(script.type.qualifiedName)?.let { scriptTypeNode ->
                                // returnType
                                g.edges.add(makeEdge(scriptNode, scriptTypeNode, 1, "returnType"))
                            }

                            script.parameters.forEach { param ->
                                // Variable
                                Node("${node.id}.${names.first}.${param.simpleName}", "Variable").let { paramNode ->
                                    paramNode["simpleName"] = param.simpleName
                                    paramNode["kind"] = "parameter"
                                    g.nodes.add(paramNode)

                                    g.nodes.findById(param.type.qualifiedName)?.let { fieldTypeNode ->
                                        // Variable-type-Type
                                        g.edges.add(makeEdge(paramNode, fieldTypeNode, 1, "type"))
                                    }

                                    // hasParameter
                                    g.edges.add(makeEdge(scriptNode, paramNode, 1, "hasParameter"))
                                }
                            }

                            val constructedTypes =
                                (script.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf())
                                    .map { it.type }
                            val constructedArrayTypes =
                                constructedTypes.filter { it.isArray }.map { (it as CtArrayTypeReference).arrayType }
                            val allConstructedTypes = (constructedTypes + constructedArrayTypes)
                            (allConstructedTypes + allConstructedTypes.flatMap { it.actualTypeArguments })
                                .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                                    g.nodes.findById(otherType.qualifiedName)?.let {
                                        // instantiates
                                        g.edges.add(makeEdge(scriptNode, it, count, "instantiates"))
                                    }
                                }
                        }
                    }
                }
            }
            println(g)
        }
    }
}
