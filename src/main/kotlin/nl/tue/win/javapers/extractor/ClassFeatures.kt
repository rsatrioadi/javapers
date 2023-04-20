package nl.tue.win.javapers.extractor

import spoon.Launcher
import spoon.reflect.CtModel
import spoon.reflect.code.CtIf
import spoon.reflect.code.CtInvocation
import spoon.reflect.code.CtLoop
import spoon.reflect.code.CtSwitch
import spoon.reflect.declaration.CtElement
import spoon.reflect.declaration.CtType
import spoon.reflect.visitor.filter.TypeFilter
import java.awt.Component


class ClassFeatures(val type: CtType<*>, val model: CtModel) {

    private val typeFactory = Launcher().factory.Type()
    private val serializableType by lazy { typeFactory.createReference(java.io.Serializable::class.java) }
    private val collectionType by lazy { typeFactory.createReference(Collection::class.java) }
    private val iterableType by lazy { typeFactory.createReference(Iterable::class.java) }
    private val mapType by lazy { typeFactory.createReference(Map::class.java) }
    private val awtComponentType by lazy { typeFactory.createReference(Component::class.java) }

    val isPublic = type.isPublic

    val isClass = type.isClass
    val isInterface = type.isInterface
    val isAbstract = type.isAbstract
    val isEnum = type.isEnum
    val isStatic = type.isStatic

    val isSerializable by lazy { type.isSubtypeOf(serializableType) }
    val isCollection by lazy { type.isSubtypeOf(collectionType) }
    val isIterable by lazy { type.isSubtypeOf(iterableType) }
    val isMap by lazy { type.isSubtypeOf(mapType) }
    val isAWTComponent by lazy { type.isSubtypeOf(awtComponentType) }

    val namedController = type.simpleName.endsWith("Controller")
    val namedManager = type.simpleName.endsWith("Manager")
    val namedListener = type.simpleName.endsWith("Listener")
    val namedTest = type.simpleName.endsWith("Test") || type.simpleName.startsWith("Test")

    val numFields by lazy { type.fields.count() }
    val numPublicFields by lazy { type.fields.count { it.isPublic } }
    val numPrivateFields by lazy { type.fields.count { it.isPrivate } }
    val numPrimitiveFields by lazy { type.fields.count { it.type.isPrimitive } }
    val numCollectionFields by lazy { type.fields.count { it.type.isSubtypeOf(collectionType) } }
    val numIterableFields by lazy { type.fields.count { it.type.isSubtypeOf(iterableType) } }
    val numMapFields by lazy { type.fields.count { it.type.isSubtypeOf(mapType) } }
    val numAWTComponentFields by lazy { type.fields.count { it.type.isSubtypeOf(awtComponentType) } }

    val ratioPublicFields by lazy { numPublicFields / numFields.toDouble() }
    val ratioPrivateFields by lazy { numPrivateFields / numFields.toDouble() }

    val numMethods by lazy { type.methods.count() }
    val numPublicMethods by lazy { type.methods.count { it.isPublic } }
    val numPrivateMethods by lazy { type.methods.count { it.isPrivate } }
    val numAbstractMethods by lazy { type.methods.count { it.isAbstract } }
    val numGetters by lazy { type.methods.count { it.simpleName.matches(Regex("^(get|is)[A-Z].*")) } }
    val numSetters by lazy { type.methods.count { it.simpleName.matches(Regex("^set[A-Z].*")) } }

    val ratioPublicMethods by lazy { numPublicMethods / numMethods.toDouble() }
    val ratioPrivateMethods by lazy { numPrivateMethods / numMethods.toDouble() }
    val ratioAbstractMethods by lazy { numAbstractMethods / numMethods.toDouble() }
    val ratioGetters by lazy { numGetters / numMethods.toDouble() }
    val ratioSetters by lazy { numSetters / numMethods.toDouble() }

    val ratioGettersToFields by lazy { numGetters / numFields.toDouble() }
    val ratioSettersToFields by lazy { numSetters / numFields.toDouble() }

    val numStatementsInMethods by lazy {
        type.methods
            .flatMap { it.body?.statements?.toList() ?: emptyList() }
            .count()
    }
    val averageStatementsPerMethod by lazy { numStatementsInMethods / numMethods.toDouble() }

    val numParametersInMethods by lazy {
        type.methods
            .flatMap { it.parameters }
            .count()
    }
    val averageParametersPerMethod by lazy { numParametersInMethods / numMethods.toDouble() }

    val numBranchingInMethods by lazy {
        type.methods
            .flatMap { it.getElements(TypeFilter(CtIf::class.java)) }
            .count() + type.methods
            .flatMap { it.getElements(TypeFilter(CtSwitch::class.java)) }
            .count()
    }
    val averageBranchingPerMethod by lazy { numBranchingInMethods / numMethods.toDouble() }

    val numLoopsInMethods by lazy {
        type.methods
            .flatMap { it.getElements(TypeFilter(CtLoop::class.java)) }
            .count()
    }
    val averageLoopsPerMethod by lazy { numLoopsInMethods / numMethods.toDouble() }

    val accessesIO by lazy {
        type.methods
            .flatMap { it.getElements(TypeFilter(CtInvocation::class.java)) }
            .mapNotNull { it.target?.type?.qualifiedName }
            .any {
                it.startsWith("java.io")
                        || it.startsWith("java.nio")
                        || it.startsWith("org.apache.commons.io")
                        || it.startsWith("com.google.common.io")
            }
    }

    val maxLoopDepth by lazy {
        type.methods
            .filter {
                return@filter !it.isAbstract
            }.maxOfOrNull { loopDepth(it.body) } ?: 0
        //.max()
    }
}

fun loopDepth(element: CtElement): Int {
    val loops = element.getElements(TypeFilter(CtLoop::class.java))
    return if (loops.isEmpty()) {
        0
    } else {
        val ret = 1 + (loops
            .filter { it.body != null }
            .maxOfOrNull { loopDepth(it.body) } ?: 0)
        ret
    }
}