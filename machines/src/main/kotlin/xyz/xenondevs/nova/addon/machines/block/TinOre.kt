package xyz.xenondevs.nova.addon.machines.block

import org.bukkit.GameMode
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.addon.machines.registry.Items
import xyz.xenondevs.nova.context.Context
import xyz.xenondevs.nova.context.intention.DefaultContextIntentions.BlockBreak
import xyz.xenondevs.nova.context.param.DefaultContextParamTypes
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.behavior.BlockBehavior
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import kotlin.random.Random

class TinOre(private val isDeepslate: Boolean) : BlockBehavior {

    override fun getDrops(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockBreak>): List<ItemStack> {
        if (ctx[DefaultContextParamTypes.SOURCE_PLAYER]?.gameMode == GameMode.CREATIVE)
            return emptyList()

        if (ctx[DefaultContextParamTypes.TOOL_ITEM_STACK]?.getEnchantmentLevel(Enchantment.SILK_TOUCH) != 0)
            return listOf(state.block.item!!.createItemStack())

        // Apply fortune logic using the more elegant Minecraft implementation
        val fortune = ctx[DefaultContextParamTypes.TOOL_ITEM_STACK]?.getEnchantmentLevel(Enchantment.FORTUNE) ?: 0

        val dropAmount = if (fortune > 0) {
            // Calculate bonus multiplier
            val i = Random.nextInt(fortune + 2) - 1

            // Ensure multiplier isn't negative
            val multiplier = maxOf(0, i)

            // Apply multiplier to base drop amount (1)
            1 * (multiplier + 1)
        } else {
            1 // Base drop amount with no fortune
        }

        // Return Raw Tin with fortune applied
        return listOf(Items.RAW_TIN.createItemStack(dropAmount))
    }
    
    override fun getExp(pos: BlockPos, state: NovaBlockState, ctx: Context<BlockBreak>) = Random.nextInt(1, 3)
    
}