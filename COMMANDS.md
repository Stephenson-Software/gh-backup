# Commands Reference

gh-backup is invoked from the command line via the built JAR. This document lists all supported arguments and modes.

## Basic Syntax

```
java [JVM_OPTIONS] -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar [OPTIONS] [USER/ORG...]
```

## Positional Arguments

### `<user/org> [user/org2] ...`

**Description:** One or more GitHub usernames or organization names whose public repositories should be backed up.  
**Required:** Yes (unless using interactive mode, web mode, or daemon mode). When no arguments are given in CLI mode, a usage message is printed and the application exits.  
**Example:**
```bash
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat github spring-projects
```

## Options

### `-i`, `--interactive` – Interactive Mode

**Description:** Start the tool in interactive mode, providing a prompt for issuing commands without restarting the application. Both forms are equivalent, and the flag is only recognized when it is the first argument; anywhere else it is an unknown option.  
**Usage:**
```bash
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar -i
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar --interactive
```

### `-h`, `--help` – Usage Message

**Description:** Print the usage message and exit with status `0`. Nothing is backed up and GitHub is not contacted.  
**Usage:**
```bash
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar --help
```

### Other Arguments Starting with a Hyphen

An argument of the form `--<property>=<value>` sets a configuration property, the same as `-D<property>=<value>` (except `backup.progress.overwrite`, which is read only from `-D`), and is not treated as a user or organization. Any other argument starting with a hyphen is an unknown option, since GitHub names cannot start with one: the error and the usage message are printed to standard error, nothing is backed up, even for the other arguments given, and the run exits with status `2`.

### Interactive Mode Commands

Once in interactive mode, the following commands are available at the `>` prompt:

| Command | Description |
|---------|-------------|
| `backup <user/org> [user/org2] ...` | Back up all public repositories for each GitHub user or organization given, one after another. Names are separated by spaces or tabs; one that fails is reported and the rest are still backed up |
| `status` | Display the backup directory path and a summary of all backed-up repositories |
| `help` | Show the list of available interactive commands |
| `exit` | Exit interactive mode and terminate the application |
| `quit` | Alias for `exit` (accepted at the prompt, but not listed by `help`) |

Commands are matched case-insensitively. An unrecognized command prints an error and returns to the prompt. Reaching the end of standard input (Ctrl-D on Linux/macOS, Ctrl-Z then Enter on Windows, or the end of piped input) ends the session the same way as `exit`.

## Exit Status

| Status | Meaning |
|--------|---------|
| `0` | Every user or organization was backed up, the usage message was printed, or an interactive session ended. A failure during an interactive session is reported at the prompt and does not change the status. |
| `1` | At least one user or organization could not be backed up, or at least one of its repositories failed. The remaining users, organizations and repositories are still attempted, each failure is printed to standard error, and the run ends with `Backup completed with errors.` |
| `2` | An unknown option was given. Nothing was backed up. |

Web and daemon modes run until stopped and do not use these statuses.

## JVM System Properties

These are passed with `-D` before the `-jar` flag.

### `-Dbackup.directory=<path>`

**Description:** Override the directory where repositories are saved.  
**Default:** `~/gh-backups/` (the `gh-backups` folder in the current user's home directory)  
**Example:**
```bash
java -Dbackup.directory=/mnt/storage/github-backups -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

### `-Dbackup.progress.overwrite=<true|false>`

**Description:** Control how clone/fetch progress is rendered. When `true`, progress percentages are rewritten in place on a single line using a carriage return. When `false`, each progress update is printed on its own line, which is easier to read in log files and CI output that do not interpret carriage returns.  
**Default:** `true`  
**Example:**
```bash
java -Dbackup.progress.overwrite=false -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

### `-Dspring.profiles.active=web`

**Description:** Start the application as a web server instead of a CLI tool.  
**Default:** CLI mode  
**Example:**
```bash
java -Dspring.profiles.active=web -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

### `-Dspring.profiles.active=daemon`

**Description:** Start the application as a background daemon that runs scheduled backups automatically, instead of a CLI tool. Set `backup.scheduled.users` as well: without it the daemon still starts, but logs a warning and performs no backups.  
**Default:** CLI mode  
**Example:**
```bash
java -Dspring.profiles.active=daemon -Dbackup.scheduled.users=octocat,github -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

### `-Dbackup.scheduled.users=<user/org1,user/org2,...>`

**Description:** Comma-separated list of GitHub users/organizations to back up automatically in daemon mode. Only effective when running with `-Dspring.profiles.active=daemon`.  
**Default:** *(empty)*  
**Example:**
```bash
java -Dspring.profiles.active=daemon -Dbackup.scheduled.users=octocat,github -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

### `-Dbackup.scheduled.interval.ms=<milliseconds>`

**Description:** Delay between scheduled backups in daemon mode. Only effective when running with `-Dspring.profiles.active=daemon`.  
**Default:** `86400000` (24 hours)  
**Example:**
```bash
java -Dspring.profiles.active=daemon -Dbackup.scheduled.users=octocat -Dbackup.scheduled.interval.ms=3600000 -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

### `-Dserver.port=<port>`

**Description:** Set the HTTP port for the web server (only effective in web mode).  
**Default:** `8080`  
**Example:**
```bash
java -Dspring.profiles.active=web -Dserver.port=9000 -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

### `-Dusage.reporting.enabled=<true|false>`

**Description:** Turn usage reporting off with `false`. When on, a `startup` event and a `backup-completed` event, carrying only the program version and a random installation ID, are sent to the maintainers' trace service; see [`usage.reporting.enabled`](CONFIG.md#usagereportingenabled) for exactly what is sent and the other ways to opt out.  
**Default:** `true`  
**Example:**
```bash
java -Dusage.reporting.enabled=false -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

## Environment Variables

### `GITHUB_TOKEN`

**Description:** A GitHub personal access token used to authenticate API requests. Increases the API rate limit from 60 to 5,000 requests per hour.  
**Required:** No  
**Example:**
```bash
export GITHUB_TOKEN=ghp_yourTokenHere
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

### `USAGE_REPORTING_ENABLED`

**Description:** Set to `false` to turn usage reporting off, the same as `-Dusage.reporting.enabled=false`. Any other value, or leaving it unset or empty, leaves reporting on.  
**Required:** No  
**Example:**
```bash
export USAGE_REPORTING_ENABLED=false
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

### `TRACE_USAGE_REPORTING` and `DO_NOT_TRACK`

**Description:** The opt-outs every trace client honours, checked before gh-backup's own setting. `TRACE_USAGE_REPORTING=off` (also `false`, `0`, `no`) or `DO_NOT_TRACK=1` (also `true`, `yes`) turns usage reporting off regardless of `usage.reporting.enabled`.  
**Required:** No  
**Example:**
```bash
export TRACE_USAGE_REPORTING=off   # or: export DO_NOT_TRACK=1
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

### `TRACE_INSTALL_ID`

**Description:** Pins the installation ID sent as the `install` tag of every usage event. The value is trimmed; when it is unset, blank or longer than 255 characters, a random ID stored in `~/.config/gh-backup/trace-install-id` is used instead.  
**Required:** No

## Web API Endpoints

When running in web mode (`-Dspring.profiles.active=web`), the following REST endpoints are available:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/backups` | Start a backup for a given user or organization |
| `GET` | `/api/backups/status` | Return backup status and a list of all backed-up repositories |

### POST /api/backups

**Request body:**
```json
{ "userOrOrg": "octocat" }
```

`userOrOrg` is required and must be a valid GitHub username or organization name: 1–39 characters of letters, digits, and single hyphens, neither starting nor ending with a hyphen.

**Response (`200 OK`):**
```json
{ "success": true, "message": "Backup completed successfully for octocat" }
```

The response is sent when the backup finishes. If the GitHub rate limit is reached, the request stays open while gh-backup waits for it to reset, which can take up to an hour; see [Rate Limits](USER_GUIDE.md#rate-limits).

**Response (`400 Bad Request`):** returned when `userOrOrg` is missing, blank, or does not match the username format. No backup is started, and `message` gives the reason: `User or organization name is required` for a missing or blank name, `Invalid GitHub username format` otherwise.
```json
{ "success": false, "message": "Invalid GitHub username format" }
```

**Response (`500 Internal Server Error`):** returned when the backup itself fails, for example because the backup directory cannot be created.
```json
{ "success": false, "message": "Error: Failed to create backup directory '/backups/octocat'" }
```

The same response is returned when any repository fails to clone or fetch. The remaining repositories are still backed up first, and `message` names the ones that failed:
```json
{ "success": false, "message": "Error: Failed to back up 1 of 8 repositories for octocat: Hello-World" }
```

### GET /api/backups/status

**Response (`200 OK`):**
```json
{
  "totalUsers": 1,
  "totalRepositories": 8,
  "users": [
    {
      "name": "octocat",
      "repositoryCount": 8,
      "repositories": [
        { "name": "Hello-World", "lastUpdated": "2026-01-10 12:30:45" }
      ]
    }
  ]
}
```

`lastUpdated` is the time, in the server's local time zone, at which the repository was last cloned or successfully fetched; the `status` command reports the same value.

When the backup directory does not exist yet, `200 OK` is still returned, with `totalUsers` and `totalRepositories` set to `0` and an empty `users` array. The same empty structure is returned with `500 Internal Server Error` if the status cannot be read.
