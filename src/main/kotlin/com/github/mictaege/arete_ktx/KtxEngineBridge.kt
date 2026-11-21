package com.github.mictaege.arete_ktx

import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.reporting.FileEntry

/**
 * Bridge from DSL runtime helpers to the engine's current listener.
 * Set by [AreteKtxEngine] while a step body executes.
 */
internal object KtxEngineBridge {
    @Volatile
    var listener: org.junit.platform.engine.EngineExecutionListener? = null

    fun publishFile(descriptor: TestDescriptor, entry: FileEntry) {
        listener?.fileEntryPublished(descriptor, entry)
    }
}
