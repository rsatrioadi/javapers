package nl.tue.win.javapers.extractor

import spoon.Launcher
import spoon.reflect.CtModel
import spoon.reflect.code.*
import spoon.reflect.declaration.CtConstructor
import spoon.reflect.declaration.CtExecutable
import spoon.reflect.declaration.CtMethod
import spoon.reflect.declaration.CtModifiable
import spoon.reflect.visitor.filter.TypeFilter
import java.awt.Component


class MethodFeatures(val script: CtExecutable<*>, val model: CtModel) {

    private val typeFactory = Launcher().factory.Type()
    private val serializableType by lazy { typeFactory.createReference(java.io.Serializable::class.java) }
    private val collectionType by lazy { typeFactory.createReference(Collection::class.java) }
    private val iterableType by lazy { typeFactory.createReference(Iterable::class.java) }
    private val mapType by lazy { typeFactory.createReference(Map::class.java) }
    private val awtComponentType by lazy { typeFactory.createReference(Component::class.java) }

    val isPublic by lazy { script is CtModifiable && script.isPublic }

    val isMethod by lazy { script is CtMethod }
    val isConstructor by lazy { script is CtConstructor }
    val isLambda by lazy { script is CtLambda }
    val isStatic by lazy { script is CtModifiable && script.isStatic }

    val isGetter by lazy { script is CtMethod && script.simpleName.matches(Regex("^(get|is)[A-Z].*")) }
    val isSetter by lazy { script is CtMethod && script.simpleName.matches(Regex("^set[A-Z].*")) }

    val constructsAWTComponent by lazy {
        (script.getElements(TypeFilter(CtConstructorCall::class.java))?.toList() ?: listOf())
            .mapNotNull { it.typeOrArrayType }.any { it.isSubtypeOf(awtComponentType) }
    }
    val returnsCollection by lazy { script.type.isSubtypeOf(collectionType) }
    val returnsIterable by lazy { script.type.isSubtypeOf(iterableType) }
    val returnsMap by lazy { script.type.isSubtypeOf(mapType) }

//    val isSerializable by lazy { script.isSubtypeOf(serializableType) }
//    val isCollection by lazy { script.isSubtypeOf(collectionType) }
//    val isIterable by lazy { script.isSubtypeOf(iterableType) }
//    val isMap by lazy { script.isSubtypeOf(mapType) }
//    val isAWTComponent by lazy { script.isSubtypeOf(awtComponentType) }
//
//    val namedController = script.simpleName.endsWith("Controller")
//    val namedManager = script.simpleName.endsWith("Manager")
//    val namedListener = script.simpleName.endsWith("Listener")
//    val namedTest = script.simpleName.endsWith("Test") || script.simpleName.startsWith("Test")
//
//    val numFields by lazy { script.fields.count() }
//    val numPublicFields by lazy { script.fields.count { it.isPublic } }
//    val numPrivateFields by lazy { script.fields.count { it.isPrivate } }
//    val numPrimitiveFields by lazy { script.fields.count { it.type.isPrimitive } }
//    val numCollectionFields by lazy { script.fields.count { it.type.isSubtypeOf(collectionType) } }
//    val numIterableFields by lazy { script.fields.count { it.type.isSubtypeOf(iterableType) } }
//    val numMapFields by lazy { script.fields.count { it.type.isSubtypeOf(mapType) } }
//    val numAWTComponentFields by lazy { script.fields.count { it.type.isSubtypeOf(awtComponentType) } }
//
//    val ratioPublicFields by lazy { numPublicFields / numFields.toDouble() }
//    val ratioPrivateFields by lazy { numPrivateFields / numFields.toDouble() }
//
//    val numMethods by lazy { script.methods.count() }
//    val numPublicMethods by lazy { script.methods.count { it.isPublic } }
//    val numPrivateMethods by lazy { script.methods.count { it.isPrivate } }
//    val numAbstractMethods by lazy { script.methods.count { it.isAbstract } }
//    val numGetters by lazy { script.methods.count { it.simpleName.matches(Regex("^(get|is)[A-Z].*")) } }
//    val numSetters by lazy { script.methods.count { it.simpleName.matches(Regex("^set[A-Z].*")) } }
//
//    val ratioPublicMethods by lazy { numPublicMethods / numMethods.toDouble() }
//    val ratioPrivateMethods by lazy { numPrivateMethods / numMethods.toDouble() }
//    val ratioAbstractMethods by lazy { numAbstractMethods / numMethods.toDouble() }
//    val ratioGetters by lazy { numGetters / numMethods.toDouble() }
//    val ratioSetters by lazy { numSetters / numMethods.toDouble() }
//
//    val ratioGettersToFields by lazy { numGetters / numFields.toDouble() }
//    val ratioSettersToFields by lazy { numSetters / numFields.toDouble() }
//
    val numStatements by lazy {
        (script.body?.statements?.toList() ?: emptyList()).count()
    }
//    val averageStatementsPerMethod by lazy { numStatementsInMethods / numMethods.toDouble() }
//
    val numParameters by lazy {
        script.parameters.count()
    }
//    val averageParametersPerMethod by lazy { numParametersInMethods / numMethods.toDouble() }
//
    val numBranching by lazy {
        script.getElements(TypeFilter(CtIf::class.java))
            .count() + script.getElements(TypeFilter(CtSwitch::class.java))
            .count()
    }
//    val averageBranchingPerMethod by lazy { numBranchingInMethods / numMethods.toDouble() }
//
    val numLoops by lazy {
        script.getElements(TypeFilter(CtLoop::class.java)).count()
    }
//    val averageLoopsPerMethod by lazy { numLoopsInMethods / numMethods.toDouble() }
//
    val accessesIO by lazy {
        script.getElements(TypeFilter(CtInvocation::class.java))
            .mapNotNull { it.target?.type?.qualifiedName }
            .any {
                it.startsWith("java.io")
                        || it.startsWith("java.nio")
                        || it.startsWith("org.apache.commons.io")
                        || it.startsWith("com.google.common.io")
            }
    }
//
//    val maxLoopDepth by lazy {
//        script.methods
//            .filter {
//                return@filter !it.isAbstract
//            }.maxOfOrNull { loopDepth(it.body) } ?: 0
//        //.max()
//    }
}
