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

## Creating New Machine Replicas

When creating a new processing machine that replicates an existing one (like Alloy Smelter), follow this comprehensive checklist:

### Code Files to Create:
- [ ] **TileEntity class**: `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/tileentity/processing/NewMachine.kt`
- [ ] **Recipe class**: Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/recipe/Recipes.kt`
- [ ] **Recipe deserializer**: Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/recipe/RecipeDeserializer.kt`
- [ ] **Recipe group**: `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/recipe/group/NewMachineRecipeGroup.kt`
- [ ] **Progress item**: Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/gui/ProgressItems.kt`

### Registry Updates:
- [ ] Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/registry/Blocks.kt`
- [ ] Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/registry/Items.kt`
- [ ] Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/registry/RecipeTypes.kt`
- [ ] Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/registry/GuiItems.kt`
- [ ] Add to `machines/src/main/kotlin/xyz/xenondevs/nova/addon/machines/registry/GuiTextures.kt`

### Resource Files to Create:
- [ ] `machines/src/main/resources/configs/new_machine.yml`
- [ ] `machines/src/main/resources/assets/models/block/new_machine_off.json`
- [ ] `machines/src/main/resources/assets/models/block/new_machine_on.json`
- [ ] `machines/src/main/resources/recipes/minecraft/shaped/new_machine.json`
- [ ] `machines/src/main/resources/recipes/machines/new_machine/` directory with example recipes
- [ ] Localization entry in `machines/src/main/resources/assets/lang/en_us.json`

### Texture Files to Copy from Template Machine:
- [ ] `textures/block/template_off.png` → `textures/block/new_machine_off.png`
- [ ] `textures/block/template_on.png` → `textures/block/new_machine_on.png`
- [ ] `textures/block/template_on.png.mcmeta` → `textures/block/new_machine_on.png.mcmeta`
- [ ] `textures/waila/template.png` → `textures/waila/new_machine.png`
- [ ] `textures/waila/template_1.png` → `textures/waila/new_machine_1.png`
- [ ] **`textures/gui/recipe/template.png` → `textures/gui/recipe/new_machine.png`** ⚠️ CRITICAL - Don't forget this one!
- [ ] **`textures/item/gui/progress/template/` → `textures/item/gui/progress/new_machine/`** ⚠️ CRITICAL - Copy entire progress directory (0.png to 16.png)!

### Multi-Input Recipe JSON Format:
```json
{
  "inputs": [
    {
      "items": ["minecraft:copper_ingot", "machines:copper_dust"],
      "count": 3
    },
    {
      "items": ["machines:tin_ingot", "machines:tin_dust"],
      "count": 1
    }
  ],
  "outputs": [
    {
      "id": "machines:bronze_ingot",
      "amount": 4
    }
  ],
  "time": 1000
}
```

### Testing:
- [ ] Run `./gradlew :machines:build` to verify compilation
- [ ] Test in-game functionality