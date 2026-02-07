# Repository Guidelines

## Project Structure & Module Organization
This repository is a small monorepo with two apps:
- `apps/api`: Kotlin + Spring Boot backend (`src/main/kotlin`, `src/main/resources`, Flyway SQL in `src/main/resources/db/migration`).
- `apps/web`: React + TypeScript + Vite frontend (`src/components`, `src/routes`, `src/lib`).
- `docker-compose.yml`: local multi-service stack (`db`, `api`, `web`).
- `scripts/`: utility scripts (for example `scripts/genDDLFile.sh`).

Keep domain/application/infrastructure boundaries in the API package tree (`domain`, `application`, `infrastructure`, `api`).

## Build, Test, and Development Commands
Run commands from each app directory unless noted.
- Backend dev: `cd apps/api && ./gradlew bootRun`
- Backend tests: `cd apps/api && ./gradlew test`
- Frontend dev server: `cd apps/web && pnpm dev`
- Frontend build: `cd apps/web && pnpm build`
- Frontend lint: `cd apps/web && pnpm lint`
- Full local stack: `docker compose up --build`

CI currently runs backend tests on PRs and pushes to `main` via `.github/workflows/backend-tests.yml`.

## Coding Style & Naming Conventions
- Kotlin: 4-space indentation, `PascalCase` types, `camelCase` members, package path under `com.briefy.api`.
- TypeScript/React: 2-space indentation, `PascalCase` components, `camelCase` functions/hooks, colocate route files in `src/routes`.
- SQL migrations: `V<timestamp>__<snake_case_description>.sql` (example: `V20260207113000__source_type_and_shared_snapshot_cache.sql`).
- Use ESLint in `apps/web` as the frontend style gate.

## Testing Guidelines
- Backend uses JUnit 5 + Spring Boot Test + MockMvc (`apps/api/src/test/kotlin`).
- Name backend tests `*Test.kt` and prefer behavior-driven test names in backticks.
- Add/update tests for domain logic, controllers, and security-sensitive flows whenever behavior changes.
- Frontend test framework is not configured yet; at minimum, keep `pnpm lint` and `pnpm build` passing for UI changes.

## Commit & Pull Request Guidelines
- Follow concise, imperative commit messages. Current history favors patterns like `FEAT: ... (#<PR>)` or short lowercase imperatives (`implement user authentication`).
- PRs should include: purpose, scope, testing evidence (commands run), and linked issue/PR context.
- Include screenshots or short recordings for frontend-visible changes.

## Security & Configuration Tips
- Copy `.env.example` for local configuration; never commit real secrets.
- Keep `JWT_SECRET`, `LLM_API_KEY`, and DB credentials out of source control.
- Prefer environment variables over hardcoded credentials in code and tests.

## Agent Notes File
- Use `AGENT_NOTES.md` as the persistent notetaking file for agent collaboration.
- Write down non-obvious findings about the codebase and behavior.
- Write down mistakes made during implementation/review and how they were corrected.
- Write down user preferences (likes/dislikes) and implications for future work.
- Append concise, dated entries rather than rewriting history.

## Context Source (Obsidian)
- Treat the Obsidian note `00 - index` as the primary source of project context, decisions, and navigation to related notes.
- When repo context is unclear, check `00 - index` first before making architectural or workflow assumptions.
- Obsidian MCP is available in this environment; use it to read/search notes and pull context directly from the vault instead of relying on memory.
