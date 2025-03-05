package xyz.xenondevs.nova.addon.machines.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Click
import xyz.xenondevs.nova.addon.machines.registry.GuiItems

/**
 * A GUI item that displays a warning icon with a customizable error message.
 * This can be used in machine GUIs to show warnings or errors to the user.
 * 
 * @param visibilityCondition A function that determines if the warning should be visible
 * @param titleKey The translation key for the warning title (optional)
 * @param descriptionKey The translation key for the warning description (optional)
 * @param titleText Direct text for the warning title (used if titleKey is null)
 * @param descriptionText Direct text for the warning description (used if descriptionKey is null)
 * @param dynamicDescription Function that returns dynamic description text
 */
class ErrorWarningItem(
    private val visibilityCondition: () -> Boolean = { true },
    private val titleKey: String? = null,
    private val descriptionKey: String? = null,
    private val titleText: String? = null,
    private val descriptionText: String? = null,
    private val dynamicDescription: (() -> String)? = null
) : AbstractItem() {
    
    /**
     * Updates the warning item, causing it to re-evaluate its visibility condition
     * and refresh its display.
     */
    fun update() {
        notifyWindows()
    }

    override fun getItemProvider(player: Player): ItemProvider {
        // If the visibility condition is false, return empty item
        if (!visibilityCondition())
            return ItemProvider.EMPTY
        
        // Create an item builder from the base warning icon
        val itemBuilder = GuiItems.ERROR_WARNING.createClientsideItemBuilder()
        
        // Set title using proper Component.translatable for translation keys
        when {
            titleKey != null -> {
                itemBuilder.setName(Component.translatable(titleKey, NamedTextColor.RED))
            }
            titleText != null -> {
                itemBuilder.setName(Component.text(titleText, NamedTextColor.RED))
            }
            else -> {
                // Default warning title if none provided
                itemBuilder.setName(Component.translatable("menu.machines.error.warning", NamedTextColor.RED))
            }
        }
        
        // Handle description/lore
        when {
            // Dynamic description that might be a translation key
            dynamicDescription != null -> {
                val value = dynamicDescription.invoke()
                
                if (value.contains(':')) {
                    // Format is "translation.key:param1/param2/etc"
                    val parts = value.split(":", limit = 2)
                    val key = parts[0]
                    val params = parts[1]
                    
                    // For insufficient heat message with format "curr/req"
                    if (key == "menu.machines.error.insufficient_heat" && params.contains('/')) {
                        val heatParts = params.split("/")
                        val currentHeat = heatParts[0]
                        val requiredHeat = heatParts[1]
                        
                        itemBuilder.addLoreLines(Component.translatable(
                            key, 
                            NamedTextColor.GRAY,
                            Component.text(currentHeat, NamedTextColor.RED),
                            Component.text(requiredHeat, NamedTextColor.GREEN)
                        ))
                    } else {
                        // Other parameterized messages
                        itemBuilder.addLoreLines(Component.translatable(
                            key,
                            NamedTextColor.GRAY,
                            Component.text(params, NamedTextColor.AQUA)
                        ))
                    }
                } else if (value.contains('.')) {
                    // It's likely a simple translation key
                    itemBuilder.addLoreLines(Component.translatable(value, NamedTextColor.GRAY))
                } else {
                    // Just a plain text message
                    itemBuilder.addLoreLines(Component.text(value, NamedTextColor.GRAY))
                }
            }
            // Static translation key
            descriptionKey != null -> {
                itemBuilder.addLoreLines(Component.translatable(descriptionKey, NamedTextColor.GRAY))
            }
            // Plain text description
            descriptionText != null -> {
                itemBuilder.addLoreLines(Component.text(descriptionText, NamedTextColor.GRAY))
            }
        }
        
        return itemBuilder
    }
    
    override fun handleClick(clickType: ClickType, player: Player, click: Click) {
        // No click action by default
    }
}