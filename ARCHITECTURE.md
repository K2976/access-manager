# AccessManager — Architecture

## Overview

AccessManager is an offline-first Android application that allows users to selectively disable Internet access for installed applications without requiring root access.

The application uses Android's `VpnService` to implement a local VPN that enforces Internet access decisions.

## Architectural Pattern

**MVVM** (Model-View-ViewModel) with unidirectional data flow.

- **View**: Jetpack Compose UI
- **ViewModel**: Holds UI state, exposes it via `StateFlow`
- **Model**: Domain models, use cases, repositories

## Package Structure

All source code lives under `dev.kartik.accessmanager`.

```
presentation/       → UI layer (Compose screens, ViewModels, navigation, theme)
    home/           → Home screen composables and ViewModel
    settings/       → Settings screen composables and ViewModel
    components/     → Shared reusable Compose components
    navigation/     → NavHost, Screen destinations
    theme/          → Material 3 theme (Color, Type, Shape, Theme)

domain/             → Business logic layer (pure Kotlin, no Android dependencies)
    model/          → Domain models (immutable data classes)
    repository/     → Repository interfaces (contracts)
    usecase/        → Use case classes (single-responsibility operations)

data/               → Data layer (implements domain contracts)
    repository/     → Repository implementations
    datasource/     → Local and remote data sources
    mapper/         → Mappers between data and domain models

vpn/                → VPN subsystem (Android-specific, most complex module)
    service/        → VpnService implementation
    engine/         → VPN lifecycle and packet processing engine
    packet/         → IP packet parsing and construction
    relay/          → Packet forwarding to/from the real network
    resolver/       → Maps packets to originating applications
    decision/       → Decides whether a packet should be allowed or blocked
    model/          → VPN-specific data models

database/           → Room database (entities, DAOs, database class)

di/                 → Hilt dependency injection modules

core/               → Shared infrastructure
    constants/      → Application-wide constants
    dispatchers/    → Coroutine dispatcher providers (for testability)
    extensions/     → Kotlin extension functions
    utils/          → General utility functions
```

## Dependency Direction

Dependencies flow **inward** toward the domain layer:

```
presentation → domain ← data
                 ↑
                vpn
```

- `presentation` depends on `domain` (never on `data` or `vpn` directly)
- `data` implements interfaces defined in `domain`
- `vpn` depends on `domain` for models and decision contracts
- `domain` depends on nothing (pure Kotlin)
- `core` is available to all layers
- `di` wires everything together via Hilt

No layer should reference implementation details of another layer.

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Composable screens | `{Feature}Screen` | `HomeScreen` |
| ViewModels | `{Feature}ViewModel` | `HomeViewModel` |
| Use cases | `{Action}{Entity}UseCase` | `ToggleAppAccessUseCase` |
| Repository interfaces | `{Entity}Repository` | `AppRuleRepository` |
| Repository implementations | `{Entity}RepositoryImpl` | `AppRuleRepositoryImpl` |
| Room entities | `{Entity}Entity` | `AppRuleEntity` |
| Room DAOs | `{Entity}Dao` | `AppRuleDao` |
| Mappers | `{Entity}Mapper` | `AppRuleMapper` |
| Hilt modules | `{Scope}Module` | `AppModule`, `DatabaseModule` |
| Navigation destinations | `Screen.{Name}` | `Screen.Home` |

## Key Principles

1. **Single Responsibility** — Every class has one purpose.
2. **Immutable Models** — Prefer `val` and `data class` over mutable state.
3. **Coroutines** — All async work uses coroutines. Never block the main thread.
4. **Flow** — Reactive state uses `Flow` / `StateFlow`. No `LiveData`.
5. **Dependency Injection** — Never manually instantiate dependencies that belong in Hilt.
6. **Compose Only** — No XML layouts. Everything uses Jetpack Compose.
7. **Fail Gracefully** — Never crash on recoverable conditions.
8. **Comments Explain Why** — Never explain what obvious code does.
