package com.github.mictaege.arete_ktx

import com.github.mictaege.arete.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.Extension
import kotlin.reflect.KClass

/** A node in the Kotlin DSL spec tree. */
class KtxNode(
    val type: String,
    val name: String,
    val annotations: MutableMap<Class<out Annotation>, Map<String, Any?>> = mutableMapOf(),
    val children: MutableList<KtxNode> = mutableListOf(),
    val body: (() -> Unit)? = null,
    var preBody: (() -> Unit)? = null,
    var disabledReason: String? = null,
    val narrativeAttachments: MutableList<Triple<String, String, ByteArray>> = mutableListOf(),
    val extensions: MutableList<Extension> = mutableListOf(),
) {

    fun annotate(annotation: Class<out Annotation>, attrs: Map<String, Any?>) {
        annotations[annotation] = attrs
    }
}

internal fun String.prefixed(prefix: String): String =
    if (startsWith(prefix)) this else prefix + this

/** An external reference of a node: either a composed annotation (e.g. `@GithubRef("14")`) or a plain `@Ref`. */
sealed interface KtxRef
data class AnnotationRef(val annotation: KClass<out Annotation>, val value: String) : KtxRef
data class PlainRef(val value: String, val url: String = "") : KtxRef

fun ref(annotation: KClass<out Annotation>, value: String) = AnnotationRef(annotation, value)
fun ref(value: String, url: String = "") = PlainRef(value, url)

internal fun String.capitalizedFirst(): String =
    if (isEmpty()) this else first().uppercaseChar() + substring(1)

@DslMarker
annotation class AreteKtxDsl

@AreteKtxDsl
abstract class KtxScope {
    val nodes = mutableListOf<KtxNode>()
    internal var target: KtxNode? = null
    internal fun add(node: KtxNode) { nodes.add(node) }

    /** Disable the enclosing container (like JUnit `@Disabled`); with [hidden] the element
     *  is hidden from the report instead of shown as skipped (like arete's `@HiddenIfDisabled`). */
    fun disabled(reason: String, hidden: Boolean = false) {
        val t = target ?: return
        t.disabledReason = reason
        t.annotate(org.junit.jupiter.api.Disabled::class.java, mapOf("value" to reason))
        if (hidden) t.annotate(com.github.mictaege.arete.HiddenIfDisabled::class.java, emptyMap())
    }

    /** Register JUnit Jupiter [Extension] classes declaratively on the enclosing container
     *  (like Java's `@ExtendWith`). Each class is instantiated via its no-arg constructor.
     *  Extensions apply to this container's whole subtree; child registrations add to
     *  (not replace) parent ones. */
    fun extendWith(vararg ext: KClass<out Extension>) {
        target?.extensions?.addAll(ext.map { it.java.getDeclaredConstructor().newInstance() })
    }

    /** Register a JUnit Jupiter [Extension] programmatically on the enclosing container
     *  (like Java's `@RegisterExtension`, allowing configured instances). */
    fun registerExtension(ext: Extension) {
        target?.extensions?.add(ext)
    }
}

/** Scope of a container node (spec, feature, scenario, describe, journey): documentable. */
@AreteKtxDsl
abstract class DocumentedScope : KtxScope() {
    /**
     * Attach a file to the narrative of the enclosing node (like Java's NarrativeContext.attach).
     * Called at DSL definition time, so the attachment is queued on the node and published when the
     * engine executes it. Use [StepBodyScope.attachToNarrative] inside a step for immediate publishing.
     */
    fun attachToNarrative(name: String, mediaType: org.junit.jupiter.api.MediaType, bytes: ByteArray) {
        target?.narrativeAttachments?.add(Triple(name, mediaType.toString(), bytes))
    }

    /** Document the enclosing node: narrative, diagrams, media, references, tags (like the Java annotations). */
    fun doc(
        narrative: String? = null,
        plantUml: List<String> = emptyList(),
        imageResourcePath: List<String> = emptyList(),
        attachmentResourcePath: List<String> = emptyList(),
        seeAlso: List<KClass<*>> = emptyList(),
        refs: List<KtxRef> = emptyList(),
        tags: List<KClass<out Annotation>> = emptyList(),
    ) {
        val t = target ?: return
        seeAlso.forEach { t.annotations[SeeAlso::class.java] = mapOf("value" to it.java) }
        if (narrative != null || plantUml.isNotEmpty() || imageResourcePath.isNotEmpty() || attachmentResourcePath.isNotEmpty()) {
            t.annotations[Narrative::class.java] = mapOf(
                "value" to arrayOf(narrative ?: ""),
                "imageResourcePath" to imageResourcePath.toTypedArray(),
                "plantUml" to plantUml.toTypedArray(),
                "attachmentResourcePath" to attachmentResourcePath.toTypedArray(),
            )
        }
        refs.forEach {
            if (it is AnnotationRef) t.annotations[it.annotation.java] = mapOf("value" to it.value)
        }
        val plain = refs.filterIsInstance<PlainRef>()
        when (plain.size) {
            0 -> {}
            1 -> t.annotations[Ref::class.java] = mapOf("value" to plain[0].value, "url" to plain[0].url)
            else -> t.annotations[Refs::class.java] = mapOf(
                "value" to plain.map { mapOf("value" to it.value, "url" to it.url) }.toTypedArray()
            )
        }
        tags.forEach { t.annotations[it.java] = emptyMap() }
    }
}

/** Scope of a container that may hold example tables: feature, scenario, describe. */
@AreteKtxDsl
abstract class ExamplesScope : DocumentedScope() {
    fun examples(name: String, pattern: String, source: ExampleSourceScope.() -> Unit, test: StepBodyScope.(List<Any?>) -> Unit) {
        val container = KtxNode(
            "examples", name.prefixed("Examples: "),
            mutableMapOf(Examples::class.java to mapOf("desc" to name, "pattern" to pattern))
        )
        ExampleSourceScope().apply(source).rows.forEachIndexed { idx, row ->
            val label = (row.firstOrNull() as? String)?.let { java.text.MessageFormat(it, java.util.Locale.ROOT).format(arrayOf(idx + 1)) } ?: ""
            val display = label + java.text.MessageFormat(pattern, java.util.Locale.ROOT).format(
                row.drop(1).map { (it as? KtxExampleParam<*>)?.toString() ?: it }.toTypedArray()
            )
            container.children.add(
                KtxNode("example", display, mutableMapOf(Examples::class.java to emptyMap()),
                    body = { StepBodyScope().apply { test(row.map { (it as? KtxExampleParam<*>)?.value ?: it }) } })
            )
        }
        add(container)
    }

    fun exampleGrid(name: String, columns: Array<String>, source: ExampleGridScope.() -> Unit, test: StepBodyScope.(List<Any?>) -> Unit) {
        val container = KtxNode(
            "exampleGrid", name,
            mutableMapOf(ExampleGrid::class.java to mapOf("desc" to name, "columns" to columns))
        )
        ExampleGridScope().apply(source).rows.forEach { row ->
            container.children.add(
                KtxNode("example",
                    "| " + row.joinToString(" | ") { (it as? KtxExampleParam<*>)?.toString() ?: it.toString() } + " |",
                    mutableMapOf(ExampleGrid::class.java to emptyMap()),
                    body = { StepBodyScope().apply { test(row.map { (it as? KtxExampleParam<*>)?.value ?: it }) } })
            )
        }
        add(container)
    }

    fun exampleCsv(name: String, columns: Array<String>, csvData: String, delimiter: Char = ',', test: StepBodyScope.(List<Any?>) -> Unit) {
        val container = KtxNode(
            "exampleCsv", name,
            mutableMapOf(ExampleCsv::class.java to mapOf("desc" to name, "columns" to columns, "delimiter" to delimiter, "csvData" to csvData))
        )
        csvData.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { idx, line ->
            val row = line.split(delimiter).map { it.trim() }
            container.children.add(
                KtxNode("example", "| " + row.joinToString(" | ") + " |", mutableMapOf(ExampleCsv::class.java to emptyMap()),
                    body = { StepBodyScope().apply { test(row) } })
            )
        }
        add(container)
    }
}

/** Body of a leaf step (given/when/then/journey step/example row): only attachments are allowed here. */
@AreteKtxDsl
class StepBodyScope {
    /** Attach a file to the currently executing step (like Java's StepContext.attach). */
    fun attachToStep(name: String, mediaType: org.junit.jupiter.api.MediaType, bytes: ByteArray) {
        KtxStepContext.current().attach(name, mediaType, bytes)
    }

    /** Attach a file to the narrative of the enclosing spec (like Java's NarrativeContext.attach). */
    fun attachToNarrative(name: String, mediaType: org.junit.jupiter.api.MediaType, bytes: ByteArray) {
        KtxNarrativeContext.current().attach(name, mediaType, bytes)
    }
}

class SpecScope : DocumentedScope() {
    fun feature(name: String, block: FeatureScope.() -> Unit) {
        val node = KtxNode("feature", name.prefixed("Feature: "), mutableMapOf(Feature::class.java to mapOf("desc" to name)))
        FeatureScope().apply { target = node; block() }.nodes.forEach { node.children.add(it) }
        add(node)
    }

    fun scenario(name: String, block: ScenarioScope.() -> Unit) {
        val attrs = mutableMapOf<String, Any?>("desc" to name)
        val node = KtxNode("scenario", name.prefixed("Scenario: "), mutableMapOf(Scenario::class.java to attrs))
        ScenarioScope().apply { target = node; block() }.ordered().forEach { node.children.add(it) }
        add(node)
    }

    fun describe(name: String, block: DescribeScope.() -> Unit) {
        val node = KtxNode("describe", name.prefixed("Describe: "), mutableMapOf(Describe::class.java to mapOf("desc" to name)))
        DescribeScope().apply { target = node; block() }.nodes.forEach { node.children.add(it) }
        add(node)
    }

    fun journey(name: String, block: JourneyScope.() -> Unit) {
        val node = KtxNode("journey", name.prefixed("Journey: "), mutableMapOf(Journey::class.java to mapOf("desc" to name)))
        JourneyScope().apply { target = node; block() }.nodes.sortedBy { it.annotations[Step::class.java]?.get("order") as? Int ?: 0 }
            .forEach { node.children.add(it) }
        add(node)
    }

    fun variableJourney(name: String, block: VariableJourneyScope.() -> Unit) {
        val node = KtxNode("variableJourney", name.prefixed("Journey: "), mutableMapOf(VariableJourney::class.java to mapOf("desc" to name)))
        VariableJourneyScope().apply { target = node; block() }.let { scope ->
            // group steps by variant: steps without variant run in every variant
            val common = scope.nodes.filter { (it.annotations[Step::class.java]?.get("variant") as? String).isNullOrEmpty() }
            val variants = scope.nodes.mapNotNull { it.annotations[Step::class.java]?.get("variant") as? String }.distinct()
            variants.forEach { variant ->
                val vNode = KtxNode("journey", "Variant: $variant", mutableMapOf(Journey::class.java to mapOf("desc" to "Variant: $variant")))
                vNode.preBody = scope.beforeVariant
                (common + scope.nodes.filter { it.annotations[Step::class.java]?.get("variant") == variant })
                    .sortedBy { it.annotations[Step::class.java]?.get("order") as? Int ?: 0 }
                    .forEach { vNode.children.add(it) }
                node.children.add(vNode)
            }
            if (variants.isEmpty()) {
                scope.nodes.sortedBy { it.annotations[Step::class.java]?.get("order") as? Int ?: 0 }
                    .forEach { node.children.add(it) }
            }
        }
        add(node)
    }
}

class VariableJourneyScope : DocumentedScope() {
    var beforeVariant: (() -> Unit)? = null
    private var orderCounter = 0
    fun beforeVariant(block: () -> Unit) { beforeVariant = block }
    fun step(name: String = "", phase: String? = null, variant: String? = null, block: StepBodyScope.() -> Unit) {
        val order = ++orderCounter
        val attrs = mutableMapOf<String, Any?>("order" to order, "value" to order)
        if (phase != null) attrs["phase"] = phase
        if (variant != null) attrs["variant"] = variant
        val display = (if (phase != null) "$phase " else "") + "⏵️ " + name.ifEmpty { "step $order" }.capitalizedFirst()
        add(KtxNode("step", display, mutableMapOf(Step::class.java to attrs), body = { StepBodyScope().apply(block) }))
    }
}

class FeatureScope : ExamplesScope() {
    fun scenario(name: String, block: ScenarioScope.() -> Unit) {
        val annotations: MutableMap<Class<out Annotation>, Map<String, Any?>> =
            mutableMapOf(Scenario::class.java to mapOf("desc" to name))
        val node = KtxNode("scenario", name.prefixed("Scenario: "), annotations)
        ScenarioScope().apply { target = node; block() }.ordered().forEach { node.children.add(it) }
        add(node)
    }

    fun journey(name: String, block: JourneyScope.() -> Unit) {
        val node = KtxNode("journey", name.prefixed("Journey: "), mutableMapOf(Journey::class.java to mapOf("desc" to name)))
        JourneyScope().apply { target = node; block() }.nodes.sortedBy { it.annotations[Step::class.java]?.get("order") as? Int ?: 0 }
            .forEach { node.children.add(it) }
        add(node)
    }
}

class ScenarioScope : ExamplesScope() {
    private var seqCounter = 0
    private val stepCounters = mutableMapOf<String, Int>()

    private fun stepPrefix(kind: String, type: String): String {
        val step = (stepCounters[type] ?: 0) + 1
        stepCounters[type] = step
        return if (step == 1) "$kind " else "And "
    }

    private fun addStep(type: String, kind: String, annotation: Class<out Annotation>, name: String, block: StepBodyScope.() -> Unit) {
        val seq = ++seqCounter
        val step = stepCounters[type] ?: 1
        add(KtxNode(type, stepPrefix(kind, type) + name.ifEmpty { kind.lowercase() }.lowercase(),
            mutableMapOf(annotation to mapOf("seq" to seq, "step" to step, "value" to 1)), body = { StepBodyScope().apply(block) }))
    }

    fun given(name: String = "", block: StepBodyScope.() -> Unit) = addStep("given", "Given", Given::class.java, name, block)

    fun `when`(name: String = "", block: StepBodyScope.() -> Unit) = addStep("when", "When", When::class.java, name, block)

    fun then(name: String = "", block: StepBodyScope.() -> Unit) = addStep("then", "Then", Then::class.java, name, block)

    internal fun ordered(): List<KtxNode> = nodes
}

/** One example parameter: a value plus the converter used to render it (like Java's ExampleParam). */
class KtxExampleParam<T>(val value: T, private val converter: (T) -> String) {
    override fun toString(): String = converter(value)
}

interface ExampleScopeMixin {
    fun <T> given(value: T) = KtxExampleParam(value) { it?.toString() ?: "" }
    fun <T> given(value: T, string: String) = KtxExampleParam(value) { string }
    fun <T> given(value: T, converter: (T) -> String) = KtxExampleParam(value, converter)
    fun <T> `when`(value: T) = KtxExampleParam(value) { it?.toString() ?: "" }
    fun <T> `when`(value: T, string: String) = KtxExampleParam(value) { string }
    fun <T> `when`(value: T, converter: (T) -> String) = KtxExampleParam(value, converter)
    fun <T> then(value: T) = KtxExampleParam(value) { it?.toString() ?: "" }
    fun <T> then(value: T, string: String) = KtxExampleParam(value) { string }
    fun <T> then(value: T, converter: (T) -> String) = KtxExampleParam(value, converter)
    fun <T> expect(value: T) = KtxExampleParam(value) { it?.toString() ?: "" }
    fun <T> expect(value: T, string: String) = KtxExampleParam(value) { string }
    fun <T> expect(value: T, converter: (T) -> String) = KtxExampleParam(value, converter)
}

@AreteKtxDsl
class ExampleSourceScope : ExampleScopeMixin {
    val rows = mutableListOf<List<Any?>>()
    fun example(label: String, vararg params: KtxExampleParam<*>) { rows.add(listOf(label) + params.toList()) }
}

@AreteKtxDsl
class ExampleGridScope : ExampleScopeMixin {
    val rows = mutableListOf<List<Any?>>()
    fun row(vararg cells: KtxExampleParam<*>) { rows.add(cells.toList()) }
}



class DescribeScope : ExamplesScope() {
    fun describe(name: String, block: DescribeScope.() -> Unit) {
        val node = KtxNode("describe", name.prefixed("Describe: "), mutableMapOf(Describe::class.java to mapOf("desc" to name)))
        DescribeScope().apply { target = node; block() }.nodes.forEach { node.children.add(it) }
        add(node)
    }

    fun itShould(name: String, block: StepBodyScope.() -> Unit) =
        add(KtxNode("itShould", name.prefixed("It should "), mutableMapOf(ItShould::class.java to mapOf("desc" to name)), body = { StepBodyScope().apply(block) }))
}

class JourneyScope : DocumentedScope() {
    private var orderCounter = 0
    fun step(name: String = "", phase: String? = null, variant: String? = null, block: StepBodyScope.() -> Unit) {
        val order = ++orderCounter
        val attrs = mutableMapOf<String, Any?>("order" to order, "value" to order)
        if (phase != null) attrs["phase"] = phase
        if (variant != null) attrs["variant"] = variant
        val display = (if (phase != null) "$phase " else "") + "⏵️ " + name.ifEmpty { "step $order" }.capitalizedFirst()
        add(KtxNode("step", display, mutableMapOf(Step::class.java to attrs), body = { StepBodyScope().apply(block) }))
    }
}

/**
 * Base class for a Kotlin DSL spec. Pass the spec definitions as the constructor lambda:
 * `class MySpec : Spec({ spec("My Spec") { ... } })` — the engine picks up
 * everything registered in [specs].
 */
open class Spec(definition: Spec.() -> Unit) {
    val specs = mutableListOf<KtxNode>()

    init {
        definition()
    }

    @Test
    fun `spec run finished`() {}

    fun spec(
        desc: String,
        block: SpecScope.() -> Unit,
    ): KtxNode = com.github.mictaege.arete_ktx.spec(desc, block)
        .also { specs.add(it) }
}

/** Entry point: define a spec inside a [Spec] constructor lambda. */
fun spec(
    desc: String,
    block: SpecScope.() -> Unit,
): KtxNode {
    val node = KtxNode("spec", desc, mutableMapOf(com.github.mictaege.arete.Spec::class.java to mapOf("desc" to desc)))
    SpecScope().apply { target = node; block() }.nodes.forEach { node.children.add(it) }
    return node
}
