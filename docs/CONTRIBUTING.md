# ForkKnight Contributing Guidelines

Thank you for considering contributing to ForkKnight! This document outlines the process and guidelines for contributing to this educational Git client project.

## How to Contribute

### Reporting Issues
Before submitting an issue, please check if it has already been reported. When reporting a new issue, include:
- Clear description of the problem
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable
- Your environment (OS, Java version, etc.)

### Suggesting Enhancements
Feature requests are welcome! Please consider:
- How the enhancement aligns with the educational goals
- Whether it maintains the knightly vocabulary theme
- Any potential complexity trade-offs
- Provide mockups or examples if possible

### Submitting Changes
1. **Fork the repository** on GitHub
2. **Create a topic branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes** following the guidelines below
4. **Commit your changes** with clear, descriptive messages
5. **Push to your fork** and submit a Pull Request

## Development Setup

### Prerequisites
- JDK 17 or later (we recommend JDK 21 LTS)
- Git
- Internet connection for dependency resolution

### Getting Started
```bash
# Clone your fork
git clone https://github.com/your-username/ForkKnight.git
cd ForkKnight

# Verify the build works
./gradlew build

# Run the application
./gradlew run
```

### Recommended Tools
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions
- **Build Tool**: Gradle (the wrapper is included)
- **Version Control**: Git (command line or GUI client)

## Coding Standards

### Java Style
- Follow the existing code style in the project
- 4-space indentation (no tabs)
- Maximum line length: 120 characters (prefer 80-100 for readability)
- Use `final` for fields and variables that don't change after initialization
- Prefer explicit access modifiers (`private`, `protected`, `public`)

### Naming Conventions
- **Classes**: `PascalCase` (e.g., `Feat`, `Chronicle`)
- **Methods and variables**: `camelCase` (e.g., `surveyTrail()`, `currentFeat`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `VAULT_CAPACITY`, `MAX_SEARCH_RESULTS`)
- **Packages**: `lowercase` (e.g., `forkknight.core`)
- **Interfaces**: Adjectives or nouns (`-able` suffix when appropriate)

### Documentation
- **Javadoc**: All public classes and methods must have Javadoc comments
- **Code comments**: Explain why, not what (unless the what is complex)
- **TODO comments**: Use sparingly and include issue reference if possible: `// TODO: [issue#123] Explain why this workaround is needed`
- **Commit messages**: Follow conventional commits format when possible

## The Knightly Vocabulary Theme

ForkKnight's unique feature is its knightly/anime vocabulary metaphor for Git concepts. When contributing:

### Maintain Consistency
- Use established terms from the VOCABULARY_REFERENCE.md
- If introducing new Git operations, create appropriate knightly metaphors
- Ensure new terms fit the medieval/anime theme consistently

### Metaphor Guidelines
- **Feudal/Medieval**: Knights, banners, feats, sigils, chronicles, councils
- **Anime/Gaming**: Kamui (from various anime), scrying, quests, etc.
- **Avoid**: Modern technical terms leaking into UI/domain (except in isolated Git layer)
- **Educational Value**: Each metaphor should help users understand the underlying Git concept

### Examples of Good Metaphors
- **Feat** = commit (a sealed achievement)
- **Banner** = branch (a campaign front to pursue)
- **Sigil** = tag (a permanent mark of importance)
- **Kamui** = stash (temporary vanishing of works)
- **Scry** = search (magical divination to find hidden knowledge)

## Making Changes

### UI Changes
- All UI changes should happen on the JavaFX Application Thread
- Use `Platform.runLater()` for UI updates from background threads
- Consider accessibility: color contrast, keyboard navigation, screen readers
- Follow existing UI patterns in the codebase

### Core Logic Changes
- The `Chronicle` class is the gateway to Git operations - keep it focused
- Maintain the CODEX pattern (knightly terms → Git commands)
- Preserve thread isolation: Git operations happen off-JavaFX thread
- Consider performance implications of changes to frequently-called methods

### Testing
- **Unit Tests**: Aim for high coverage of core logic (Vault, Scryer, Weave, Chronicle parsing)
- **Integration Tests**: Test complete user workflows where possible
- **UI Tests**: Consider TestFX for testing UI components (optional but valuable)
- **Test Naming**: Use descriptive names that explain what scenario is being tested
- **Test Constants**: Use meaningful constants instead of magic numbers

### Performance Considerations
- Be mindful of object creation in frequently-called methods
- Consider caching strategies for expensive operations
- Remember that Git operations are inherently slow (process creation) - optimize around them
- UI responsiveness is paramount - long operations must be backgrounded

## Pull Request Process

### Before Submitting
1. Ensure your code compiles: `./gradlew build`
2. Run all tests: `./gradlew test`
3. Check for warnings: `./gradlew check`
4. Format your code if needed (we don't have an auto-formatter yet, but follow existing style)
5. Update documentation if your changes affect usage

### Pull Request Checklist
- [ ] Descriptive title explaining what the PR does
- [ ] Detailed description in the PR body
- [ ] Reference to any related issues (`Fixes #123` or `Related to #456`)
- [ ] Screenshots/GIFs for UI changes (highly encouraged)
- [ ] All tests pass
- [ ] Documentation updated if needed
- [ ] No breaking changes without excellent justification
- [ ] Follows the knightly vocabulary theme (if applicable)

### Review Process
1. Maintainers will review your PR within [timeframe]
2. You may be asked to make changes or clarifications
3. Once approved, a maintainer will merge your PR
4. Your branch may be deleted after merging (unless you request otherwise)

## Licensing

By contributing to ForkKnight, you agree that your contributions will be licensed under the project's license. Please ensure you have the right to contribute any code you submit.

## Questions?

If you have questions about contributing, please:
- Check the existing documentation
- Look at recent commits and PRs for examples
- Ask in the issue tracker if something is unclear

Thank you for helping make ForkKnight better!

---

*These guidelines help ensure that ForkKnight remains a high-quality educational tool that effectively teaches Git concepts through its unique knightly vocabulary approach.*