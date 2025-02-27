# Nova Addons Extra Development Guide

## Build Commands
- Build all addons: `./gradlew build`
- Clean build: `./gradlew clean build`
- Build specific addon: `./gradlew :jetpacks:build` (or machines, logistics, etc.)
- Run checks: `./gradlew check`
- Build jar: `./gradlew jar`

## Code Style Guidelines
- Use Kotlin idioms and follow Kotlin conventions
- Package structure: `xyz.xenondevs.nova.addon.[module]`
- Classes use PascalCase, functions/variables use camelCase
- Prefer immutability (val over var) when possible
- Type all parameters and return values for clarity
- Import statements: organize by package hierarchy, no wildcard imports
- Error handling: use kotlin `Result` type or exceptions with meaningful messages
- Use sealed classes for exhaustive state handling 
- Documentation: document public APIs with KDoc comments

## Recipe Structure
For Alloy Smelter recipes:
```json
{
  "inputs": ["item1", "item2"],
  "result": "output_item",
  "amount": 1,
  "time": 1000
}
```