package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MobTraitEditorManager implements Listener {
    private static final String GUI_TITLE = "§8몬스터 고유 특성 편집";
    private static final List<Trait> TRAITS = List.of(
            new Trait("sun_burn", Material.SUNFLOWER, "햇빛에 연소", "낮의 직사광선에서 불탑니다."),
            new Trait("ignite_attack", Material.FLINT_AND_STEEL, "공격 시 발화", "공격한 대상을 불태웁니다."),
            new Trait("darkness_aura", Material.SCULK_CATALYST, "어둠 오라", "주변 플레이어에게 어둠을 부여합니다."),
            new Trait("fire_immune", Material.MAGMA_CREAM, "화염 면역", "화염·용암 피해를 받지 않습니다."),
            new Trait("fall_immune", Material.FEATHER, "낙하 피해 면역", "낙하 피해를 받지 않습니다."),
            new Trait("projectile_immune", Material.SHIELD, "투사체 면역", "화살 등 투사체 피해를 받지 않습니다."),
            new Trait("knockback_immune", Material.IRON_BOOTS, "넉백 면역", "피격 시 밀려나지 않습니다.")
    );

    private final JavaPlugin plugin;
    private final NamespacedKey previewKey;
    private final NamespacedKey toolKey;
    private final Map<UUID, UUID> editing = new HashMap<>();

    public MobTraitEditorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        toolKey = new NamespacedKey(plugin, "custom_mob_trait_tool");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickTraits, 10L, 10L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onToolUse(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin") || !player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity) || !isPreview(entity)) return;
        if (!isTool(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        open(player, entity);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity attacker) || !(event.getEntity() instanceof LivingEntity victim)) return;
        State state = state(attacker, "ignite_attack");
        if (state == State.ENABLED) victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
        if (state == State.DISABLED) {
            int before = victim.getFireTicks();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (victim.isValid() && victim.getFireTicks() > before) victim.setFireTicks(before);
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (state(entity, "fire_immune") == State.ENABLED && switch (cause) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> true;
            default -> false;
        }) event.setCancelled(true);
        if (state(entity, "fall_immune") == State.ENABLED && cause == EntityDamageEvent.DamageCause.FALL) event.setCancelled(true);
        if (state(entity, "projectile_immune") == State.ENABLED && cause == EntityDamageEvent.DamageCause.PROJECTILE) event.setCancelled(true);
        if (state(entity, "knockback_immune") == State.ENABLED) {
            var velocity = entity.getVelocity().clone();
            Bukkit.getScheduler().runTask(plugin, () -> { if (entity.isValid()) entity.setVelocity(velocity); });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LivingEntity entity = editingEntity(player);
        if (entity == null) { player.closeInventory(); return; }
        int slot = event.getRawSlot();
        if (slot == 22) { player.closeInventory(); return; }
        int index = slot - 10;
        if (index < 0 || index >= TRAITS.size()) return;
        Trait trait = TRAITS.get(index);
        State next = event.isRightClick() ? state(entity, trait.key()).previous() : state(entity, trait.key()).next();
        setState(entity, trait.key(), next);
        applyImmediate(entity, trait.key(), next);
        open(player, entity);
    }

    private void open(Player player, LivingEntity entity) {
        editing.put(player.getUniqueId(), entity.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 27, GUI_TITLE);
        for (int i = 0; i < TRAITS.size(); i++) {
            Trait trait = TRAITS.get(i);
            State state = state(entity, trait.key());
            ItemStack item = new ItemStack(trait.material());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(state.color() + trait.name() + " §7- " + state.korean());
            meta.setLore(List.of("§7" + trait.description(), "§f좌클릭 §7다음 상태 / §f우클릭 §7이전 상태", "§8기본값은 몹 본래 행동을 유지합니다."));
            if (state != State.DEFAULT) meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
            inventory.setItem(10 + i, item);
        }
        ItemStack done = new ItemStack(Material.LIME_DYE);
        ItemMeta doneMeta = done.getItemMeta();
        doneMeta.setDisplayName("§a설정 완료");
        done.setItemMeta(doneMeta);
        inventory.setItem(22, done);
        player.openInventory(inventory);
    }

    private void tickTraits() {
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!isPreview(entity)) continue;
                State sun = state(entity, "sun_burn");
                if (sun == State.DISABLED && entity.getFireTicks() > 0 && isInSunlight(entity)) entity.setFireTicks(0);
                if (sun == State.ENABLED && isInSunlight(entity)) entity.setFireTicks(Math.max(entity.getFireTicks(), 30));

                State darkness = state(entity, "darkness_aura");
                if (darkness == State.ENABLED) {
                    for (Player player : nearbyPlayers(entity, 20.0)) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, false));
                    }
                    entity.getWorld().spawnParticle(Particle.SCULK_SOUL, entity.getLocation().add(0, 1, 0), 1, 0.3, 0.4, 0.3, 0.01);
                } else if (darkness == State.DISABLED) {
                    for (Player player : nearbyPlayers(entity, 20.0)) player.removePotionEffect(PotionEffectType.DARKNESS);
                }
            }
        }
    }

    private List<Player> nearbyPlayers(LivingEntity entity, double range) {
        double squared = range * range;
        return entity.getWorld().getPlayers().stream()
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                .filter(player -> player.getLocation().distanceSquared(entity.getLocation()) <= squared)
                .toList();
    }

    private boolean isInSunlight(LivingEntity entity) {
        var location = entity.getLocation();
        return entity.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL
                && entity.getWorld().getTime() < 12300L
                && entity.getWorld().getHighestBlockYAt(location) <= location.getBlockY();
    }

    private void applyImmediate(LivingEntity entity, String key, State state) {
        if (key.equals("fire_immune") && state == State.ENABLED) entity.setFireTicks(0);
        if (key.equals("knockback_immune")) {
            AttributeInstance attribute = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (attribute != null) attribute.setBaseValue(state == State.ENABLED ? 1.0 : 0.0);
        }
    }

    private State state(LivingEntity entity, String trait) {
        String value = entity.getPersistentDataContainer().getOrDefault(key(trait), PersistentDataType.STRING, "DEFAULT");
        try { return State.valueOf(value); } catch (IllegalArgumentException ignored) { return State.DEFAULT; }
    }

    private void setState(LivingEntity entity, String trait, State state) {
        entity.getPersistentDataContainer().set(key(trait), PersistentDataType.STRING, state.name());
    }

    private NamespacedKey key(String trait) { return new NamespacedKey(plugin, "custom_trait_" + trait); }
    private boolean isPreview(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isTool(ItemStack item) { return item != null && item.getType() == Material.AMETHYST_SHARD && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getOrDefault(toolKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private LivingEntity editingEntity(Player player) { UUID id = editing.get(player.getUniqueId()); Entity entity = id == null ? null : Bukkit.getEntity(id); return entity instanceof LivingEntity living && living.isValid() ? living : null; }

    private record Trait(String key, Material material, String name, String description) { }
    private enum State {
        DEFAULT("§e", "기본값"), ENABLED("§a", "강제 활성"), DISABLED("§c", "강제 비활성");
        private final String color;
        private final String korean;
        State(String color, String korean) { this.color = color; this.korean = korean; }
        String color() { return color; }
        String korean() { return korean; }
        State next() { return values()[(ordinal() + 1) % values().length]; }
        State previous() { return values()[Math.floorMod(ordinal() - 1, values().length)]; }
    }
}
