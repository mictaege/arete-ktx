package com.github.mictaege.arete_ktx

import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.reporting.FileEntry
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

    private fun execute(descriptor: TestDescriptor, listener: EngineExecutionListener) {
        listener.dynamicTestRegistered(descriptor)
        val node = (descriptor as? KtxDescriptor)?.node
        val reason = node?.disabledReason
        if (reason != null) {
            val hidden = node?.annotations?.containsKey(com.github.mictaege.arete.HiddenIfDisabled::class.java) == true
            if (!hidden) {
                listener.executionSkipped(descriptor, reason)
                if (node?.body != null) publishScreenshot(descriptor, com.github.mictaege.arete.ScreenshotTaker.TestResult.DISABLED, listener)
            }
            return
        }
        listener.executionStarted(descriptor)
        val result = try {
            node?.preBody?.invoke()
            if (node?.body != null) {
                KtxStepContext.setCurrent(descriptor)
                try {
                    node.body.invoke()
                } finally {
                    KtxStepContext.clearCurrent()
                }
            }
            // Bind the narrative context to the innermost container currently executing, so
            // attachToNarrative targets that container's narrative (like Java's nested-class
            // NarrativeContextExtension).
            val isContainer = node != null && descriptor.children.isNotEmpty()
            val previousNarrative = if (isContainer) KtxNarrativeContext.replaceCurrent(node) else null
            try {
                descriptor.children.forEach { execute(it, listener) }
            } finally {
                if (isContainer) KtxNarrativeContext.setCurrent(previousNarrative)
            }
            TestExecutionResult.successful()
        } catch (t: Throwable) {
            t.stackTrace = t.stackTrace.filter { !it.className.startsWith("com.github.mictaege.arete_ktx.") }.toTypedArray()
            TestExecutionResult.failed(t)
        }
        // Publish this node's narrative attachments while the node itself is the current
        // execution, so the launcher keys them under the node's uniqueId (its report page).
        node?.narrativeAttachments?.forEach { (name, mediaType, bytes) ->
            KtxNarrativeContext.publish(descriptor, name, mediaType, bytes)
        }
        listener.executionFinished(descriptor, result)
        if (node?.body != null) publishScreenshot(descriptor, result.toScreenshotResult(), listener)
    }

    private fun TestExecutionResult.toScreenshotResult() =
        when (status) {
            TestExecutionResult.Status.SUCCESSFUL -> com.github.mictaege.arete.ScreenshotTaker.TestResult.SUCCESS
            TestExecutionResult.Status.ABORTED -> com.github.mictaege.arete.ScreenshotTaker.TestResult.ABORTION
            else -> com.github.mictaege.arete.ScreenshotTaker.TestResult.FAILURE
        }

    private fun publishScreenshot(
        descriptor: TestDescriptor,
        result: com.github.mictaege.arete.ScreenshotTaker.TestResult,
        listener: EngineExecutionListener,
    ) {
        var d: TestDescriptor? = descriptor
        while (d != null) {
            val cfg = (d as? KtxDescriptor)?.node?.screenshot
            if (cfg != null) {
                if (result in cfg.takeWhen) {
                    val bytes = runCatching { cfg.bytes() }.getOrDefault(ByteArray(0))
                    if (bytes.isNotEmpty()) {
                        val name = "${com.github.mictaege.arete.ScreenshotTaker.SCREENSHOT_NAME_PREFIX}.${cfg.extension}"
                        val mediaType = when (cfg.extension.lowercase()) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            else -> "application/octet-stream"
                        }
                        val dir = java.nio.file.Files.createTempDirectory("arete-ktx-screenshot-")
                        val tmp = dir.resolve(name)
                        java.nio.file.Files.write(tmp, bytes)
                        listener.fileEntryPublished(descriptor, FileEntry.from(tmp, mediaType))
                    }
                }
                return
            }
            d = d.parent.orElse(null)
        }
    }

    private fun KtxNode.toDescriptor(uniqueId: UniqueId, specClass: Class<*>? = null): TestDescriptor {
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
        val descriptor = KtxDescriptor(childUniqueId, name, this, source)
        KtxNodeRegistry.register(childUniqueId.toString(), this)
        children.forEach { descriptor.addChild(it.toDescriptor(descriptor.uniqueId, specClass)) }
        return descriptor
    }
}

class KtxDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    val node: KtxNode,
    private val source: TestSource,
) : AbstractTestDescriptor(uniqueId, displayName) {

    override fun getSource(): Optional<TestSource> = Optional.of(source)

    override fun getType(): TestDescriptor.Type =
        if (node.body != null) TestDescriptor.Type.TEST else TestDescriptor.Type.CONTAINER
}
