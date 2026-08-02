package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RoleSettingsManager implements Listener {
    private static final String TRADER_TITLE = "§8거래인 설정";
    private static final String REWARD_TITLE = "§8보상지급인 설정";
    private static final String BOSS_TITLE = "§8보스몹 설정";
    private static final String ALLY_TITLE = "§8조력자 설정";
    private static final int[] TRADE_COST_1 = {10, 19, 28, 37};
    private static final int[] TRADE_COST_2 = {11, 20, 29, 38};
    private static final int[] TRADE_RESULT = {13, 22, 31, 40};
    private static final int[] REWARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final NamespacedKey previewKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey traderDataKey;
    private final NamespacedKey rewardDataKey;
    private final NamespacedKey rewardOnceKey;
    private final NamespacedKey bossBarKey;
    private final NamespacedKey bossColorKey;
    private final NamespacedKey allyModeKey;
    private final NamespacedKey allyPowerKey;
    private final NamespacedKey allyRangeKey;
    private final Map<UUID, UUID> editing = new HashMap<>();
    private final Set<String> rewardedPlayers = new HashSet<>();

    public RoleSettingsManager(JavaPlugin plugin) {
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        roleKey = new NamespacedKey(plugin, "custom_mob_role");
        traderDataKey = new NamespacedKey(plugin, "role_trader_data");
        rewardDataKey = new NamespacedKey(plugin, "role_reward_data");
        rewardOnceKey = new NamespacedKey(plugin, "role_reward_once");
        bossBarKey = new NamespacedKey(plugin, "role_boss_bar");
        bossColorKey = new NamespacedKey(plugin, "role_boss_color");
        allyModeKey = new NamespacedKey(plugin, "role_ally_mode");
        allyPowerKey = new NamespacedKey(plugin, "role_ally_power");
        allyRangeKey = new NamespacedKey(plugin, "role_ally_range");
    }

    public void initializeDefaults(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (!data.has(rewardOnceKey, PersistentDataType.BYTE)) data.set(rewardOnceKey, PersistentDataType.BYTE, (byte) 1);
        if (!data.has(bossBarKey, PersistentDataType.BYTE)) data.set(bossBarKey, PersistentDataType.BYTE, (byte) 1);
        if (!data.has(bossColorKey, PersistentDataType.STRING)) data.set(bossColorKey, PersistentDataType.STRING, "RED");
        if (!data.has(allyModeKey, PersistentDataType.STRING)) data.set(allyModeKey, PersistentDataType.STRING, "ATTACK");
        if (!data.has(allyPowerKey, PersistentDataType.DOUBLE)) data.set(allyPowerKey, PersistentDataType.DOUBLE, 4.0);
        if (!data.has(allyRangeKey, PersistentDataType.DOUBLE)) data.set(allyRangeKey, PersistentDataType.DOUBLE, 12.0);
    }

    public void openSettings(Player player, LivingEntity entity, String role) {
        initializeDefaults(entity);
        editing.put(player.getUniqueId(), entity.getUniqueId());
        switch (role) {
            case "TRADER" -> openTrader(player, entity);
            case "REWARD" -> openReward(player, entity);
            case "BOSS" -> openBoss(player, entity);
            case "ALLY" -> openAlly(player, entity);
            default -> player.sendMessage("§7일반몹은 메인 편집창의 공격 설정을 사용합니다.");
        }
    }

    private void openTrader(Player player, LivingEntity entity) {
        Inventory inv = Bukkit.createInventory(null, 54, TRADER_TITLE);
        List<Trade> trades = readTrades(entity);
        for (int i = 0; i < 4; i++) {
            Trade trade = i < trades.size() ? trades.get(i) : null;
            inv.setItem(TRADE_COST_1[i], trade == null ? placeholder(Material.EMERALD, "§e필요 물건 1") : trade.cost1());
            inv.setItem(TRADE_COST_2[i], trade == null || trade.cost2() == null ? placeholder(Material.GRAY_DYE, "§7필요 물건 2 §8(선택)") : trade.cost2());
            inv.setItem(TRADE_RESULT[i], trade == null ? placeholder(Material.CHEST, "§a받는 물건") : trade.result());
        }
        inv.setItem(49, button(Material.LIME_DYE, "§a저장 후 닫기", "§7아래 인벤토리에서 아이템을 집어 각 칸에 넣으세요."));
        player.openInventory(inv);
    }

    private void openReward(Player player, LivingEntity entity) {
        Inventory inv = Bukkit.createInventory(null, 45, REWARD_TITLE);
        List<ItemStack> rewards = readItems(entity.getPersistentDataContainer().get(rewardDataKey, PersistentDataType.BYTE_ARRAY));
        for (int i = 0; i < Math.min(rewards.size(), REWARD_SLOTS.length); i++) inv.setItem(REWARD_SLOTS[i], rewards.get(i));
        boolean once = entity.getPersistentDataContainer().getOrDefault(rewardOnceKey, PersistentDataType.BYTE, (byte) 1) == (byte) 1;
        inv.setItem(40, button(once ? Material.LIME_DYE : Material.GRAY_DYE, "§e플레이어당 1회: " + (once ? "§aON" : "§cOFF"), "§7클릭하여 전환"));
        inv.setItem(42, button(Material.BARRIER, "§c보상 전체 삭제"));
        player.openInventory(inv);
    }

    private void openBoss(Player player, LivingEntity entity) {
        Inventory inv = Bukkit.createInventory(null, 27, BOSS_TITLE);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        boolean bar = data.getOrDefault(bossBarKey, PersistentDataType.BYTE, (byte) 1) == (byte) 1;
        String color = data.getOrDefault(bossColorKey, PersistentDataType.STRING, "RED");
        inv.setItem(11, button(bar ? Material.LIME_DYE : Material.GRAY_DYE, "§e보스바 표시: " + (bar ? "§aON" : "§cOFF"), "§7클릭하여 전환"));
        inv.setItem(15, button(Material.DRAGON_BREATH, "§d보스바 색상: §f" + color, "§7좌/우클릭으로 변경"));
        inv.setItem(22, button(Material.ARROW, "§a완료"));
        player.openInventory(inv);
    }

    private void openAlly(Player player, LivingEntity entity) {
        Inventory inv = Bukkit.createInventory(null, 27, ALLY_TITLE);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String mode = data.getOrDefault(allyModeKey, PersistentDataType.STRING, "ATTACK");
        double power = data.getOrDefault(allyPowerKey, PersistentDataType.DOUBLE, 4.0);
        double range = data.getOrDefault(allyRangeKey, PersistentDataType.DOUBLE, 12.0);
        inv.setItem(11, button(mode.equals("ATTACK") ? Material.IRON_SWORD : Material.GOLDEN_APPLE, "§b지원 방식: §f" + (mode.equals("ATTACK") ? "공격" : "회복"), "§7클릭하여 전환"));
        inv.setItem(13, button(Material.BLAZE_POWDER, "§6지원 수치: §f" + format(power), "§7좌클릭 +1 / 우클릭 -1", "§7Shift 클릭 ±5"));
        inv.setItem(15, button(Material.SPYGLASS, "§e지원 범위: §f" + format(range) + "블록", "§7좌클릭 +1 / 우클릭 -1", "§7Shift 클릭 ±5"));
        inv.setItem(22, button(Material.ARROW, "§a완료"));
        player.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.equals(TRADER_TITLE) && !title.equals(REWARD_TITLE) && !title.equals(BOSS_TITLE) && !title.equals(ALLY_TITLE)) return;
        LivingEntity entity = target(editing.get(player.getUniqueId()));
        if (entity == null) { player.closeInventory(); return; }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() >= topSize) return;
        event.setCancelled(true);
        if (title.equals(TRADER_TITLE)) handleTraderClick(event, player, entity);
        else if (title.equals(REWARD_TITLE)) handleRewardClick(event, player, entity);
        else if (title.equals(BOSS_TITLE)) handleBossClick(event, player, entity);
        else handleAllyClick(event, player, entity);
    }

    private void handleTraderClick(InventoryClickEvent event, Player player, LivingEntity entity) {
        if (event.getRawSlot() == 49) { saveTrades(entity, event.getInventory()); player.closeInventory(); return; }
        if (!isTradeSlot(event.getRawSlot())) return;
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) event.getInventory().setItem(event.getRawSlot(), cursor.clone());
        else if (event.isRightClick()) event.getInventory().setItem(event.getRawSlot(), null);
        saveTrades(entity, event.getInventory());
    }

    private void handleRewardClick(InventoryClickEvent event, Player player, LivingEntity entity) {
        if (event.getRawSlot() == 40) {
            byte value = entity.getPersistentDataContainer().getOrDefault(rewardOnceKey, PersistentDataType.BYTE, (byte) 1);
            entity.getPersistentDataContainer().set(rewardOnceKey, PersistentDataType.BYTE, value == 1 ? (byte) 0 : (byte) 1);
            openReward(player, entity);
            return;
        }
        if (event.getRawSlot() == 42) {
            for (int slot : REWARD_SLOTS) event.getInventory().setItem(slot, null);
            saveRewards(entity, event.getInventory());
            return;
        }
        if (!isRewardSlot(event.getRawSlot())) return;
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) event.getInventory().setItem(event.getRawSlot(), cursor.clone());
        else if (event.isRightClick()) event.getInventory().setItem(event.getRawSlot(), null);
        saveRewards(entity, event.getInventory());
    }

    private void handleBossClick(InventoryClickEvent event, Player player, LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (event.getRawSlot() == 11) {
            byte current = data.getOrDefault(bossBarKey, PersistentDataType.BYTE, (byte) 1);
            data.set(bossBarKey, PersistentDataType.BYTE, current == 1 ? (byte) 0 : (byte) 1);
            openBoss(player, entity);
        } else if (event.getRawSlot() == 15) {
            List<String> colors = List.of("RED", "BLUE", "GREEN", "YELLOW", "PURPLE", "WHITE", "PINK");
            String current = data.getOrDefault(bossColorKey, PersistentDataType.STRING, "RED");
            int index = colors.indexOf(current);
            data.set(bossColorKey, PersistentDataType.STRING, colors.get(Math.floorMod(index + (event.isRightClick() ? -1 : 1), colors.size())));
            openBoss(player, entity);
        } else if (event.getRawSlot() == 22) player.closeInventory();
    }

    private void handleAllyClick(InventoryClickEvent event, Player player, LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        double step = event.isShiftClick() ? 5.0 : 1.0;
        if (event.getRawSlot() == 11) {
            String mode = data.getOrDefault(allyModeKey, PersistentDataType.STRING, "ATTACK");
            data.set(allyModeKey, PersistentDataType.STRING, mode.equals("ATTACK") ? "HEAL" : "ATTACK");
        } else if (event.getRawSlot() == 13) {
            double value = data.getOrDefault(allyPowerKey, PersistentDataType.DOUBLE, 4.0);
            data.set(allyPowerKey, PersistentDataType.DOUBLE, clamp(value + (event.isRightClick() ? -step : step), 0.0, 100.0));
        } else if (event.getRawSlot() == 15) {
            double value = data.getOrDefault(allyRangeKey, PersistentDataType.DOUBLE, 12.0);
            data.set(allyRangeKey, PersistentDataType.DOUBLE, clamp(value + (event.isRightClick() ? -step : step), 1.0, 64.0));
        } else if (event.getRawSlot() == 22) { player.closeInventory(); return; }
        openAlly(player, entity);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        LivingEntity entity = target(editing.get(player.getUniqueId()));
        if (entity == null) return;
        if (event.getView().getTitle().equals(TRADER_TITLE)) saveTrades(entity, event.getInventory());
        else if (event.getView().getTitle().equals(REWARD_TITLE)) saveRewards(entity, event.getInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onRoleInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;
        PersistentDataContainer data = entity.getPersistentDataContainer();
        String role = data.getOrDefault(roleKey, PersistentDataType.STRING, "NORMAL");
        if (role.equals("TRADER")) {
            List<Trade> trades = readTrades(entity);
            if (trades.isEmpty()) return;
            event.setCancelled(true);
            Merchant merchant = Bukkit.createMerchant(entity.getCustomName() == null ? "거래인" : ChatColor.stripColor(entity.getCustomName()));
            List<MerchantRecipe> recipes = new ArrayList<>();
            for (Trade trade : trades) {
                MerchantRecipe recipe = new MerchantRecipe(trade.result().clone(), 999999);
                List<ItemStack> ingredients = new ArrayList<>();
                ingredients.add(trade.cost1().clone());
                if (trade.cost2() != null && !trade.cost2().getType().isAir()) ingredients.add(trade.cost2().clone());
                recipe.setIngredients(ingredients);
                recipes.add(recipe);
            }
            merchant.setRecipes(recipes);
            event.getPlayer().openMerchant(merchant, true);
        } else if (role.equals("REWARD")) {
            List<ItemStack> rewards = readItems(data.get(rewardDataKey, PersistentDataType.BYTE_ARRAY));
            if (rewards.isEmpty()) return;
            event.setCancelled(true);
            boolean once = data.getOrDefault(rewardOnceKey, PersistentDataType.BYTE, (byte) 1) == (byte) 1;
            String key = entity.getUniqueId() + ":" + event.getPlayer().getUniqueId();
            if (once && rewardedPlayers.contains(key)) {
                event.getPlayer().sendMessage("§7이미 이 보상을 받았습니다.");
                return;
            }
            for (ItemStack item : rewards) event.getPlayer().getInventory().addItem(item.clone());
            if (once) rewardedPlayers.add(key);
            event.getPlayer().sendMessage("§6§l[Dungeons] §a보상을 받았습니다.");
        }
    }

    public void copyToTool(LivingEntity entity, PersistentDataContainer tool) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        copyBytes(data, tool, traderDataKey);
        copyBytes(data, tool, rewardDataKey);
        copyByte(data, tool, rewardOnceKey);
        copyByte(data, tool, bossBarKey);
        copyString(data, tool, bossColorKey);
        copyString(data, tool, allyModeKey);
        copyDouble(data, tool, allyPowerKey);
        copyDouble(data, tool, allyRangeKey);
    }

    public void applyFromTool(LivingEntity entity, PersistentDataContainer tool) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        applyBytes(tool, data, traderDataKey);
        applyBytes(tool, data, rewardDataKey);
        applyByte(tool, data, rewardOnceKey);
        applyByte(tool, data, bossBarKey);
        applyString(tool, data, bossColorKey);
        applyString(tool, data, allyModeKey);
        applyDouble(tool, data, allyPowerKey);
        applyDouble(tool, data, allyRangeKey);
        initializeDefaults(entity);
    }

    private void saveTrades(LivingEntity entity, Inventory inv) {
        List<Trade> trades = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ItemStack cost1 = clean(inv.getItem(TRADE_COST_1[i]));
            ItemStack cost2 = clean(inv.getItem(TRADE_COST_2[i]));
            ItemStack result = clean(inv.getItem(TRADE_RESULT[i]));
            if (cost1 != null && result != null) trades.add(new Trade(cost1, cost2, result));
        }
        entity.getPersistentDataContainer().set(traderDataKey, PersistentDataType.BYTE_ARRAY, writeTrades(trades));
    }

    private List<Trade> readTrades(LivingEntity entity) {
        byte[] bytes = entity.getPersistentDataContainer().get(traderDataKey, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) return List.of();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = in.readInt();
            List<Trade> result = new ArrayList<>();
            for (int i = 0; i < size; i++) result.add(new Trade(readItem(in), readItem(in), readItem(in)));
            return result;
        } catch (IOException | IllegalArgumentException ex) { return List.of(); }
    }

    private byte[] writeTrades(List<Trade> trades) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(trades.size());
            for (Trade trade : trades) { writeItem(out, trade.cost1()); writeItem(out, trade.cost2()); writeItem(out, trade.result()); }
            return bytes.toByteArray();
        } catch (IOException ex) { return new byte[0]; }
    }

    private void saveRewards(LivingEntity entity, Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot : REWARD_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) items.add(item.clone());
        }
        entity.getPersistentDataContainer().set(rewardDataKey, PersistentDataType.BYTE_ARRAY, writeItems(items));
    }

    private byte[] writeItems(List<ItemStack> items) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(items.size());
            for (ItemStack item : items) writeItem(out, item);
            return bytes.toByteArray();
        } catch (IOException ex) { return new byte[0]; }
    }

    private List<ItemStack> readItems(byte[] bytes) {
        if (bytes == null) return List.of();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int size = in.readInt();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < size; i++) { ItemStack item = readItem(in); if (item != null) items.add(item); }
            return items;
        } catch (IOException | IllegalArgumentException ex) { return List.of(); }
    }

    private void writeItem(DataOutputStream out, ItemStack item) throws IOException {
        if (item == null || item.getType().isAir()) { out.writeInt(0); return; }
        byte[] data = item.serializeAsBytes(); out.writeInt(data.length); out.write(data);
    }

    private ItemStack readItem(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0) return null;
        return ItemStack.deserializeBytes(in.readNBytes(length));
    }

    private ItemStack clean(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("설정")) return null;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() && (item.getItemMeta().getDisplayName().contains("필요 물건") || item.getItemMeta().getDisplayName().contains("받는 물건"))) return null;
        return item.clone();
    }

    private ItemStack placeholder(Material material, String name) { return button(material, name, "§7커서 아이템으로 클릭", "§c빈 커서 우클릭으로 제거"); }
    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(List.of(lore)); item.setItemMeta(meta); return item;
    }
    private LivingEntity target(UUID id) { Entity entity = id == null ? null : Bukkit.getEntity(id); return entity instanceof LivingEntity living && living.isValid() ? living : null; }
    private boolean isTradeSlot(int slot) { for (int v : TRADE_COST_1) if (v == slot) return true; for (int v : TRADE_COST_2) if (v == slot) return true; for (int v : TRADE_RESULT) if (v == slot) return true; return false; }
    private boolean isRewardSlot(int slot) { for (int v : REWARD_SLOTS) if (v == slot) return true; return false; }
    private double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private String format(double value) { return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.US, "%.1f", value); }
    private void copyBytes(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { byte[] v = from.get(key, PersistentDataType.BYTE_ARRAY); if (v != null) to.set(key, PersistentDataType.BYTE_ARRAY, v); }
    private void copyByte(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { Byte v = from.get(key, PersistentDataType.BYTE); if (v != null) to.set(key, PersistentDataType.BYTE, v); }
    private void copyString(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { String v = from.get(key, PersistentDataType.STRING); if (v != null) to.set(key, PersistentDataType.STRING, v); }
    private void copyDouble(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { Double v = from.get(key, PersistentDataType.DOUBLE); if (v != null) to.set(key, PersistentDataType.DOUBLE, v); }
    private void applyBytes(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { copyBytes(from, to, key); }
    private void applyByte(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { copyByte(from, to, key); }
    private void applyString(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { copyString(from, to, key); }
    private void applyDouble(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) { copyDouble(from, to, key); }

    private record Trade(ItemStack cost1, ItemStack cost2, ItemStack result) { }
}
