package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.gui.ScrollGui
import xyz.xenondevs.nova.addon.machines.recipe.ImplosionCompressorRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.createRecipeChoiceItem
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.RecipeGroup
import xyz.xenondevs.nova.ui.menu.item.ScrollLeftItem
import xyz.xenondevs.nova.ui.menu.item.ScrollRightItem
import xyz.xenondevs.nova.world.item.DefaultGuiItems

object ImplosionCompressingRecipeGroup : RecipeGroup<ImplosionCompressorRecipe>() {
    override val priority = 5
    override val icon = Items.IMPLOSION_COMPRESSOR.clientsideProvider
    override val texture = GuiTextures.RECIPE_IMPLOSION_COMPRESSOR

    override fun createGui(recipe: ImplosionCompressorRecipe): Gui {
        // Create time item to display the recipe's processing time
        val timeItem = DefaultGuiItems.INVISIBLE_ITEM.createClientsideItemBuilder()
            .setName("Time: ${recipe.time} ticks")
        
        return ScrollGui.items()
            .setStructure(
                "< . . . . . . . >",
                ". x x . t . r . .",
                ". . . . . . . . .")
            .addIngredient('r', createRecipeChoiceItem(recipe.results))
            .addIngredient('<', ::ScrollLeftItem)
            .addIngredient('>', ::ScrollRightItem)
            .addIngredient('t', timeItem)
            .setContent(recipe.inputs.map(::createRecipeChoiceItem))
            .build()
    }
}