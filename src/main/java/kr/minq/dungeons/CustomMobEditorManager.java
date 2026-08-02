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
    private static final double DEFAULT_RANGE = 24.0;
    private static final List<String> ABILITIES = List.of("NONE", "DASH", "FIREBALL", "HEAL", "SUMMON");
    private static final List<String> ROLES = List.of("NORMAL", "BOSS", "TRADER", "REWARD", "ALLY");
    private static final List<String> COLORS = List.of("RED", "GOLD", "YELLOW", "GREEN", "AQUA", "BLUE", "LIGHT_PURPLE", "WHITE", "GRAY");

    private final JavaPlugin plugin;
    private final EquipmentDropEditorManager equipmentDropEditor;
    private final NamespacedKey previewKey, healthKey, damageKey, roleKey, rangeKey, nameColorKey, abilityKey, scaleKey, yawKey;
    private final NamespacedKey editorToolKey, cloneToolKey, deleteToolKey;
    private final NamespacedKey cloneTypeKey, cloneNameKey, cloneHealthKey, cloneDamageKey, cloneRoleKey, cloneRangeKey, cloneNameColorKey, cloneAbilityKey, cloneScaleKey, cloneYawKey;
    private final Map<UUID, UUID> editingTargets = new HashMap<>();
    private final Map<UUID, UUID> awaitingName = new HashMap<>();

    public CustomMobEditorManager(JavaPlugin plugin, EquipmentDropEditorManager equipmentDropEditor) {
        this.plugin = plugin;
        this.equipmentDropEditor = equipmentDropEditor;
        previewKey = key("custom_mob_preview"); healthKey = key("custom_mob_health"); damageKey = key("custom_mob_damage");
        roleKey = key("custom_mob_role"); rangeKey = key("custom_mob_range"); nameColorKey = key("custom_mob_name_color");
        abilityKey = key("custom_mob_ability"); scaleKey = key("custom_mob_scale"); yawKey = key("custom_mob_yaw");
        editorToolKey = key("custom_mob_editor_tool"); cloneToolKey = key("custom_mob_clone_tool"); deleteToolKey = key("custom_mob_delete_tool");
        cloneTypeKey = key("clone_type"); cloneNameKey = key("clone_name"); cloneHealthKey = key("clone_health"); cloneDamageKey = key("clone_damage");
        cloneRoleKey = key("clone_role"); cloneRangeKey = key("clone_range"); cloneNameColorKey = key("clone_name_color");
        cloneAbilityKey = key("clone_ability"); cloneScaleKey = key("clone_scale"); cloneYawKey = key("clone_yaw");
    }

    private NamespacedKey key(String value) { return new NamespacedKey(plugin, value); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnEgg(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG && event.getLocation().getWorld().getName().equals(TemplateWorldManager.WORLD_NAME))
            Bukkit.getScheduler().runTask(plugin, () -> makePreview(event.getEntity(), null));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin") || !player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return;
        if (!(event.getRightClicked() instanceof LivingEntity entity) || !isPreview(entity)) return;
        ItemStack held = player.getInventory().getItemInMainHand();
        if (isEditorTool(held)) { event.setCancelled(true); openEditor(player, entity); }
        else if (isCloneTool(held)) { event.setCancelled(true); player.getInventory().addItem(createCloneTool(entity)); }
        else if (isDeleteTool(held)) { event.setCancelled(true); entity.remove(); }
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
        Float yaw = data.get(cloneYawKey, PersistentDataType.FLOAT); location.setYaw(yaw == null ? player.getLocation().getYaw() : yaw);
        Entity spawned = player.getWorld().spawnEntity(location, type);
        if (spawned instanceof LivingEntity living) makePreview(living, event.getItem()); else spawned.remove();
    }

    @EventHandler(ignoreCancelled = true)
    public void onEditorClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LivingEntity entity = editingEntity(player); if (entity == null) { player.closeInventory(); return; }
        boolean right = event.isRightClick(), shift = event.isShiftClick(); double amount = shift ? 10 : 1;
        switch (event.getRawSlot()) {
            case 9 -> { cycleColor(entity, right ? -1 : 1); openEditor(player, entity); }
            case 10 -> { awaitingName.put(player.getUniqueId(), entity.getUniqueId()); player.closeInventory(); player.sendMessage("§e채팅에 새 이름을 입력하세요. §7취소: cancel"); }
            case 11 -> { cycleRole(entity, right ? -1 : 1); openEditor(player, entity); }
            case 12 -> { setHealth(entity, getHealth(entity) + (right ? -amount : amount)); openEditor(player, entity); }
            case 13 -> { setRange(entity, getRange(entity) + (right ? -amount : amount)); openEditor(player, entity); }
            case 14 -> { setDamage(entity, getDamage(entity) + (right ? -amount : amount)); openEditor(player, entity); }
            case 18 -> { setYaw(entity, getYaw(entity) + (right ? (shift ? 90 : 15) : -(shift ? 90 : 15))); openEditor(player, entity); }
            case 20 -> { setScale(entity, getScale(entity) + (right ? -(shift ? 0.5 : 0.1) : (shift ? 0.5 : 0.1))); openEditor(player, entity); }
            case 22 -> { cycleAbility(entity, right ? -1 : 1); openEditor(player, entity); }
            case 24 -> player.getInventory().addItem(createCloneTool(entity));
            case 26 -> { setYaw(entity, player.getLocation().getYaw()); openEditor(player, entity); }
            case 28 -> equipmentDropEditor.openEquipmentEditor(player, entity);
            case 30 -> equipmentDropEditor.openDropEditor(player, entity);
            case 31 -> { entity.remove(); player.closeInventory(); }
            default -> { }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNameInput(AsyncPlayerChatEvent event) {
        UUID entityId = awaitingName.remove(event.getPlayer().getUniqueId()); if (entityId == null) return;
        event.setCancelled(true); String input = ChatColor.stripColor(event.getMessage()).trim();
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
        entity.setAI(false); entity.setInvulnerable(true); entity.setSilent(true); entity.setPersistent(true); entity.setCanPickupItems(false); entity.setRemoveWhenFarAway(false);
        if (sourceItem == null || !sourceItem.hasItemMeta()) {
            setHealth(entity, Math.max(1, currentAttribute(entity, Attribute.MAX_HEALTH, DEFAULT_HEALTH)));
            setDamage(entity, currentAttribute(entity, Attribute.ATTACK_DAMAGE, DEFAULT_DAMAGE)); setRole(entity, "NORMAL"); setRange(entity, DEFAULT_RANGE);
            setNameColor(entity, "RED"); setAbility(entity, "NONE"); setScale(entity, currentAttribute(entity, Attribute.SCALE, 1)); setYaw(entity, entity.getLocation().getYaw());
            setPlainName(entity, prettyEntityName(entity.getType())); return;
        }
        PersistentDataContainer source = sourceItem.getItemMeta().getPersistentDataContainer();
        setHealth(entity, number(source.get(cloneHealthKey, PersistentDataType.DOUBLE), DEFAULT_HEALTH));
        setDamage(entity, number(source.get(cloneDamageKey, PersistentDataType.DOUBLE), DEFAULT_DAMAGE));
        setRole(entity, text(source.get(cloneRoleKey, PersistentDataType.STRING), "NORMAL"));
        setRange(entity, number(source.get(cloneRangeKey, PersistentDataType.DOUBLE), DEFAULT_RANGE));
        setNameColor(entity, text(source.get(cloneNameColorKey, PersistentDataType.STRING), "RED"));
        setAbility(entity, text(source.get(cloneAbilityKey, PersistentDataType.STRING), "NONE"));
        setScale(entity, number(source.get(cloneScaleKey, PersistentDataType.DOUBLE), 1));
        Float yaw = source.get(cloneYawKey, PersistentDataType.FLOAT); setYaw(entity, yaw == null ? entity.getLocation().getYaw() : yaw);
        setPlainName(entity, text(source.get(cloneNameKey, PersistentDataType.STRING), prettyEntityName(entity.getType())));
        equipmentDropEditor.applyFromTool(entity, source);
    }

    private void openEditor(Player player, LivingEntity entity) {
        editingTargets.put(player.getUniqueId(), entity.getUniqueId()); Inventory inv = Bukkit.createInventory(null, 36, GUI_TITLE);
        inv.setItem(9, menu(Material.PINK_DYE, "§d이름 색상: " + colorCode(getNameColor(entity)) + colorKorean(getNameColor(entity)), "§7좌/우클릭으로 변경"));
        inv.setItem(10, menu(Material.NAME_TAG, "§e이름", "§7현재: " + coloredName(entity), "§a클릭 후 채팅 입력"));
        inv.setItem(11, menu(roleMaterial(getRole(entity)), "§b역할: §f" + roleKorean(getRole(entity)), "§7좌/우클릭으로 변경"));
        inv.setItem(12, menu(Material.RED_DYE, "§c체력: §f" + format(getHealth(entity)), "§7클릭 ±1, Shift ±10"));
        inv.setItem(13, menu(Material.SPYGLASS, "§e사정거리: §f" + format(getRange(entity)) + "블록", "§7클릭 ±1, Shift ±10"));
        inv.setItem(14, menu(Material.IRON_SWORD, "§6공격력: §f" + format(getDamage(entity)), "§7클릭 ±1, Shift ±10"));
        inv.setItem(18, menu(Material.COMPASS, "§b방향: §f" + Math.round(getYaw(entity)) + "°", "§7클릭 ±15°, Shift ±90°"));
        inv.setItem(20, menu(Material.SLIME_BALL, "§a크기: §f" + format(getScale(entity)) + "배", "§7클릭 ±0.1, Shift ±0.5"));
        inv.setItem(22, menu(Material.BLAZE_POWDER, "§d특수 능력: §f" + getAbility(entity), "§7좌/우클릭으로 변경"));
        inv.setItem(24, menu(Material.BLAZE_ROD, "§6배치 도구 받기")); inv.setItem(26, menu(Material.ENDER_EYE, "§3내 방향으로 맞추기"));
        inv.setItem(28, menu(Material.DIAMOND_CHESTPLATE, "§b장비 편집기")); inv.setItem(30, menu(Material.CHEST, "§6드롭 편집기")); inv.setItem(31, menu(Material.BARRIER, "§c몬스터 삭제"));
        player.openInventory(inv);
    }

    private ItemStack createCloneTool(LivingEntity entity) {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD); ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§6§l[" + displayName(entity) + "] 배치 도구"); meta.setEnchantmentGlintOverride(true);
        PersistentDataContainer d = meta.getPersistentDataContainer();
        d.set(cloneToolKey, PersistentDataType.BYTE, (byte) 1); d.set(cloneTypeKey, PersistentDataType.STRING, entity.getType().name()); d.set(cloneNameKey, PersistentDataType.STRING, displayName(entity));
        d.set(cloneHealthKey, PersistentDataType.DOUBLE, getHealth(entity)); d.set(cloneDamageKey, PersistentDataType.DOUBLE, getDamage(entity));
        d.set(cloneRoleKey, PersistentDataType.STRING, getRole(entity)); d.set(cloneRangeKey, PersistentDataType.DOUBLE, getRange(entity)); d.set(cloneNameColorKey, PersistentDataType.STRING, getNameColor(entity));
        d.set(cloneAbilityKey, PersistentDataType.STRING, getAbility(entity)); d.set(cloneScaleKey, PersistentDataType.DOUBLE, getScale(entity)); d.set(cloneYawKey, PersistentDataType.FLOAT, getYaw(entity));
        equipmentDropEditor.copyToTool(entity, d); tool.setItemMeta(meta); return tool;
    }

    private LivingEntity editingEntity(Player p) { Entity e = Bukkit.getEntity(editingTargets.get(p.getUniqueId())); return e instanceof LivingEntity l && l.isValid() ? l : null; }
    private ItemStack menu(Material m, String name, String... lore) { ItemStack i = new ItemStack(m); ItemMeta meta = i.getItemMeta(); meta.setDisplayName(name); meta.setLore(List.of(lore)); i.setItemMeta(meta); return i; }
    private boolean tagged(ItemStack i, Material m, NamespacedKey k) { return i != null && i.getType() == m && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().getOrDefault(k, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private boolean isEditorTool(ItemStack i) { return tagged(i, Material.STICK, editorToolKey); } private boolean isCloneTool(ItemStack i) { return tagged(i, Material.BLAZE_ROD, cloneToolKey); } private boolean isDeleteTool(ItemStack i) { return tagged(i, Material.BREEZE_ROD, deleteToolKey); }
    private boolean isPreview(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1; }
    private double getHealth(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(healthKey, PersistentDataType.DOUBLE, DEFAULT_HEALTH); }
    private void setHealth(LivingEntity e, double v) { v = clamp(v, 1, 2048); e.getPersistentDataContainer().set(healthKey, PersistentDataType.DOUBLE, v); setAttribute(e, Attribute.MAX_HEALTH, v); e.setHealth(Math.min(v, currentAttribute(e, Attribute.MAX_HEALTH, v))); }
    private double getDamage(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(damageKey, PersistentDataType.DOUBLE, DEFAULT_DAMAGE); }
    private void setDamage(LivingEntity e, double v) { v = clamp(v, 0, 1024); e.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, v); setAttribute(e, Attribute.ATTACK_DAMAGE, v); }
    private double getRange(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(rangeKey, PersistentDataType.DOUBLE, DEFAULT_RANGE); }
    private void setRange(LivingEntity e, double v) { v = clamp(v, 1, 128); e.getPersistentDataContainer().set(rangeKey, PersistentDataType.DOUBLE, v); setAttribute(e, Attribute.FOLLOW_RANGE, v); }
    private String getRole(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(roleKey, PersistentDataType.STRING, "NORMAL"); } private void setRole(LivingEntity e, String v) { e.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, v); }
    private void cycleRole(LivingEntity e, int d) { setRole(e, ROLES.get(Math.floorMod(ROLES.indexOf(getRole(e)) + d, ROLES.size()))); }
    private String getNameColor(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(nameColorKey, PersistentDataType.STRING, "RED"); } private void setNameColor(LivingEntity e, String v) { e.getPersistentDataContainer().set(nameColorKey, PersistentDataType.STRING, v); refreshName(e); }
    private void cycleColor(LivingEntity e, int d) { setNameColor(e, COLORS.get(Math.floorMod(COLORS.indexOf(getNameColor(e)) + d, COLORS.size()))); }
    private String getAbility(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(abilityKey, PersistentDataType.STRING, "NONE"); } private void setAbility(LivingEntity e, String v) { e.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, v); }
    private void cycleAbility(LivingEntity e, int d) { setAbility(e, ABILITIES.get(Math.floorMod(ABILITIES.indexOf(getAbility(e)) + d, ABILITIES.size()))); }
    private double getScale(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(scaleKey, PersistentDataType.DOUBLE, currentAttribute(e, Attribute.SCALE, 1)); }
    private void setScale(LivingEntity e, double v) { v = Math.round(clamp(v, 0.25, 4) * 100) / 100.0; e.getPersistentDataContainer().set(scaleKey, PersistentDataType.DOUBLE, v); setAttribute(e, Attribute.SCALE, v); }
    private float getYaw(LivingEntity e) { return e.getPersistentDataContainer().getOrDefault(yawKey, PersistentDataType.FLOAT, e.getLocation().getYaw()); }
    private void setYaw(LivingEntity e, float v) { v = (v % 360 + 360) % 360; Location l = e.getLocation(); l.setYaw(v); e.teleport(l); e.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, v); }
    private void setPlainName(LivingEntity e, String n) { e.setCustomName(colorCode(getNameColor(e)) + n); e.setCustomNameVisible(true); }
    private void refreshName(LivingEntity e) { setPlainName(e, displayName(e)); } private String coloredName(LivingEntity e) { return colorCode(getNameColor(e)) + displayName(e); }
    private String displayName(LivingEntity e) { return e.getCustomName() == null ? prettyEntityName(e.getType()) : ChatColor.stripColor(e.getCustomName()); }
    private ChatColor colorCode(String c) { try { return ChatColor.valueOf(c); } catch (Exception ex) { return ChatColor.RED; } }
    private String colorKorean(String c) { return switch (c) { case "GOLD" -> "주황"; case "YELLOW" -> "노랑"; case "GREEN" -> "초록"; case "AQUA" -> "하늘"; case "BLUE" -> "파랑"; case "LIGHT_PURPLE" -> "분홍"; case "WHITE" -> "흰색"; case "GRAY" -> "회색"; default -> "빨강"; }; }
    private String roleKorean(String r) { return switch (r) { case "BOSS" -> "보스몹"; case "TRADER" -> "거래인"; case "REWARD" -> "보상지급인"; case "ALLY" -> "조력자"; default -> "일반몹"; }; }
    private Material roleMaterial(String r) { return switch (r) { case "BOSS" -> Material.NETHER_STAR; case "TRADER" -> Material.EMERALD; case "REWARD" -> Material.CHEST; case "ALLY" -> Material.TOTEM_OF_UNDYING; default -> Material.IRON_SWORD; }; }
    private String prettyEntityName(EntityType t) { StringBuilder b = new StringBuilder(); for (String p : t.name().toLowerCase(Locale.ROOT).split("_")) { if (!b.isEmpty()) b.append(' '); b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)); } return b.toString(); }
    private void setAttribute(LivingEntity e, Attribute a, double v) { AttributeInstance i = e.getAttribute(a); if (i != null) i.setBaseValue(v); } private double currentAttribute(LivingEntity e, Attribute a, double f) { AttributeInstance i = e.getAttribute(a); return i == null ? f : i.getBaseValue(); }
    private double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); } private double number(Double v, double f) { return v == null ? f : v; } private String text(String v, String f) { return v == null ? f : v; }
    private String format(double v) { return v == Math.rint(v) ? Integer.toString((int) v) : String.format(Locale.US, "%.1f", v); }
}
