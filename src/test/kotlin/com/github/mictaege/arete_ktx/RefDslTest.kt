package com.github.mictaege.arete_ktx

import com.github.mictaege.arete.Ref
import com.github.mictaege.arete.Refs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RefDslTest {

    class SingleRefSpec : Spec({
        spec("Single Ref Spec") {
            doc(refs = listOf(ref("5", url = "https://github.com/mictaege/arete/issues/", project = "arete")))
        }
    })

    class MultiRefSpec : Spec({
        spec("Multi Ref Spec") {
            doc(
                refs = listOf(
                    ref("5", url = "https://github.com/mictaege/arete/issues/", project = "arete"),
                    ref("6", url = "https://github.com/mictaege/arete/issues/"),
                )
            )
        }
    })

    class BlankProjectSpec : Spec({
        spec("Blank Project Spec") {
            doc(refs = listOf(ref("16", url = "https://github.com/mictaege/arete-gradle/issues/")))
        }
    })

    private fun sourceOf(spec: Spec) =
        KtxTestSource("spec", spec.specs[0].annotations, spec::class.java)

    @Test
    fun singleRefProxyCarriesProject() {
        val proxy = sourceOf(SingleRefSpec()).proxy(Ref::class.java)
        assertEquals("5", proxy?.value)
        assertEquals("https://github.com/mictaege/arete/issues/", proxy?.url)
        assertEquals("arete", proxy?.project)
    }

    @Test
    fun refsContainerCarriesProjectPerEntry() {
        val refs = sourceOf(MultiRefSpec()).proxy(Refs::class.java)?.value
        assertEquals(2, refs?.size)
        assertEquals("arete", refs?.get(0)?.project)
        assertEquals("", refs?.get(1)?.project)
    }

    @Test
    fun projectDefaultsToBlank() {
        val proxy = sourceOf(BlankProjectSpec()).proxy(Ref::class.java)
        assertEquals("16", proxy?.value)
        assertEquals("", proxy?.project)
    }
}
