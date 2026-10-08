# Configuration Guide

Configuration is provided through Spring Boot property files and JVM system properties.

The main configuration file is `src/main/resources/application.properties`. Values can be overridden at runtime with `-D<property>=<value>`.

---

## backup.directory

**Type:** string (file path)  
**Default:** `${user.home}/gh-backups`  
**Description:** The directory where cloned repositories are stored. The path is resolved relative to the user's home directory by default and is normalized for cross-platform compatibility (Linux, macOS, Windows).

**Override at runtime:**
```bash
java -Dbackup.directory=/mnt/storage/github-backups -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

Windows example:
```bat
java -Dbackup.directory=C:\Backups\GitHub -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

---

## backup.progress.overwrite

**Type:** boolean  
**Default:** `true`  
**Description:** Controls how clone and fetch progress is rendered during a backup. When `true`, progress percentages are rewritten in place on a single line using a carriage return. When `false`, each update is printed on its own line, which is easier to read in log files and CI output that do not interpret carriage returns. This value is read directly from the JVM system properties, so it must be passed with `-D` rather than set in `application.properties`.

**Override at runtime:**
```bash
java -Dbackup.progress.overwrite=false -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

---

## backup.mode

**Type:** string (`cli` | `web` | `daemon`)  
**Default:** `cli`  
**Description:** Controls whether the application runs as a command-line tool (`cli`), a web server (`web`), or a background daemon that runs scheduled backups (`daemon`). Use `web` or `daemon` by activating the corresponding Spring profile.

**Override at runtime:**
```bash
java -Dspring.profiles.active=web -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

The `web` profile (`application-web.properties`) automatically sets `backup.mode=web` and enables the servlet web application type. The `daemon` profile (`application-daemon.properties`) automatically sets `backup.mode=daemon` and disables the embedded web server.

---

## backup.scheduled.users

**Type:** string (comma-separated list)  
**Default:** *(empty)*  
**Description:** The GitHub users/organizations to back up automatically when running in daemon mode (`-Dspring.profiles.active=daemon`). If empty, the daemon logs a warning on startup and performs no scheduled backups.

**Override at runtime:**
```bash
java -Dspring.profiles.active=daemon -Dbackup.scheduled.users=octocat,github -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

---

## backup.scheduled.interval.ms

**Type:** integer (milliseconds)  
**Default:** `86400000` (24 hours)  
**Description:** The delay between the end of one scheduled backup and the start of the next, when running in daemon mode. The first backup runs immediately on startup.

**Override at runtime:**
```bash
java -Dspring.profiles.active=daemon -Dbackup.scheduled.users=octocat -Dbackup.scheduled.interval.ms=3600000 -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

---

## spring.main.web-application-type

**Type:** string (`none` | `servlet`)  
**Default:** `none` (CLI mode)  
**Description:** Spring Boot property that controls whether an embedded web server is started. Set to `servlet` when using web mode. Managed automatically by the `web` Spring profile.

---

## server.port

**Type:** integer  
**Default:** `8080`  
**Description:** The HTTP port on which the web server listens. Only effective when running in web mode.

**Override at runtime:**
```bash
java -Dspring.profiles.active=web -Dserver.port=9000 -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

---

## logging.level.com.github.backup

**Type:** string (log level)  
**Default:** `INFO`  
**Description:** Log level for application classes. Increase to `DEBUG` for verbose output during troubleshooting.

**Override at runtime:**
```bash
java -Dlogging.level.com.github.backup=DEBUG -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

---

## logging.level.root

**Type:** string (log level)  
**Default:** `WARN`  
**Description:** Log level for everything outside the application's own packages, including Spring Boot and the GitHub and JGit libraries. It is deliberately quieter than `logging.level.com.github.backup` (`WARN` rather than `INFO`) so that CLI output stays readable; lower it to `INFO` or `DEBUG` when framework or library behavior needs to be diagnosed.

**Override at runtime:**
```bash
java -Dlogging.level.root=INFO -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

---

## usage.reporting.enabled

**Type:** boolean  
**Default:** `true`  
**Description:** Whether gh-backup reports that it was used to the maintainers' trace service. When on, two events are sent, both from a background thread that never blocks or delays a backup: `startup` once per process and `backup-completed` when a backup run finishes. Both carry the same two tags and nothing else: `version`, the program version, and `install`, a random installation ID that lets installations be counted rather than events. The installation ID is the value of the `TRACE_INSTALL_ID` environment variable when that is set and not blank; otherwise it is a random UUID written the first time reporting runs to `~/.config/gh-backup/trace-install-id` and reused after that (delete the file to get a new one). Nothing about the users, organizations or repositories being backed up is sent, and no usernames, hostnames, IP addresses or paths. The first time reporting runs on a machine, one `INFO` line saying so is logged, and a marker file (`~/.config/gh-backup/usage-reporting-notice-shown`) keeps it from repeating; delete that file to see the notice again. gh-backup has no settings file of its own, which is why the marker and the installation ID file live there. Setting the property to `false` sends nothing, logs no notice, writes no marker, and neither reads nor writes the installation ID file. As with every other property, it can also be set in an `application.properties` next to the JAR, and it is read from a `USAGE_REPORTING_ENABLED` environment variable, which is how the Docker image is configured. Independently of this property, the environment variables every trace client honours also turn reporting off and are checked first: `TRACE_USAGE_REPORTING=off` (also `false`, `0`, `no`) and `DO_NOT_TRACK=1` (also `true`, `yes`; see https://consoledonottrack.com). Details on what trace collects and why: https://danielstephenson.dev/usage-reporting

**Override at runtime:**
```bash
java -Dusage.reporting.enabled=false -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

or:
```bash
export USAGE_REPORTING_ENABLED=false
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

or, for every trace-reporting program at once:
```bash
export TRACE_USAGE_REPORTING=off   # or: export DO_NOT_TRACK=1
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

---

## usage.reporting.endpoint

**Type:** string (URL)  
**Default:** `https://trace.danielstephenson.dev`  
**Description:** The trace server usage events are sent to. Only worth changing to point a development build at a local stub.

---

## usage.reporting.key

**Type:** string  
**Default:** the key issued to gh-backup (bundled in `application.properties`)  
**Description:** The write-only key gh-backup authenticates to the trace server with. It can only record usage events; it cannot read anything. Leaving it empty disables reporting.

---

## GITHUB_TOKEN (environment variable)

**Type:** string  
**Default:** not set  
**Description:** A GitHub personal access token used to authenticate GitHub API requests. When set, the rate limit increases from 60 to 5,000 requests per hour. Not set via `application.properties`; pass it as an environment variable.

```bash
export GITHUB_TOKEN=ghp_yourTokenHere
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

---

## Docker environment variables

When the image built from the included `Dockerfile` is used, the container always starts in daemon mode. `docker-entrypoint.sh` translates the following environment variables into JVM system properties before launching the application.

| Variable | Maps to | Default in the image | Description |
|----------|---------|----------------------|-------------|
| `SCHEDULED_USERS` | `backup.scheduled.users` | *(empty)* | Comma-separated list of GitHub users/organizations to back up automatically. Passed to the application only when non-empty. |
| `BACKUP_INTERVAL_MS` | `backup.scheduled.interval.ms` | *(empty)* | Delay between scheduled backups, in milliseconds. Passed to the application only when non-empty; when empty, the default of `86400000` (24 hours) from `application-daemon.properties` applies. |
| `BACKUP_DIRECTORY` | `backup.directory` | `/backups` | Backup directory inside the container. |
| `GITHUB_TOKEN` | *(read directly by the application)* | *(unset)* | GitHub personal access token, as described above. |
| `USAGE_REPORTING_ENABLED` | `usage.reporting.enabled` *(read directly by the application)* | `true` | Set to `false` to stop the daemon reporting `startup` and `backup-completed` events to the trace service, as described above. |
| `TRACE_USAGE_REPORTING` | *(read directly by the vendored trace client)* | *(unset)* | `off` (or `false`, `0`, `no`) stops reporting regardless of `USAGE_REPORTING_ENABLED`. |
| `DO_NOT_TRACK` | *(read directly by the vendored trace client)* | *(unset)* | `1` (or `true`, `yes`) stops reporting too, per https://consoledonottrack.com. |
| `TRACE_INSTALL_ID` | *(read directly by the application)* | *(unset)* | Pins the installation ID sent as the `install` tag. When unset, the ID file is kept inside the container, so a recreated container counts as a new installation. |

Of these, `GITHUB_TOKEN`, `SCHEDULED_USERS`, `BACKUP_INTERVAL_MS`, `USAGE_REPORTING_ENABLED`, `TRACE_USAGE_REPORTING` and `DO_NOT_TRACK` are wired through to a `.env` file by the included `docker-compose.yml` (see `.env.example`); that file pins `BACKUP_DIRECTORY` to `/backups` and mounts the host's `./backups` directory there so backups persist outside the container.

**Example `.env`:**
```
GITHUB_TOKEN=ghp_yourTokenHere
SCHEDULED_USERS=octocat,github
BACKUP_INTERVAL_MS=3600000
USAGE_REPORTING_ENABLED=true
TRACE_USAGE_REPORTING=
DO_NOT_TRACK=
```
