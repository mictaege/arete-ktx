package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExecutableInvoker
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ExtensionContext.Store
import org.junit.jupiter.api.extension.ExtensionContext.StoreScope

import org.junit.jupiter.api.function.ThrowingConsumer
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import java.lang.reflect.AnnotatedElement
import java.nio.file.Path
import java.util.Optional
import java.util.function.Function

/**
 * Minimal [ExtensionContext] facade backed by a [KtxNode] and its [TestDescriptor].
 * Exposes the descriptor, display name, unique id, and a per-node [Store] so that
 * registered extensions can share state (like `@RegisterExtension` in Java).
 */
class KtxExtensionContext(
    private val descriptor: TestDescriptor,
    private val node: KtxNode,
) : ExtensionContext {

    private val store = KtxStore()

    override fun getTestClass(): Optional<Class<*>> =
        descriptor.source.orElse(null)?.let {
            if (it is org.junit.platform.engine.support.descriptor.ClassSource) Optional.of(it.javaClass) else Optional.empty()
        } ?: Optional.empty()

    override fun getTestInstance(): Optional<Any> = Optional.empty()

    override fun getTestInstances(): Optional<org.junit.jupiter.api.extension.TestInstances> = Optional.empty()

    override fun getDisplayName(): String = descriptor.displayName

    override fun getUniqueId(): String = descriptor.uniqueId.toString()

    override fun getTags(): Set<String> = emptySet()

    override fun getElement(): Optional<AnnotatedElement> = Optional.empty()

    override fun getTestMethod(): Optional<java.lang.reflect.Method> = Optional.empty()

    override fun getTestInstanceLifecycle(): Optional<org.junit.jupiter.api.TestInstance.Lifecycle> = Optional.empty()

    override fun getExecutionMode(): org.junit.jupiter.api.parallel.ExecutionMode =
        org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT

    override fun getConfigurationParameter(key: String): Optional<String> = Optional.empty()

    override fun <T : Any> getConfigurationParameter(key: String, transformer: Function<in String, out T?>): Optional<T> = Optional.empty()

    override fun getRoot(): ExtensionContext = this

    override fun getParent(): Optional<ExtensionContext> = Optional.empty()

    override fun getStore(namespace: Namespace): Store = store

    override fun getStore(scope: StoreScope, namespace: Namespace): Store = store

    override fun getRequiredTestClass(): Class<*> =
        getTestClass().orElseThrow { IllegalStateException("No test class available") }

    override fun getRequiredTestMethod(): java.lang.reflect.Method =
        getTestMethod().orElseThrow { IllegalStateException("No test method available") }

    override fun getEnclosingTestClasses(): List<Class<*>> = emptyList()

    override fun publishReportEntry(map: Map<String, String>) {
        // no-op: report entries are handled by the arete-gradle report plugin
    }

    override fun publishFile(name: String, mediaType: org.junit.jupiter.api.MediaType, action: ThrowingConsumer<Path>) {
        val dir = java.nio.file.Files.createTempDirectory("arete-ktx-ext-")
        val tmp = dir.resolve(name)
        action.accept(tmp)
        KtxEngineBridge.publishFile(descriptor, org.junit.platform.engine.reporting.FileEntry.from(tmp, mediaType.toString()))
    }

    override fun publishDirectory(name: String, action: ThrowingConsumer<Path>) {
        val dir = java.nio.file.Files.createTempDirectory("arete-ktx-ext-dir-")
        action.accept(dir)
        // Publish each file in the directory
        dir.toFile().listFiles()?.forEach { file ->
            KtxEngineBridge.publishFile(descriptor, org.junit.platform.engine.reporting.FileEntry.from(file.toPath(), "application/octet-stream"))
        }
    }

    override fun getExecutionException(): Optional<Throwable> = Optional.empty()

    override fun getExecutableInvoker(): ExecutableInvoker =
        throw UnsupportedOperationException("ExecutableInvoker is not supported in the Kotlin DSL")

    /** Per-node [Store] backed by a simple map. */
    private class KtxStore : Store {
        private val map = mutableMapOf<Any, Any?>()

        override fun get(key: Any): Any? = map[key]

        override fun <V : Any> get(key: Any, requiredType: Class<V>): V? {
            @Suppress("UNCHECKED_CAST")
            return map[key] as? V
        }

        override fun <K : Any, V> getOrComputeIfAbsent(key: K, defaultCreator: Function<in K, out V>): Any? =
            map.getOrPut(key) { defaultCreator.apply(key) }

        override fun <K : Any, V : Any> computeIfAbsent(key: K, defaultCreator: Function<in K, out V>): Any =
            map.getOrPut(key) { defaultCreator.apply(key) }!!

        override fun <K : Any, V> getOrComputeIfAbsent(
            key: K,
            defaultCreator: Function<in K, out V>,
            requiredType: Class<V>,
        ): V? {
            @Suppress("UNCHECKED_CAST")
            return map.getOrPut(key) { defaultCreator.apply(key) } as V?
        }

        override fun <K : Any, V : Any> computeIfAbsent(
            key: K,
            defaultCreator: Function<in K, out V>,
            requiredType: Class<V>,
        ): V {
            @Suppress("UNCHECKED_CAST")
            return map.getOrPut(key) { defaultCreator.apply(key) } as V
        }

        override fun put(key: Any, value: Any?) { map[key] = value }

        override fun remove(key: Any): Any? = map.remove(key)

        override fun <V : Any> remove(key: Any, requiredType: Class<V>): V? {
            @Suppress("UNCHECKED_CAST")
            return map.remove(key) as? V
        }
    }
}
