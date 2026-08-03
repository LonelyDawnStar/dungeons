package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MobTestToolManager implements Listener {
    private static final long TOGGLE_DEBOUNCE_MILLIS = 300L;

    private final NamespacedKey previewKey, roleKey, attackRangeKey, testActiveKey, testToolKey;
    private final NamespacedKey bossBarKey, bossColorKey, allyModeKey, allyPowerKey, allyRangeKey;
    private final NamespacedKey aiTypeKey, detectionRangeKey, moveSpeedKey, attackIntervalKey, preferredDistanceKey, targetPriorityKey;
    private final Map<UUID, Long> lastToggleAt = new HashMap<>();
    private final Map<UUID, Long> lastActionAt = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    public MobTestToolManager(JavaPlugin plugin) {
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        roleKey = new NamespacedKey(plugin, "custom_mob_role");
        attackRangeKey = new NamespacedKey(plugin, "custom_mob_range");
        testActiveKey = new NamespacedKey(plugin, "custom_mob_test_active");
        testToolKey = new NamespacedKey(plugin, "custom_mob_test_tool");
        bossBarKey = new NamespacedKey(plugin, "role_boss_bar");
        bossColorKey = new NamespacedKey(plugin, "role_boss_color");
        allyModeKey = new NamespacedKey(plugin, "role_ally_mode");
        allyPowerKey = new NamespacedKey(plugin, "role_ally_power");
        allyRangeKey = new NamespacedKey(plugin, "role_ally_range");
        aiTypeKey = new NamespacedKey(plugin, "custom_mob_ai_type");
        detectionRangeKey = new NamespacedKey(plugin, "custom_mob_detection_range");
        moveSpeedKey = new NamespacedKey(plugin, "custom_mob_move_speed");
        attackIntervalKey = new NamespacedKey(plugin, "custom_mob_attack_interval");
        preferredDistanceKey = new NamespacedKey(plugin, "custom_mob_preferred_distance");
        targetPriorityKey = new NamespacedKey(plugin, "custom_mob_target_priority");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTests, 5L, 5L);
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
        if (now - lastToggleAt.getOrDefault(player.getUniqueId(), 0L) < TOGGLE_DEBOUNCE_MILLIS) return;
        lastToggleAt.put(player.getUniqueId(), now);
        if (isTestActive(entity)) {
            freeze(entity);
            player.sendMessage("§6§l[Dungeons] §bAI 테스트를 종료했습니다.");
        } else {
            activate(entity);
            player.sendMessage("§6§l[Dungeons] §a" + roleKorean(role(entity)) + " AI 테스트를 시작했습니다.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!isPreview(entity) || !isTestActive(entity)) return;
        if (!isHostile(entity)) {
            event.setCancelled(true);
            if (entity instanceof Mob mob) mob.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPreviewAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!isPreview(attacker) || !isTestActive(attacker)) return;
        String role = role(attacker);
        if (!role.equals("NORMAL") && !role.equals("BOSS") && !role.equals("ALLY")) event.setCancelled(true);
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

    private void tickTests() {
        var world = Bukkit.getWorld(TemplateWorldManager.WORLD_NAME);
        if (world == null) return;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (!isPreview(entity) || !isTestActive(entity)) continue;
            switch (role(entity)) {
                case "NORMAL", "BOSS" -> tickEnemy(entity);
                case "ALLY" -> tickAlly(entity);
                default -> {
                    if (entity instanceof Mob mob) mob.setTarget(null);
                    entity.setVelocity(new Vector());
                }
            }
            updateBossBar(entity);
        }
    }

    private void tickEnemy(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String aiType = data.getOrDefault(aiTypeKey, PersistentDataType.STRING, "MELEE");
        double detection = data.getOrDefault(detectionRangeKey, PersistentDataType.DOUBLE, Math.max(16.0, attackRange(entity) + 12.0));
        double speed = data.getOrDefault(moveSpeedKey, PersistentDataType.DOUBLE, role(entity).equals("BOSS") ? 0.34 : 0.28);
        double preferred = data.getOrDefault(preferredDistanceKey, PersistentDataType.DOUBLE, 8.0);
        Player target = selectTarget(entity, detection, data.getOrDefault(targetPriorityKey, PersistentDataType.STRING, "NEAREST"));
        if (target == null) {
            if (entity instanceof Mob mob) mob.setTarget(null);
            return;
        }
        if (entity instanceof Mob mob) mob.setTarget(target);
        double distance = entity.getLocation().distance(target.getLocation());
        switch (aiType) {
            case "RANGED" -> tickRanged(entity, target, distance, preferred, speed);
            case "CHARGE" -> tickCharge(entity, target, distance, preferred, speed);
            case "FLEE" -> tickFlee(entity, target, distance, preferred, speed);
            case "TURRET" -> tickTurret(entity, target, distance);
            default -> tickMelee(entity, target, distance, speed);
        }
    }

    private void tickMelee(LivingEntity entity, Player target, double distance, double speed) {
        if (distance > attackRange(entity)) {
            moveToward(entity, target, speed);
            return;
        }
        if (actionReady(entity)) target.damage(attackDamage(entity), entity);
    }

    private void tickRanged(LivingEntity entity, Player target, double distance, double preferred, double speed) {
        if (distance < preferred * 0.65) moveAway(entity, target, speed);
        else if (distance > preferred * 1.2) moveToward(entity, target, speed);
        else entity.setVelocity(new Vector(0, entity.getVelocity().getY(), 0));
        if (distance <= Math.max(preferred * 1.8, attackRange(entity)) && actionReady(entity)) {
            entity.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 18, 0.25, 0.4, 0.25, 0.08);
            target.damage(attackDamage(entity), entity);
        }
    }

    private void tickCharge(LivingEntity entity, Player target, double distance, double preferred, double speed) {
        if (distance < preferred * 0.75 && !actionReady(entity)) {
            moveAway(entity, target, speed * 0.7);
            return;
        }
        if (actionReady(entity)) {
            Vector direction = target.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize();
            entity.setVelocity(direction.multiply(Math.max(0.8, speed * 3.5)).setY(0.18));
            entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation().add(0, 0.5, 0), 24, 0.4, 0.2, 0.4, 0.12);
        } else if (distance > attackRange(entity)) {
            moveToward(entity, target, speed * 0.65);
        } else {
            target.damage(attackDamage(entity) * 1.35, entity);
        }
    }

    private void tickFlee(LivingEntity entity, Player target, double distance, double preferred, double speed) {
        if (distance < preferred) moveAway(entity, target, speed);
        else entity.setVelocity(new Vector(0, entity.getVelocity().getY(), 0));
        if (distance <= Math.max(preferred, attackRange(entity)) && actionReady(entity)) {
            target.damage(Math.max(1.0, attackDamage(entity) * 0.6), entity);
        }
    }

    private void tickTurret(LivingEntity entity, Player target, double distance) {
        entity.setVelocity(new Vector());
        if (distance <= Math.max(attackRange(entity), preferredDistance(entity)) && actionReady(entity)) {
            entity.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, entity.getEyeLocation(), 12, 0.15, 0.15, 0.15, 0.05);
            target.damage(attackDamage(entity), entity);
        }
    }

    private void tickAlly(LivingEntity entity) {
        String mode = entity.getPersistentDataContainer().getOrDefault(allyModeKey, PersistentDataType.STRING, "ATTACK");
        double power = entity.getPersistentDataContainer().getOrDefault(allyPowerKey, PersistentDataType.DOUBLE, 4.0);
        double range = entity.getPersistentDataContainer().getOrDefault(allyRangeKey, PersistentDataType.DOUBLE, 12.0);
        if (mode.equals("HEAL")) {
            Player player = nearestPlayer(entity, range);
            if (player != null && player.getHealth() < maxHealth(player) && actionReady(entity)) {
                player.setHealth(Math.min(maxHealth(player), player.getHealth() + power));
                player.sendActionBar("§a조력자에게서 " + format(power) + "만큼 회복받았습니다.");
            }
            return;
        }
        LivingEntity target = nearestEnemy(entity, range);
        if (target == null) return;
        double hitRange = Math.min(3.0, range);
        if (entity.getLocation().distanceSquared(target.getLocation()) > hitRange * hitRange) {
            moveToward(entity, target, 0.3);
            return;
        }
        if (actionReady(entity)) target.damage(power, entity);
    }

    private Player selectTarget(LivingEntity entity, double range, String priority) {
        List<Player> players = new ArrayList<>();
        for (Player player : entity.getWorld().getPlayers()) {
            GameMode mode = player.getGameMode();
            if ((mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !player.isDead() && player.isValid()
                    && entity.getLocation().distanceSquared(player.getLocation()) <= range * range) players.add(player);
        }
        if (players.isEmpty()) return null;
        return switch (priority) {
            case "LOWEST_HEALTH" -> players.stream().min(Comparator.comparingDouble(Player::getHealth)).orElse(players.getFirst());
            case "FARTHEST" -> players.stream().max(Comparator.comparingDouble(p -> entity.getLocation().distanceSquared(p.getLocation()))).orElse(players.getFirst());
            case "RANDOM" -> players.get(ThreadLocalRandom.current().nextInt(players.size()));
            default -> players.stream().min(Comparator.comparingDouble(p -> entity.getLocation().distanceSquared(p.getLocation()))).orElse(players.getFirst());
        };
    }

    private Player nearestPlayer(LivingEntity entity, double range) { return selectTarget(entity, range, "NEAREST"); }

    private LivingEntity nearestEnemy(LivingEntity ally, double range) {
        LivingEntity nearest = null;
        double nearestDistance = range * range;
        for (LivingEntity other : ally.getWorld().getLivingEntities()) {
            if (other.equals(ally) || !isPreview(other) || !isTestActive(other) || !isHostile(other)) continue;
            double distance = ally.getLocation().distanceSquared(other.getLocation());
            if (distance < nearestDistance) { nearestDistance = distance; nearest = other; }
        }
        return nearest;
    }

    private void moveToward(LivingEntity entity, LivingEntity target, double speed) {
        Vector direction = target.getLocation().toVector().subtract(entity.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() > 0.001) {
            direction.normalize().multiply(speed);
            direction.setY(entity.getVelocity().getY());
            entity.setVelocity(direction);
        }
    }

    private void moveAway(LivingEntity entity, LivingEntity target, double speed) {
        Vector direction = entity.getLocation().toVector().subtract(target.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() > 0.001) {
            direction.normalize().multiply(speed);
            direction.setY(entity.getVelocity().getY());
            entity.setVelocity(direction);
        }
    }

    private boolean actionReady(LivingEntity entity) {
        long now = System.currentTimeMillis();
        long cooldown = Math.max(100L, entity.getPersistentDataContainer().getOrDefault(attackIntervalKey, PersistentDataType.INTEGER, 20) * 50L);
        if (now - lastActionAt.getOrDefault(entity.getUniqueId(), 0L) < cooldown) return false;
        lastActionAt.put(entity.getUniqueId(), now);
        return true;
    }

    private void activate(LivingEntity entity) {
        entity.getPersistentDataContainer().set(testActiveKey, PersistentDataType.BYTE, (byte) 1);
        entity.setInvulnerable(false);
        entity.setSilent(false);
        entity.setAI(true);
        if (role(entity).equals("BOSS")) createBossBar(entity);
        if (!isHostile(entity) && entity instanceof Mob mob) mob.setTarget(null);
    }

    private void freeze(LivingEntity entity) {
        entity.getPersistentDataContainer().set(testActiveKey, PersistentDataType.BYTE, (byte) 0);
        lastActionAt.remove(entity.getUniqueId());
        removeBossBar(entity);
        if (entity instanceof Mob mob) mob.setTarget(null);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setVelocity(new Vector());
        entity.setFireTicks(0);
        entity.setHealth(maxHealth(entity));
    }

    private void createBossBar(LivingEntity entity) {
        if (entity.getPersistentDataContainer().getOrDefault(bossBarKey, PersistentDataType.BYTE, (byte) 1) != (byte) 1) return;
        String colorName = entity.getPersistentDataContainer().getOrDefault(bossColorKey, PersistentDataType.STRING, "RED");
        BarColor color;
        try { color = BarColor.valueOf(colorName); } catch (IllegalArgumentException ex) { color = BarColor.RED; }
        BossBar bar = Bukkit.createBossBar(entity.getCustomName() == null ? "보스" : entity.getCustomName(), color, BarStyle.SEGMENTED_10);
        for (Player player : entity.getWorld().getPlayers()) bar.addPlayer(player);
        bossBars.put(entity.getUniqueId(), bar);
    }

    private void updateBossBar(LivingEntity entity) {
        BossBar bar = bossBars.get(entity.getUniqueId());
        if (bar != null) bar.setProgress(Math.max(0.0, Math.min(1.0, entity.getHealth() / maxHealth(entity))));
    }

    private void removeBossBar(LivingEntity entity) {
        BossBar bar = bossBars.remove(entity.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    private double maxHealth(LivingEntity entity) { AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH); return attribute == null ? Math.max(1.0, entity.getHealth()) : Math.max(1.0, attribute.getValue()); }
    private double attackDamage(LivingEntity entity) { AttributeInstance attribute = entity.getAttribute(Attribute.ATTACK_DAMAGE); return attribute == null ? 2.0 : Math.max(0.0, attribute.getValue()); }
    private double attackRange(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(attackRangeKey, PersistentDataType.DOUBLE, 2.1); }
    private double preferredDistance(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(preferredDistanceKey, PersistentDataType.DOUBLE, 8.0); }
    private String role(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(roleKey, PersistentDataType.STRING, "NORMAL"); }
    private boolean isHostile(LivingEntity entity) { String role = role(entity); return role.equals("NORMAL") || role.equals("BOSS"); }
    private boolean isPreview(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isTestActive(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(testActiveKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isTestTool(ItemStack item) { return item != null && item.getType() == Material.ECHO_SHARD && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getOrDefault(testToolKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private String roleKorean(String role) { return switch (role) { case "BOSS" -> "보스몹"; case "TRADER" -> "거래인"; case "REWARD" -> "보상지급인"; case "ALLY" -> "조력자"; default -> "일반몹"; }; }
    private String format(double value) { return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.US, "%.1f", value); }
}
