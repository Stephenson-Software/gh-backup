# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- Usage reporting to the maintainers' trace service: a `startup` event (program name and version only) once per process and a `backup-completed` event (nothing else) when a backup run finishes, sent from a background thread that never delays a backup or holds up exit by more than the client's 5-second timeout. Nothing about the users, organizations or repositories being backed up is sent. On by default; a one-line notice is logged the first time it runs on a machine (recorded in `~/.config/gh-backup/usage-reporting-notice-shown`), and it is turned off with `-Dusage.reporting.enabled=false` or `USAGE_REPORTING_ENABLED=false` (`usage.reporting.endpoint` and `usage.reporting.key` are documented in `CONFIG.md`; the Docker daemon takes `USAGE_REPORTING_ENABLED` from `.env`)
- `TraceClient`, the [trace-client-java](https://github.com/Stephenson-Software/trace-client-java) client, vendored unmodified apart from its package line as `com.github.backup.trace.TraceClient` together with its tests
- `BACKUP_INTERVAL_MS` environment variable for the Docker daemon image, mapped by `docker-entrypoint.sh` to `-Dbackup.scheduled.interval.ms`, so the backup interval can be configured from `.env`/`docker-compose.yml` without overriding the entrypoint
- `docker-entrypoint-test.sh`, a shell test that runs `docker-entrypoint.sh` against a stub `java` and asserts the argument list built for each combination of `BACKUP_DIRECTORY`, `SCHEDULED_USERS` and `BACKUP_INTERVAL_MS`, including empty values and values containing spaces; it runs in the `docker-build` CI job, so entrypoint changes are no longer merged unexecuted
- `DocumentationVersionTest`, which fails `mvn test` when a `gh-backup-<version>.jar` reference in `README.md`, `USER_GUIDE.md`, `CONFIG.md`, `COMMANDS.md` or `Dockerfile` does not match `<version>` in `pom.xml`, so a version bump can no longer leave some of them stale; `CONTRIBUTING.md` notes the check
- Offline unit tests for `GitHubService.getPublicRepositories`, run against a mocked GitHub client: private repositories are filtered out, a name that is not an organization falls back to the user lookup, a user lookup failing with a 404 is rewrapped as the "not found as GitHub organization or user" message, and any other failure is rethrown unchanged. Before these, the method was exercised only by a test that calls the live GitHub API
- Offline unit tests for `BackupService`: cloning and re-fetching run against a local source repository instead of `github.com`, a repository that fails to clone is listed in the failure summary while the others are still backed up, an uncreatable backup directory raises the "Failed to create backup directory" error, `getBackupStatusData` ignores plain files and formats the last-updated time, and `showBackupStatus` output is asserted for the missing-directory, empty-directory and populated cases. Before these, the clone test attempted a real clone from `github.com` and the status tests only checked that nothing was thrown

### Changed

- The vendored `TraceClient` is now trace-client-java 0.2.0, which honours `TRACE_USAGE_REPORTING=off` and `DO_NOT_TRACK=1` in the environment before anything gh-backup passes it and can say why reporting is off; the first-run notice now names `TRACE_USAGE_REPORTING=off` and links to https://github.com/Stephenson-Software/trace#usage-reporting, and `README.md`, `CONFIG.md`, `.env.example` and `docker-compose.yml` document both variables alongside `USAGE_REPORTING_ENABLED`
- CI now pins `actions/checkout@v5` and `actions/setup-java@v5` in both `.github/workflows/build.yml` and `.github/workflows/release.yml`, replacing the `v4` pins that GitHub has deprecated along with the Node.js 20 runtime they target
- The `docker-build` CI job now pins `docker/setup-buildx-action@v4` and `docker/build-push-action@v7`, the current major of each and the first to declare `node24`, replacing the `v3` and `v5` pins that were the last actions in `.github/workflows/build.yml` still running on the deprecated Node.js 20 runtime

### Fixed

- The Docker image no longer declares `ENV GITHUB_TOKEN=""`, which tripped the `SecretsUsedInArgOrEnv` Dockerfile build check reported by `docker/build-push-action@v7`; `GitHubService` treats an unset `GITHUB_TOKEN` exactly like an empty one (anonymous access), the token is still supplied at container start through `docker-compose.yml`/`.env`, and `CONFIG.md` now lists the image default as *(unset)*
- Documentation accuracy: the `java -jar` examples in `README.md`, `USER_GUIDE.md` and `CONFIG.md` now name `target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar`, the artifact the build actually produces, instead of the non-existent `target/gh-backup-1.0.0.jar`
- The `Build` workflow no longer triggers on a `develop` branch, and `.github/copilot-instructions.md` no longer instructs contributors to branch from and open pull requests against `develop`; the repository has only ever had `main`, and `CONTRIBUTING.md` was corrected to match in an earlier change
- `.github/copilot-instructions.md` now lists `application-daemon.properties` among the files under `src/main/resources/`, alongside `application.properties` and `application-web.properties`

## [2.0.0-SNAPSHOT-8-8-2026] – 2026-08-08

### Changed
- gh-backup is now developed AI-first. Day-to-day feature work, grooming, review and maintenance run through AI agents working directly against this repository, with the maintainers setting direction and approving what lands. The major version bump marks that change in how the project is built — it is not a break in behaviour, configuration or stored data, and existing installations can upgrade in place. Released as `2.0.0-SNAPSHOT-8-8-2026`: the AI-first line has not yet been verified in live operation, and the dated snapshot designation stays until it has.

### Added

- Daemon mode (`-Dspring.profiles.active=daemon`): runs scheduled backups automatically for configured users/organizations at a configurable interval (`backup.scheduled.users`, `backup.scheduled.interval.ms`), defaulting to every 24 hours
- Docker support (`Dockerfile`, `docker-compose.yml`, `.env.example`) for running daemon mode as a containerized background service

### Fixed

- Documentation accuracy: the `--interactive` flag, the `quit` command alias, the `backup.progress.overwrite` and `logging.level.root` properties, the Docker environment variables, the web API error responses, and the anonymous fallback for an invalid `GITHUB_TOKEN` are now documented, and the repository links in `README.md` and `CONTRIBUTING.md` point at `Stephenson-Software/gh-backup`

## [1.0.0] – 2026-01-01

### Added

- CLI mode: back up public repositories for one or more GitHub users or organizations by passing their names as arguments
- Interactive mode (`-i`/`--interactive` flag): prompt-based interface with `backup`, `status`, `help`, and `exit` commands
- Web UI mode (`-Dspring.profiles.active=web`): browser-based interface for managing backups
- REST API endpoints (`POST /api/backups`, `GET /api/backups/status`) for programmatic access
- GitHub authentication via `GITHUB_TOKEN` environment variable for higher API rate limits
- Configurable backup directory via `backup.directory` system property (defaults to `~/gh-backups/`)
- Cross-platform path normalization (Linux, macOS, Windows)
- Backing up multiple users and organizations in a single invocation, processed one after another
- Incremental updates: existing repositories are updated with `git fetch` instead of re-cloned
