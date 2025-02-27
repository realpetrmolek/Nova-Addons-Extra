package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.nova.addon.machines.recipe.AlloySmelterRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.ConversionRecipeGroup

object AlloySmeltingRecipeGroup : ConversionRecipeGroup<AlloySmelterRecipe>() {
    override val priority = 4
    override val icon = Items.ALLOY_SMELTER.clientsideProvider
    override val texture = GuiTextures.RECIPE_ALLOY_SMELTER
}