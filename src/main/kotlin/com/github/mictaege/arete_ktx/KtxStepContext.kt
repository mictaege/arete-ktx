package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.MediaType
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.reporting.FileEntry

/**
 * Access to the currently executing Kotlin DSL step, e.g. to attach files to it.
 * Reachable from any step body via [current].
 */
class KtxStepContext private constructor(private val descriptor: TestDescriptor) {

    companion object {
        const val ATTACHMENT_NAME_PREFIX = "arete-attach"

        private val CURRENT = ThreadLocal<TestDescriptor>()

        fun current(): KtxStepContext {
            val d = CURRENT.get() ?: throw IllegalStateException("No step is currently executing on this thread")
            return KtxStepContext(d)
        }

        internal fun setCurrent(descriptor: TestDescriptor) { CURRENT.set(descriptor) }
        internal fun clearCurrent() { CURRENT.remove() }
    }

    /** The descriptor of the step currently executing. */
    internal val currentDescriptor: TestDescriptor get() = descriptor

    /**
     * Attaches a file to the current step. Published under the [ATTACHMENT_NAME_PREFIX]
     * namespace, same as arete's [com.github.mictaege.arete.StepContext].
     */
    fun attach(name: String, mediaType: MediaType, bytes: ByteArray) {
        val fileName = "$ATTACHMENT_NAME_PREFIX.${name.replace(Regex("[/\\\\]"), "_")}"
        val dir = java.nio.file.Files.createTempDirectory("arete-ktx-attach-")
        val tmp = dir.resolve(fileName)
        java.nio.file.Files.write(tmp, bytes)
        KtxEngineBridge.publishFile(descriptor, FileEntry.from(tmp, mediaType.toString()))
    }
}
