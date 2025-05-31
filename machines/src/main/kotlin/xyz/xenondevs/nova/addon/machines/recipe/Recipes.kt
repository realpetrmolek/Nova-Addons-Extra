package xyz.xenondevs.nova.addon.machines.recipe

import net.kyori.adventure.key.Key
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.potion.PotionEffectType
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.world.block.tileentity.network.type.fluid.FluidType
import xyz.xenondevs.nova.world.item.recipe.ConversionNovaRecipe
import xyz.xenondevs.nova.world.item.recipe.MultiInputChoiceRecipe
import xyz.xenondevs.nova.world.item.recipe.MultiResultRecipe
import xyz.xenondevs.nova.world.item.recipe.NovaRecipe

class AlloySmelterRecipe(
    override val id: Key,
    override val inputs: List<RecipeChoice>,
    val time: Int,
    val inputCounts: List<Int> = listOf(),
    override val results: List<ItemStack> = listOf()
) : NovaRecipe, MultiInputChoiceRecipe, MultiResultRecipe {
    override val type = RecipeTypes.ALLOY_SMELTER
}

class IndustrialCentrifugeRecipe(
    override val id: Key,
    override val inputs: List<RecipeChoice>,
    val time: Int,
    val inputCounts: List<Int> = listOf(),
    override val results: List<ItemStack> = listOf()
) : NovaRecipe, MultiInputChoiceRecipe, MultiResultRecipe {
    override val type = RecipeTypes.INDUSTRIAL_CENTRIFUGE
}

class ImplosionCompressorRecipe(
    override val id: Key,
    override val inputs: List<RecipeChoice>,
    val time: Int,
    val inputCounts: List<Int> = listOf(),
    override val results: List<ItemStack> = listOf()
) : NovaRecipe, MultiInputChoiceRecipe, MultiResultRecipe {
    override val type = RecipeTypes.IMPLOSION_COMPRESSOR
}

class ElectricBlastFurnaceRecipe(
    override val id: Key,
    override val inputs: List<RecipeChoice>,
    val time: Int,
    val inputCounts: List<Int> = listOf(),
    override val results: List<ItemStack> = listOf(),
    val requiredHeat: Int = 0
) : NovaRecipe, MultiInputChoiceRecipe, MultiResultRecipe {
    override val type = RecipeTypes.ELECTRIC_BLAST_FURNACE
}

class PulverizerRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int,
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.PULVERIZER
}

class PlatePressRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.PLATE_PRESS
}

class GearPressRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.GEAR_PRESS
}

class FluidInfuserRecipe(
    override val id: Key,
    val mode: InfuserMode,
    val fluidType: FluidType,
    val fluidAmount: Long,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.FLUID_INFUSER
    
    enum class InfuserMode {
        INSERT,
        EXTRACT
    }
    
}

class ElectricBrewingStandRecipe(
    override val id: Key,
    override val inputs: List<RecipeChoice>,
    val result: PotionEffectType,
    val defaultTime: Int,
    val redstoneMultiplier: Double,
    val glowstoneMultiplier: Double,
    val maxDurationLevel: Int,
    val maxAmplifierLevel: Int
) : NovaRecipe, MultiInputChoiceRecipe {
    override val type = RecipeTypes.ELECTRIC_BREWING_STAND
}

class CrystallizerRecipe(
    id: Key,
    input: RecipeChoice,
    result: ItemStack,
    time: Int
) : ConversionNovaRecipe(id, input, result, time) {
    override val type = RecipeTypes.CRYSTALLIZER
}
