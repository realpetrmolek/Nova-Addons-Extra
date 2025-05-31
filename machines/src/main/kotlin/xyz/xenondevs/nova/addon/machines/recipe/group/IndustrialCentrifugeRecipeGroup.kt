package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.nova.addon.machines.recipe.IndustrialCentrifugeRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.item.ScrollLeftItem
import xyz.xenondevs.nova.ui.menu.item.ScrollRightItem
import xyz.xenondevs.nova.world.item.DefaultGuiItems

object IndustrialCentrifugeRecipeGroup : RecipeGroup<IndustrialCentrifugeRecipe>() {
    override val priority = 5
    override val icon = Items.INDUSTRIAL_CENTRIFUGE.clientsideProvider
    override val texture = GuiTextures.RECIPE_INDUSTRIAL_CENTRIFUGE

    override fun createGui(recipe: IndustrialCentrifugeRecipe): Gui {
        // Create time item to display the recipe's processing time
        val timeItem = DefaultGuiItems.INVISIBLE_ITEM.createClientsideItemBuilder()
            .setName("Time: ${recipe.time} ticks")

        return Gui.normal()
            .setStructure(
                "< . . . . . . . >",
                ". i . t . r r r .",
                ". . . . . r r . .")
            .addIngredient('i', createRecipeChoiceItem(recipe.inputs.first()))
            .addIngredient('r', createRecipeChoiceItem(recipe.results))
            .addIngredient('<', ::ScrollLeftItem)
            .addIngredient('>', ::ScrollRightItem)
            .addIngredient('t', timeItem)
            .build()
    }
}