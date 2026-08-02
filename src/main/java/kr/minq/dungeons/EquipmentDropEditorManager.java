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
    private static EquipmentDropEditorManager instance;

    private final NamespacedKey equipmentDataKey;
    private final NamespacedKey dropDataKey;
    private final NamespacedKey dropChanceKey;
    private final Map<UUID, UUID> equipmentTargets = new HashMap<>();
    private final Map<UUID, UUID> dropTargets = new HashMap<>();

    public EquipmentDropEditorManager(JavaPlugin plugin) {
        instance = this;
        equipmentDataKey = new NamespacedKey(plugin, "custom_mob_equipment_data");
        dropDataKey = new NamespacedKey(plugin, "custom_mob_drop_data");
        dropChanceKey = new NamespacedKey(plugin, "custom_mob_drop_chance");
    }

    public static EquipmentDropEditorManager getInstance() {
        return instance;
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
        inventory.setItem(40, button(Material.ARROW, "§a완료", List.of("§7클릭하면 설정을 저장하고 닫습니다.")));
        player.openInventory(inventory);
    }

    public void openDropEditor(Player player, LivingEntity entity) {
        dropTargets.put(player.getUniqueId(), entity.getUniqueId());
        Inventory inventory = Bukkit.createInventory(null, 45, DROP_TITLE);
        List<DropEntry> entries = readDrops(entity.getPersistentDataContainer());
        for (int i = 0; i < Math.min(entries.size(), DROP_SLOTS.length); i++) {
            inventory.setItem(DROP_SLOTS[i], decorateDrop(entries.get(i).item(), entries.get(i).chance()));
        }
        inventory.setItem(40, button(Material.ARROW, "§a완료", List.of("§7클릭하면 설정을 저장하고 닫습니다.")));
        inventory.setItem(42, button(Material.BARRIER, "§c전체 삭제", List.of("§7등록된 드롭 아이템을 모두 제거합니다.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (EQUIPMENT_TITLE.equals(title)) {
            handleEquipmentClick(event, player);
        } else if (DROP_TITLE.equals(title)) {
            handleDropClick(event, player);
        }
    }

    private void handleEquipmentClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        LivingEntity entity = target(equipmentTargets.get(player.getUniqueId()));
        if (entity == null) { player.closeInventory(); return; }
        if (event.getRawSlot() == 40) { saveEquipmentFromGui(entity, event.getInventory()); player.closeInventory(); return; }
        if (!isEquipmentSlot(event.getRawSlot())) return;
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            event.getInventory().setItem(event.getRawSlot(), cursor.clone());
        } else if (event.isRightClick()) {
            event.getInventory().setItem(event.getRawSlot(), null);
        }
        saveEquipmentFromGui(entity, event.getInventory());
    }

    private void handleDropClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        LivingEntity entity = target(dropTargets.get(player.getUniqueId()));
        if (entity == null) { player.closeInventory(); return; }
        if (event.getRawSlot() == 40) { saveDropsFromGui(entity, event.getInventory()); player.closeInventory(); return; }
        if (event.getRawSlot() == 42) {
            for (int slot : DROP_SLOTS) event.getInventory().setItem(slot, null);
            saveDropsFromGui(entity, event.getInventory());
            return;
        }
        if (!isDropSlot(event.getRawSlot())) return;
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getInventory().getItem(event.getRawSlot());
        if (cursor != null && !cursor.getType().isAir()) {
            event.getInventory().setItem(event.getRawSlot(), decorateDrop(cursor.clone(), 100.0));
        } else if (current != null && !current.getType().isAir()) {
            double chance = getChance(current);
            double step = event.isShiftClick() ? 1.0 : 10.0;
            chance += event.isRightClick() ? -step : step;
            if (chance < 0.0) chance = 0.0;
            if (chance > 100.0) chance = 100.0;
            event.getInventory().setItem(event.getRawSlot(), decorateDrop(stripDropDecoration(current), chance));
        }
        saveDropsFromGui(entity, event.getInventory());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (EQUIPMENT_TITLE.equals(event.getView().getTitle())) {
            LivingEntity entity = target(equipmentTargets.remove(player.getUniqueId()));
            if (entity != null) saveEquipmentFromGui(entity, event.getInventory());
        } else if (DROP_TITLE.equals(event.getView().getTitle())) {
            LivingEntity entity = target(dropTargets.remove(player.getUniqueId()));
            if (entity != null) saveDropsFromGui(entity, event.getInventory());
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
        if (equipment != null) toolData.set(equipmentDataKey, PersistentDataType.BYTE_ARRAY, equipment);
        if (drops != null) toolData.set(dropDataKey, PersistentDataType.BYTE_ARRAY, drops);
    }

    public void applyFromTool(LivingEntity entity, PersistentDataContainer toolData) {
        byte[] equipment = toolData.get(equipmentDataKey, PersistentDataType.BYTE_ARRAY);
        byte[] drops = toolData.get(dropDataKey, PersistentDataType.BYTE_ARRAY);
        if (equipment != null) {
            entity.getPersistentDataContainer().set(equipmentDataKey, PersistentDataType.BYTE_ARRAY, equipment);
            applyEquipment(entity, readItems(equipment, 6));
        }
        if (drops != null) entity.getPersistentDataContainer().set(dropDataKey, PersistentDataType.BYTE_ARRAY, drops);
    }

    private void saveEquipmentFromGui(LivingEntity entity, Inventory inventory) {
        ItemStack[] items = {
                cleanDisplay(inventory.getItem(10)), cleanDisplay(inventory.getItem(12)),
                cleanDisplay(inventory.getItem(14)), cleanDisplay(inventory.getItem(16)),
                cleanDisplay(inventory.getItem(28)), cleanDisplay(inventory.getItem(30))
        };
        byte[] bytes = writeItems(items);
        entity.getPersistentDataContainer().set(equipmentDataKey, PersistentDataType.BYTE_ARRAY, bytes);
        applyEquipment(entity, items);
    }

    private void applyEquipment(LivingEntity entity, ItemStack[] items) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null || items.length < 6) return;
        equipment.setHelmet(items[0]);
        equipment.setChestplate(items[1]);
        equipment.setLeggings(items[2]);
        equipment.setBoots(items[3]);
        equipment.setItemInMainHand(items[4]);
        equipment.setItemInOffHand(items[5]);
        equipment.setHelmetDropChance(0.0f);
        equipment.setChestplateDropChance(0.0f);
        equipment.setLeggingsDropChance(0.0f);
        equipment.setBootsDropChance(0.0f);
        equipment.setItemInMainHandDropChance(0.0f);
        equipment.setItemInOffHandDropChance(0.0f);
    }

    private void saveDropsFromGui(LivingEntity entity, Inventory inventory) {
        List<DropEntry> entries = new ArrayList<>();
        for (int slot : DROP_SLOTS) {
            ItemStack shown = inventory.getItem(slot);
            if (shown == null || shown.getType().isAir()) continue;
            entries.add(new DropEntry(stripDropDecoration(shown), getChance(shown)));
        }
        entity.getPersistentDataContainer().set(dropDataKey, PersistentDataType.BYTE_ARRAY, writeDrops(entries));
    }

    private ItemStack displayEquipment(ItemStack equipped, Material placeholder, String label) {
        if (equipped != null && !equipped.getType().isAir()) return equipped.clone();
        return button(placeholder, label + " §7(비어 있음)", List.of("§7커서의 아이템으로 클릭하여 설정", "§c빈 커서로 우클릭하여 제거"));
    }

    private ItemStack cleanDisplay(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("(비어 있음)")) return null;
        return item.clone();
    }

    private ItemStack decorateDrop(ItemStack original, double chance) {
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.startsWith("§8[DungeonsDrop]"));
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
            lore.removeIf(line -> line.startsWith("§8[DungeonsDrop]") || line.startsWith("§7좌클릭") || line.startsWith("§7Shift 클릭"));
            meta.setLore(lore.isEmpty() ? null : lore);
        }
        meta.getPersistentDataContainer().remove(dropChanceKey);
        item.setItemMeta(meta);
        return item;
    }

    private double getChance(ItemStack item) {
        if (!item.hasItemMeta()) return 100.0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(dropChanceKey, PersistentDataType.DOUBLE, 100.0);
    }

    private List<DropEntry> readDrops(PersistentDataContainer container) {
        byte[] bytes = container.get(dropDataKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) return List.of();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = input.readInt();
            List<DropEntry> result = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                int length = input.readInt();
                byte[] itemBytes = input.readNBytes(length);
                ItemStack item = ItemStack.deserializeBytes(itemBytes);
                double chance = input.readDouble();
                result.add(new DropEntry(item, chance));
            }
            return result;
        } catch (IOException | IllegalArgumentException exception) {
            return List.of();
        }
    }

    private byte[] writeDrops(List<DropEntry> entries) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(entries.size());
            for (DropEntry entry : entries) {
                byte[] item = entry.item().serializeAsBytes();
                output.writeInt(item.length);
                output.write(item);
                output.writeDouble(entry.chance());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    private byte[] writeItems(ItemStack[] items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
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
            int size = Math.min(input.readInt(), expected);
            for (int i = 0; i < size; i++) {
                int length = input.readInt();
                if (length > 0) result[i] = ItemStack.deserializeBytes(input.readNBytes(length));
            }
        } catch (IOException | IllegalArgumentException ignored) { }
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
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.US, "%.1f", value);
    }

    private record DropEntry(ItemStack item, double chance) { }
}
