package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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

public final class MobAiEditorManager implements Listener {
    private static final String GUI_TITLE = "§8몬스터 AI 편집";
    private static final List<String> TYPES = List.of("MELEE", "RANGED", "CHARGE", "FLEE", "TURRET");
    private static final List<String> TARGETS = List.of("NEAREST", "LOWEST_HEALTH", "FARTHEST", "RANDOM");

    private final NamespacedKey previewKey;
    private final NamespacedKey aiTypeKey;
    private final NamespacedKey detectionRangeKey;
    private final NamespacedKey moveSpeedKey;
    private final NamespacedKey attackIntervalKey;
    private final NamespacedKey preferredDistanceKey;
    private final NamespacedKey targetPriorityKey;
    private final Map<UUID, UUID> editing = new HashMap<>();

    public MobAiEditorManager(JavaPlugin plugin) {
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        aiTypeKey = new NamespacedKey(plugin, "custom_mob_ai_type");
        detectionRangeKey = new NamespacedKey(plugin, "custom_mob_detection_range");
        moveSpeedKey = new NamespacedKey(plugin, "custom_mob_move_speed");
        attackIntervalKey = new NamespacedKey(plugin, "custom_mob_attack_interval");
        preferredDistanceKey = new NamespacedKey(plugin, "custom_mob_preferred_distance");
        targetPriorityKey = new NamespacedKey(plugin, "custom_mob_target_priority");
    }

    public String openForLookedAtMob(Player player) {
        if (!player.hasPermission("dungeons.admin")) return "§c관리자 권한이 필요합니다.";
        if (!player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) return "§c템플릿 월드 안에서만 AI를 편집할 수 있습니다.";
        Entity target = player.getTargetEntity(8);
        if (!(target instanceof LivingEntity living) || !isPreview(living)) {
            return "§c8블록 안의 프리뷰 몹을 바라보고 다시 입력하세요.";
        }
        initializeDefaults(living);
        open(player, living);
        return "§aAI 편집기를 열었습니다.";
    }

    public void initializeDefaults(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (!data.has(aiTypeKey, PersistentDataType.STRING)) data.set(aiTypeKey, PersistentDataType.STRING, "MELEE");
        if (!data.has(detectionRangeKey, PersistentDataType.DOUBLE)) data.set(detectionRangeKey, PersistentDataType.DOUBLE, 20.0);
        if (!data.has(moveSpeedKey, PersistentDataType.DOUBLE)) data.set(moveSpeedKey, PersistentDataType.DOUBLE, 0.28);
        if (!data.has(attackIntervalKey, PersistentDataType.INTEGER)) data.set(attackIntervalKey, PersistentDataType.INTEGER, 20);
        if (!data.has(preferredDistanceKey, PersistentDataType.DOUBLE)) data.set(preferredDistanceKey, PersistentDataType.DOUBLE, 8.0);
        if (!data.has(targetPriorityKey, PersistentDataType.STRING)) data.set(targetPriorityKey, PersistentDataType.STRING, "NEAREST");
    }

    private void open(Player player, LivingEntity entity) {
        editing.put(player.getUniqueId(), entity.getUniqueId());
        initializeDefaults(entity);
        Inventory inventory = Bukkit.createInventory(null, 27, GUI_TITLE);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String type = data.getOrDefault(aiTypeKey, PersistentDataType.STRING, "MELEE");
        double detection = data.getOrDefault(detectionRangeKey, PersistentDataType.DOUBLE, 20.0);
        double speed = data.getOrDefault(moveSpeedKey, PersistentDataType.DOUBLE, 0.28);
        int interval = data.getOrDefault(attackIntervalKey, PersistentDataType.INTEGER, 20);
        double preferred = data.getOrDefault(preferredDistanceKey, PersistentDataType.DOUBLE, 8.0);
        String target = data.getOrDefault(targetPriorityKey, PersistentDataType.STRING, "NEAREST");

        inventory.setItem(10, item(typeMaterial(type), "§bAI 유형: §f" + typeKorean(type), "§7좌/우클릭으로 변경", typeDescription(type)));
        inventory.setItem(11, item(Material.SPYGLASS, "§e감지 거리: §f" + format(detection) + "블록", "§7클릭 ±1", "§7Shift 클릭 ±5"));
        inventory.setItem(12, item(Material.SUGAR, "§a이동 속도: §f" + format(speed), "§7클릭 ±0.02", "§7Shift 클릭 ±0.10"));
        inventory.setItem(13, item(Material.CLOCK, "§6공격 주기: §f" + interval + "틱", "§7클릭 ±2틱", "§7Shift 클릭 ±10틱"));
        inventory.setItem(14, item(Material.TARGET, "§d선호 거리: §f" + format(preferred) + "블록", "§7원거리·도주 AI가 유지할 거리", "§7클릭 ±0.5, Shift ±2"));
        inventory.setItem(15, item(Material.PLAYER_HEAD, "§c목표 우선순위: §f" + targetKorean(target), "§7좌/우클릭으로 변경"));
        inventory.setItem(22, item(Material.LIME_DYE, "§a설정 완료", "§7메아리 조각 테스트에 즉시 적용됩니다."));
        player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        LivingEntity entity = editingEntity(player);
        if (entity == null) { player.closeInventory(); return; }
        PersistentDataContainer data = entity.getPersistentDataContainer();
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        int direction = right ? -1 : 1;
        switch (event.getRawSlot()) {
            case 10 -> data.set(aiTypeKey, PersistentDataType.STRING,
                    cycle(TYPES, data.getOrDefault(aiTypeKey, PersistentDataType.STRING, "MELEE"), direction));
            case 11 -> setDouble(data, detectionRangeKey,
                    data.getOrDefault(detectionRangeKey, PersistentDataType.DOUBLE, 20.0) + direction * (shift ? 5.0 : 1.0), 2.0, 128.0);
            case 12 -> setDouble(data, moveSpeedKey,
                    data.getOrDefault(moveSpeedKey, PersistentDataType.DOUBLE, 0.28) + direction * (shift ? 0.10 : 0.02), 0.0, 1.5);
            case 13 -> data.set(attackIntervalKey, PersistentDataType.INTEGER, Math.max(2,
                    Math.min(400, data.getOrDefault(attackIntervalKey, PersistentDataType.INTEGER, 20) + direction * (shift ? 10 : 2))));
            case 14 -> setDouble(data, preferredDistanceKey,
                    data.getOrDefault(preferredDistanceKey, PersistentDataType.DOUBLE, 8.0) + direction * (shift ? 2.0 : 0.5), 1.0, 64.0);
            case 15 -> data.set(targetPriorityKey, PersistentDataType.STRING,
                    cycle(TARGETS, data.getOrDefault(targetPriorityKey, PersistentDataType.STRING, "NEAREST"), direction));
            case 22 -> { player.closeInventory(); player.sendMessage("§6§l[Dungeons] §aAI 설정을 저장했습니다."); return; }
            default -> { return; }
        }
        open(player, entity);
    }

    private LivingEntity editingEntity(Player player) {
        UUID id = editing.get(player.getUniqueId());
        Entity entity = id == null ? null : Bukkit.getEntity(id);
        return entity instanceof LivingEntity living && living.isValid() && isPreview(living) ? living : null;
    }

    private boolean isPreview(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private void setDouble(PersistentDataContainer data, NamespacedKey key, double value, double min, double max) {
        data.set(key, PersistentDataType.DOUBLE, Math.max(min, Math.min(max, value)));
    }

    private String cycle(List<String> values, String current, int direction) {
        int index = values.indexOf(current);
        if (index < 0) index = 0;
        return values.get(Math.floorMod(index + direction, values.size()));
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private Material typeMaterial(String type) {
        return switch (type) {
            case "RANGED" -> Material.BOW;
            case "CHARGE" -> Material.GOAT_HORN;
            case "FLEE" -> Material.RABBIT_FOOT;
            case "TURRET" -> Material.DISPENSER;
            default -> Material.IRON_SWORD;
        };
    }

    private String typeKorean(String type) {
        return switch (type) {
            case "RANGED" -> "원거리형";
            case "CHARGE" -> "돌격형";
            case "FLEE" -> "도주형";
            case "TURRET" -> "고정 포대형";
            default -> "근접형";
        };
    }

    private String typeDescription(String type) {
        return switch (type) {
            case "RANGED" -> "§7선호 거리를 유지하며 원거리 피해를 줍니다.";
            case "CHARGE" -> "§7거리를 벌린 뒤 빠르게 돌진합니다.";
            case "FLEE" -> "§7플레이어에게서 도망치며 거리를 유지합니다.";
            case "TURRET" -> "§7이동하지 않고 사거리 안의 적을 공격합니다.";
            default -> "§7대상에게 접근해 근접 공격합니다.";
        };
    }

    private String targetKorean(String target) {
        return switch (target) {
            case "LOWEST_HEALTH" -> "체력이 가장 낮은 대상";
            case "FARTHEST" -> "가장 먼 대상";
            case "RANDOM" -> "무작위 대상";
            default -> "가장 가까운 대상";
        };
    }

    private String format(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(Locale.US, "%.2f", value);
    }
}
