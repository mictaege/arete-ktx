package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler
import org.junit.jupiter.api.extension.TestWatcher
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import java.util.Optional

class AreteKtxEngine : TestEngine {

    override fun getId(): String = "arete-ktx"

    override fun discover(request: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val root = EngineDescriptor(uniqueId, "Arete Kotlin DSL")
        request.getSelectorsByType(ClassSelector::class.java).forEach { selector ->
            val cls = selector.javaClass
            if (!Spec::class.java.isAssignableFrom(cls)) return@forEach
            val instance = cls.getDeclaredConstructor().newInstance()
            val specNodes = if (instance is Spec) {
                instance.specs
            } else {
                cls.declaredFields.filter { KtxNode::class.java.isAssignableFrom(it.type) }.map { field ->
                    field.isAccessible = true
                    field.get(instance) as KtxNode
                }
            }
            val classDescriptor = object : AbstractTestDescriptor(
                uniqueId.append("class", cls.name),
                specNodes.firstOrNull()?.name ?: cls.simpleName
            ) {
                override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER
                override fun getSource(): Optional<TestSource> = Optional.of(ClassSource.from(cls))
            }
            specNodes.forEach { classDescriptor.addChild(it.toDescriptor(classDescriptor.uniqueId, cls)) }
            root.addChild(classDescriptor)
        }
        return root
    }

    override fun execute(request: ExecutionRequest) {
        val listener = request.engineExecutionListener
        KtxEngineBridge.listener = listener
        try {
            execute(request.rootTestDescriptor, listener)
        } finally {
            KtxEngineBridge.listener = null
        }
    }

    private fun execute(descriptor: TestDescriptor, listener: EngineExecutionListener, inheritedExtensions: List<Extension> = emptyList()) {
        listener.dynamicTestRegistered(descriptor)
        val node = (descriptor as? KtxDescriptor)?.node
        val allExtensions = inheritedExtensions + (node?.extensions ?: emptyList())
        val reason = node?.disabledReason
        if (reason != null) {
            val hidden = node?.annotations?.containsKey(com.github.mictaege.arete.HiddenIfDisabled::class.java) == true
            if (!hidden) {
                listener.executionSkipped(descriptor, reason)
            }
            // Dispatch TestWatcher.testDisabled for extensions (leaf nodes only, like JUnit)
            val disabledCtx = node?.let { KtxExtensionContext(descriptor, it) }
            if (disabledCtx != null && node?.body != null) {
                allExtensions.filterIsInstance<TestWatcher>().forEach {
                    runCatching { it.testDisabled(disabledCtx, java.util.Optional.ofNullable(reason)) }
                }
            }
            return
        }
        listener.executionStarted(descriptor)
        val ctx = node?.let { KtxExtensionContext(descriptor, it) }
        val isContainer = node != null && descriptor.children.isNotEmpty()
        val isLeaf = node?.body != null
        val result = try {
            if (isContainer && ctx != null) {
                allExtensions.filterIsInstance<BeforeAllCallback>().forEach { it.beforeAll(ctx) }
            }
            node?.preBody?.invoke()
            if (isLeaf && ctx != null) {
                allExtensions.filterIsInstance<BeforeEachCallback>().forEach { it.beforeEach(ctx) }
                KtxStepContext.setCurrent(descriptor)
                try {
                    node?.body?.invoke()
                } finally {
                    KtxStepContext.clearCurrent()
                }
                allExtensions.filterIsInstance<AfterEachCallback>().reversed().forEach { it.afterEach(ctx) }
            }
            // Bind the narrative context to the innermost container currently executing, so
            // attachToNarrative targets that container's narrative (like Java's nested-class
            // NarrativeContextExtension).
            val previousNarrative = if (isContainer) KtxNarrativeContext.replaceCurrent(node) else null
            try {
                descriptor.children.forEach { execute(it, listener, allExtensions) }
            } finally {
                if (isContainer) KtxNarrativeContext.setCurrent(previousNarrative)
            }
            if (isContainer && ctx != null) {
                allExtensions.filterIsInstance<AfterAllCallback>().reversed().forEach { it.afterAll(ctx) }
            }
            TestExecutionResult.successful()
        } catch (t: Throwable) {
            var handled: Throwable? = t
            if (isLeaf && ctx != null) {
                for (handler in allExtensions.filterIsInstance<TestExecutionExceptionHandler>()) {
                    try {
                        handler.handleTestExecutionException(ctx, handled!!)
                        handled = null
                        break
                    } catch (rethrown: Throwable) {
                        handled = rethrown
                    }
                }
            }
            if (handled != null) {
                handled.stackTrace = handled.stackTrace.filter { !it.className.startsWith("com.github.mictaege.arete_ktx.") }.toTypedArray()
                TestExecutionResult.failed(handled)
            } else {
                TestExecutionResult.aborted(org.opentest4j.TestAbortedException("Step aborted by extension"))
            }
        }
        // Publish this node's narrative attachments while the node itself is the current
        // execution, so the launcher keys them under the node's uniqueId (its report page).
        node?.narrativeAttachments?.forEach { (name, mediaType, bytes) ->
            KtxNarrativeContext.publish(descriptor, name, mediaType, bytes)
        }
        // Dispatch TestWatcher callbacks with the final result (leaf nodes only, like JUnit)
        if (ctx != null && isLeaf) {
            val watchers = allExtensions.filterIsInstance<TestWatcher>()
            when (result.status) {
                TestExecutionResult.Status.SUCCESSFUL ->
                    watchers.forEach { runCatching { it.testSuccessful(ctx) } }
                TestExecutionResult.Status.FAILED ->
                    watchers.forEach { runCatching { it.testFailed(ctx, result.throwable.orElse(null)) } }
                TestExecutionResult.Status.ABORTED ->
                    watchers.forEach { runCatching { it.testAborted(ctx, result.throwable.orElse(null)) } }
            }
        }
        listener.executionFinished(descriptor, result)
    }

    private fun KtxNode.toDescriptor(uniqueId: UniqueId, specClass: Class<*>? = null,
                                     inheritedTags: Set<org.junit.platform.engine.TestTag> = emptySet()): TestDescriptor {
        val segmentType = when (type) {
            "given" -> "given"; "when" -> "when"; "then" -> "then"
            else -> type
        }
        val childUniqueId = uniqueId.append(segmentType, name)
        // The spec node carries a real ClassSource so IDEs can navigate to the spec class and
        // offer run gutter icons; its arete metadata is resolved via KtxNodeRegistry.
        val source: TestSource = if (type == "spec" && specClass != null) {
            ClassSource.from(specClass)
        } else {
            KtxTestSource(type, annotations, specClass)
        }
        val ownTags = annotations.keys
            .filter { it.isAnnotationPresent(org.junit.jupiter.api.Tag::class.java) }
            .map { org.junit.platform.engine.TestTag.create(it.getAnnotation(org.junit.jupiter.api.Tag::class.java).value) }
        val descriptor = KtxDescriptor(childUniqueId, name, this, source, inheritedTags + ownTags)
        KtxNodeRegistry.register(childUniqueId.toString(), this)
        children.forEach { descriptor.addChild(it.toDescriptor(descriptor.uniqueId, specClass, inheritedTags + ownTags)) }
        return descriptor
    }
}

class KtxDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    val node: KtxNode,
    private val source: TestSource,
    private val tags: Set<org.junit.platform.engine.TestTag> = emptySet(),
) : AbstractTestDescriptor(uniqueId, displayName) {

    override fun getSource(): Optional<TestSource> = Optional.of(source)

    override fun getTags(): Set<org.junit.platform.engine.TestTag> = tags

    override fun getType(): TestDescriptor.Type =
        if (node.body != null) TestDescriptor.Type.TEST else TestDescriptor.Type.CONTAINER
}
