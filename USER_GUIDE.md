# User Guide

## Prerequisites

- Java 17 or higher installed on your system
- A GitHub personal access token (optional, but recommended for higher API rate limits)

A separate Git installation is not needed: repositories are cloned and fetched with JGit, a Git implementation bundled in the JAR.

## First Steps

After building the project (`mvn clean package`), you can start backing up GitHub repositories immediately.

### Quick Start – CLI Mode

Back up all public repositories of a GitHub user or organization:

```bash
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

Repositories will be saved to `~/gh-backups/` by default.

### Quick Start – Web UI Mode

Start the web server:

```bash
java -Dspring.profiles.active=web -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

Open `http://localhost:8080` in your browser to use the web interface.

### Quick Start – Daemon Mode

Run the application as a background daemon that backs up configured users/organizations automatically every 24 hours:

```bash
java -Dspring.profiles.active=daemon -Dbackup.scheduled.users=octocat,github -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar
```

The first backup runs immediately on startup, then repeats at the configured interval. See [CONFIG.md](CONFIG.md) for daemon options, and the repository's `Dockerfile`/`docker-compose.yml` for a containerized deployment.

## Common Scenarios

### Backing up a single user or organization

```bash
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

### Backing up multiple users or organizations at once

```bash
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat github spring-projects
```

### Using GitHub authentication for higher rate limits

Set the `GITHUB_TOKEN` environment variable before running:

```bash
export GITHUB_TOKEN=your_github_token_here
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

To create a token: GitHub Settings → Developer settings → Personal access tokens → Generate new token. The `public_repo` scope is sufficient for public repositories.

The token is validated on startup. If it is invalid or expired, a warning is printed and the tool falls back to anonymous access with the lower rate limit rather than failing.

### Using a custom backup directory

```bash
java -Dbackup.directory=/path/to/backup -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

Windows example:
```bat
java -Dbackup.directory=C:\Backups\GitHub -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar octocat
```

### Using interactive mode

Start in interactive mode for an on-screen menu, using either `-i` or the equivalent `--interactive`:

```bash
java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar -i
```

Available interactive commands:

| Command | Description |
|---------|-------------|
| `backup <user/org>` | Back up all public repositories for the specified user or organization |
| `status` | Show the backup directory, number of users/organizations, and a list of all backed up repositories |
| `help` | Show the list of available interactive commands |
| `exit` | Quit the interactive session |
| `quit` | Alias for `exit` |

Ending standard input (Ctrl-D on Linux/macOS, Ctrl-Z then Enter on Windows) also quits the session, as does reaching the end of piped input — for example, `printf 'status\n' | java -jar target/gh-backup-2.0.0-SNAPSHOT-8-8-2026.jar -i` prints the status and exits.

### Example interactive session

```
> backup octocat
Fetching repositories for: octocat
Found 8 public repositories
...

> status
Backup Status
=============
Backup directory: /home/user/gh-backups

octocat/ (8 repositories)
  - Hello-World (last updated: 2026-01-10 12:30:45)
  - Spoon-Knife (last updated: 2026-01-10 12:30:47)
  ...

Total: 1 users/organizations, 8 repositories

> exit
Exiting interactive mode...
```

## Output Structure

Repositories are organized by user or organization inside the backup directory:

```
~/gh-backups/
├── octocat/
│   ├── Hello-World/
│   ├── Spoon-Knife/
│   └── ...
└── github/
    ├── docs/
    └── roadmap/
```

Each subdirectory is a full Git repository. If a repository already exists on disk, running the tool again will update it with `git fetch`. A fetch downloads new commits and updates the remote-tracking branches (`origin/<branch>`), but does not move the local branch or change the checked-out files; run `git merge` or `git pull` inside the backup to bring its working tree up to date.

## Rate Limits

| Access type | Limit |
|-------------|-------|
| Anonymous | 60 requests per hour |
| Authenticated (token) | 5,000 requests per hour |

Using a GitHub token is strongly recommended when backing up users or organizations with many repositories.
