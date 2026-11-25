package com.github.mictaege.arete_ktx

import org.junit.jupiter.api.Test
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.junit.platform.launcher.listeners.SummaryGeneratingListener
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass

class LifecycleHooksTest {

    companion object {
        val events = mutableListOf<String>()
        var failStep = false

        private fun run(cls: Class<*>): Pair<Long, Long> {
            events.clear()
            val listener = SummaryGeneratingListener()
            LauncherFactory.openSession().use { session ->
                val request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(cls))
                    .build()
                session.launcher.execute(request, listener)
            }
            return listener.summary.let { Pair(it.testsFailedCount, it.testsSucceededCount) }
        }
    }

    class HookSpec : Spec({
        spec("Lifecycle") {
            beforeAll { events.add("spec.beforeAll") }
            afterAll { events.add("spec.afterAll") }
            feature("F") {
                beforeAll { events.add("feature.beforeAll") }
                afterAll { events.add("feature.afterAll") }
                beforeEach { events.add("feature.beforeEach") }
                afterEach { events.add("feature.afterEach") }
                scenario("S") {
                    beforeEach { events.add("scenario.beforeEach") }
                    given("g") { events.add("given") }
                    then("t") { events.add("then") }
                }
            }
        }
    })

    @Test
    fun hooksRunWithJUnitOrdering() {
        val (failed, _) = run(HookSpec::class.java)
        assert(failed == 0L) { "failures: $failed" }
        assert(events == listOf(
            "spec.beforeAll", "feature.beforeAll",
            "feature.beforeEach", "scenario.beforeEach", "given", "feature.afterEach",
            "feature.beforeEach", "scenario.beforeEach", "then", "feature.afterEach",
            "feature.afterAll", "spec.afterAll"
        )) { "unexpected order: $events" }
    }

    class SingleSlotSpec : Spec({
        spec("SingleSlot") {
            scenario("S") {
                beforeEach { events.add("first") }
                beforeEach { events.add("second") }
                given("g") { events.add("step") }
            }
        }
    })

    @Test
    fun secondRegistrationReplacesFirst() {
        val (failed, _) = run(SingleSlotSpec::class.java)
        assert(failed == 0L)
        assert(events == listOf("second", "step")) { "expected single-slot replace: $events" }
    }

    class FailingStepSpec : Spec({
        spec("Failing") {
            scenario("S") {
                afterEach { events.add("afterEach") }
                given("boom") { if (failStep) throw AssertionError("boom") }
            }
        }
    })

    @Test
    fun afterEachRunsOnFailure() {
        failStep = true
        try {
            val (failed, _) = run(FailingStepSpec::class.java)
            assert(failed >= 1L)
            assert(events == listOf("afterEach")) { "afterEach must run on failure: $events" }
        } finally {
            failStep = false
        }
    }

    class VariantSpec : Spec({
        spec("Variants") {
            variableJourney("J") {
                beforeVariant { events.add("beforeVariant") }
                afterVariant { events.add("afterVariant") }
                step("a", variant = "v1") { events.add("a") }
                step("b", variant = "v2") { events.add("b") }
            }
        }
    })

    @Test
    fun variantHooksRunPerVariant() {
        val (failed, _) = run(VariantSpec::class.java)
        assert(failed == 0L)
        assert(events == listOf("beforeVariant", "a", "afterVariant", "beforeVariant", "b", "afterVariant")) {
            "unexpected variant order: $events"
        }
    }

    @Test
    fun dslRegistrationStoresHooksOnNodes() {
        val node = spec("S") {
            beforeAll { }
            beforeEach { }
            afterEach { }
            afterAll { }
            scenario("sc") { given("g") { } }
        }
        assert(node.beforeAllHook != null && node.beforeEachHook != null &&
            node.afterEachHook != null && node.afterAllHook != null)
        val scenario = node.children.single()
        assert(scenario.beforeEachHook == null) { "hooks must not leak to children at definition time" }
    }
}
