package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.MediaType
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.reporting.FileEntry

/**
 * Access to the narrative of the currently executing spec, e.g. to attach files to it.
 * Reachable from any step body via [current].
 */
class KtxNarrativeContext private constructor(private val node: KtxNode) {

    companion object {
        const val ATTACHMENT_NAME_PREFIX = "arete-narrative-attach"

        private val CURRENT = ThreadLocal<KtxNode>()

        fun current(): KtxNarrativeContext {
            val n = CURRENT.get() ?: throw IllegalStateException("No spec narrative is currently active on this thread")
            return KtxNarrativeContext(n)
        }

        internal fun setCurrent(node: KtxNode?) { if (node == null) CURRENT.remove() else CURRENT.set(node) }
        internal fun replaceCurrent(node: KtxNode): KtxNode? {
            val previous = CURRENT.get()
            CURRENT.set(node)
            return previous
        }
        internal fun clearCurrent() { CURRENT.remove() }

        internal fun publish(descriptor: TestDescriptor, name: String, mediaType: String, bytes: ByteArray) {
            val fileName = "$ATTACHMENT_NAME_PREFIX.${name.replace(Regex("[/\\\\]"), "_")}"
            val dir = java.nio.file.Files.createTempDirectory("arete-ktx-narrative-attach-")
            val tmp = dir.resolve(fileName)
            java.nio.file.Files.write(tmp, bytes)
            KtxEngineBridge.publishFile(descriptor, FileEntry.from(tmp, mediaType))
        }
    }

    /**
     * Attaches a file to the current spec narrative. The attachment is queued on the spec node and
     * published by the engine while the spec node itself is executing, so the arete report keys it
     * under the spec page (same as arete's [com.github.mictaege.arete.NarrativeContext]).
     */
    fun attach(name: String, mediaType: MediaType, bytes: ByteArray) {
        node.narrativeAttachments.add(Triple(name, mediaType.toString(), bytes))
    }
}
