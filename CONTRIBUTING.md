# Contributing

## Thank You

Thank you for your interest in contributing to gh-backup! This guide will help you get started.

## Links

- [Website](https://dansplugins.com)
- [Discord](https://discord.gg/xXtuAQ2)

## Requirements

- A GitHub account
- Git installed on your local machine
- Java 17 or higher
- Maven 3.6 or higher
- A Java IDE or text editor

## Getting Started

1. [Sign up for GitHub](https://github.com/signup) if you don't have an account.
2. Fork the repository by clicking **Fork** at the top right of the repo page.
3. Clone your fork:
   ```bash
   git clone https://github.com/<your-username>/gh-backup.git
   ```
4. Open the project in your IDE.
5. Build the project:
   ```bash
   mvn clean package
   ```
   If you encounter errors, please open an issue.

## Identifying What to Work On

### Issues

Work items are tracked as [GitHub issues](https://github.com/Stephenson-Software/gh-backup/issues).

### Milestones

Issues are grouped into [milestones](https://github.com/Stephenson-Software/gh-backup/milestones) representing upcoming releases.

## Making Changes

1. Make sure an issue exists for the work. If not, create one.
2. Switch to the `main` branch: `git checkout main`
3. Create a feature branch: `git checkout -b <descriptive-branch-name>`
4. Make your changes.
5. Test your changes (see [Testing](#testing)).
6. Commit: `git commit -m "Brief description of changes"`
7. Push to your fork: `git push origin <branch-name>`
8. Open a pull request against the `main` branch of the upstream repository. Include a description and link the related issue using `#<number>`.
9. Address any review feedback.
10. Once approved, the maintainer will merge the PR into `main`.

## Testing

Run the unit tests with:

Linux / macOS:
```bash
mvn clean test
```

Windows:
```bat
mvn clean test
```

The Docker entrypoint script is covered by a separate shell test, which runs `docker-entrypoint.sh` against a stub `java` and asserts the arguments it builds. It requires a POSIX shell, so run it on Linux / macOS (or under WSL or Git Bash on Windows):

```bash
sh docker-entrypoint-test.sh
```

Both suites run in CI on every pull request.

When you change `<version>` in `pom.xml`, also update every `gh-backup-<version>.jar` reference in `README.md`, `USER_GUIDE.md`, `CONFIG.md`, `COMMANDS.md` and `Dockerfile`. `DocumentationVersionTest` fails the build and lists each reference that still names another version.

## Questions

Ask in the [Discord server](https://discord.gg/xXtuAQ2).
