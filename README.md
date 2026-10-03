# gh-backup

## Description

gh-backup is a Spring Boot tool for backing up public GitHub repositories for specified users or organizations. It is available as a command-line tool, a web application, and a daemon that runs scheduled backups automatically (with Docker support).

## Installation

### First Time Installation

1. Ensure [Java 17+](https://adoptium.net/) and [Maven 3.6+](https://maven.apache.org/) are installed.
2. Clone the repository:
   ```bash
   git clone https://github.com/Stephenson-Software/gh-backup.git
   cd gh-backup
   ```
3. Build the project:
   ```bash
   mvn clean package
   ```
4. The executable JAR will be created at `target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar`.

## Usage

### Documentation

- [User Guide](USER_GUIDE.md) – Getting started and common scenarios
- [Commands Reference](COMMANDS.md) – Complete list of all CLI commands and options
- [Configuration Guide](CONFIG.md) – Detailed configuration options

## Support

You can find the support Discord server [here](https://discord.gg/xXtuAQ2).

### Experiencing a bug?

Please fill out a bug report [here](https://github.com/Stephenson-Software/gh-backup/issues/new).

- [Known Bugs](https://github.com/Stephenson-Software/gh-backup/issues?q=is%3Aissue+is%3Aopen+label%3Abug)

## Usage reporting

Usage reporting is on by default: gh-backup reports that it was used to the maintainers' [trace](https://github.com/Stephenson-Software/trace) service at `https://trace.danielstephenson.dev`, sending a `startup` event carrying its name and version once per process, and a `backup-completed` event carrying only the version when a backup run finishes. Nothing else is sent: nothing about the users, organizations or repositories being backed up, and no usernames, hostnames, IP addresses, paths or command-line arguments. A one-line notice is logged the first time it runs on a machine (recorded in `~/.config/gh-backup/usage-reporting-notice-shown`).

Every event also carries a random installation ID (the tag `install`), so installations can be counted rather than events. It is the value of `TRACE_INSTALL_ID` when that is set, and otherwise a random UUID written the first time reporting runs to `~/.config/gh-backup/trace-install-id`, next to the notice marker, and reused after that (in the Docker image that directory is inside the container, so a recreated container counts as a new installation unless `TRACE_INSTALL_ID` is set). It identifies no person, account, host or address. Delete the file to get a new one. Every opt-out below also stops it: when reporting is off, no ID is made up and the file is neither read nor written.

To turn it off, any one of these is enough:

- `java -Dusage.reporting.enabled=false -jar ...` (or `usage.reporting.enabled=false` in an `application.properties` next to the JAR)
- `USAGE_REPORTING_ENABLED=false` in the environment (`.env` for the Docker daemon)
- `TRACE_USAGE_REPORTING=off` (also `false`, `0`, `no`) in the environment — the switch every trace client honours, checked before gh-backup's own setting
- `DO_NOT_TRACK=1` (also `true`, `yes`) in the environment, per [consoledonottrack.com](https://consoledonottrack.com)

See [`usage.reporting.enabled`](CONFIG.md#usagereportingenabled) in the Configuration Guide, and for what trace collects and why: https://github.com/Stephenson-Software/trace#usage-reporting

## Contributing

- [CONTRIBUTING.md](CONTRIBUTING.md)

## Testing

### Unit Tests

Linux / macOS:

```bash
mvn clean test
```

Windows:

```bat
mvn clean test
```

If you see `BUILD SUCCESS`, the tests have passed.

### Entrypoint Script Test

`docker-entrypoint.sh` is covered by a shell test that runs it against a stub `java` and asserts the arguments it builds. A POSIX shell is required, so on Windows it is run under WSL or Git Bash.

Linux / macOS:

```bash
sh docker-entrypoint-test.sh
```

Windows (Git Bash / WSL):

```bash
sh docker-entrypoint-test.sh
```

If you see `All docker-entrypoint.sh tests passed.`, the tests have passed.

## Development

### Building and Running Locally

1. Clone the repository and build:
   ```bash
   mvn clean package
   ```
2. Run in CLI mode:
   ```bash
   java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
   ```
3. Run in web mode:
   ```bash
   java -Dspring.profiles.active=web -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
   ```
   Then open `http://localhost:8080` in your browser.
4. Run in daemon mode (automatic scheduled backups):
   ```bash
   java -Dspring.profiles.active=daemon -Dbackup.scheduled.users=octocat,github -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
   ```
   See [Docker Deployment](#docker-deployment-daemon-mode) below for a containerized setup, and [COMMANDS.md](COMMANDS.md) for all daemon options.

## Docker Deployment (Daemon Mode)

The repository includes a `Dockerfile` and `docker-compose.yml` for running gh-backup as a background service that backs up configured users/organizations every 24 hours by default.

1. Create a `.env` file from the example and configure it:
   ```bash
   cp .env.example .env
   ```
2. Start the daemon:
   ```bash
   docker-compose up -d
   ```
3. View logs:
   ```bash
   docker-compose logs -f
   ```
4. Stop the daemon:
   ```bash
   docker-compose down
   ```

See [CONFIG.md](CONFIG.md) for the full list of daemon configuration options.

## Authors and Acknowledgement

### Developers

| Name | Main Contributions |
|------|--------------------|
| dmccoystephenson | Initial development and maintenance |

## License

This project is licensed under the [MIT License](LICENSE).

You are free to use, modify, and distribute this software under the terms of the MIT License.

See the [LICENSE](LICENSE) file for the full license text.

## Project Status

This project is in active development.

### Changelog

See [CHANGELOG.md](CHANGELOG.md) for a release-by-release summary of changes.