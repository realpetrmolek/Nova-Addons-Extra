package xyz.xenondevs.nova.addon.machines.tileentity.processing

import net.kyori.adventure.key.Key
import net.minecraft.core.particles.ParticleTypes
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.mapNonNull
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.ProgressArrowItem
import xyz.xenondevs.nova.addon.machines.gui.AlloySmelterProgressItem
import xyz.xenondevs.nova.addon.machines.recipe.AlloySmelterRecipe
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.ALLOY_SMELTER
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.util.energyConsumption
import xyz.xenondevs.nova.addon.machines.util.speedMultipliedValue
import xyz.xenondevs.nova.addon.simpleupgrades.gui.OpenUpgradesItem
import xyz.xenondevs.nova.addon.simpleupgrades.registry.UpgradeTypes
import xyz.xenondevs.nova.addon.simpleupgrades.storedEnergyHolder
import xyz.xenondevs.nova.addon.simpleupgrades.storedUpgradeHolder
import xyz.xenondevs.nova.config.entry
import xyz.xenondevs.nova.ui.menu.EnergyBar
import xyz.xenondevs.nova.ui.menu.sideconfig.OpenSideConfigItem
import xyz.xenondevs.nova.ui.menu.sideconfig.SideConfigMenu
import xyz.xenondevs.nova.util.BlockSide
import xyz.xenondevs.nova.util.PacketTask
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.particle.particle
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.state.NovaBlockState
import xyz.xenondevs.nova.world.block.state.property.DefaultBlockStateProperties
import xyz.xenondevs.nova.world.block.tileentity.NetworkedTileEntity
import xyz.xenondevs.nova.world.block.tileentity.menu.TileEntityMenuClass
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.EXTRACT
import xyz.xenondevs.nova.world.block.tileentity.network.type.NetworkConnectionType.INSERT
import xyz.xenondevs.nova.world.item.recipe.NovaRecipe
import xyz.xenondevs.nova.world.item.recipe.RecipeManager
import kotlin.math.max

private val BLOCKED_SIDES = enumSetOf(BlockSide.FRONT)

private val MAX_ENERGY = ALLOY_SMELTER.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = ALLOY_SMELTER.config.entry<Long>("energy_per_tick")
private val ALLOY_SMELTER_SPEED = ALLOY_SMELTER.config.entry<Int>("speed")

class AlloySmelter(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {

    private val inputInv = storedInventory("input", 3, false, IntArray(3) { 64 }, ::handleInputUpdate, null)
    private val outputInv = storedInventory("output", 2, false, IntArray(2) { 64 }, ::handleOutputUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inputInv to INSERT, outputInv to EXTRACT, blockedSides = BLOCKED_SIDES)

    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val alloySmelterSpeed by speedMultipliedValue(ALLOY_SMELTER_SPEED, upgradeHolder)
    
    private var active: Boolean = blockState.getOrThrow(BlockStateProperties.ACTIVE)
        set(active) {
            if (field != active) {
                field = active
                updateBlockState(blockState.with(BlockStateProperties.ACTIVE, active))
            }
        }

    private var timeLeft by storedValue("alloySmelterTime") { 0 }

    private var currentRecipe: AlloySmelterRecipe? by storedValue<Key>("currentRecipe").mapNonNull(
        { RecipeManager.getRecipe(RecipeTypes.ALLOY_SMELTER, it) },
        NovaRecipe::id
    )

    private val particleTask = PacketTask(
        particle(ParticleTypes.SMOKE) {
            val facing = blockState.getOrThrow(DefaultBlockStateProperties.FACING)
            location(pos.location.add(0.5, 0.8, 0.5).advance(facing, 0.6))
            offset(0.05, 0.2, 0.05)
            speed(0f)
        },
        6,
        ::getViewers
    )

    override fun handleDisable() {
        super.handleDisable()
        particleTask.stop()
        active = false
    }

    override fun handleTick() {
        if (energyHolder.energy >= energyPerTick) {
            if (timeLeft == 0) {
                takeItem()

                if (particleTask.isRunning())
                    particleTask.stop()
                active = false
            } else {
                timeLeft = max(timeLeft - alloySmelterSpeed, 0)
                energyHolder.energy -= energyPerTick

                if (!particleTask.isRunning())
                    particleTask.start()
                active = true

                if (timeLeft == 0) {
                    currentRecipe?.let { recipe ->
                        // Add all output items to the output inventory
                        recipe.outputs.forEach { output ->
                            outputInv.addItem(SELF_UPDATE_REASON, output.clone())
                        }
                    }
                    currentRecipe = null
                }

                menuContainer.forEachMenu(AlloySmelterMenu::updateProgress)
            }

        } else {
            if (particleTask.isRunning()) {
                particleTask.stop()
            }
            active = false
        }
    }

    private fun takeItem() {
        // Get all non-empty input items
        val inputItems = inputInv.items.filterNotNull()

        if (inputItems.isEmpty()) return

        // Get all alloy smelter recipes
        val allRecipes = RecipeManager.novaRecipes[RecipeTypes.ALLOY_SMELTER]?.values
            ?.filterIsInstance<AlloySmelterRecipe>() ?: return

        // Find a matching recipe
        recipeLoop@ for (recipe in allRecipes) {
            // Check if we have enough input items
            if (recipe.inputs.size > inputItems.size) continue

            // Create a mutable map of slot index to item stack
            val availableItemsBySlot = inputInv.items.withIndex()
                .filter { it.value != null }
                .associate { it.index to it.value!! }
                .toMutableMap()

            // Try to match each input choice
            val matchedInputs = mutableListOf<Pair<Int, ItemStack>>() // Pair of slot index and item

            for ((index, choice) in recipe.inputs.withIndex()) {
                // Get required count for this input (default to 1 if not specified)
                val requiredCount = if (recipe.inputCounts.isNotEmpty() && index < recipe.inputCounts.size) 
                    recipe.inputCounts[index] else 1
                
                // Find slot with matching item and enough quantity
                val matchingEntry = availableItemsBySlot.entries.firstOrNull { (_, item) -> 
                    choice.test(item) && item.amount >= requiredCount 
                }

                if (matchingEntry != null) {
                    val (slotIndex, item) = matchingEntry
                    availableItemsBySlot.remove(slotIndex)
                    matchedInputs.add(Pair(slotIndex, item))
                } else {
                    // If any input can't be matched, move to the next recipe
                    continue@recipeLoop
                }
            }

            // If we got here, we matched all inputs
            // Check if we can hold ALL outputs
            val canHoldAllOutputs = recipe.outputs.all { outputInv.canHold(it) }
            
            if (canHoldAllOutputs) {
                // Remove the required amount of each matched item
                for (i in matchedInputs.indices) {
                    val (slotIndex, _) = matchedInputs[i]
                    val requiredCount = if (recipe.inputCounts.isNotEmpty() && i < recipe.inputCounts.size) 
                        recipe.inputCounts[i] else 1
                    
                    // Get the item directly from the inventory to ensure we're working with the current state
                    val item = inputInv.getItem(slotIndex)
                    if (item != null && item.amount >= requiredCount) {
                        // If there's exactly the required count, remove the item completely
                        if (item.amount == requiredCount) {
                            inputInv.setItem(SELF_UPDATE_REASON, slotIndex, null)
                        } else {
                            // Otherwise reduce the amount
                            item.amount -= requiredCount
                            inputInv.setItem(SELF_UPDATE_REASON, slotIndex, item)
                        }
                    }
                }

                timeLeft = recipe.time
                currentRecipe = recipe
                return
            }
        }
    }

    private fun handleInputUpdate(event: ItemPreUpdateEvent) {
        // Allow any item in input slots since recipes can use different combinations
        event.isCancelled = false
    }

    private fun handleOutputUpdate(event: ItemPreUpdateEvent) {
        event.isCancelled = !event.isRemove && event.updateReason != SELF_UPDATE_REASON
    }

    @TileEntityMenuClass
    inner class AlloySmelterMenu : GlobalTileEntityMenu() {

        private val mainProgress = ProgressArrowItem()
        private val alloySmelterProgress = AlloySmelterProgressItem()

        private val sideConfigGui = SideConfigMenu(
            this@AlloySmelter,
            mapOf(
                itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output"
            ),
            ::openWindow
        )

        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| i # # # # # e |",
                "| i # , # o a e |",
                "| i # c # s u e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .addIngredient(',', mainProgress)
            .addIngredient('c', alloySmelterProgress)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()

        init {
            updateProgress()
        }

        fun updateProgress() {
            val recipeTime = currentRecipe?.time ?: 0
            val percentage = if (timeLeft == 0) 0.0 else (recipeTime - timeLeft).toDouble() / recipeTime.toDouble()
            mainProgress.percentage = percentage
            alloySmelterProgress.percentage = percentage
        }

    }

}
