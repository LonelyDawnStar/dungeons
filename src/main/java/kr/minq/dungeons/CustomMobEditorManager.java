package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
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
    private static final List<String> ABILITIES = List.of("NONE", "DASH", "FIREBALL", "HEAL", "SUMMON");

    private final JavaPlugin plugin;
    private final NamespacedKey previewKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey aggressiveKey;
    private final NamespacedKey abilityKey;
    private final NamespacedKey sizeKey;
    private final NamespacedKey yawKey;
    private final NamespacedKey cloneToolKey;
    private final NamespacedKey cloneTypeKey;
    private final NamespacedKey cloneNameKey;
    private final NamespacedKey cloneHealthKey;
    private final NamespacedKey cloneDamageKey;
    private final NamespacedKey cloneAggressiveKey;
    private final NamespacedKey cloneAbilityKey;
    private final NamespacedKey cloneSizeKey;
    private final NamespacedKey cloneYawKey;

    private final Map<UUID, UUID> editingTargets = new HashMap<>();
    private final Map<UUID, UUID> awaitingName = new HashMap<>();

    public CustomMobEditorManager(JavaPlugin plugin) {
        this.plugin = plugin;
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        healthKey = new NamespacedKey(plugin, "custom_mob_health");
        damageKey = new NamespacedKey(plugin, "custom_mob_damage");
        aggressiveKey = new NamespacedKey(plugin, "custom_mob_aggressive");
        abilityKey = new NamespacedKey(plugin, "custom_mob_ability");
        sizeKey = new NamespacedKey(plugin, "custom_mob_size");
        yawKey = new NamespacedKey(plugin, "custom_mob_yaw");
        cloneToolKey = new NamespacedKey(plugin, "custom_mob_clone_tool");
        cloneTypeKey = new NamespacedKey(plugin, "clone_type");
        cloneNameKey = new NamespacedKey(plugin, "clone_name");
        cloneHealthKey = new NamespacedKey(plugin, "clone_health");
        cloneDamageKey = new NamespacedKey(plugin, "clone_damage");
        cloneAggressiveKey = new NamespacedKey(plugin, "clone_aggressive");
        cloneAbilityKey = new NamespacedKey(plugin, "clone_ability");
        cloneSizeKey = new NamespacedKey(plugin, "clone_size");
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

        Material held = player.getInventory().getItemInMainHand().getType();
        if (held == Material.STICK) {
            event.setCancelled(true);
            openEditor(player, entity);
        } else if (held == Material.BLAZE_ROD) {
            event.setCancelled(true);
            player.getInventory().addItem(createCloneTool(entity));
            player.sendMessage("§6§l[Dungeons] §a이 몬스터의 배치 도구를 지급했습니다.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCloneToolUse(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getItem() == null) return;
        if (!isCloneTool(event.getItem())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("dungeons.admin")) return;
        if (!player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) {
            player.sendMessage("§c커스텀 몹 배치 도구는 템플릿 월드에서만 사용할 수 있습니다.");
            return;
        }

        event.setCancelled(true);
        ItemStack tool = event.getItem();
        PersistentDataContainer data = tool.getItemMeta().getPersistentDataContainer();
        String typeName = data.get(cloneTypeKey, PersistentDataType.STRING);
        if (typeName == null) return;

        EntityType type;
        try {
            type = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException exception) {
            player.sendMessage("§c저장된 몬스터 종류가 올바르지 않습니다.");
            return;
        }

        Float yaw = data.get(cloneYawKey, PersistentDataType.FLOAT);
        Location spawnLocation = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        spawnLocation.setYaw(yaw == null ? player.getLocation().getYaw() : yaw);
        Entity spawned = player.getWorld().spawnEntity(spawnLocation, type);
        if (!(spawned instanceof LivingEntity living)) {
            spawned.remove();
            return;
        }
        makePreview(living, tool);
        player.sendMessage("§6§l[Dungeons] §a" + displayName(living) + "§a을(를) 배치했습니다.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEditorClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID entityId = editingTargets.get(player.getUniqueId());
        Entity raw = entityId == null ? null : Bukkit.getEntity(entityId);
        if (!(raw instanceof LivingEntity entity) || !entity.isValid()) {
            player.closeInventory();
            player.sendMessage("§c편집 중인 몬스터를 찾을 수 없습니다.");
            return;
        }

        int slot = event.getRawSlot();
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        double amount = shift ? 10.0 : 1.0;

        switch (slot) {
            case 10 -> {
                awaitingName.put(player.getUniqueId(), entity.getUniqueId());
                player.closeInventory();
                player.sendMessage("§e채팅에 새 이름을 입력하세요. §7취소하려면 §fcancel§7을 입력하세요.");
            }
            case 12 -> {
                double next = getHealth(entity) + (right ? -amount : amount);
                setHealth(entity, Math.max(1.0, Math.min(2048.0, next)));
                openEditor(player, entity);
            }
            case 14 -> {
                double next = getDamage(entity) + (right ? -amount : amount);
                setDamage(entity, Math.max(0.0, Math.min(1024.0, next)));
                openEditor(player, entity);
            }
            case 16 -> {
                setAggressive(entity, !isAggressive(entity));
                openEditor(player, entity);
            }
            case 18 -> {
                float step = shift ? 90.0f : 15.0f;
                rotate(entity, right ? step : -step);
                openEditor(player, entity);
            }
            case 20 -> {
                cycleSize(entity, right ? -1 : 1);
                openEditor(player, entity);
            }
            case 22 -> {
                cycleAbility(entity, right ? -1 : 1);
                openEditor(player, entity);
            }
            case 24 -> {
                player.getInventory().addItem(createCloneTool(entity));
                player.sendMessage("§a배치 도구를 지급했습니다.");
            }
            case 26 -> {
                setYaw(entity, player.getLocation().getYaw());
                openEditor(player, entity);
            }
            case 31 -> {
                editingTargets.remove(player.getUniqueId());
                entity.remove();
                player.closeInventory();
                player.sendMessage("§c프리뷰 몬스터를 삭제했습니다.");
            }
            default -> {
            }
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
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage("§7이름 변경을 취소했습니다.");
            } else if (input.isBlank() || input.length() > 40) {
                player.sendMessage("§c이름은 1~40자로 입력하세요.");
            } else {
                entity.setCustomName("§c" + input);
                entity.setCustomNameVisible(true);
                player.sendMessage("§a몬스터 이름을 §f" + input + "§a으로 변경했습니다.");
            }
            openEditor(player, entity);
        });
    }

    private void makePreview(LivingEntity entity, ItemStack cloneSource) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        data.set(previewKey, PersistentDataType.BYTE, (byte) 1);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(true);
        entity.setCanPickupItems(false);
        entity.setRemoveWhenFarAway(false);

        if (cloneSource == null || !cloneSource.hasItemMeta()) {
            setHealth(entity, Math.max(1.0, currentMaxHealth(entity)));
            setDamage(entity, currentAttackDamage(entity));
            setAggressive(entity, false);
            setAbility(entity, "NONE");
            data.set(sizeKey, PersistentDataType.INTEGER, currentSize(entity));
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
        Integer size = source.get(cloneSizeKey, PersistentDataType.INTEGER);
        Float yaw = source.get(cloneYawKey, PersistentDataType.FLOAT);

        entity.setCustomName(name == null ? "§c" + prettyEntityName(entity.getType()) : name);
        entity.setCustomNameVisible(true);
        setHealth(entity, health == null ? DEFAULT_HEALTH : health);
        setDamage(entity, damage == null ? DEFAULT_DAMAGE : damage);
        setAggressive(entity, aggressive != null && aggressive == (byte) 1);
        setAbility(entity, ability == null ? "NONE" : ability);
        applySize(entity, size == null ? 0 : size);
        setYaw(entity, yaw == null ? entity.getLocation().getYaw() : yaw);
    }

    private void openEditor(Player player, LivingEntity entity) {
        editingTargets.put(player.getUniqueId(), entity.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 36, GUI_TITLE);
        inventory.setItem(10, menuItem(Material.NAME_TAG, "§e이름", List.of(
                "§7현재: §f" + displayName(entity),
                "§a클릭 후 채팅으로 입력"
        )));
        inventory.setItem(12, menuItem(Material.RED_DYE, "§c체력: §f" + format(getHealth(entity)), List.of(
                "§a좌클릭 §7+1", "§c우클릭 §7-1", "§eShift 클릭 §7±10"
        )));
        inventory.setItem(14, menuItem(Material.IRON_SWORD, "§6공격력: §f" + format(getDamage(entity)), List.of(
                "§a좌클릭 §7+1", "§c우클릭 §7-1", "§eShift 클릭 §7±10"
        )));
        inventory.setItem(16, menuItem(isAggressive(entity) ? Material.LIME_DYE : Material.GRAY_DYE,
                "§e공격 여부: " + (isAggressive(entity) ? "§aON" : "§cOFF"),
                List.of("§7실제 던전 생성 시 공격 여부", "§a클릭하여 전환")));
        inventory.setItem(18, menuItem(Material.COMPASS, "§b바라보는 방향: §f" + directionDescription(getYaw(entity)), List.of(
                "§a좌클릭 §7왼쪽 15°", "§c우클릭 §7오른쪽 15°",
                "§eShift+좌클릭 §7왼쪽 90°", "§eShift+우클릭 §7오른쪽 90°"
        )));
        inventory.setItem(20, menuItem(Material.SLIME_BALL, "§a크기: §f" + sizeDescription(entity), List.of(
                "§7슬라임: 1~8 / 팬텀: 0~64", "§7좀비·동물: 성체/아기", "§7갑옷 거치대: 일반/소형",
                "§a좌클릭 §7크게/다음", "§c우클릭 §7작게/이전"
        )));
        inventory.setItem(22, menuItem(Material.BLAZE_POWDER, "§d특수 능력: §f" + getAbility(entity), List.of(
                "§a좌클릭 §7다음 능력", "§c우클릭 §7이전 능력"
        )));
        inventory.setItem(24, menuItem(Material.BLAZE_ROD, "§6배치 도구 받기", List.of(
                "§7현재 설정과 방향이 담긴 배치 도구 지급"
        )));
        inventory.setItem(26, menuItem(Material.ENDER_EYE, "§3내가 보는 방향으로 맞추기", List.of(
                "§7현재 플레이어가 보는 수평 방향으로 몹을 회전"
        )));
        inventory.setItem(31, menuItem(Material.BARRIER, "§c프리뷰 몬스터 삭제", List.of("§7클릭하면 즉시 삭제됩니다.")));
        player.openInventory(inventory);
    }

    private ItemStack createCloneTool(LivingEntity entity) {
        ItemStack tool = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName("§6§l[" + displayName(entity) + "§6§l] 배치 도구");
        meta.setLore(List.of(
                "§7블록을 우클릭하면 같은 프리뷰 몬스터를 배치합니다.",
                "§8종류: " + entity.getType().name(),
                "§8체력: " + format(getHealth(entity)) + " / 공격력: " + format(getDamage(entity)),
                "§8방향: " + directionDescription(getYaw(entity))
        ));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(cloneToolKey, PersistentDataType.BYTE, (byte) 1);
        data.set(cloneTypeKey, PersistentDataType.STRING, entity.getType().name());
        data.set(cloneNameKey, PersistentDataType.STRING, entity.getCustomName() == null ? displayName(entity) : entity.getCustomName());
        data.set(cloneHealthKey, PersistentDataType.DOUBLE, getHealth(entity));
        data.set(cloneDamageKey, PersistentDataType.DOUBLE, getDamage(entity));
        data.set(cloneAggressiveKey, PersistentDataType.BYTE, isAggressive(entity) ? (byte) 1 : (byte) 0);
        data.set(cloneAbilityKey, PersistentDataType.STRING, getAbility(entity));
        data.set(cloneSizeKey, PersistentDataType.INTEGER, currentSize(entity));
        data.set(cloneYawKey, PersistentDataType.FLOAT, getYaw(entity));
        tool.setItemMeta(meta);
        return tool;
    }

    private ItemStack menuItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isPreview(LivingEntity entity) {
        Byte value = entity.getPersistentDataContainer().get(previewKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private boolean isCloneTool(ItemStack item) {
        if (item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(cloneToolKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private double getHealth(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(healthKey, PersistentDataType.DOUBLE, DEFAULT_HEALTH);
    }

    private void setHealth(LivingEntity entity, double value) {
        entity.getPersistentDataContainer().set(healthKey, PersistentDataType.DOUBLE, value);
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute != null) attribute.setBaseValue(value);
        entity.setHealth(Math.min(value, currentMaxHealth(entity)));
    }

    private double getDamage(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(damageKey, PersistentDataType.DOUBLE, DEFAULT_DAMAGE);
    }

    private void setDamage(LivingEntity entity, double value) {
        entity.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, value);
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (attribute != null) attribute.setBaseValue(value);
    }

    private boolean isAggressive(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(aggressiveKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private void setAggressive(LivingEntity entity, boolean value) {
        entity.getPersistentDataContainer().set(aggressiveKey, PersistentDataType.BYTE, value ? (byte) 1 : (byte) 0);
    }

    private String getAbility(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(abilityKey, PersistentDataType.STRING, "NONE");
    }

    private void setAbility(LivingEntity entity, String value) {
        entity.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, value);
    }

    private void cycleAbility(LivingEntity entity, int direction) {
        int index = ABILITIES.indexOf(getAbility(entity));
        if (index < 0) index = 0;
        int next = Math.floorMod(index + direction, ABILITIES.size());
        setAbility(entity, ABILITIES.get(next));
    }

    private void rotate(LivingEntity entity, float amount) {
        setYaw(entity, getYaw(entity) + amount);
    }

    private void setYaw(LivingEntity entity, float yaw) {
        float normalized = normalizeYaw(yaw);
        Location location = entity.getLocation();
        location.setYaw(normalized);
        entity.teleport(location);
        entity.getPersistentDataContainer().set(yawKey, PersistentDataType.FLOAT, normalized);
    }

    private float getYaw(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(
                yawKey, PersistentDataType.FLOAT, normalizeYaw(entity.getLocation().getYaw()));
    }

    private float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized < 0.0f) normalized += 360.0f;
        return normalized;
    }

    private String directionDescription(float yaw) {
        float normalized = normalizeYaw(yaw);
        String cardinal;
        if (normalized >= 337.5f || normalized < 22.5f) cardinal = "남쪽";
        else if (normalized < 67.5f) cardinal = "남서쪽";
        else if (normalized < 112.5f) cardinal = "서쪽";
        else if (normalized < 157.5f) cardinal = "북서쪽";
        else if (normalized < 202.5f) cardinal = "북쪽";
        else if (normalized < 247.5f) cardinal = "북동쪽";
        else if (normalized < 292.5f) cardinal = "동쪽";
        else cardinal = "남동쪽";
        return cardinal + " (" + Math.round(normalized) + "°)";
    }

    private void cycleSize(LivingEntity entity, int direction) {
        if (entity instanceof Slime slime) {
            int next = Math.max(1, Math.min(8, slime.getSize() + direction));
            applySize(entity, next);
        } else if (entity instanceof Phantom phantom) {
            int next = Math.max(0, Math.min(64, phantom.getSize() + direction));
            applySize(entity, next);
        } else if (entity instanceof Zombie zombie) {
            applySize(entity, zombie.isBaby() ? 0 : 1);
        } else if (entity instanceof Ageable ageable) {
            applySize(entity, ageable.isAdult() ? 1 : 0);
        } else if (entity instanceof ArmorStand armorStand) {
            applySize(entity, armorStand.isSmall() ? 0 : 1);
        }
    }

    private void applySize(LivingEntity entity, int value) {
        if (entity instanceof Slime slime) {
            slime.setSize(Math.max(1, Math.min(8, value == 0 ? 1 : value)));
            entity.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, slime.getSize());
        } else if (entity instanceof Phantom phantom) {
            phantom.setSize(Math.max(0, Math.min(64, value)));
            entity.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, phantom.getSize());
        } else if (entity instanceof Zombie zombie) {
            zombie.setBaby(value == 1);
            entity.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, zombie.isBaby() ? 1 : 0);
        } else if (entity instanceof Ageable ageable) {
            if (value == 1) ageable.setBaby();
            else ageable.setAdult();
            ageable.setAgeLock(true);
            entity.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, value == 1 ? 1 : 0);
        } else if (entity instanceof ArmorStand armorStand) {
            armorStand.setSmall(value == 1);
            entity.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, armorStand.isSmall() ? 1 : 0);
        } else {
            entity.getPersistentDataContainer().set(sizeKey, PersistentDataType.INTEGER, 0);
        }
    }

    private int currentSize(LivingEntity entity) {
        if (entity instanceof Slime slime) return slime.getSize();
        if (entity instanceof Phantom phantom) return phantom.getSize();
        if (entity instanceof Zombie zombie) return zombie.isBaby() ? 1 : 0;
        if (entity instanceof Ageable ageable) return ageable.isAdult() ? 0 : 1;
        if (entity instanceof ArmorStand armorStand) return armorStand.isSmall() ? 1 : 0;
        return entity.getPersistentDataContainer().getOrDefault(sizeKey, PersistentDataType.INTEGER, 0);
    }

    private String sizeDescription(LivingEntity entity) {
        if (entity instanceof Slime slime) return "단계 " + slime.getSize() + "/8";
        if (entity instanceof Phantom phantom) return "단계 " + phantom.getSize() + "/64";
        if (entity instanceof Zombie zombie) return zombie.isBaby() ? "아기" : "성체";
        if (entity instanceof Ageable ageable) return ageable.isAdult() ? "성체" : "아기";
        if (entity instanceof ArmorStand armorStand) return armorStand.isSmall() ? "소형" : "일반";
        return "기본 크기 (변경 불가)";
    }

    private double currentMaxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute == null ? DEFAULT_HEALTH : attribute.getBaseValue();
    }

    private double currentAttackDamage(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        return attribute == null ? DEFAULT_DAMAGE : attribute.getBaseValue();
    }

    private String displayName(LivingEntity entity) {
        String custom = entity.getCustomName();
        return custom == null ? prettyEntityName(entity.getType()) : ChatColor.stripColor(custom);
    }

    private String prettyEntityName(EntityType type) {
        String[] parts = type.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.US, "%.1f", value);
    }
}
