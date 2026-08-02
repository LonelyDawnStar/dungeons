package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CustomMobEditorManager implements Listener {
    private static final String GUI_TITLE = "§8커스텀 몬스터 편집";
    private static final double DEFAULT_HEALTH = 20.0;
    private static final double DEFAULT_DAMAGE = 4.0;
    private static final double MIN_SCALE = 0.25;
    private static final double MAX_SCALE = 4.0;
    private static final List<String> ABILITIES = List.of("NONE", "DASH", "FIREBALL", "HEAL", "SUMMON");

    private final JavaPlugin plugin;
    private final EquipmentDropEditorManager equipmentDropEditor;
    private final NamespacedKey previewKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey aggressiveKey;
    private final NamespacedKey abilityKey;
    private final NamespacedKey scaleKey;
    private final NamespacedKey yawKey;
    private final NamespacedKey editorToolKey;
    private final NamespacedKey cloneToolKey;
    private final NamespacedKey deleteToolKey;
    private final NamespacedKey cloneTypeKey;
    private final NamespacedKey cloneNameKey;
    private final NamespacedKey cloneHealthKey;
    private final NamespacedKey cloneDamageKey;
    private final NamespacedKey cloneAggressiveKey;
    private final NamespacedKey cloneAbilityKey;
    private final NamespacedKey cloneScaleKey;
    private final NamespacedKey cloneYawKey;
    private final Map<UUID, UUID> editingTargets = new HashMap<>();
    private final Map<UUID, UUID> awaitingName = new HashMap<>();

    public CustomMobEditorManager(JavaPlugin plugin, EquipmentDropEditorManager equipmentDropEditor) {
        this.plugin = plugin;
        this.equipmentDropEditor = equipmentDropEditor;
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        healthKey = new NamespacedKey(plugin, "custom_mob_health");
        damageKey = new NamespacedKey(plugin, "custom_mob_damage");
        aggressiveKey = new NamespacedKey(plugin, "custom_mob_aggressive");
        abilityKey = new NamespacedKey(plugin, "custom_mob_ability");
        scaleKey = new NamespacedKey(plugin, "custom_mob_scale");
        yawKey = new NamespacedKey(plugin, "custom_mob_yaw");
        editorToolKey = new NamespacedKey(plugin, "custom_mob_editor_tool");
        cloneToolKey = new NamespacedKey(plugin, "custom_mob_clone_tool");
        deleteToolKey = new NamespacedKey(plugin, "custom_mob_delete_tool");
        cloneTypeKey = new NamespacedKey(plugin, "clone_type");
        cloneNameKey = new NamespacedKey(plugin, "clone_name");
        cloneHealthKey = new NamespacedKey(plugin, "clone_health");
        cloneDamageKey = new NamespacedKey(plugin, "clone_damage");
        cloneAggressiveKey = new NamespacedKey(plugin, "clone_aggressive");
        cloneAbilityKey = new NamespacedKey(plugin, "clone_ability");
        cloneScaleKey = new NamespacedKey(plugin, "clone_scale");
        cloneYawKey = new NamespacedKey(plugin, "clone_yaw");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnEgg(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        if (!event.getLocation().getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        Bukkit.getScheduler().runTask(plugin, () -> makePreview(event.getEntity(), null));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin")) return;
        if (!player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity) || !isPreview(entity)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isEditorTool(held)) {
            event.setCancelled(true);
            openEditor(player, entity);
        } else if (isCloneTool(held)) {
            event.setCancelled(true);
            player.getInventory().addItem(createCloneTool(entity));
            player.sendMessage("§6§l[Dungeons] §a현재 몬스터 설정이 담긴 배치 도구를 지급했습니다.");
        } else if (isDeleteTool(held)) {
            event.setCancelled(true);
            editingTargets.values().removeIf(entity.getUniqueId()::equals);
            awaitingName.values().removeIf(entity.getUniqueId()::equals);
            String name = displayName(entity);
            entity.remove();
            player.sendMessage("§6§l[Dungeons] §c" + name + "§c을(를) 즉시 삭제했습니다.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCloneToolUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getItem() == null || !isCloneTool(event.getItem())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin")) return;
        if (!player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) {
            player.sendMessage("§c배치 도구는 템플릿 월드에서만 사용할 수 있습니다.");
            return;
        }
        event.setCancelled(true);
        ItemStack tool = event.getItem();
        PersistentDataContainer data = tool.getItemMeta().getPersistentDataContainer();
        String typeName = data.get(cloneTypeKey, PersistentDataType.STRING);
        if (typeName == null) {
            player.sendMessage("§e먼저 이 블레이즈 막대기로 프리뷰 몬스터를 우클릭해 설정을 복사하세요.");
            return;
        }
        EntityType type;
        try { type = EntityType.valueOf(typeName); }
        catch (IllegalArgumentException exception) {
            player.sendMessage("§c저장된 몬스터 종류가 올바르지 않습니다.");
            return;
        }
        Float yaw = data.get(cloneYawKey, PersistentDataType.FLOAT);
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        location.setYaw(yaw == null ? player.getLocation().getYaw() : yaw);
        Entity spawned = player.getWorld().spawnEntity(location, type);
        if (!(spawned instanceof LivingEntity living)) { spawned.remove(); return; }
        makePreview(living, tool);
        player.sendMessage("§6§l[Dungeons] §a" + displayName(living) + "§a을(를) 배치했습니다.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEditorClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LivingEntity entity = editingEntity(player);
        if (entity == null) {
            player.closeInventory();
            player.sendMessage("§c편집 중인 몬스터를 찾을 수 없습니다.");
            return;
        }
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        double amount = shift ? 10.0 : 1.0;
        switch (event.getRawSlot()) {
            case 10 -> {
                awaitingName.put(player.getUniqueId(), entity.getUniqueId());
                player.closeInventory();
                player.sendMessage("§e채팅에 새 이름을 입력하세요. §7취소: §fcancel");
            }
            case 12 -> { setHealth(entity, clamp(getHealth(entity) + (right ? -amount : amount), 1.0, 2048.0)); openEditor(player, entity); }
            case 14 -> { setDamage(entity, clamp(getDamage(entity) + (right ? -amount : amount), 0.0, 1024.0)); openEditor(player, entity); }
            case 16 -> { setAggressive(entity, !isAggressive(entity)); openEditor(player, entity); }
            case 18 -> { float step = shift ? 90.0f : 15.0f; setYaw(entity, getYaw(entity) + (right ? step : -step)); openEditor(player, entity); }
            case 20 -> { double step = shift ? 0.5 : 0.1; setScale(entity, getScale(entity) + (right ? -step : step)); openEditor(player, entity); }
            case 22 -> { cycleAbility(entity, right ? -1 : 1); openEditor(player, entity); }
            case 24 -> { player.getInventory().addItem(createCloneTool(entity)); player.sendMessage("§a배치 도구를 지급했습니다."); }
            case 26 -> { setYaw(entity, player.getLocation().getYaw()); openEditor(player, entity); }
            case 28 -> equipmentDropEditor.openEquipmentEditor(player, entity);
            case 30 -> equipmentDropEditor.openDropEditor(player, entity);
            case 31 -> {
                editingTargets.remove(player.getUniqueId());
                entity.remove();
                player.closeInventory();
                player.sendMessage("§c프리뷰 몬스터를 삭제했습니다.");
            }
            default -> { }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNameInput(AsyncPlayerChatEvent event) {
        UUID entityId = awaitingName.remove(event.getPlayer().getUniqueId());
        if (entityId == null) return;
        event.setCancelled(true);
        String input = ChatColor.stripColor(event.getMessage()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            Entity raw = Bukkit.getEntity(entityId);
            if (!(raw instanceof LivingEntity entity) || !entity.isValid()) {
                player.sendMessage("§c몬스터를 찾을 수 없습니다.");
                return;
            }
            if (input.equalsIgnoreCase("cancel")) player.sendMessage("§7이름 변경을 취소했습니다.");
            else if (input.isBlank() || input.length() > 40) player.sendMessage("§c이름은 1~40자로 입력하세요.");
            else {
                entity.setCustomName("§c" + input);
                entity.setCustomNameVisible(true);
                player.sendMessage("§a몬스터 이름을 §f" + input + "§a으로 변경했습니다.");
            }
            openEditor(player, entity);
        });
    }

    private void makePreview(LivingEntity entity, ItemStack cloneSource) {
        entity.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(true);
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);
        if (cloneSource == null || !cloneSource.hasItemMeta()) {
            setHealth(entity, Math.max(1.0, currentAttribute(entity, Attribute.MAX_HEALTH, DEFAULT_HEALTH)));
            setDamage(entity, currentAttribute(entity, Attribute.ATTACK_DAMAGE, DEFAULT_DAMAGE));
            setAggressive(entity, false);
            setAbility(entity, "NONE");
            setScale(entity, currentAttribute(entity, Attribute.SCALE, 1.0));
            setYaw(entity, entity.getLocation().getYaw());
            entity.setCustomName("§c" + prettyEntityName(entity.getType()));
            entity.setCustomNameVisible(true);
            return;
        }
        PersistentDataContainer source = cloneSource.getItemMeta().getPersistentDataContainer();
        String name = source.get(cloneNameKey, PersistentDataType.STRING);
        Double health = source.get(cloneHealthKey, PersistentDataType.DOUBLE);
        Double damage = source.get(cloneDamageKey, PersistentDataType.DOUBLE);
        Byte aggressive = source.get(cloneAggressiveKey, PersistentDataType.BYTE);
        String ability = source.get(cloneAbilityKey, PersistentDataType.STRING);
        Double scale = source.get(cloneScaleKey, PersistentDataType.DOUBLE);
        Float yaw = source.get(cloneYawKey, PersistentDataType.FLOAT);
        entity.setCustomName(name == null ? "§c" + prettyEntityName(entity.getType()) : name);
        entity.setCustomNameVisible(true);
        setHealth(entity, health == null ? DEFAULT_HEALTH : health);
        setDamage(entity, damage == null ? DEFAULT_DAMAGE : damage);
        setAggressive(entity, aggressive != null && aggressive == (byte) 1);
        setAbility(entity, ability == null ? "NONE" : ability);
        setScale(entity, scale == null ? 1.0 : scale);
        setYaw(entity, yaw == null ? entity.getLocation().getYaw() : yaw);
        equipmentDropEditor.applyFromTool(entity, source);
    }

    private void openEditor(Player player, LivingEntity entity) {
        editingTargets.put(player.getUniqueId(), entity.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 36, GUI_TITLE);
        inventory.setItem(10, menuItem(Material.NAME_TAG, "§e이름", List.of("§7현재: §f" + displayName(entity), "§a클릭 후 채팅으로 입력")));
        inventory.setItem(12, menuItem(Material.RED_DYE, "§c체력: §f" + format(getHealth(entity)), List.of("§a좌클릭 §7+1", "§c우클릭 §7-1", "§eShift 클릭 §7±10")));
        inventory.setItem(14, menuItem(Material.IRON_SWORD, "§6공격력: §f" + format(getDamage(entity)), List.of("§a좌클릭 §7+1", "§c우클릭 §7-1", "§eShift 클릭 §7±10")));
        inventory.setItem(16, menuItem(isAggressive(entity) ? Material.LIME_DYE : Material.GRAY_DYE, "§e공격 여부: " + (isAggressive(entity) ? "§aON" : "§cOFF"), List.of("§a클릭하여 전환")));
        inventory.setItem(18, menuItem(Material.COMPASS, "§b방향: §f" + directionDescription(getYaw(entity)), List.of("§a좌클릭 §7왼쪽 15°", "§c우클릭 §7오른쪽 15°", "§eShift 클릭 §7±90°")));
        inventory.setItem(20, menuItem(Material.SLIME_BALL, "§a크기 배율: §f" + format(getScale(entity)) + "배", List.of("§7범위: 0.25배~4.0배", "§a좌클릭 §7+0.1", "§c우클릭 §7-0.1", "§eShift 클릭 §7±0.5")));
        inventory.setItem(22, menuItem(Material.BLAZE_POWDER, "§d특수 능력: §f" + getAbility(entity), List.of("§a좌클릭 §7다음", "§c우클릭 §7이전")));
        inventory.setItem(24, menuItem(Material.BLAZE_ROD, "§6배치 도구 받기", List.of("§7모든 설정을 복제 도구에 저장합니다.")));
        inventory.setItem(26, menuItem(Material.ENDER_EYE, "§3내가 보는 방향으로 맞추기", List.of("§7플레이어의 수평 방향으로 회전")));
        inventory.setItem(28, menuItem(Material.ARMOR_STAND, "§b장비 편집기", List.of("§7방어구·주무기·보조무기 설정")));
        inventory.setItem(30, menuItem(Material.CHEST, "§6드롭 아이템 편집기", List.of("§7아이템과 개별 드롭 확률 설정")));
        inventory.setItem(31, menuItem(Material.BARRIER, "§c프리뷰 몬스터 삭제", List.of("§7클릭하면 삭제됩니다.")));
        player.openInventory(inventory);
    }

    private ItemStack createCloneTool(LivingEntity entity) {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§6§l[" + displayName(entity) + "§6§l] 배치 도구");
        meta.setLore(List.of("§7블록 우클릭: 같은 프리뷰 몬스터 배치", "§8종류: " + entity.getType().name(), "§8체력: " + format(getHealth(entity)) + " / 공격력: " + format(getDamage(entity)), "§8크기: " + format(getScale(entity)) + "배 / 방향: " + directionDescription(getYaw(entity)), "§8장비와 드롭 설정 포함"));
        meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(cloneToolKey, PersistentDataType.BYTE, (byte) 1);
        data.set(cloneTypeKey, PersistentDataType.STRING, entity.getType().name());
        data.set(cloneNameKey, PersistentDataType.STRING, entity.getCustomName() == null ? displayName(entity) : entity.getCustomName());
        data.set(cloneHealthKey, PersistentDataType.DOUBLE, getHealth(entity));
        data.set(cloneDamageKey, PersistentDataType.DOUBLE, getDamage(entity));
        data.set(cloneAggressiveKey, PersistentDataType.BYTE, isAggressive(entity) ? (byte) 1 : (byte) 0);
        data.set(cloneAbilityKey, PersistentDataType.STRING, getAbility(entity));
        data.set(cloneScaleKey, PersistentDataType.DOUBLE, getScale(entity));
        data.set(cloneYawKey, PersistentDataType.FLOAT, getYaw(entity));
        equipmentDropEditor.copyToTool(entity, data);
        tool.setItemMeta(meta);
        return tool;
    }

    private LivingEntity editingEntity(Player player) {
        UUID id = editingTargets.get(player.getUniqueId());
        Entity raw = id == null ? null : Bukkit.getEntity(id);
        return raw instanceof LivingEntity living && living.isValid() ? living : null;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasToolKey(ItemStack item, Material material, NamespacedKey key) {
        if (item == null || item.getType() != material || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }
    private boolean isEditorTool(ItemStack item) { return hasToolKey(item, Material.STICK, editorToolKey); }
    private boolean isCloneTool(ItemStack item) { return hasToolKey(item, Material.BLAZE_ROD, cloneToolKey); }
    private boolean isDeleteTool(ItemStack item) { return hasToolKey(item, Material.BREEZE_ROD, deleteToolKey); }
    private boolean isPreview(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private double getHealth(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(healthKey, PersistentDataType.DOUBLE, DEFAULT_HEALTH); }
    private void setHealth(LivingEntity entity, double value) {
        double safe = clamp(value, 1.0, 2048.0);
        entity.getPersistentDataContainer().set(healthKey, PersistentDataType.DOUBLE, safe);
        setAttribute(entity, Attribute.MAX_HEALTH, safe);
        entity.setHealth(Math.min(safe, currentAttribute(entity, Attribute.MAX_HEALTH, safe)));
    }
    private double getDamage(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(damageKey, PersistentDataType.DOUBLE, DEFAULT_DAMAGE); }
    private void setDamage(LivingEntity entity, double value) {
        double safe = clamp(value, 0.0, 1024.0);
        entity.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, safe);
        setAttribute(entity, Attribute.ATTACK_DAMAGE, safe);
    }
    private double getScale(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(scaleKey, PersistentDataType.DOUBLE, currentAttribute(entity, Attribute.SCALE, 1.0)); }
    private void setScale(LivingEntity entity, double value) {
        double safe = Math.round(clamp(value, MIN_SCALE, MAX_SCALE) * 100.0) / 100.0;
        entity.getPersistentDataContainer().set(scaleKey, PersistentDataType.DOUBLE, safe);
        setAttribute(entity, Attribute.SCALE, safe);
    }
    private boolean isAggressive(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(aggressiveKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private void setAggressive(LivingEntity entity, boolean value) { entity.getPersistentDataContainer().set(aggressiveKey, PersistentDataType.BYTE, value ? (byte) 1 : (byte) 0); }
    private String getAbility(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(abilityKey, PersistentDataType.STRING, "NONE"); }
    private void setAbility(LivingEntity entity, String ability) { entity.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, ability); }
    private void cycleAbility(LivingEntity entity, int direction) {
        int index = ABILITIES.indexOf(getAbility(entity));
        if (index < 0) index = 0;
        setAbility(entity, ABILITIES.get(Math.floorMod(index + direction, ABILITIES.size())));
    }
    private void setYaw(LivingEntity entity, float yaw) {
        float normalized = normalizeYaw(yaw);
        Location location = entity.getLocation();
        location.setYaw(normalized);
        entity.teleport(location);
        entity.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, normalized);
    }
    private float getYaw(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(yawKey, PersistentDataType.FLOAT, normalizeYaw(entity.getLocation().getYaw())); }
    private float normalizeYaw(float yaw) { float normalized = yaw % 360.0f; return normalized < 0.0f ? normalized + 360.0f : normalized; }
    private String directionDescription(float yaw) {
        float value = normalizeYaw(yaw);
        String direction;
        if (value >= 337.5f || value < 22.5f) direction = "남쪽";
        else if (value < 67.5f) direction = "남서쪽";
        else if (value < 112.5f) direction = "서쪽";
        else if (value < 157.5f) direction = "북서쪽";
        else if (value < 202.5f) direction = "북쪽";
        else if (value < 247.5f) direction = "북동쪽";
        else if (value < 292.5f) direction = "동쪽";
        else direction = "남동쪽";
        return direction + " (" + Math.round(value) + "°)";
    }
    private void setAttribute(LivingEntity entity, Attribute attribute, double value) { AttributeInstance instance = entity.getAttribute(attribute); if (instance != null) instance.setBaseValue(value); }
    private double currentAttribute(LivingEntity entity, Attribute attribute, double fallback) { AttributeInstance instance = entity.getAttribute(attribute); return instance == null ? fallback : instance.getBaseValue(); }
    private String displayName(LivingEntity entity) { String custom = entity.getCustomName(); return custom == null ? prettyEntityName(entity.getType()) : ChatColor.stripColor(custom); }
    private String prettyEntityName(EntityType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) { if (!builder.isEmpty()) builder.append(' '); builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); }
        return builder.toString();
    }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private String format(double value) { return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
}
