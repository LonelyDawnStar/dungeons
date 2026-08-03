package kr.minq.dungeons;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobAiToolBridgeManager implements Listener {
    private final NamespacedKey previewKey;
    private final NamespacedKey toolKey;

    public MobAiToolBridgeManager(JavaPlugin plugin) {
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        toolKey = new NamespacedKey(plugin, "custom_mob_ai_tool");
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin") || !player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;
        if (entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) != (byte) 1) return;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.RECOVERY_COMPASS || !item.hasItemMeta()
                || item.getItemMeta().getPersistentDataContainer().getOrDefault(toolKey, PersistentDataType.BYTE, (byte) 0) != (byte) 1) return;
        event.setCancelled(true);
        player.performCommand("dungeon mobai");
    }
}
