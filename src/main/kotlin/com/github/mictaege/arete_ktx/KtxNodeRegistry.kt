package com.github.mictaege.arete_ktx

import java.util.concurrent.ConcurrentHashMap

/**
 * Maps descriptor uniqueIds to their [KtxNode]. The spec node itself carries a plain
 * [org.junit.platform.engine.support.descriptor.ClassSource] so IDEs can navigate to the spec
 * class; arete-gradle resolves the arete annotation metadata for such nodes via this registry.
 */
object KtxNodeRegistry {
    private val byUniqueId = ConcurrentHashMap<String, KtxNode>()

    fun register(uniqueId: String, node: KtxNode) { byUniqueId[uniqueId] = node }

    fun nodeFor(uniqueId: String): KtxNode? = byUniqueId[uniqueId]

    fun clear() { byUniqueId.clear() }
}
