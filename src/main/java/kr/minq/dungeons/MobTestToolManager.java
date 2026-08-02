package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MobTestToolManager implements Listener {
    private static final long TOGGLE_DEBOUNCE_MILLIS = 300L;
    private static final long ACTION_COOLDOWN_MILLIS = 1000L;

    private final NamespacedKey previewKey, roleKey, attackRangeKey, testActiveKey, testToolKey;
    private final NamespacedKey bossBarKey, bossColorKey, allyModeKey, allyPowerKey, allyRangeKey;
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
            player.sendMessage("§6§l[Dungeons] §b역할 테스트를 종료했습니다.");
        } else {
            activate(entity);
            player.sendMessage("§6§l[Dungeons] §a" + roleKorean(role(entity)) + " 테스트를 시작했습니다.");
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
        double hitRange = attackRange(entity);
        double detectionRange = Math.max(16.0, hitRange + 12.0);
        Player target = nearestPlayer(entity, detectionRange);
        if (target == null) {
            if (entity instanceof Mob mob) mob.setTarget(null);
            return;
        }
        if (entity instanceof Mob mob) mob.setTarget(target);
        if (entity.getLocation().distanceSquared(target.getLocation()) > hitRange * hitRange) {
            moveToward(entity, target, role(entity).equals("BOSS") ? 0.34 : 0.28);
            return;
        }
        if (!actionReady(entity)) return;
        target.damage(attackDamage(entity), entity);
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

    private Player nearestPlayer(LivingEntity entity, double range) {
        Player nearest = null;
        double nearestDistance = range * range;
        for (Player player : entity.getWorld().getPlayers()) {
            GameMode mode = player.getGameMode();
            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) continue;
            if (player.isDead() || !player.isValid()) continue;
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < nearestDistance) { nearestDistance = distance; nearest = player; }
        }
        return nearest;
    }

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

    private boolean actionReady(LivingEntity entity) {
        long now = System.currentTimeMillis();
        if (now - lastActionAt.getOrDefault(entity.getUniqueId(), 0L) < ACTION_COOLDOWN_MILLIS) return false;
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
    private String role(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(roleKey, PersistentDataType.STRING, "NORMAL"); }
    private boolean isHostile(LivingEntity entity) { String role = role(entity); return role.equals("NORMAL") || role.equals("BOSS"); }
    private boolean isPreview(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isTestActive(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(testActiveKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isTestTool(ItemStack item) { return item != null && item.getType() == Material.ECHO_SHARD && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getOrDefault(testToolKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private String roleKorean(String role) { return switch (role) { case "BOSS" -> "보스몹"; case "TRADER" -> "거래인"; case "REWARD" -> "보상지급인"; case "ALLY" -> "조력자"; default -> "일반몹"; }; }
    private String format(double value) { return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.US, "%.1f", value); }
}
