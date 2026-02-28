# Agent Notes

Purpose: persistent memory for agent-to-agent collaboration. Read at the start of each session.
See `AGENTS.md` → "Agent Notes File" for what belongs in each section and how to write entries.

## Mistakes Log

- [2026-02-07 20:20] Wrote a brittle archive list assertion that depended on result ordering in `SourceControllerTest` -> changed to ID-presence assertion (`$[?(@.id=='...')]`) -> avoid index-based JSON assertions when endpoint order is not guaranteed.
- [2026-02-09 08:35] Tried verifying event publication through `@MockitoBean ApplicationEventPublisher` in controller integration tests -> mock wasn't reliably intercepting Spring's publisher wiring -> moved publication verification to focused service unit tests (`SourceServiceEventTest`); keep controller tests on HTTP/state behavior only.
- [2026-02-11 15:46] `POST /api/topics` returned 500 when `sourceIds` was omitted because request binding treated it as null for a non-null constructor param -> changed `CreateTopicRequest.sourceIds` to nullable, normalized via `orEmpty()` in controller -> make request fields nullable when they have a sensible empty default.
- [2026-02-11 23:56] Generated two Flyway files in the same second with `scripts/genDDLFile.sh`, causing duplicate version numbers -> renamed to unique timestamps -> when creating multiple migrations quickly, verify timestamp uniqueness before editing.
- [2026-02-12 00:19] Adding runtime usage of `ExtractionProvider.id` broke tests using Mockito mocks because interface properties default to null in unstubbed mocks -> explicitly stub `extractionProvider.id` in affected tests before extraction calls.
- [2026-02-14 14:22] Kotlin service methods with default parameters: Mockito `verify` fails due to generated `$default` bridge -> remove default args from mocked service methods and pass values explicitly at call sites.
- [2026-02-18] Attempted to append AGENT_NOTES with unescaped backticks in zsh, causing command substitution errors -> use single-quoted `printf` input when writing multi-line shell strings.
- [2026-02-19 10:24] pgvector JDBC upsert bound `Instant` directly, causing PostgreSQL type inference failure -> switched to `Timestamp.from(now)` -> with `NamedParameterJdbcTemplate` + PostgreSQL, bind timestamps explicitly.
- [2026-02-19 10:24] Integration test for `source_embeddings` failed FK checks because JPA `save` had not flushed before JDBC insert in the same transaction -> use `saveAndFlush` when mixing JPA writes and JDBC reads/writes in one test transaction.
- [2026-02-19 14:28] Modeled large briefing JSON fields with `@Lob`, causing PostgreSQL schema validation mismatch (`expected oid` vs migrated `TEXT`) in Testcontainers ITs -> use `@Column(columnDefinition = "TEXT")` instead of `@Lob` for large text fields.
- [2026-02-19 14:28] Added new briefing exceptions but missed handler methods in `GlobalExceptionHandler`, causing 500 responses -> fixed by adding explicit 400/403/404 handlers -> always register exception handlers before shipping a new exception type.
- [2026-02-21 17:45] `pnpm build` failed because TanStack route types were not regenerated after adding a new file-route -> run Vite generation once, then rerun `pnpm build` -> after adding file-routes, always regenerate route tree before TypeScript build checks (`apps/web/src/routeTree.gen.ts`).
- [2026-02-22 17:32] Used shell backticks in `gh pr create --body`, causing command substitution and a malformed PR description -> pass content through a heredoc or `--body-file` instead; avoid unescaped backticks in shell string flags.
- [2026-02-23 09:35] Made `metadata_formatting_state` `NOT NULL`, which broke source creation because `Source` is first persisted in `SUBMITTED` state with `metadata = null` -> make formatting-state columns nullable at schema level.
- [2026-02-26 10:41] Introduced a `spring.ai.model.chat` runtime guard that made `minimax` fail when `LLM_PROVIDER=zhipuai` even though user settings allowed Minimax selection -> fixed by replacing single-bean guard with provider-specific chat model map -> when supporting selectable providers, avoid coupling runtime dispatch to Spring's single auto-config switch.
- [2026-02-28 16:34] Combined multiple expected DB-violation assertions in one `@Transactional` Postgres test, causing SQL state `25P02` (`current transaction is aborted`) after the first check-constraint failure -> split into isolated tests, one expected DB violation per transaction.
- [2026-02-28 20:05] Mapped `RunEvent.sequenceId` with `@GeneratedValue` on a non-identifier field -> Hibernate failed context startup (`AnnotationException`) and schema validation (`nullable` mismatch) -> removed `@GeneratedValue`, kept column read-only, and set `nullable = false` to match DB identity column.

## Non-Obvious Code Findings

- [2026-02-09 08:35] Source restore is `ARCHIVED -> ACTIVE` only; no re-extraction, no status memory. Service behavior is idempotent (`Source.kt`, `SourceStatus.kt`, `SourceService.kt`).
- [2026-02-11 10:19] Spring AI Zhipu starter requires explicitly disabling non-chat models (`spring.ai.model.embedding=none`, `spring.ai.model.image=none`); otherwise context boot fails without a Zhipu API key (`application.yml`, `application-test.yml`).
- [2026-02-11 12:40] Zhipu 401s can come from platform/base-url mismatch despite a valid key: Z.ai keys require `https://api.z.ai/api/paas`, BigModel keys use `https://open.bigmodel.cn/api/paas`.
- [2026-02-11 19:00] Orphan `SUGGESTED` topics are hard-deleted (not archived) when their last live source link is dismissed; topics with remaining links are preserved (`TopicService.kt`).
- [2026-02-12 01:05] Shared snapshots have immutable `content`/`metadata` (`val`); formatter creates a new snapshot version instead of mutating in-place, keeping cache freshness timestamps unchanged (`SourceContentFormatterService.kt`).
- [2026-02-12 23:22] `com.fasterxml.jackson.databind.JsonNode` in X API DTOs fails at runtime with the `tools.jackson` mapper in this stack; use `Map<String, Any?>` access helpers instead.
- [2026-02-14 14:39] `Process.waitFor()` before draining stdout can deadlock `yt-dlp` on large JSON output; stream output concurrently before waiting (`YouTubeExtractionProvider.kt`).
- [2026-02-14 17:30] `telegrambots-springboot-webhook-starter:9.3.0` still imports Spring Boot 3.5.5 BOM despite compiling on Spring Boot 4; watch for transitive drift after version bumps (`build.gradle.kts`).
- [2026-02-15 12:55] This repo standardizes on the unified `radix-ui` package, not per-package `@radix-ui/*` imports; new UI wrappers must follow that pattern (`apps/web/src/components/ui/*`).
- [2026-02-18 22:53] Spring OTLP exporter for Langfuse must be a dedicated bean, not a system-property set at startup; system-property approach causes init-order drops where spans are produced but never exported (`AiObservabilityExporterConfig.kt`).
- [2026-02-19 10:24] `SourceContentFinalizedEvent` is the canonical downstream trigger for finalized source text. Emitted by `SourceService` on immediate-final paths and by `SourceContentFormatterService` on success/skip/fallback paths — but NOT on transient formatter failures.
- [2026-02-19 10:54] `EmbeddingProperties` validates fixed provider/model/dimension at startup and fails fast on accidental overrides. There are no runtime toggles for embedding config (`EmbeddingProperties.kt`).
- [2026-02-28 20:05] Briefing execution state-machine core adds dedicated execution entities/enums/converters/repositories and `ExecutionStateTransitionService`; transitions are validated before state mutation and each accepted transition persists an idempotent `run_events.event_id`.

## User Preferences

- [2026-02-07 20:19] "Delete" semantics for Sources must mean archive (DEC-012), including batch flows -> keep UI copy and API naming explicit about archive semantics behind delete actions.
- [2026-02-11 13:58] Prefers a single "keep selected, discard rest" action over checkbox-per-suggestion UIs -> streamline batch-selection interactions in review flows.
- [2026-02-11 14:18] Does not want archived topics surfaced in the Topics UI -> keep Topics page focused on active/suggested only.
- [2026-02-12 14:38] Prefers commercial landing copy over internal/product-development language -> prioritize audience outcomes and conversion CTAs on public-facing pages.
- [2026-02-15 12:55] For copy/action UX, prefers lightweight inline feedback (icon toggles briefly to a check) rather than toast notifications.
- [2026-02-22 09:59] Chat trigger: icon-only floating circular button (`Sparkles`) at bottom-right on logged-in non-settings routes, plus global `Cmd+J` toggle.
- [2026-02-22 10:45] Keep pace slower in domain discovery; persist domain decisions in Obsidian before moving into implementation framing. No coding during architecture discussion sessions.
- [2026-02-22 18:41] Annotation popover: annotation body as primary visual focus, metadata/actions secondary; use softer translucent + blur card treatment.
- [2026-02-23 09:35] Formatting/loading failures should be surfaced immediately with an in-card retry option, not left on indefinite loading spinners.
- [2026-02-25 09:21] Prefers simple, deterministic V1 behavior over heavier infra/modeling when both are viable.
- [2026-02-28 16:34] Keep schema scope to mandatory tables per execution feature milestone; track deferred items (e.g., `subagent_tool_calls`) explicitly in Obsidian instead of adding speculative columns now.
- [2026-02-28 20:05] Briefing execution state-machine preference: keep contracts/transitions isolated from current `BriefingGenerationJob` runtime wiring in an initial focused PR, then wire runtime integration in follow-up feature PRs.
- [2026-02-28 20:22] For run event idempotency, catching `DataIntegrityViolationException` inside `@Transactional` is unreliable due deferred flush/invalid session semantics; use a conflict-safe insert (`ON CONFLICT (event_id) DO NOTHING`) and then validate run coordinates on conflict.
- [2026-02-28 20:56] New briefing execution orchestration IT initially used `postgres:16-alpine`, which fails Flyway migration `V20260219100000__source_embeddings_pgvector.sql` (`extension "vector" is not available`) -> use `pgvector/pgvector:pg16` consistently for backend ITs that run full migrations.
- [2026-02-28 20:56] Briefing execution orchestration wiring landed behind `briefing.execution.enabled`; legacy engine remains as fallback path for safe rollout while execution runtime becomes the source of truth when enabled.
- [2026-02-28 20:58] Avoid using standalone "Slice X" naming in PRs, file names, or agent notes; always tie work to the concrete feature/domain because a feature can span multiple slices and slice-only labels lose traceability after merges.
