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
import xyz.xenondevs.nova.addon.machines.gui.IndustrialCentrifugeProgressItem
import xyz.xenondevs.nova.addon.machines.recipe.IndustrialCentrifugeRecipe
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.INDUSTRIAL_CENTRIFUGE
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

private val MAX_ENERGY = INDUSTRIAL_CENTRIFUGE.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = INDUSTRIAL_CENTRIFUGE.config.entry<Long>("energy_per_tick")
private val INDUSTRIAL_CENTRIFUGE_SPEED = INDUSTRIAL_CENTRIFUGE.config.entry<Int>("speed")

class IndustrialCentrifuge(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {

    private val inputInv = storedInventory("input", 1, false, IntArray(1) { 64 }, ::handleInputUpdate, null)
    private val outputInv = storedInventory("output", 5, false, IntArray(5) { 64 }, ::handleOutputUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inputInv to INSERT, outputInv to EXTRACT, blockedSides = BLOCKED_SIDES)

    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val industrialCentrifugeSpeed by speedMultipliedValue(INDUSTRIAL_CENTRIFUGE_SPEED, upgradeHolder)
    
    private var active: Boolean = blockState.getOrThrow(BlockStateProperties.ACTIVE)
        set(active) {
            if (field != active) {
                field = active
                updateBlockState(blockState.with(BlockStateProperties.ACTIVE, active))
            }
        }

    private var timeLeft by storedValue("industrialCentrifugeTime") { 0 }

    private var currentRecipe: IndustrialCentrifugeRecipe? by storedValue<Key>("currentRecipe").mapNonNull(
        { RecipeManager.getRecipe(RecipeTypes.INDUSTRIAL_CENTRIFUGE, it) },
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
                timeLeft = max(timeLeft - industrialCentrifugeSpeed, 0)
                energyHolder.energy -= energyPerTick

                if (!particleTask.isRunning())
                    particleTask.start()
                active = true

                if (timeLeft == 0) {
                    currentRecipe?.let { recipe ->
                        // Add all output items to the output inventory
                        recipe.results.forEach { output ->
                            outputInv.addItem(SELF_UPDATE_REASON, output.clone())
                        }
                    }
                    currentRecipe = null
                }

                menuContainer.forEachMenu(IndustrialCentrifugeMenu::updateProgress)
            }

        } else {
            if (particleTask.isRunning()) {
                particleTask.stop()
            }
            active = false
        }
    }

    private fun takeItem() {
        // Get the single input item
        val inputItem = inputInv.getItem(0) ?: return

        // Get all industrial centrifuge recipes
        val allRecipes = RecipeManager.novaRecipes[RecipeTypes.INDUSTRIAL_CENTRIFUGE]?.values
            ?.filterIsInstance<IndustrialCentrifugeRecipe>() ?: return

        // Find a matching recipe
        for (recipe in allRecipes) {
            // Check if the single input matches and we have enough quantity
            val requiredCount = if (recipe.inputCounts.isNotEmpty()) recipe.inputCounts[0] else 1
            
            if (recipe.inputs.size == 1 && recipe.inputs[0].test(inputItem) && inputItem.amount >= requiredCount) {
                // Check if we can hold ALL outputs
                val canHoldAllOutputs = recipe.results.all { outputInv.canHold(it) }
                
                if (canHoldAllOutputs) {
                    // Remove the required amount from input
                    if (inputItem.amount == requiredCount) {
                        inputInv.setItem(SELF_UPDATE_REASON, 0, null)
                    } else {
                        inputItem.amount -= requiredCount
                        inputInv.setItem(SELF_UPDATE_REASON, 0, inputItem)
                    }

                    timeLeft = recipe.time
                    currentRecipe = recipe
                    return
                }
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
    inner class IndustrialCentrifugeMenu : GlobalTileEntityMenu() {

        private val mainProgress = ProgressArrowItem()
        private val industrialCentrifugeProgress = IndustrialCentrifugeProgressItem()

        private val sideConfigGui = SideConfigMenu(
            this@IndustrialCentrifuge,
            mapOf(
                itemHolder.getNetworkedInventory(inputInv) to "inventory.nova.input",
                itemHolder.getNetworkedInventory(outputInv) to "inventory.nova.output"
            ),
            ::openWindow
        )

        override val gui = Gui.normal()
            .setStructure(
                "1 - - - - - - - 2",
                "| i # , # o o e |",
                "| # # c # o o e |",
                "| # # # # o s u |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .addIngredient(',', mainProgress)
            .addIngredient('c', industrialCentrifugeProgress)
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
            industrialCentrifugeProgress.percentage = percentage
        }

    }

}