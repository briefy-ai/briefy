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
