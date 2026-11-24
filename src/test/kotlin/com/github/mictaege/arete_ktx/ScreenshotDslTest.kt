package com.github.mictaege.arete_ktx

import com.github.mictaege.arete.ScreenshotExtension
import com.github.mictaege.arete.ScreenshotTaker
import org.junit.jupiter.api.MediaType
import org.junit.jupiter.api.Test
import org.junit.platform.engine.reporting.FileEntry
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

class ScreenshotDslTest {

    class DummyTaker : ScreenshotTaker {
        override fun takeWhen(): Set<ScreenshotTaker.TestResult> = setOf(ScreenshotTaker.TestResult.SUCCESS)
        override fun getImageBytes(): ByteArray = byteArrayOf(1, 2, 3)
    }

    class Shot : Spec({
        spec("Shot Spec") {
            registerExtension(ScreenshotExtension(DummyTaker()))
            scenario("passing scenario") {
                given { }
                `when` {
                    KtxStepContext.current().attach("note.txt", MediaType.TEXT_PLAIN, "hi".toByteArray())
                }
                then { }
            }
        }
    })

    @Test
    fun screenshotAndAttachmentArePublished() {
        val files = mutableListOf<Pair<String, String>>()
        val listener = object : org.junit.platform.launcher.TestExecutionListener {
            override fun fileEntryPublished(testIdentifier: org.junit.platform.launcher.TestIdentifier, entry: FileEntry) {
                files.add(entry.path.fileName.toString() to testIdentifier.displayName)
            }
        }
        LauncherFactory.openSession().use { session ->
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Shot::class.java))
                .build()
            session.launcher.execute(request, SummaryGeneratingListener(), listener)
        }
        assert(files.any { it.first.startsWith("arete-screenshot.") }) { "no screenshot published: $files" }
        assert(files.any { it.first.startsWith("arete-attach.") && it.first.endsWith("note.txt") }) { "no attachment published: $files" }
    }
}
