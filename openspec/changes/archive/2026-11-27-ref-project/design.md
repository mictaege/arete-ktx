# Design

## Context

`@Ref` in arete core now has `String project() default ""` (grouping key for the arete-gradle
references page). arete-ktx materializes DSL metadata as annotation proxies: `KtxNode.annotations`
maps annotation classes to attribute maps, and `KtxTestSource.proxyOf` falls back to the
annotation method's default value for attributes missing from the map. The plain-ref DSL is
`fun ref(value: String, url: String = "") = PlainRef(value, url)`, mapped onto `@Ref`
(single ref) or `@Refs` (multiple refs) in `DocumentedScope`.

## Goals / Non-Goals

- Goals: `ref(value, url, project)` parity with `Ref#project`; the attribute reaches the
  emitted `@Ref`/`@Refs` proxies so arete-gradle reads it like a Java-declared ref.
- Non-goals: composed-annotation refs (`ref(GithubRef::class, "14")`) need no DSL change —
  their `project` comes from the meta `@Ref` on the composed annotation (as in Java).

## Decisions

1. **Add `project` to `PlainRef` and `ref(...)`, always emitted in the attribute map.**
   `PlainRef(value, url, project = "")`; the `@Ref`/`@Refs` maps include `"project" to ...`
   unconditionally — the proxy's default fallback already handles blank, and always emitting
   keeps the single- and multi-ref code paths symmetric with `value`/`url`.
   Alternative considered: emit `project` only when non-blank — rejected, because the map is
   also used for `hashCode`/`toString` and omitting it would make proxies inconsistent.
2. **Demo alignment lives in arete-gradle-test kotlin-specs** (GithubRef meta `@Ref` gets
   `project = "arete-gradle"`; BookingProcessSpec refs 14/16/5/6; ComplaintMediationSpec
   ref 4 with `project = "arete"`), mirroring the Java travel_agency specs so both produce
   identical references-page groups.

## Risks / Trade-offs

- [Proxy attribute maps must name real annotation methods] → `project` exists on `Ref`
  since the core change; arete-ktx depends on arete via includeBuild in arete-gradle-test,
  so the local build picks it up.

## Migration Plan

Backward compatible: `project` defaults to `""`, existing `ref(value, url)` call sites are
unaffected. No rollback needed.

## Open Questions

(none)
