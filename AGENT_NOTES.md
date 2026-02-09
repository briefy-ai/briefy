# Agent Notes

Purpose: persistent notes for agent behavior and repo-specific learnings.

## How To Update
- Add entries with date/time.
- Keep notes short and actionable.
- Prefer concrete examples (file paths, commands, behaviors).

## Mistakes Log
- Format: `[YYYY-MM-DD HH:MM] mistake -> fix -> prevention`

## Non-Obvious Code Findings
- Format: `[YYYY-MM-DD HH:MM] finding (why it matters) [paths/commands]`

## User Preferences (Likes / Dislikes)
- Format: `[YYYY-MM-DD HH:MM] like/dislike -> implication for future work`

## Non-Obvious Code Findings
- [2026-02-07 20:19] Multi-select delete in `apps/web/src/routes/sources/index.tsx` was implemented as N single `DELETE /api/sources/{id}` calls via `Promise.all`, which allowed partial archive on failures. Introduced atomic batch archive endpoint planning/implementation to enforce all-or-nothing behavior.
- [2026-02-07 22:46] Prefer named constants over magic numbers in domain/application logic (for example, batch limits in `SourceService`) to keep constraints explicit and easier to change safely.

## User Preferences (Likes / Dislikes)
- [2026-02-07 20:19] User confirmed that "delete" semantics for Sources must mean archive (DEC-012 behavior), including batch flows -> keep UI copy and API naming explicit about archive semantics behind delete actions.

## Mistakes Log
- [2026-02-07 20:20] Wrote a brittle archive list assertion that depended on result ordering in `SourceControllerTest` -> changed to ID-presence assertion (`$[?(@.id=='...')]`) -> avoid index-based JSON assertions when endpoint order is not guaranteed.
- [2026-02-09 08:35] Tried verifying event publication through `@MockitoBean ApplicationEventPublisher` in controller integration tests -> mock wasn’t reliably intercepting Spring's publisher wiring -> moved publication verification to focused service unit tests (`SourceServiceEventTest`) and kept controller tests on HTTP/state behavior -> for event assertions prefer unit/service tests over full-context controller tests.

## Non-Obvious Code Findings
- [2026-02-09 08:35] Source restore aligns with domain model/events as `ARCHIVED -> ACTIVE` only; no re-extraction and no status memory. Implemented via `Source.restore()` + `SourceStatus.canTransitionTo` update and idempotent service behavior in `restoreSource` (`apps/api/src/main/kotlin/com/briefy/api/domain/knowledgegraph/source/Source.kt`, `apps/api/src/main/kotlin/com/briefy/api/domain/knowledgegraph/source/SourceStatus.kt`, `apps/api/src/main/kotlin/com/briefy/api/application/source/SourceService.kt`).
- [2026-02-09 08:35] Domain events were added as plain data events (`SourceArchivedEvent`, `SourceRestoredEvent`) and logged via `@EventListener` in `EventsConfig`; this gives event visibility now without introducing downstream coupling yet (`apps/api/src/main/kotlin/com/briefy/api/domain/knowledgegraph/source/event`, `apps/api/src/main/kotlin/com/briefy/api/infrastructure/events/EventsConfig.kt`).
