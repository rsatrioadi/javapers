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
import spoon.reflect.reference.CtArrayTypeReference
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

            model.allPackages
                .filter { !it.isUnnamedPackage }
                .forEach { pkg ->
                    // Containers
                    makeNode(pkg.qualifiedName, "Container", simpleName = pkg.simpleName)
                        .let { node ->
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

            val allTypes = model.allTypes.flatMap { allTypesForReal(it) }

            allTypes.forEach { type ->
                // Structure
                makeNode(type.qualifiedName, "Structure", simpleName = type.simpleName).let { node ->
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
                            g.edges.add(makeEdge(node, nestedTypeNode, 1, "nests"))
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

                                        g.nodes.findById(typeArg.qualifiedName)?.let { fieldTypeNode ->
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

                    // Scripts
                    type.typeMembers
                        .filter { it is CtExecutable<*> }
                        .map { it as CtExecutable<*> }
                        .forEach { script ->
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
                                                g.edges.add(
                                                    makeEdge(
                                                        scriptNode,
                                                        fieldTypeNode,
                                                        count,
                                                        "returnType"
                                                    ).also { edge ->
                                                        edge["kind"] = "type argument"
                                                    })
                                            }

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

                                        if (param.type.isArray) {

                                            g.nodes.findById((param.type as CtArrayTypeReference).arrayType.qualifiedName)
                                                ?.let { paramTypeNode ->
                                                    // Variable-type-Type
                                                    g.edges.add(
                                                        makeEdge(
                                                            paramNode,
                                                            paramTypeNode,
                                                            1,
                                                            "type"
                                                        ).also { edge ->
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
                                                        g.edges.add(
                                                            makeEdge(
                                                                paramNode,
                                                                paramTypeNode,
                                                                count,
                                                                "type"
                                                            ).also { edge ->
                                                                edge["kind"] = "type argument"
                                                            })
                                                    }

                                                }

                                        }

                                        // hasParameter
                                        g.edges.add(makeEdge(scriptNode, paramNode, 1, "hasParameter"))
                                    }
                                }

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
                                            g.edges.add(
                                                makeEdge(
                                                    scriptNode,
                                                    constructedTypeNode,
                                                    count,
                                                    "instantiates"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                }
            }

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
}
