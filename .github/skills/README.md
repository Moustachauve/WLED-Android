# Android Agent Skills

This directory contains AI agent skills imported and adapted from the [awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills) repository.

These skills are organized into domain categories to provide targeted, specialized guidance for AI coding assistants.

These skills are licensed under the Apache License 2.0. See the `LICENSE` file in this directory for details.

---

## Available Skills by Category

### Architecture (`architecture/`)
* **[Android Architecture](architecture/android-architecture/SKILL.md)**: Clean Architecture, modularization, and Dependency Injection with Hilt.
* **[ViewModel & State Management](architecture/android-viewmodel/SKILL.md)**: ViewModel best practices with Kotlin Explicit Backing Fields, `StateFlow`, and `SharedFlow`.
* **[Data Layer & Offline-First](architecture/android-data-layer/SKILL.md)**: Offline-first repository pattern with Room and Retrofit.

### UI (`ui/`)
* **[Jetpack Compose UI](ui/compose-ui/SKILL.md)**: Stateless composables, modifier chains, and slot APIs.
* **[Navigation 3](ui/navigation3/SKILL.md)**: State-driven navigation keys, back stacks, and NavDisplay used in WLED-Android.
* **[Coil for Jetpack Compose](ui/coil-compose/SKILL.md)**: Image loading with Coil 3 in Compose.
* **[Accessibility](ui/android-accessibility/SKILL.md)**: Auditing touch targets, semantics, and content descriptions.

### Concurrency & Networking (`concurrency_and_networking/`)
* **[Android Coroutines](concurrency_and_networking/android-coroutines/SKILL.md)**: Coroutine scopes, dispatchers, cancellation, and exception handling.
* **[Retrofit](concurrency_and_networking/android-retrofit/SKILL.md)**: API service definitions, Moshi converters, OkHttp interceptors.
* **[Kotlin Concurrency Expert](concurrency_and_networking/kotlin-concurrency-expert/SKILL.md)**: Advanced flow transformations and thread safety.

### Build & Tooling (`build_and_tooling/`)
* **[Android Gradle Logic](build_and_tooling/android-gradle-logic/SKILL.md)**: Gradle Kotlin DSL, convention plugins, and version catalogs.

### Performance (`performance/`)
* **[Compose Performance Audit](performance/compose-performance-audit/SKILL.md)**: Diagnosing recomposition storms, layout thrashing, and Profiler usage.
* **[Gradle Build Performance](performance/gradle-build-performance/SKILL.md)**: Build cache, configuration cache, and build scan optimization.

### Testing & Automation (`testing_and_automation/`)
* **[Android Testing](testing_and_automation/android-testing/SKILL.md)**: Unit testing with MockK/Turbine and UI testing with Compose Test Rule.
* **[Android Emulator Skill](testing_and_automation/android-emulator-skill/SKILL.md)**: Python automation scripts and environment health checks (`.sh` and `.ps1`).

### Migration (`migration/`)
* **[XML to Compose Migration](migration/xml-to-compose-migration/SKILL.md)**: Strategies for incrementally migrating legacy XML layouts to Compose.
* **[RxJava to Coroutines Migration](migration/rxjava-to-coroutines-migration/SKILL.md)**: Converting RxJava Observables/Single/Completable to Kotlin Flow and Coroutines.
