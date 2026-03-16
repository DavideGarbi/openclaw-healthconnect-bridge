# Contributing

Thank you for your interest in contributing to OpenClaw HealthConnect Bridge!

## Getting Started

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/YOUR_USERNAME/openclaw-healthconnect-bridge.git
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
4. Open the project in Android Studio (Arctic Fox or later)
5. Make your changes
6. Test on a device with Health Connect installed

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use the project's `.editorconfig` settings
- Keep functions small and focused
- Use meaningful names for variables, functions, and classes

## Commit Messages

Use clear, descriptive commit messages:

```
feat: add blood oxygen trend chart
fix: correct sleep session timezone offset
docs: update setup instructions for API v2
refactor: extract permission handling to separate class
```

Prefixes: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`

## Pull Request Process

1. Ensure your code compiles: `./gradlew assembleDebug`
2. Run tests: `./gradlew test`
3. Keep PRs focused — one feature or fix per PR
4. Fill in the PR template describing your changes
5. Link any related issues
6. Request review from a maintainer

## Reporting Bugs

Use the [Bug Report](https://github.com/davidegarbi/openclaw-healthconnect-bridge/issues/new?template=bug_report.yml) issue template. Include:

- Steps to reproduce
- Expected vs actual behavior
- Device model and Android version
- Health Connect version

## Suggesting Features

Use the [Feature Request](https://github.com/davidegarbi/openclaw-healthconnect-bridge/issues/new?template=feature_request.yml) issue template.

## License

By contributing, you agree that your contributions will be licensed under the same [PolyForm Noncommercial License 1.0.0](LICENSE) as the project.
