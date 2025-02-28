package xyz.xenondevs.nova.addon.machines.tileentity.processing

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.kyori.adventure.key.Key
import net.minecraft.core.particles.ParticleTypes
import org.bukkit.block.BlockFace
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.cbf.Compound
import xyz.xenondevs.commons.collections.enumSetOf
import xyz.xenondevs.commons.provider.mapNonNull
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.nova.addon.machines.gui.ProgressArrowItem
import xyz.xenondevs.nova.addon.machines.gui.ElectricBlastFurnaceProgressItem
import xyz.xenondevs.nova.addon.machines.recipe.ElectricBlastFurnaceRecipe
import xyz.xenondevs.nova.addon.machines.registry.BlockStateProperties
import xyz.xenondevs.nova.addon.machines.registry.Blocks.ELECTRIC_BLAST_FURNACE
import xyz.xenondevs.nova.addon.machines.registry.RecipeTypes
import xyz.xenondevs.nova.addon.machines.tileentity.multiblock.MultiblockOrientation
import xyz.xenondevs.nova.addon.machines.tileentity.multiblock.MultiblockOrientation.Companion.getOppositeDirection
import xyz.xenondevs.nova.addon.machines.tileentity.multiblock.MultiblockStructure
import xyz.xenondevs.nova.addon.machines.tileentity.multiblock.MultiblockBlockCounts
import xyz.xenondevs.nova.addon.machines.tileentity.multiblock.isMultiblockValid
import xyz.xenondevs.nova.addon.machines.tileentity.multiblock.getMultiblockInfo
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

private val MAX_ENERGY = ELECTRIC_BLAST_FURNACE.config.entry<Long>("capacity")
private val ENERGY_PER_TICK = ELECTRIC_BLAST_FURNACE.config.entry<Long>("energy_per_tick")
private val ELECTRIC_BLAST_FURNACE_SPEED = ELECTRIC_BLAST_FURNACE.config.entry<Int>("speed")

// Parse multiblock configuration from JSON string or fallback to traditional config
private val MULTIBLOCK_STRUCTURE = run {
    // Try to read from JSON string first
    try {
        // Read the multiblock_json field as a JSON string
        val multiblockJson = ELECTRIC_BLAST_FURNACE.config.entry<String>("multiblock_json").get()

        // Parse the JSON
        val jsonElement = Gson().fromJson(multiblockJson, com.google.gson.JsonElement::class.java)
        val jsonObject = jsonElement.asJsonObject
        
        // Extract the structure
        val structure = jsonObject.getAsJsonArray("structure").map { layerElement ->
            layerElement.asJsonArray.map { rowElement ->
                rowElement.asString
            }
        }
        
        // Extract the mapping with special handling for arrays
        val mapping = jsonObject.getAsJsonObject("mapping").entrySet().associate { (key, value) ->
            // Convert key to char
            val charKey = key.single()
            
            // Handle value based on its JSON type
            val mappingValue = when {
                value.isJsonArray -> {
                    // Convert JSON array to List<String>
                    value.asJsonArray.map { it.asString }
                }
                value.isJsonPrimitive -> {
                    // Simple string value
                    value.asString
                }
                else -> {
                    // Fallback for unexpected types
                    value.toString()
                }
            }
            
            charKey to mappingValue
        }
        
        // Create the multiblock structure
        MultiblockStructure(structure, mapping)
    } catch (e: Exception) {
        // Print exception for debugging
        e.printStackTrace()
        
        // Fallback to traditional config format
        val pattern = ELECTRIC_BLAST_FURNACE.config.entry<List<List<String>>>("multiblock.structure").get()
        val mapping = ELECTRIC_BLAST_FURNACE.config.entry<Map<String, String>>("multiblock.mapping").get()
            .entries.associate { (k, v) -> k.single() to v }

        MultiblockStructure(pattern, mapping)
    }
}

class ElectricBlastFurnace(pos: BlockPos, blockState: NovaBlockState, data: Compound) : NetworkedTileEntity(pos, blockState, data) {

    private val inputInv = storedInventory("input", 2, false, IntArray(2) { 64 }, ::handleInputUpdate)
    private val outputInv = storedInventory("output", 2, false, IntArray(2) { 64 },  ::handleOutputUpdate)
    private val upgradeHolder = storedUpgradeHolder(UpgradeTypes.SPEED, UpgradeTypes.EFFICIENCY, UpgradeTypes.ENERGY)
    private val energyHolder = storedEnergyHolder(MAX_ENERGY, upgradeHolder, INSERT, BLOCKED_SIDES)
    private val itemHolder = storedItemHolder(inputInv to INSERT, outputInv to EXTRACT, blockedSides = BLOCKED_SIDES)

    private val energyPerTick by energyConsumption(ENERGY_PER_TICK, upgradeHolder)
    private val electricBlastFurnaceSpeed by speedMultipliedValue(ELECTRIC_BLAST_FURNACE_SPEED, upgradeHolder)

    private var active: Boolean = blockState.getOrThrow(BlockStateProperties.ACTIVE)
        set(active) {
            if (field != active) {
                field = active
                updateBlockState(blockState.with(BlockStateProperties.ACTIVE, active))
            }
        }

    private var timeLeft by storedValue("electricBlastFurnaceTime") { 0 }

    // Track multiblock validation state
    private var multiblockValid by storedValue("multiblockValid") { false }
    private var multiblockCounts: MultiblockBlockCounts = MultiblockBlockCounts()
    
    private val facing: BlockFace
        get() = blockState.getOrThrow(DefaultBlockStateProperties.FACING)
    private val orientation: MultiblockOrientation
        get() = MultiblockOrientation.fromBlockFace(getOppositeDirection(facing))

    private var currentRecipe: ElectricBlastFurnaceRecipe? by storedValue<Key>("currentRecipe").mapNonNull(
        { RecipeManager.getRecipe(RecipeTypes.ELECTRIC_BLAST_FURNACE, it) },
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

    // Calculate the heat value of the multiblock structure
    private var heatValue by storedValue("heatValue") { 0 }
    
    private fun validateMultiblock() {
        try {
            // Check if the multiblock structure is valid and get block counts
            val (isValid, blockCounts) = getMultiblockInfo(MULTIBLOCK_STRUCTURE, orientation)
            multiblockValid = isValid
            
            if (isValid) {
                multiblockCounts = blockCounts
                
                // Count machine casings and calculate heat
                val standardCasingCount = blockCounts.blocks["machines:standard_machine_casing"] ?: 0
                val reinforcedCasingCount = blockCounts.blocks["machines:reinforced_machine_casing"] ?: 0
                val advancedCasingCount = blockCounts.blocks["machines:advanced_machine_casing"] ?: 0
                val lavaCount = blockCounts.blocks["minecraft:lava"] ?: 0
                
                // Calculate heat value based on block types
                heatValue = (standardCasingCount * 30) + 
                            (reinforcedCasingCount * 50) + 
                            (advancedCasingCount * 80) + 
                            (lavaCount * 250)
            } else {
                heatValue = 0
            }
        } catch (e: Exception) {
            multiblockValid = false
            heatValue = 0
        }
    }

    override fun handleTick() {
        try {
            // Always validate multiblock when a recipe is in progress to continuously check heat
            if (timeLeft > 0) {
                validateMultiblock()
            } else if (active) {
                // Also check when the machine is active but not processing yet
                validateMultiblock()
            }

            // Get current recipe heat requirement if we have one
            val currentHeatRequired = currentRecipe?.requiredHeat ?: 0

            // Check multiblock validity and heat requirements
            if (!multiblockValid || (currentHeatRequired > 0 && heatValue < currentHeatRequired)) {
                if (particleTask.isRunning()) {
                    particleTask.stop()
                }
                active = false
                // Don't return here - we still want to check if we have energy but just not process
            }
        } catch (e: Exception) {
            active = false
        }

        if (energyHolder.energy >= energyPerTick) {
            if (timeLeft == 0) {
                takeItem()

                if (particleTask.isRunning())
                    particleTask.stop()
                active = false
            } else {
                // Get current recipe heat requirement
                val recipe = currentRecipe
                val requiredHeat = recipe?.requiredHeat ?: 0
                
                // Only continue processing if the recipe heat requirement is met
                if (multiblockValid && (requiredHeat <= 0 || heatValue >= requiredHeat)) {
                    // We have enough heat, process the recipe
                    timeLeft = max(timeLeft - electricBlastFurnaceSpeed, 0)
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

                    menuContainer.forEachMenu(ElectricBlastFurnaceMenu::updateProgress)
                } else {
                    // We don't have enough heat or multiblock is invalid, don't process this tick
                    if (particleTask.isRunning())
                        particleTask.stop()
                    active = false
                }
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

        if (inputItems.isEmpty()) {
            return
        }

        // Get all electric blast furnace recipes
        val allRecipes = RecipeManager.novaRecipes[RecipeTypes.ELECTRIC_BLAST_FURNACE]?.values
            ?.filterIsInstance<ElectricBlastFurnaceRecipe>() ?: return

        // Find a matching recipe
        recipeLoop@ for ((recipeIndex, recipe) in allRecipes.withIndex()) {
            
            // Check if we have sufficient heat for the recipe
            if (recipe.requiredHeat > 0 && heatValue < recipe.requiredHeat) {
                // Skip this recipe if we don't have enough heat
                continue
            }

            // Check if we have enough input items
            if (recipe.inputs.size > inputItems.size) {
                continue
            }

            // Create a mutable map of slot index to item stack
            val availableItemsBySlot = inputInv.items.withIndex()
                .filter { it.value != null }
                .associate { it.index to it.value!! }
                .toMutableMap()

            // Try to match each input choice
            val matchedInputs = mutableListOf<Pair<Int, ItemStack>>() // Pair of slot index and item

            var allInputsMatched = true
            for ((index, choice) in recipe.inputs.withIndex()) {
                // Get required count for this input (default to 1 if not specified)
                val requiredCount = if (recipe.inputCounts.isNotEmpty() && index < recipe.inputCounts.size)
                    recipe.inputCounts[index] else 1

                // Find slot with matching item and enough quantity
                val matchingEntry = availableItemsBySlot.entries.firstOrNull { (_, item) ->
                    val testResult = choice.test(item)
                    val hasEnough = item.amount >= requiredCount
                    testResult && hasEnough
                }

                if (matchingEntry != null) {
                    val (slotIndex, item) = matchingEntry
                    availableItemsBySlot.remove(slotIndex)
                    matchedInputs.add(Pair(slotIndex, item))
                } else {
                    // If any input can't be matched, move to the next recipe
                    allInputsMatched = false
                    continue@recipeLoop
                }
            }

            // If we got here, we matched all inputs
            // Check if we can hold ALL outputs
            val outputs = recipe.results.map { "${it.type}:${it.amount}" }.joinToString()

            val canHoldAllOutputs = recipe.results.all { outputInv.canHold(it) }

            if (canHoldAllOutputs) {
                // Remove the required amount of each matched item
                for (i in matchedInputs.indices) {
                    val (slotIndex, item) = matchedInputs[i]
                    val requiredCount = if (recipe.inputCounts.isNotEmpty() && i < recipe.inputCounts.size)
                        recipe.inputCounts[i] else 1

                    // Get the item directly from the inventory to ensure we're working with the current state
                    val currentItem = inputInv.getItem(slotIndex)
                    if (currentItem != null && currentItem.amount >= requiredCount) {
                        // If there's exactly the required count, remove the item completely
                        if (currentItem.amount == requiredCount) {
                            inputInv.setItem(SELF_UPDATE_REASON, slotIndex, null)
                        } else {
                            // Otherwise reduce the amount
                            currentItem.amount -= requiredCount
                            inputInv.setItem(SELF_UPDATE_REASON, slotIndex, currentItem)
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
    inner class ElectricBlastFurnaceMenu : GlobalTileEntityMenu() {

        private val mainProgress = ProgressArrowItem()
        private val electricBlastFurnaceProgress = ElectricBlastFurnaceProgressItem()

        private val sideConfigGui = SideConfigMenu(
            this@ElectricBlastFurnace,
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
                "| i # , # o o e |",
                "| # # c # s u e |",
                "3 - - - - - - - 4")
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .addIngredient(',', mainProgress)
            .addIngredient('c', electricBlastFurnaceProgress)
            .addIngredient('s', OpenSideConfigItem(sideConfigGui))
            .addIngredient('u', OpenUpgradesItem(upgradeHolder))
            .addIngredient('e', EnergyBar(3, energyHolder))
            .build()

        init {
            updateProgress()
            // Validate multiblock on GUI open
            validateMultiblock()
        }

        fun updateProgress() {
            val recipeTime = currentRecipe?.time ?: 0
            val percentage = if (timeLeft == 0) 0.0 else (recipeTime - timeLeft).toDouble() / recipeTime.toDouble()
            mainProgress.percentage = percentage
            electricBlastFurnaceProgress.percentage = percentage

            // Disable progress indicators if multiblock is invalid
            if (!multiblockValid) {
                mainProgress.percentage = 0.0
                electricBlastFurnaceProgress.percentage = 0.0
            }
        }
    }

}