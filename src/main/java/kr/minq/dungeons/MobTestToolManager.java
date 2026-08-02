package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MobTestToolManager implements Listener {
    private static final long TOGGLE_DEBOUNCE_MILLIS = 300L;
    private static final long ATTACK_COOLDOWN_MILLIS = 1000L;
    private static final double TARGET_RANGE = 24.0;
    private static final double ATTACK_RANGE = 2.1;

    private final NamespacedKey previewKey;
    private final NamespacedKey aggressiveKey;
    private final NamespacedKey testActiveKey;
    private final NamespacedKey testToolKey;
    private final Map<UUID, Long> lastToggleAt = new HashMap<>();
    private final Map<UUID, Long> lastAttackAt = new HashMap<>();

    public MobTestToolManager(JavaPlugin plugin) {
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        aggressiveKey = new NamespacedKey(plugin, "custom_mob_aggressive");
        testActiveKey = new NamespacedKey(plugin, "custom_mob_test_active");
        testToolKey = new NamespacedKey(plugin, "custom_mob_test_tool");

        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAggressiveTests, 5L, 5L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTestToolUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin")) return;
        if (!player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity) || !isPreview(entity)) return;
        if (!isTestTool(player.getInventory().getItemInMainHand())) return;

        event.setCancelled(true);
        long now = System.currentTimeMillis();
        long previous = lastToggleAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < TOGGLE_DEBOUNCE_MILLIS) return;
        lastToggleAt.put(player.getUniqueId(), now);

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
            entity.setHealth(maxHealth(entity));
        }
    }

    private void tickAggressiveTests() {
        var world = Bukkit.getWorld(TemplateWorldManager.WORLD_NAME);
        if (world == null) return;

        for (LivingEntity entity : world.getLivingEntities()) {
            if (!isPreview(entity) || !isTestActive(entity) || !isAggressive(entity)) continue;

            Player target = nearestTarget(entity);
            if (target == null) {
                if (entity instanceof Mob mob) mob.setTarget(null);
                continue;
            }

            if (entity instanceof Mob mob) mob.setTarget(target);

            double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
            if (distanceSquared > ATTACK_RANGE * ATTACK_RANGE) {
                Vector direction = target.getLocation().toVector()
                        .subtract(entity.getLocation().toVector());
                direction.setY(0.0);
                if (direction.lengthSquared() > 0.001) {
                    direction.normalize().multiply(0.28);
                    direction.setY(entity.getVelocity().getY());
                    entity.setVelocity(direction);
                }
                continue;
            }

            long now = System.currentTimeMillis();
            long previous = lastAttackAt.getOrDefault(entity.getUniqueId(), 0L);
            if (now - previous < ATTACK_COOLDOWN_MILLIS) continue;
            lastAttackAt.put(entity.getUniqueId(), now);
            target.damage(attackDamage(entity), entity);
            target.setVelocity(target.getVelocity().add(
                    target.getLocation().toVector()
                            .subtract(entity.getLocation().toVector())
                            .setY(0.2)
                            .normalize()
                            .multiply(0.35)
            ));
        }
    }

    private Player nearestTarget(LivingEntity entity) {
        Player nearest = null;
        double nearestDistance = TARGET_RANGE * TARGET_RANGE;
        for (Player player : entity.getWorld().getPlayers()) {
            GameMode mode = player.getGameMode();
            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) continue;
            if (player.isDead() || !player.isValid()) continue;
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
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
        lastAttackAt.remove(entity.getUniqueId());
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

    private double attackDamage(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        return attribute == null ? 2.0 : Math.max(0.0, attribute.getValue());
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
