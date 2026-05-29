---
name: java-conventions
description: Java coding conventions and best practices for this project
runAs: inline
---

# Java Coding Conventions

When writing or modifying Java code in this project, follow these conventions:

## Code Style

1. **Indentation**: 4 spaces, no tabs
2. **Line length**: 120 characters max
3. **Braces**: Opening brace on same line
4. **Naming**: camelCase for variables/methods, PascalCase for classes

## Project Structure

```
src/main/java/
  com/example/
    config/      — Configuration classes
    controller/  — REST controllers
    service/     — Business logic
    model/       — Data models
    repository/  — Data access
```

## Best Practices

1. **Use Lombok** — `@Getter`, `@Setter`, `@Builder` to reduce boilerplate
2. **Prefer composition** — Over inheritance
3. **Null safety** — Use `Optional` instead of returning null
4. **Immutability** — Prefer `final` fields and immutable collections
5. **Error handling** — Use custom exceptions, not generic RuntimeException

## Testing

1. **Unit tests** — For business logic
2. **Integration tests** — For database/external calls
3. **Naming**: `should[ExpectedBehavior]When[Condition]`

## Comments

- Write **why**, not **what**
- Use Javadoc for public APIs
- Keep comments up-to-date with code changes