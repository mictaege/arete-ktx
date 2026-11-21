package com.github.mictaege.arete_ktx

import org.junit.platform.engine.TestSource
import java.lang.reflect.Proxy

/**
 * TestSource for Kotlin DSL nodes. Carries the arete annotation metadata as
 * attribute maps so arete-gradle can serve them as proxied annotations.
 */
class KtxTestSource(
    val nodeType: String,
    val annotations: Map<Class<out Annotation>, Map<String, Any?>>,
    val specClass: Class<*>? = null,
) : TestSource {

    fun has(annotation: Class<out Annotation>): Boolean = annotations.containsKey(annotation)

    fun <A : Annotation> proxy(annotation: Class<A>): A? =
        annotations[annotation]?.let { proxyOf(annotation, it) }

    @Suppress("UNCHECKED_CAST")
    private fun <A : Annotation> proxyOf(annotation: Class<A>, attrs: Map<String, Any?>): A {
        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "annotationType" -> annotation
                "toString" -> "@${annotation.simpleName}($attrs)"
                "hashCode" -> attrs.hashCode()
                "equals" -> false
                else -> coerce(attrs[method.name] ?: method.defaultValue, method.returnType)
            }
        }
        return Proxy.newProxyInstance(annotation.classLoader, arrayOf(annotation), handler) as A
    }

    fun allProxies(): Array<Annotation> =
        annotations.keys.mapNotNull { proxy(it) }.toTypedArray()

    private fun coerce(value: Any?, returnType: Class<*>): Any? {
        if (value == null) return null
        if (returnType.isArray && value is Array<*> && returnType.componentType.isAnnotation) {
            val component = returnType.componentType
            val arr = java.lang.reflect.Array.newInstance(component, value.size)
            value.forEachIndexed { i, el ->
                @Suppress("UNCHECKED_CAST")
                val proxy = proxyOf(component as Class<Annotation>, el as? Map<String, Any?> ?: emptyMap())
                java.lang.reflect.Array.set(arr, i, proxy)
            }
            return arr
        }
        if (returnType.isArray && value !is Array<*>) {
            val arr = java.lang.reflect.Array.newInstance(returnType.componentType, 1)
            java.lang.reflect.Array.set(arr, 0, value)
            return arr
        }
        return value
    }
}
