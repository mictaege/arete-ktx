# Tasks

## 1. DSL implementation

- [x] 1.1 Add `project: String = ""` to `PlainRef` and `ref(value, url, project)`, and emit `"project"` in the `@Ref` / `@Refs` proxy attribute maps in `KtxDsl.kt`; verify with `./gradlew test` in arete-ktx including a new unit test asserting `Ref.project()` on the emitted proxies (single and container cases, plus blank default)

## 2. Specs

- [x] 2.1 Land the delta spec (`specs/kotlin-dsl/spec.md` in this change); verify `openspec validate ref-project` passes

## 3. Demo alignment and end-to-end validation

- [x] 3.1 Align arete-gradle-test kotlin-specs with the Java travel_agency specs: `GithubRef` meta `@Ref` gets `project = "arete-gradle"`; `BookingProcessSpec` refs become 14/16/5/6 (5 with `project = "arete"`); `ComplaintMediationSpec` gets ref 4 with `project = "arete"`; verify `./gradlew :kotlin-specs:test` in arete-gradle-test runs and `build/reports/arete/test/references.html` groups the Kotlin refs under `arete` / `arete-gradle` exactly like the Java report
- [x] 3.2 Run `./gradlew test` in arete-ktx and `openspec validate ref-project`; verify all green

## Workflow follow-up

- Archive this change with `/openspec-archive-change` after implementation and validation (syncs the delta spec into `openspec/specs/`).
