package nl.tue.win.javapers.extractor

import nl.tue.win.lib.md5
import nl.tue.win.lpg.Graph
import spoon.reflect.CtModel
import spoon.reflect.code.CtConstructorCall
import spoon.reflect.code.CtInvocation
import spoon.reflect.declaration.CtConstructor
import spoon.reflect.declaration.CtExecutable
import spoon.reflect.declaration.CtMethod
import spoon.reflect.declaration.CtModifiable
import spoon.reflect.visitor.filter.TypeFilter

class ExpandedExtractor(private val projectName: String, val model: CtModel) : GraphExtractor {

    override fun extract(): Graph {
        return Graph(projectName).also { g ->

            // let's ignore void
            primitiveTypes
                .forEach { prim ->
                    // Primitives
                    makeNode(prim, "Primitive", simpleName = prim).let { node -> g.nodes.add(node) }
                }
            // Let's consider String a Primitive
            g.nodes.add(makeNode("java.lang.String", "Structure", simpleName = "String"))

            model.allPackages.filter { !it.isUnnamedPackage }.forEach { pkg ->
                // Containers
                makeNode(pkg.qualifiedName, "Container", simpleName = pkg.simpleName)
                    .let { node ->
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
                    node["docComment"] = type.docComment
                    g.nodes.add(node)
                    // Container-contains-Structure
                    g.nodes.findById(type.`package`.qualifiedName)?.let { pkgNode ->
                        g.edges.add(makeEdge(pkgNode, node, 1, "contains"))
                    }
                }
            }

            allTypes.forEach { type ->

                g.nodes.findById(type.qualifiedName)?.let { node ->

                    type.nestedTypes.forEach { nestedType ->

                        g.nodes.findById(nestedType.qualifiedName)?.let { nestedTypeNode ->
                            g.edges.add(makeEdge(node, nestedTypeNode, 1, "contains").also { edge ->
                                edge["containmentType"] = "nested class"
                            })
                        }
                    }

                    type.ancestors.forEach {
                        g.nodes.findById(it.qualifiedName)?.let { ancestor ->
                            // specializes
                            g.edges.add(makeEdge(node, ancestor, 1, "specializes"))
                        }
                    }

                    type.fields.forEach { field ->
                        // Variable [kind=field]
                        makeNode(
                            "${type.qualifiedName}.${field.simpleName}",
                            "Variable",
                            simpleName = field.simpleName
                        ).let { fieldNode ->
                            fieldNode["kind"] = "field"
                            fieldNode["sourceText"] = field.toString()
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
                                Triple(md5(script.toString()), "Script", "script")
                            }
                        }
                        // Script
                        makeNode(
                            "${node.id}.${names.first}",
                            names.second,
                            simpleName = names.first
                        ).let { scriptNode ->
                            scriptNode["kind"] = names.third
                            scriptNode["sourceText"] = script.toString()
                            scriptNode["docComment"] = script.docComment
                            scriptNode["visibility"] = when (script) {
                                is CtModifiable -> if (script.isPublic) {
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

                            script.typeOrArrayType?.also { scriptType ->
                                g.nodes.findById(scriptType.qualifiedName)?.let { scriptTypeNode ->
                                    // returnType
                                    g.edges.add(makeEdge(scriptNode, scriptTypeNode, 1, "returnType"))
                                }
                            }

                            script.parameters.forEach { param ->
                                // Variable
                                makeNode(
                                    "${node.id}.${names.first}.${param.simpleName}",
                                    "Variable",
                                    simpleName = param.simpleName
                                ).let { paramNode ->
                                    paramNode["kind"] = "parameter"
                                    g.nodes.add(paramNode)

                                    param.typeOrArrayType?.also { paramType ->
                                        g.nodes.findById(paramType.qualifiedName)?.let { fieldTypeNode ->
                                            // Variable-type-Type
                                            g.edges.add(makeEdge(paramNode, fieldTypeNode, 1, "type"))
                                        }
                                    }

                                    // hasParameter
                                    g.edges.add(makeEdge(scriptNode, paramNode, 1, "hasParameter"))
                                }
                            }

                            val constructedTypes =
                                (script.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf())
                                    .map { it.typeOrArrayType }
                            (constructedTypes + constructedTypes.flatMap { it?.actualTypeArguments ?: listOf() })
                                .groupingBy { it }.eachCount().forEach { (otherType, count) ->
                                    otherType?.also {
                                        g.nodes.findById(it.qualifiedName)?.let {
                                            // instantiates
                                            g.edges.add(makeEdge(scriptNode, it, count, "instantiates"))
                                        }
                                    }
                                }
                        }
                    }
                }
            }

            allTypes.forEach { type ->
                g.nodes.findById(type.qualifiedName)?.let { node ->

                    type.typeMembers.filter { it is CtExecutable<*> }.map { it as CtExecutable<*> }.forEach { script ->
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
                            invokedMethods.groupingBy { it.executable }.eachCount().forEach { (method, count) ->
                                g.nodes.findById("${method.declaringType?.qualifiedName}.${method.signature}")?.let {
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
}
