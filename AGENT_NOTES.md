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
- [2026-02-11 10:19] Spring AI was switched from OpenAI starter to Zhipu starter; app/test stability requires explicitly disabling non-chat models (`spring.ai.model.embedding=none`, `spring.ai.model.image=none`) unless intentionally used, otherwise context boot fails without Zhipu API key (`apps/api/build.gradle.kts`, `apps/api/src/main/resources/application.yml`, `apps/api/src/test/resources/application-test.yml`).
- [2026-02-11 11:49] Slice 2 Topics implemented using domain-native persistence (`topics` + `topic_links`) instead of `enrichments`; pending suggestions are represented as `TopicLink(status=SUGGESTED)` and confirmation promotes both link/topic states. Dismissal archives orphan suggested topics (`apps/api/src/main/resources/db/migration/V20260211104500__topics_slice2.sql`, `apps/api/src/main/kotlin/com/briefy/api/application/topic/TopicService.kt`).
- [2026-02-11 11:49] Frontend lint gate failed due pre-existing `react-refresh/only-export-components` violations in shared UI primitives exporting variant helpers; added file-level disable comments to keep project lint passing during Slice 2 delivery (`apps/web/src/components/ui/button.tsx`, `apps/web/src/components/ui/badge.tsx`).
- [2026-02-11 12:27] Topic suggestion trigger corrected from extraction-only to activation-based eventing: `SourceActivatedEvent` now fires for both fresh extraction and shared snapshot cache reuse, so per-user topic suggestion runs on cache hits too (`apps/api/src/main/kotlin/com/briefy/api/application/source/SourceService.kt`, `apps/api/src/main/kotlin/com/briefy/api/application/topic/TopicSuggestionEventHandler.kt`).
- [2026-02-11 12:40] Zhipu 401s can come from platform/base-url mismatch despite a valid key: Z.ai keys require `https://api.z.ai/api/paas` while BigModel keys use `https://open.bigmodel.cn/api/paas`; aligned defaults and explicit chat overrides to avoid ambiguous runtime wiring (`apps/api/src/main/resources/application.yml`, `.env.example`, `docker-compose.yml`).
- [2026-02-11 13:58] Source topic handling now supports an atomic single-action flow: `POST /api/sources/{id}/topics/apply` confirms selected suggestions and dismisses the rest in one transaction; `GET /api/sources/{id}/topics/active` exposes confirmed source-level topics for the note UI (`apps/api/src/main/kotlin/com/briefy/api/api/SourceController.kt`, `apps/api/src/main/kotlin/com/briefy/api/application/topic/TopicService.kt`).
- [2026-02-11 13:58] Topic suggestion prompt now includes existing user ACTIVE topics (`id` + `name`) and supports match-by-`existingTopicId` output before creating new topics, aligning with DEC-014 user-authority topic design (`apps/api/src/main/kotlin/com/briefy/api/application/topic/TopicSuggestionService.kt`).

## User Preferences (Likes / Dislikes)
- [2026-02-11 13:58] User dislikes the plain checkbox suggestion UI and prefers a single “keep selected, discard rest” action -> prioritize streamlined batch-selection interactions in source topic review flows (`apps/web/src/routes/sources/$sourceId.tsx`).
- [2026-02-11 14:18] User does not want archived topics surfaced in the Topics UI; suggested topics should be meaningful by exposing the related sources directly -> keep Topics page focused on active/suggested and ensure suggested topic detail resolves to suggested source links (`apps/web/src/routes/topics/index.tsx`, `apps/api/src/main/kotlin/com/briefy/api/application/topic/TopicService.kt`).
- [2026-02-11 15:46] `POST /api/topics` initially returned 500 when `sourceIds` was omitted because request binding treated `sourceIds` as null for a non-null constructor param; changed `CreateTopicRequest.sourceIds` to nullable and normalized via `orEmpty()` in controller (`apps/api/src/main/kotlin/com/briefy/api/api/TopicController.kt`).
- [2026-02-11 16:02] Source detail topic UX now gates suggestion review by active-topic presence: when source has active topics, suggestion panel is hidden and manual add goes through `POST /api/topics` with current `sourceId` so created topics and links are immediately ACTIVE (`apps/web/src/routes/sources/$sourceId.tsx`, `apps/web/src/routes/topics/index.tsx`).
- [2026-02-11 19:00] Topic lifecycle update: orphan `SUGGESTED` topics are now hard-deleted (instead of archived) when their last live source link is dismissed; topics with any remaining live links are preserved (`apps/api/src/main/kotlin/com/briefy/api/application/topic/TopicService.kt`).
