package xyz.xenondevs.nova.addon.machines.tileentity.multiblock

import org.bukkit.Material
import org.bukkit.block.BlockFace
import xyz.xenondevs.nova.util.id
import xyz.xenondevs.nova.world.BlockPos
import xyz.xenondevs.nova.world.block.tileentity.TileEntity

/**
 * Represents the orientation of a multiblock structure.
 * Only horizontal directions are supported as multiblocks
 * typically only rotate around the Y axis.
 */
enum class MultiblockOrientation(val blockFace: BlockFace) {
    NORTH(BlockFace.NORTH),
    EAST(BlockFace.EAST),
    SOUTH(BlockFace.SOUTH),
    WEST(BlockFace.WEST);
    
    companion object {
        /**
         * Gets the orientation from a tile entity's facing direction.
         */
        fun fromBlockFace(blockFace: BlockFace): MultiblockOrientation {
            return when (blockFace) {
                BlockFace.NORTH -> NORTH
                BlockFace.EAST -> EAST
                BlockFace.SOUTH -> SOUTH
                BlockFace.WEST -> WEST
                else -> NORTH // Default
            }
        }

        fun getOppositeDirection(dir: BlockFace): BlockFace {
            var dir = dir
            if (dir == BlockFace.UP) {
                dir = BlockFace.DOWN
            } else if (dir == BlockFace.DOWN) {
                dir = BlockFace.UP
            } else if (dir == BlockFace.NORTH) {
                dir = BlockFace.SOUTH
            } else if (dir == BlockFace.SOUTH) {
                dir = BlockFace.NORTH
            } else if (dir == BlockFace.EAST) {
                dir = BlockFace.WEST
            } else if (dir == BlockFace.WEST) {
                dir = BlockFace.EAST
            }
            return dir
        }
    }
}

/**
 * Simple class to validate multiblock structures.
 * The pattern now includes the controller position (marked with 'c').
 */
class MultiblockStructure(
    val pattern: List<List<String>>,
    val blocks: Map<Char, String>
) {
    // Character that represents a wild card (any block is acceptable)
    private val wildCardChar = '*'
    
    /**
     * Checks if the multiblock structure is valid at the controller's position.
     * Uses the orientation of the machine to rotate the pattern.
     */
    fun isValid(controller: TileEntity, orientation: MultiblockOrientation): Boolean {
        // Find controller position in pattern
        var controllerLayer = -1
        var controllerRow = -1
        var controllerCol = -1
        
        // Find controller position in pattern
        patternLoop@ for ((layerIdx, layer) in pattern.withIndex()) {
            for ((rowIdx, row) in layer.withIndex()) {
                for ((colIdx, char) in row.withIndex()) {
                    if (char == 'c') {
                        controllerLayer = layerIdx
                        controllerRow = rowIdx
                        controllerCol = colIdx
                        break@patternLoop
                    }
                }
            }
        }
        
        if (controllerLayer == -1) {
            throw IllegalStateException("Controller position 'c' not found in pattern")
        }
        
        // Validate all blocks in pattern
        for ((layerIdx, layer) in pattern.withIndex()) {
            for ((rowIdx, row) in layer.withIndex()) {
                for ((colIdx, char) in row.withIndex()) {
                    // Skip empty spaces
                    if (char == ' ' || char == '\u0000') {
                        continue
                    }
                    
                    // If this is the controller position, skip it since we already know it's valid
                    if (layerIdx == controllerLayer && rowIdx == controllerRow && colIdx == controllerCol) {
                        continue
                    }
                    
                    // Calculate relative position from controller
                    val relX = colIdx - controllerCol
                    val relY = layerIdx - controllerLayer
                    val relZ = rowIdx - controllerRow
                    
                    // Get rotated position based on orientation
                    val (rotX, rotZ) = when (orientation) {
                        MultiblockOrientation.NORTH -> Pair(relX, relZ)
                        MultiblockOrientation.EAST -> Pair(-relZ, relX)
                        MultiblockOrientation.SOUTH -> Pair(-relX, -relZ)
                        MultiblockOrientation.WEST -> Pair(relZ, -relX)
                    }
                    
                    val blockPos = BlockPos(
                        controller.pos.block.world,
                        controller.pos.x + rotX,
                        controller.pos.y + relY,
                        controller.pos.z + rotZ
                    )
                    
                    // Check if the block matches what's expected
                    if (!isBlockValid(blockPos, char)) {
                        return false
                    }
                }
            }
        }
        
        return true
    }
    
    /**
     * Checks if a block at the given position matches what's expected in the pattern.
     */
    private fun isBlockValid(pos: BlockPos, patternChar: Char): Boolean {
        // Wildcard character - any block is acceptable
        if (patternChar == wildCardChar) {
            return true
        }
        
        val requiredId = blocks[patternChar] ?: return false
        
        // Get the actual block ID at this position 
        val block = pos.block
        val actualId = block.id?.toString()
        
        // Special case for ANY_BLOCK wildcard
        if (requiredId == "ANY_BLOCK") {
            return true
        }
        
        // For air blocks
        if (requiredId == "minecraft:air") {
            return block.type == Material.AIR
        }
        
        // Return true if the block ID matches the required ID
        return actualId == requiredId
    }
}

/**
 * Extension function for TileEntity to check if a multiblock structure is valid.
 */
fun TileEntity.isMultiblockValid(structure: MultiblockStructure, orientation: MultiblockOrientation): Boolean {
    return structure.isValid(this, orientation)
}