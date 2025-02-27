package xyz.xenondevs.nova.addon.machines.recipe.group

import xyz.xenondevs.nova.addon.machines.recipe.ImplosionCompressorRecipe
import xyz.xenondevs.nova.addon.machines.registry.GuiTextures
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.ui.menu.explorer.recipes.group.ConversionRecipeGroup

object ImplosionCompressingRecipeGroup : ConversionRecipeGroup<ImplosionCompressorRecipe>() {
    override val priority = 5
    override val icon = Items.IMPLOSION_COMPRESSOR.clientsideProvider
    override val texture = GuiTextures.RECIPE_IMPLOSION_COMPRESSOR
}