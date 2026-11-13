# Webull Trading Helper

Use OpenSpec for spec-driven development in this project.

- Read `HELP.md`, `openspec/config.yaml`, and the relevant existing specs and active change before feature work.
- Use `REQUIREMENTS.md` as the ordered backlog. Implement one requirement at a time unless requested otherwise; keep its status and OpenSpec change link current. Do not invent product requirements to fill the backlog.
- Use the generated OpenSpec skills in `.agents/skills/openspec-*/SKILL.md` for proposing, exploring, applying, updating, syncing, and archiving changes.
- Capture new behavior and material behavior changes in an OpenSpec change before implementing them. Keep requirements, design decisions, and tasks consistent with the implementation.
- A request to explore or propose is planning work. A request to implement authorizes implementation within its stated scope; do not repeatedly ask for authorization already given.
- For small fixes that restore specified behavior, use the existing spec and add appropriate regression coverage. Documentation-only corrections do not require a separate feature proposal.
- Run `openspec validate --all --strict` for spec changes and `./mvnw test` for Java changes. Report actual results and any checks that could not run. Do not mark unfinished tasks complete.
- Use Java 21 and the existing Maven wrapper. Keep application code under `org.rl.webulltradinghelper` and tests under `src/test/java`.
- Do not invent Webull API contracts or treat mock responses as verified broker behavior. Keep credentials out of source files, specs, fixtures, and logs. The user explicitly requested temporary credential storage in the ignored `src/main/resources/application.properties`; preserve that exception. Use only this application configuration file; do not create additional example or profile configuration files.

OpenSpec CLI setup and usage are documented in `HELP.md`.
