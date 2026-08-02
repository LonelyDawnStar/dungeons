package kr.minq.dungeons;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class MobTestToolManager implements Listener {
    private final NamespacedKey previewKey;
    private final NamespacedKey aggressiveKey;
    private final NamespacedKey testActiveKey;
    private final NamespacedKey testToolKey;

    public MobTestToolManager(JavaPlugin plugin) {
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        aggressiveKey = new NamespacedKey(plugin, "custom_mob_aggressive");
        testActiveKey = new NamespacedKey(plugin, "custom_mob_test_active");
        testToolKey = new NamespacedKey(plugin, "custom_mob_test_tool");
    }

    @EventHandler(ignoreCancelled = true)
    public void onTestToolUse(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin")) return;
        if (!player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity) || !isPreview(entity)) return;
        if (!isTestTool(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        if (isTestActive(entity)) {
            freeze(entity);
            player.sendMessage("§6§l[Dungeons] §b몬스터 테스트를 종료하고 다시 정지시켰습니다.");
        } else {
            activate(entity);
            player.sendMessage("§6§l[Dungeons] §a몬스터 테스트를 시작했습니다. §7다시 우클릭하면 정지합니다.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!isPreview(entity) || !isTestActive(entity)) return;
        if (!isAggressive(entity)) {
            event.setCancelled(true);
            if (entity instanceof Mob mob) mob.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!isPreview(attacker) || !isTestActive(attacker)) return;
        if (!isAggressive(attacker)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void preventPreviewDeath(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!isPreview(entity) || !isTestActive(entity)) return;
        if (event.getFinalDamage() >= entity.getHealth()) {
            event.setCancelled(true);
            entity.setHealth(Math.min(maxHealth(entity), Math.max(1.0, maxHealth(entity))));
        }
    }

    private void activate(LivingEntity entity) {
        entity.getPersistentDataContainer().set(testActiveKey, PersistentDataType.BYTE, (byte) 1);
        entity.setInvulnerable(false);
        entity.setSilent(false);
        entity.setAI(true);
        if (!isAggressive(entity) && entity instanceof Mob mob) mob.setTarget(null);
    }

    private void freeze(LivingEntity entity) {
        entity.getPersistentDataContainer().set(testActiveKey, PersistentDataType.BYTE, (byte) 0);
        if (entity instanceof Mob mob) mob.setTarget(null);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setVelocity(new Vector());
        entity.setFireTicks(0);
        entity.setHealth(maxHealth(entity));
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? Math.max(1.0, entity.getHealth()) : Math.max(1.0, attribute.getValue());
    }

    private boolean isPreview(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(
                previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private boolean isAggressive(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(
                aggressiveKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private boolean isTestActive(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(
                testActiveKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private boolean isTestTool(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(
                testToolKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }
}
