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
    private static final double DEFAULT_ATTACK_RANGE = 2.1;
    private static final List<String> ABILITIES = List.of("NONE", "DASH", "FIREBALL", "HEAL", "SUMMON");
    private static final List<String> ROLES = List.of("NORMAL", "BOSS", "TRADER", "REWARD", "ALLY");
    private static final List<String> COLORS = List.of("RED", "GOLD", "YELLOW", "GREEN", "AQUA", "BLUE", "LIGHT_PURPLE", "WHITE", "GRAY");

    private final JavaPlugin plugin;
    private final EquipmentDropEditorManager equipmentDropEditor;
    private final RoleSettingsManager roleSettingsManager;
    private final NamespacedKey previewKey, healthKey, damageKey, roleKey, rangeKey, nameColorKey, abilityKey, scaleKey, yawKey;
    private final NamespacedKey editorToolKey, cloneToolKey, deleteToolKey;
    private final NamespacedKey cloneTypeKey, cloneNameKey, cloneHealthKey, cloneDamageKey, cloneRoleKey, cloneRangeKey, cloneNameColorKey, cloneAbilityKey, cloneScaleKey, cloneYawKey;
    private final Map<UUID, UUID> editingTargets = new HashMap<>();
    private final Map<UUID, UUID> awaitingName = new HashMap<>();

    public CustomMobEditorManager(JavaPlugin plugin, EquipmentDropEditorManager equipmentDropEditor, RoleSettingsManager roleSettingsManager) {
        this.plugin = plugin;
        this.equipmentDropEditor = equipmentDropEditor;
        this.roleSettingsManager = roleSettingsManager;
        previewKey = key("custom_mob_preview");
        healthKey = key("custom_mob_health");
        damageKey = key("custom_mob_damage");
        roleKey = key("custom_mob_role");
        rangeKey = key("custom_mob_range");
        nameColorKey = key("custom_mob_name_color");
        abilityKey = key("custom_mob_ability");
        scaleKey = key("custom_mob_scale");
        yawKey = key("custom_mob_yaw");
        editorToolKey = key("custom_mob_editor_tool");
        cloneToolKey = key("custom_mob_clone_tool");
        deleteToolKey = key("custom_mob_delete_tool");
        cloneTypeKey = key("clone_type");
        cloneNameKey = key("clone_name");
        cloneHealthKey = key("clone_health");
        cloneDamageKey = key("clone_damage");
        cloneRoleKey = key("clone_role");
        cloneRangeKey = key("clone_range");
        cloneNameColorKey = key("clone_name_color");
        cloneAbilityKey = key("clone_ability");
        cloneScaleKey = key("clone_scale");
        cloneYawKey = key("clone_yaw");
    }

    private NamespacedKey key(String value) { return new NamespacedKey(plugin, value); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnEgg(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                && event.getLocation().getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) {
            Bukkit.getScheduler().runTask(plugin, () -> makePreview(event.getEntity(), null));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin") || !player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity) || !isPreview(entity)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isEditorTool(held)) {
            event.setCancelled(true);
            openEditor(player, entity);
        } else if (isCloneTool(held)) {
            event.setCancelled(true);
            player.getInventory().addItem(createCloneTool(entity));
            player.sendMessage("§6§l[Dungeons] §a모든 역할 설정이 포함된 배치 도구를 받았습니다.");
        } else if (isDeleteTool(held)) {
            event.setCancelled(true);
            editingTargets.values().removeIf(entity.getUniqueId()::equals);
            awaitingName.values().removeIf(entity.getUniqueId()::equals);
            entity.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCloneToolUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getItem() == null || !isCloneTool(event.getItem())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin") || !player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        event.setCancelled(true);
        PersistentDataContainer data = event.getItem().getItemMeta().getPersistentDataContainer();
        String typeName = data.get(cloneTypeKey, PersistentDataType.STRING);
        if (typeName == null) return;
        EntityType type;
        try { type = EntityType.valueOf(typeName); } catch (IllegalArgumentException ex) { return; }
        Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
        Float yaw = data.get(cloneYawKey, PersistentDataType.FLOAT);
        location.setYaw(yaw == null ? player.getLocation().getYaw() : yaw);
        Entity spawned = player.getWorld().spawnEntity(location, type);
        if (spawned instanceof LivingEntity living) makePreview(living, event.getItem()); else spawned.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEditorClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LivingEntity entity = editingEntity(player);
        if (entity == null) { player.closeInventory(); return; }
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        double amount = shift ? 10.0 : 1.0;
        switch (event.getRawSlot()) {
            case 9 -> { cycleColor(entity, right ? -1 : 1); openEditor(player, entity); }
            case 10 -> {
                awaitingName.put(player.getUniqueId(), entity.getUniqueId());
                player.closeInventory();
                player.sendMessage("§e채팅에 새 이름을 입력하세요. §7취소: cancel");
            }
            case 11 -> { cycleRole(entity, right ? -1 : 1); roleSettingsManager.initializeDefaults(entity); openEditor(player, entity); }
            case 12 -> { setHealth(entity, getHealth(entity) + (right ? -amount : amount)); openEditor(player, entity); }
            case 13 -> {
                double step = shift ? 1.0 : 0.25;
                setRange(entity, getRange(entity) + (right ? -step : step));
                openEditor(player, entity);
            }
            case 14 -> { setDamage(entity, getDamage(entity) + (right ? -amount : amount)); openEditor(player, entity); }
            case 16 -> roleSettingsManager.openSettings(player, entity, getRole(entity));
            case 18 -> { setYaw(entity, getYaw(entity) + (right ? (shift ? 90 : 15) : -(shift ? 90 : 15))); openEditor(player, entity); }
            case 20 -> { setScale(entity, getScale(entity) + (right ? -(shift ? 0.5 : 0.1) : (shift ? 0.5 : 0.1))); openEditor(player, entity); }
            case 22 -> { cycleAbility(entity, right ? -1 : 1); openEditor(player, entity); }
            case 24 -> player.getInventory().addItem(createCloneTool(entity));
            case 26 -> { setYaw(entity, player.getLocation().getYaw()); openEditor(player, entity); }
            case 28 -> equipmentDropEditor.openEquipmentEditor(player, entity);
            case 30 -> equipmentDropEditor.openDropEditor(player, entity);
            case 40 -> { entity.remove(); player.closeInventory(); }
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
            Entity raw = Bukkit.getEntity(entityId);
            if (raw instanceof LivingEntity entity && entity.isValid()) {
                if (!input.equalsIgnoreCase("cancel") && !input.isBlank() && input.length() <= 40) setPlainName(entity, input);
                openEditor(event.getPlayer(), entity);
            }
        });
    }

    private void makePreview(LivingEntity entity, ItemStack sourceItem) {
        entity.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(true);
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);
        if (sourceItem == null || !sourceItem.hasItemMeta()) {
            setHealth(entity, Math.max(1, currentAttribute(entity, Attribute.MAX_HEALTH, DEFAULT_HEALTH)));
            setDamage(entity, currentAttribute(entity, Attribute.ATTACK_DAMAGE, DEFAULT_DAMAGE));
            setRole(entity, "NORMAL");
            setRange(entity, DEFAULT_ATTACK_RANGE);
            setNameColor(entity, "RED");
            setAbility(entity, "NONE");
            setScale(entity, currentAttribute(entity, Attribute.SCALE, 1));
            setYaw(entity, entity.getLocation().getYaw());
            setPlainName(entity, prettyEntityName(entity.getType()));
            roleSettingsManager.initializeDefaults(entity);
            return;
        }
        PersistentDataContainer source = sourceItem.getItemMeta().getPersistentDataContainer();
        setHealth(entity, number(source.get(cloneHealthKey, PersistentDataType.DOUBLE), DEFAULT_HEALTH));
        setDamage(entity, number(source.get(cloneDamageKey, PersistentDataType.DOUBLE), DEFAULT_DAMAGE));
        setRole(entity, text(source.get(cloneRoleKey, PersistentDataType.STRING), "NORMAL"));
        setRange(entity, number(source.get(cloneRangeKey, PersistentDataType.DOUBLE), DEFAULT_ATTACK_RANGE));
        setNameColor(entity, text(source.get(cloneNameColorKey, PersistentDataType.STRING), "RED"));
        setAbility(entity, text(source.get(cloneAbilityKey, PersistentDataType.STRING), "NONE"));
        setScale(entity, number(source.get(cloneScaleKey, PersistentDataType.DOUBLE), 1));
        Float yaw = source.get(cloneYawKey, PersistentDataType.FLOAT);
        setYaw(entity, yaw == null ? entity.getLocation().getYaw() : yaw);
        setPlainName(entity, text(source.get(cloneNameKey, PersistentDataType.STRING), prettyEntityName(entity.getType())));
        equipmentDropEditor.applyFromTool(entity, source);
        roleSettingsManager.applyFromTool(entity, source);
    }

    private void openEditor(Player player, LivingEntity entity) {
        editingTargets.put(player.getUniqueId(), entity.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 45, GUI_TITLE);
        String role = getRole(entity);
        inv.setItem(9, menu(Material.PINK_DYE, "§d이름 색상: " + colorCode(getNameColor(entity)) + colorKorean(getNameColor(entity)), "§7좌/우클릭으로 변경"));
        inv.setItem(10, menu(Material.NAME_TAG, "§e이름", "§7현재: " + coloredName(entity), "§a클릭 후 채팅 입력"));
        inv.setItem(11, menu(roleMaterial(role), "§b역할: §f" + roleKorean(role), "§7좌/우클릭으로 변경"));
        inv.setItem(12, menu(Material.RED_DYE, "§c체력: §f" + format(getHealth(entity)), "§7클릭 ±1, Shift ±10"));
        if (isCombatRole(role)) {
            inv.setItem(13, menu(Material.TARGET, "§e공격 사거리: §f" + format(getRange(entity)) + "블록", "§7이 거리 안에서 공격이 발동합니다.", "§7클릭 ±0.25, Shift ±1"));
            inv.setItem(14, menu(Material.IRON_SWORD, "§6공격력: §f" + format(getDamage(entity)), "§7클릭 ±1, Shift ±10"));
            inv.setItem(22, menu(Material.BLAZE_POWDER, "§d특수 능력: §f" + getAbility(entity), "§7좌/우클릭으로 변경"));
        } else {
            inv.setItem(13, menu(Material.GRAY_STAINED_GLASS_PANE, "§8공격 사거리 비활성", "§7현재 역할은 플레이어를 공격하지 않습니다."));
            inv.setItem(14, menu(Material.GRAY_STAINED_GLASS_PANE, "§8공격력 비활성", "§7현재 역할은 플레이어를 공격하지 않습니다."));
            inv.setItem(22, menu(Material.GRAY_STAINED_GLASS_PANE, "§8전투 능력 비활성"));
        }
        inv.setItem(16, menu(roleSettingsMaterial(role), "§a역할 전용 설정: §f" + roleKorean(role), roleSettingsLore(role)));
        inv.setItem(18, menu(Material.COMPASS, "§b방향: §f" + Math.round(getYaw(entity)) + "°", "§7클릭 ±15°, Shift ±90°"));
        inv.setItem(20, menu(Material.SLIME_BALL, "§a크기: §f" + format(getScale(entity)) + "배", "§7클릭 ±0.1, Shift ±0.5"));
        inv.setItem(24, menu(Material.BLAZE_ROD, "§6배치 도구 받기"));
        inv.setItem(26, menu(Material.ENDER_EYE, "§3내 방향으로 맞추기"));
        inv.setItem(28, menu(Material.DIAMOND_CHESTPLATE, "§b장비 편집기"));
        inv.setItem(30, menu(Material.CHEST, "§6드롭 편집기"));
        inv.setItem(40, menu(Material.BARRIER, "§c몬스터 삭제"));
        player.openInventory(inv);
    }

    private ItemStack createCloneTool(LivingEntity entity) {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§6§l[" + displayName(entity) + "] 배치 도구");
        meta.setLore(List.of("§7역할: §f" + roleKorean(getRole(entity)), "§7역할 전용 설정까지 함께 복제됩니다."));
        meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer d = meta.getPersistentDataContainer();
        d.set(cloneToolKey, PersistentDataType.BYTE, (byte) 1);
        d.set(cloneTypeKey, PersistentDataType.STRING, entity.getType().name());
        d.set(cloneNameKey, PersistentDataType.STRING, displayName(entity));
        d.set(cloneHealthKey, PersistentDataType.DOUBLE, getHealth(entity));
        d.set(cloneDamageKey, PersistentDataType.DOUBLE, getDamage(entity));
        d.set(cloneRoleKey, PersistentDataType.STRING, getRole(entity));
        d.set(cloneRangeKey, PersistentDataType.DOUBLE, getRange(entity));
        d.set(cloneNameColorKey, PersistentDataType.STRING, getNameColor(entity));
        d.set(cloneAbilityKey, PersistentDataType.STRING, getAbility(entity));
        d.set(cloneScaleKey, PersistentDataType.DOUBLE, getScale(entity));
        d.set(cloneYawKey, PersistentDataType.FLOAT, getYaw(entity));
        equipmentDropEditor.copyToTool(entity, d);
        roleSettingsManager.copyToTool(entity, d);
        tool.setItemMeta(meta);
        return tool;
    }

    private LivingEntity editingEntity(Player player) {
        UUID id = editingTargets.get(player.getUniqueId());
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        return entity instanceof LivingEntity living && living.isValid() ? living : null;
    }
    private ItemStack menu(Material material, String name, String... lore) { ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(List.of(lore)); item.setItemMeta(meta); return item; }
    private boolean tagged(ItemStack item, Material material, NamespacedKey key) { return item != null && item.getType() == material && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isEditorTool(ItemStack item) { return tagged(item, Material.STICK, editorToolKey); }
    private boolean isCloneTool(ItemStack item) { return tagged(item, Material.BLAZE_ROD, cloneToolKey); }
    private boolean isDeleteTool(ItemStack item) { return tagged(item, Material.BREEZE_ROD, deleteToolKey); }
    private boolean isPreview(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isCombatRole(String role) { return role.equals("NORMAL") || role.equals("BOSS"); }
    private double getHealth(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(healthKey, PersistentDataType.DOUBLE, DEFAULT_HEALTH); }
    private void setHealth(LivingEntity entity, double value) { double safe = clamp(value, 1, 2048); entity.getPersistentDataContainer().set(healthKey, PersistentDataType.DOUBLE, safe); setAttribute(entity, Attribute.MAX_HEALTH, safe); entity.setHealth(Math.min(safe, currentAttribute(entity, Attribute.MAX_HEALTH, safe))); }
    private double getDamage(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(damageKey, PersistentDataType.DOUBLE, DEFAULT_DAMAGE); }
    private void setDamage(LivingEntity entity, double value) { double safe = clamp(value, 0, 1024); entity.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, safe); setAttribute(entity, Attribute.ATTACK_DAMAGE, safe); }
    private double getRange(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(rangeKey, PersistentDataType.DOUBLE, DEFAULT_ATTACK_RANGE); }
    private void setRange(LivingEntity entity, double value) { entity.getPersistentDataContainer().set(rangeKey, PersistentDataType.DOUBLE, Math.round(clamp(value, 0.5, 64) * 100.0) / 100.0); }
    private String getRole(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(roleKey, PersistentDataType.STRING, "NORMAL"); }
    private void setRole(LivingEntity entity, String role) { entity.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role); }
    private void cycleRole(LivingEntity entity, int direction) { int index = ROLES.indexOf(getRole(entity)); setRole(entity, ROLES.get(Math.floorMod((index < 0 ? 0 : index) + direction, ROLES.size()))); }
    private String getNameColor(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(nameColorKey, PersistentDataType.STRING, "RED"); }
    private void setNameColor(LivingEntity entity, String color) { entity.getPersistentDataContainer().set(nameColorKey, PersistentDataType.STRING, color); refreshName(entity); }
    private void cycleColor(LivingEntity entity, int direction) { int index = COLORS.indexOf(getNameColor(entity)); setNameColor(entity, COLORS.get(Math.floorMod((index < 0 ? 0 : index) + direction, COLORS.size()))); }
    private String getAbility(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(abilityKey, PersistentDataType.STRING, "NONE"); }
    private void setAbility(LivingEntity entity, String ability) { entity.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, ability); }
    private void cycleAbility(LivingEntity entity, int direction) { int index = ABILITIES.indexOf(getAbility(entity)); setAbility(entity, ABILITIES.get(Math.floorMod((index < 0 ? 0 : index) + direction, ABILITIES.size()))); }
    private double getScale(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(scaleKey, PersistentDataType.DOUBLE, currentAttribute(entity, Attribute.SCALE, 1)); }
    private void setScale(LivingEntity entity, double value) { double safe = Math.round(clamp(value, 0.25, 4.0) * 100.0) / 100.0; entity.getPersistentDataContainer().set(scaleKey, PersistentDataType.DOUBLE, safe); setAttribute(entity, Attribute.SCALE, safe); }
    private float getYaw(LivingEntity entity) { return entity.getPersistentDataContainer().getOrDefault(yawKey, PersistentDataType.FLOAT, normalize(entity.getLocation().getYaw())); }
    private void setYaw(LivingEntity entity, float yaw) { float safe = normalize(yaw); Location location = entity.getLocation(); location.setYaw(safe); entity.teleport(location); entity.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, safe); }
    private void setPlainName(LivingEntity entity, String plain) { entity.setCustomName(colorCode(getNameColor(entity)) + ChatColor.stripColor(plain)); entity.setCustomNameVisible(true); }
    private void refreshName(LivingEntity entity) { setPlainName(entity, displayName(entity)); }
    private String coloredName(LivingEntity entity) { return colorCode(getNameColor(entity)) + displayName(entity); }
    private String displayName(LivingEntity entity) { String name = entity.getCustomName(); return name == null ? prettyEntityName(entity.getType()) : ChatColor.stripColor(name); }
    private String prettyEntityName(EntityType type) { String[] parts = type.name().toLowerCase(Locale.ROOT).split("_"); StringBuilder builder = new StringBuilder(); for (String part : parts) { if (!builder.isEmpty()) builder.append(' '); builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); } return builder.toString(); }
    private ChatColor colorCode(String color) { try { return ChatColor.valueOf(color); } catch (IllegalArgumentException ex) { return ChatColor.RED; } }
    private String colorKorean(String color) { return switch (color) { case "GOLD" -> "주황"; case "YELLOW" -> "노랑"; case "GREEN" -> "초록"; case "AQUA" -> "하늘"; case "BLUE" -> "파랑"; case "LIGHT_PURPLE" -> "분홍"; case "WHITE" -> "흰색"; case "GRAY" -> "회색"; default -> "빨강"; }; }
    private String roleKorean(String role) { return switch (role) { case "BOSS" -> "보스몹"; case "TRADER" -> "거래인"; case "REWARD" -> "보상지급인"; case "ALLY" -> "조력자"; default -> "일반몹"; }; }
    private Material roleMaterial(String role) { return switch (role) { case "BOSS" -> Material.DRAGON_HEAD; case "TRADER" -> Material.EMERALD; case "REWARD" -> Material.CHEST; case "ALLY" -> Material.TOTEM_OF_UNDYING; default -> Material.IRON_SWORD; }; }
    private Material roleSettingsMaterial(String role) { return switch (role) { case "BOSS" -> Material.NETHER_STAR; case "TRADER" -> Material.EMERALD_BLOCK; case "REWARD" -> Material.ENDER_CHEST; case "ALLY" -> Material.GOLDEN_APPLE; default -> Material.TARGET; }; }
    private String roleSettingsLore(String role) { return switch (role) { case "BOSS" -> "§7보스바와 보스 표시 설정"; case "TRADER" -> "§7거래 비용과 결과 아이템 설정"; case "REWARD" -> "§7지급 보상과 1회 제한 설정"; case "ALLY" -> "§7공격형·회복형 지원 설정"; default -> "§7메인 화면의 공격 설정을 사용합니다."; }; }
    private void setAttribute(LivingEntity entity, Attribute attribute, double value) { AttributeInstance instance = entity.getAttribute(attribute); if (instance != null) instance.setBaseValue(value); }
    private double currentAttribute(LivingEntity entity, Attribute attribute, double fallback) { AttributeInstance instance = entity.getAttribute(attribute); return instance == null ? fallback : instance.getBaseValue(); }
    private float normalize(float yaw) { float value = yaw % 360.0f; return value < 0 ? value + 360.0f : value; }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private String format(double value) { return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.US, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private double number(Double value, double fallback) { return value == null ? fallback : value; }
    private String text(String value, String fallback) { return value == null ? fallback : value; }
}
