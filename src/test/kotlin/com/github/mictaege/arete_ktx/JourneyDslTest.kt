package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.Test
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

class JourneyDslTest {

    companion object {
        val executed = mutableListOf<String>()
    }

    class RefundJourney : Spec({
        spec("ComplaintMediation") {
            journey("refund not paid") {
                step() { executed.add("first") }
                step() { executed.add("second") }
                step(phase = "Refunding") { executed.add("third") }
            }
        }
    })

    @Test
    fun journeyRunsStepsInSourceOrder() {
        executed.clear()
        val listener = SummaryGeneratingListener()
        LauncherFactory.openSession().use { session ->
            val request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(RefundJourney::class.java))
                .build()
            session.launcher.execute(request, listener)
        }
        assert(listener.summary.testsSucceededCount >= 3) { "expected >= 3 successful, got ${listener.summary.testsSucceededCount}" }
        assert(executed == listOf("first", "second", "third")) { "steps out of order: $executed" }
    }
}
