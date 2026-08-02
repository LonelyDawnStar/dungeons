package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class EquipmentDropEditorManager implements Listener {
    private static final String EQUIPMENT_TITLE = "§8커스텀 몹 장비 편집";
    private static final String DROP_TITLE = "§8커스텀 몹 드롭 편집";
    private static final int[] DROP_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final NamespacedKey equipmentDataKey;
    private final NamespacedKey dropDataKey;
    private final NamespacedKey dropChanceKey;
    private final Map<UUID, UUID> equipmentTargets = new HashMap<>();
    private final Map<UUID, UUID> dropTargets = new HashMap<>();

    public EquipmentDropEditorManager(JavaPlugin plugin) {
        equipmentDataKey = new NamespacedKey(plugin, "custom_mob_equipment_data");
        dropDataKey = new NamespacedKey(plugin, "custom_mob_drop_data");
        dropChanceKey = new NamespacedKey(plugin, "custom_mob_drop_chance");
    }

    public void openEquipmentEditor(Player player, LivingEntity entity) {
        equipmentTargets.put(player.getUniqueId(), entity.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 45, EQUIPMENT_TITLE);
        EntityEquipment equipment = entity.getEquipment();
        inventory.setItem(10, displayEquipment(equipment == null ? null : equipment.getHelmet(), Material.IRON_HELMET, "§e머리"));
        inventory.setItem(12, displayEquipment(equipment == null ? null : equipment.getChestplate(), Material.IRON_CHESTPLATE, "§e가슴"));
        inventory.setItem(14, displayEquipment(equipment == null ? null : equipment.getLeggings(), Material.IRON_LEGGINGS, "§e다리"));
        inventory.setItem(16, displayEquipment(equipment == null ? null : equipment.getBoots(), Material.IRON_BOOTS, "§e신발"));
        inventory.setItem(28, displayEquipment(equipment == null ? null : equipment.getItemInMainHand(), Material.IRON_SWORD, "§6주무기"));
        inventory.setItem(30, displayEquipment(equipment == null ? null : equipment.getItemInOffHand(), Material.SHIELD, "§6보조무기"));
        inventory.setItem(40, button(Material.LIME_DYE, "§a저장하고 닫기", List.of(
                "§7아래 인벤토리에서 아이템을 집은 뒤",
                "§7원하는 장비 슬롯을 클릭하세요.",
                "§c빈 커서로 우클릭하면 장비를 제거합니다.")));
        player.openInventory(inventory);
    }

    public void openDropEditor(Player player, LivingEntity entity) {
        dropTargets.put(player.getUniqueId(), entity.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 45, DROP_TITLE);
        List<DropEntry> entries = readDrops(entity.getPersistentDataContainer());
        for (int i = 0; i < Math.min(entries.size(), DROP_SLOTS.length); i++) {
            inventory.setItem(DROP_SLOTS[i], decorateDrop(entries.get(i).item(), entries.get(i).chance()));
        }
        inventory.setItem(40, button(Material.LIME_DYE, "§a저장하고 닫기", List.of(
                "§7아래 인벤토리에서 아이템을 집은 뒤",
                "§7빈 드롭 슬롯을 클릭해 등록하세요.")));
        inventory.setItem(42, button(Material.BARRIER, "§c전체 삭제", List.of("§7등록된 드롭 아이템을 모두 제거합니다.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!EQUIPMENT_TITLE.equals(title) && !DROP_TITLE.equals(title)) return;

        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0) return;

        // 아래쪽 플레이어 인벤토리는 정상적으로 조작할 수 있게 둔다.
        if (event.getRawSlot() >= topSize) return;

        if (EQUIPMENT_TITLE.equals(title)) {
            handleEquipmentClick(event, player);
        } else {
            handleDropClick(event, player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (!EQUIPMENT_TITLE.equals(title) && !DROP_TITLE.equals(title)) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private void handleEquipmentClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        LivingEntity entity = target(equipmentTargets.get(player.getUniqueId()));
        if (entity == null) {
            player.closeInventory();
            player.sendMessage("§c편집할 몬스터를 찾을 수 없습니다.");
            return;
        }

        Inventory top = event.getView().getTopInventory();
        int slot = event.getRawSlot();
        if (slot == 40) {
            saveEquipmentFromGui(entity, top);
            player.closeInventory();
            player.sendMessage("§a몬스터 장비 설정을 저장했습니다.");
            return;
        }
        if (!isEquipmentSlot(slot)) return;

        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            top.setItem(slot, cursor.clone());
            saveEquipmentFromGui(entity, top);
            player.sendMessage("§a장비를 설정했습니다.");
            return;
        }

        if (event.isRightClick()) {
            top.setItem(slot, emptyEquipmentPlaceholder(slot));
            saveEquipmentFromGui(entity, top);
            player.sendMessage("§c해당 장비를 제거했습니다.");
        }
    }

    private void handleDropClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        LivingEntity entity = target(dropTargets.get(player.getUniqueId()));
        if (entity == null) {
            player.closeInventory();
            player.sendMessage("§c편집할 몬스터를 찾을 수 없습니다.");
            return;
        }

        Inventory top = event.getView().getTopInventory();
        int slot = event.getRawSlot();
        if (slot == 40) {
            saveDropsFromGui(entity, top);
            player.closeInventory();
            player.sendMessage("§a드롭 아이템 설정을 저장했습니다.");
            return;
        }
        if (slot == 42) {
            for (int dropSlot : DROP_SLOTS) top.setItem(dropSlot, null);
            saveDropsFromGui(entity, top);
            player.sendMessage("§c드롭 아이템을 모두 삭제했습니다.");
            return;
        }
        if (!isDropSlot(slot)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = top.getItem(slot);
        if (cursor != null && !cursor.getType().isAir()) {
            top.setItem(slot, decorateDrop(cursor.clone(), 100.0));
            saveDropsFromGui(entity, top);
            player.sendMessage("§a드롭 아이템을 등록했습니다. §7기본 확률: 100%");
            return;
        }

        if (current == null || current.getType().isAir()) return;

        double chance = getChance(current);
        double step = event.isShiftClick() ? 1.0 : 10.0;
        chance += event.isRightClick() ? -step : step;
        chance = Math.max(0.0, Math.min(100.0, chance));
        top.setItem(slot, decorateDrop(stripDropDecoration(current), chance));
        saveDropsFromGui(entity, top);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (EQUIPMENT_TITLE.equals(event.getView().getTitle())) {
            LivingEntity entity = target(equipmentTargets.remove(player.getUniqueId()));
            if (entity != null) saveEquipmentFromGui(entity, event.getView().getTopInventory());
        } else if (DROP_TITLE.equals(event.getView().getTitle())) {
            LivingEntity entity = target(dropTargets.remove(player.getUniqueId()));
            if (entity != null) saveDropsFromGui(entity, event.getView().getTopInventory());
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        List<DropEntry> entries = readDrops(event.getEntity().getPersistentDataContainer());
        if (entries.isEmpty()) return;
        event.getDrops().clear();
        for (DropEntry entry : entries) {
            if (ThreadLocalRandom.current().nextDouble(100.0) < entry.chance()) {
                event.getDrops().add(entry.item().clone());
            }
        }
    }

    public void copyToTool(LivingEntity entity, PersistentDataContainer toolData) {
        byte[] equipment = entity.getPersistentDataContainer().get(equipmentDataKey, PersistentDataType.BYTE_ARRAY);
        byte[] drops = entity.getPersistentDataContainer().get(dropDataKey, PersistentDataType.BYTE_ARRAY);
        if (equipment != null) toolData.set(equipmentDataKey, PersistentDataType.BYTE_ARRAY, equipment.clone());
        if (drops != null) toolData.set(dropDataKey, PersistentDataType.BYTE_ARRAY, drops.clone());
    }

    public void applyFromTool(LivingEntity entity, PersistentDataContainer toolData) {
        byte[] equipment = toolData.get(equipmentDataKey, PersistentDataType.BYTE_ARRAY);
        byte[] drops = toolData.get(dropDataKey, PersistentDataType.BYTE_ARRAY);
        if (equipment != null) {
            entity.getPersistentDataContainer().set(equipmentDataKey, PersistentDataType.BYTE_ARRAY, equipment.clone());
            applyEquipment(entity, readItems(equipment, 6));
        }
        if (drops != null) {
            entity.getPersistentDataContainer().set(dropDataKey, PersistentDataType.BYTE_ARRAY, drops.clone());
        }
    }

    private void saveEquipmentFromGui(LivingEntity entity, Inventory inventory) {
        ItemStack[] items = {
                cleanDisplay(inventory.getItem(10)), cleanDisplay(inventory.getItem(12)),
                cleanDisplay(inventory.getItem(14)), cleanDisplay(inventory.getItem(16)),
                cleanDisplay(inventory.getItem(28)), cleanDisplay(inventory.getItem(30))
        };
        entity.getPersistentDataContainer().set(equipmentDataKey, PersistentDataType.BYTE_ARRAY, writeItems(items));
        applyEquipment(entity, items);
    }

    private void applyEquipment(LivingEntity entity, ItemStack[] items) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null || items.length < 6) return;
        equipment.setHelmet(cloneOrNull(items[0]));
        equipment.setChestplate(cloneOrNull(items[1]));
        equipment.setLeggings(cloneOrNull(items[2]));
        equipment.setBoots(cloneOrNull(items[3]));
        equipment.setItemInMainHand(cloneOrNull(items[4]));
        equipment.setItemInOffHand(cloneOrNull(items[5]));
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.getType().isAir() ? null : item.clone();
    }

    private void saveDropsFromGui(LivingEntity entity, Inventory inventory) {
        List<DropEntry> entries = new ArrayList<>();
        for (int slot : DROP_SLOTS) {
            ItemStack shown = inventory.getItem(slot);
            if (shown == null || shown.getType().isAir()) continue;
            double chance = getChance(shown);
            entries.add(new DropEntry(stripDropDecoration(shown), chance));
        }
        entity.getPersistentDataContainer().set(dropDataKey, PersistentDataType.BYTE_ARRAY, writeDrops(entries));
    }

    private ItemStack displayEquipment(ItemStack equipped, Material placeholder, String label) {
        if (equipped != null && !equipped.getType().isAir()) return equipped.clone();
        return button(placeholder, label + " §7(비어 있음)", List.of(
                "§7아래 인벤토리에서 아이템을 집은 뒤 클릭",
                "§c빈 커서로 우클릭하면 제거"));
    }

    private ItemStack emptyEquipmentPlaceholder(int slot) {
        return switch (slot) {
            case 10 -> displayEquipment(null, Material.IRON_HELMET, "§e머리");
            case 12 -> displayEquipment(null, Material.IRON_CHESTPLATE, "§e가슴");
            case 14 -> displayEquipment(null, Material.IRON_LEGGINGS, "§e다리");
            case 16 -> displayEquipment(null, Material.IRON_BOOTS, "§e신발");
            case 28 -> displayEquipment(null, Material.IRON_SWORD, "§6주무기");
            case 30 -> displayEquipment(null, Material.SHIELD, "§6보조무기");
            default -> null;
        };
    }

    private ItemStack cleanDisplay(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("(비어 있음)")) return null;
        return item.clone();
    }

    private ItemStack decorateDrop(ItemStack original, double chance) {
        ItemStack item = stripDropDecoration(original);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("§8[DungeonsDrop] §e드롭 확률: §f" + formatChance(chance) + "%");
        lore.add("§7좌클릭 +10% / 우클릭 -10%");
        lore.add("§7Shift 클릭 ±1%");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(dropChanceKey, PersistentDataType.DOUBLE, chance);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack stripDropDecoration(ItemStack shown) {
        ItemStack item = shown.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta.hasLore()) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(line -> line.startsWith("§8[DungeonsDrop]")
                    || line.equals("§7좌클릭 +10% / 우클릭 -10%")
                    || line.equals("§7Shift 클릭 ±1%"));
            meta.setLore(lore.isEmpty() ? null : lore);
        }
        meta.getPersistentDataContainer().remove(dropChanceKey);
        item.setItemMeta(meta);
        return item;
    }

    private double getChance(ItemStack item) {
        if (!item.hasItemMeta()) return 100.0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(
                dropChanceKey, PersistentDataType.DOUBLE, 100.0);
    }

    private List<DropEntry> readDrops(PersistentDataContainer container) {
        byte[] bytes = container.get(dropDataKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null || bytes.length == 0) return List.of();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = input.readInt();
            List<DropEntry> result = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                int length = input.readInt();
                if (length <= 0) continue;
                ItemStack item = ItemStack.deserializeBytes(input.readNBytes(length));
                double chance = input.readDouble();
                result.add(new DropEntry(item, chance));
            }
            return result;
        } catch (IOException | IllegalArgumentException exception) {
            return List.of();
        }
    }

    private byte[] writeDrops(List<DropEntry> entries) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(entries.size());
            for (DropEntry entry : entries) {
                byte[] serialized = entry.item().serializeAsBytes();
                output.writeInt(serialized.length);
                output.write(serialized);
                output.writeDouble(entry.chance());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    private byte[] writeItems(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(items.length);
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) {
                    output.writeInt(0);
                } else {
                    byte[] serialized = item.serializeAsBytes();
                    output.writeInt(serialized.length);
                    output.write(serialized);
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    private ItemStack[] readItems(byte[] bytes, int expected) {
        ItemStack[] result = new ItemStack[expected];
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int stored = input.readInt();
            for (int i = 0; i < stored; i++) {
                int length = input.readInt();
                byte[] serialized = length > 0 ? input.readNBytes(length) : new byte[0];
                if (i < expected && length > 0) result[i] = ItemStack.deserializeBytes(serialized);
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // 손상된 데이터는 빈 장비로 처리한다.
        }
        return result;
    }

    private LivingEntity target(UUID id) {
        if (id == null) return null;
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof LivingEntity living && living.isValid() ? living : null;
    }

    private boolean isEquipmentSlot(int slot) {
        return slot == 10 || slot == 12 || slot == 14 || slot == 16 || slot == 28 || slot == 30;
    }

    private boolean isDropSlot(int slot) {
        for (int value : DROP_SLOTS) if (value == slot) return true;
        return false;
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatChance(double value) {
        return value == Math.rint(value)
                ? Integer.toString((int) value)
                : String.format(java.util.Locale.US, "%.1f", value);
    }

    private record DropEntry(ItemStack item, double chance) { }
}
