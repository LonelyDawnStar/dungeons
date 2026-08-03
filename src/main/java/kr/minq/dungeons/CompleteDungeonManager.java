package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CompleteDungeonManager implements Listener {
    private static final String GUI_TITLE = "§8던전 선택";
    private static final double RESET_RADIUS = 192.0;

    private final JavaPlugin plugin;
    private final RoomTemplateManager templates;
    private final PartyManager parties;
    private final NamespacedKey dungeonItemKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey previewKey;
    private final Map<String, DungeonDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, DungeonRun> activeRuns = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, String> playerDungeon = new HashMap<>();

    public CompleteDungeonManager(JavaPlugin plugin, RoomTemplateManager templates, PartyManager parties) {
        this.plugin = plugin;
        this.templates = templates;
        this.parties = parties;
        dungeonItemKey = new NamespacedKey(plugin, "dungeon_menu_id");
        roleKey = new NamespacedKey(plugin, "custom_mob_role");
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        loadDefinitions();
        // 서버가 비정상 종료된 뒤에도 잠금이 남지 않도록 실행 상태는 메모리에만 둔다.
        activeRuns.clear();
    }

    public void openMenu(Player player) {
        int size = Math.max(9, Math.min(54, ((definitions.size() + 8) / 9) * 9));
        Inventory inventory = Bukkit.createInventory(null, size, GUI_TITLE);
        int slot = 0;
        for (DungeonDefinition definition : definitions.values()) {
            boolean busy = activeRuns.containsKey(definition.id());
            ItemStack item = new ItemStack(busy ? Material.RED_CONCRETE : Material.LIME_CONCRETE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((busy ? "§c" : "§a") + definition.displayName());
            List<String> lore = new ArrayList<>();
            lore.add("§7템플릿: §f" + definition.template());
            lore.add("§7상태: " + (busy ? "§c도전 중" : "§a입장 가능"));
            if (busy) {
                DungeonRun run = activeRuns.get(definition.id());
                lore.add("§7도전 파티장: §f" + run.leaderName());
            } else {
                lore.add("§e클릭하여 파티 도전 시작");
            }
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(dungeonItemKey, PersistentDataType.STRING, definition.id());
            item.setItemMeta(meta);
            inventory.setItem(slot++, item);
        }
        if (definitions.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            ItemMeta meta = empty.getItemMeta();
            meta.setDisplayName("§c등록된 던전이 없습니다");
            meta.setLore(List.of("§7/dungeon register <ID> <템플릿>"));
            empty.setItemMeta(meta);
            inventory.setItem(4, empty);
        }
        player.openInventory(inventory);
    }

    public String register(Player player, String rawId, String template) {
        if (!player.hasPermission("dungeons.admin")) return "§c관리자 권한이 필요합니다.";
        String id = normalize(rawId);
        if (id == null) return "§c던전 ID는 영문·숫자·_·-만 사용할 수 있습니다.";
        if (!templates.listNames().contains(template.toLowerCase(Locale.ROOT))) return "§c해당 템플릿을 찾을 수 없습니다.";
        Location origin = player.getLocation().getBlock().getLocation();
        definitions.put(id, new DungeonDefinition(id, template.toLowerCase(Locale.ROOT), rawId, origin));
        saveDefinitions();
        return "§a던전 §f" + id + "§a을 등록했습니다. §7플레이 위치: " + format(origin);
    }

    public String unregister(String rawId) {
        String id = normalize(rawId);
        if (id == null || !definitions.containsKey(id)) return "§c등록된 던전을 찾을 수 없습니다.";
        if (activeRuns.containsKey(id)) return "§c현재 도전 중인 던전은 삭제할 수 없습니다.";
        definitions.remove(id);
        saveDefinitions();
        return "§a던전 §f" + id + "§a을 등록 해제했습니다.";
    }

    public List<String> ids() { return new ArrayList<>(definitions.keySet()); }

    public String start(Player leader, String rawId) {
        String id = normalize(rawId);
        DungeonDefinition definition = id == null ? null : definitions.get(id);
        if (definition == null) return "§c등록된 던전을 찾을 수 없습니다.";
        if (activeRuns.containsKey(id)) return "§c현재 다른 파티가 이 던전을 도전 중입니다.";
        if (playerDungeon.containsKey(leader.getUniqueId())) return "§c이미 던전에 참가 중입니다.";

        PartyManager.Party party = parties.getParty(leader);
        if (party == null) {
            parties.createParty(leader);
            party = parties.getParty(leader);
        }
        if (!parties.isLeader(leader)) return "§c파티장만 던전을 시작할 수 있습니다.";
        List<Player> members = parties.getOnlineMembers(party);
        for (Player member : members) {
            if (playerDungeon.containsKey(member.getUniqueId())) return "§c파티원 중 이미 다른 던전에 참가 중인 사람이 있습니다.";
        }

        activeRuns.put(id, new DungeonRun(id, leader.getUniqueId(), leader.getName(), members.stream().map(Player::getUniqueId).toList(), System.currentTimeMillis()));
        resetArea(definition);
        Location temporary = leader.getLocation().clone();
        leader.teleport(definition.origin());
        String pasted = templates.paste(leader, definition.template());
        leader.teleport(temporary);
        if (pasted.startsWith("§c")) {
            activeRuns.remove(id);
            return pasted;
        }
        activateDungeonEntities(definition.origin());
        Location entrance = definition.origin().clone().add(0.5, 1.0, 0.5);
        for (Player member : members) {
            returnLocations.put(member.getUniqueId(), member.getLocation().clone());
            playerDungeon.put(member.getUniqueId(), id);
            member.teleport(entrance);
            member.setGameMode(GameMode.ADVENTURE);
            member.sendTitle("§6" + definition.displayName(), "§f던전 도전을 시작합니다", 10, 50, 15);
        }
        return "§a던전 §f" + definition.displayName() + "§a 도전을 시작했습니다.";
    }

    public String finish(Player requester, boolean abandoned) {
        String id = playerDungeon.get(requester.getUniqueId());
        if (id == null) return "§c현재 참가 중인 던전이 없습니다.";
        DungeonRun run = activeRuns.get(id);
        if (run == null) return "§c던전 실행 정보를 찾을 수 없습니다.";
        boolean admin = requester.hasPermission("dungeons.admin");
        if (!admin && !run.leader().equals(requester.getUniqueId())) return "§c파티장만 종료할 수 있습니다.";
        DungeonDefinition definition = definitions.get(id);
        for (UUID memberId : run.members()) {
            Player member = Bukkit.getPlayer(memberId);
            playerDungeon.remove(memberId);
            Location destination = returnLocations.remove(memberId);
            if (member != null && member.isOnline()) {
                if (destination != null && destination.getWorld() != null) member.teleport(destination);
                else member.teleport(Bukkit.getWorlds().getFirst().getSpawnLocation());
                member.setGameMode(GameMode.SURVIVAL);
                member.sendTitle(abandoned ? "§c도전 포기" : "§a던전 완료", "§7던전이 초기화됩니다", 10, 40, 15);
            }
        }
        activeRuns.remove(id);
        if (definition != null) {
            resetArea(definition);
            Player operator = requester;
            Location previous = operator.getLocation().clone();
            operator.teleport(definition.origin());
            templates.paste(operator, definition.template());
            operator.teleport(previous);
            freezeRestoredEntities(definition.origin());
        }
        return "§a던전이 종료되고 초기화되었습니다.";
    }

    public String status() {
        if (definitions.isEmpty()) return "§7등록된 던전이 없습니다.";
        long available = definitions.size() - activeRuns.size();
        return "§f등록 §e" + definitions.size() + "§f개 / 입장 가능 §a" + available + "§f개 / 도전 중 §c" + activeRuns.size() + "§f개";
    }

    public void shutdown() {
        for (String id : new ArrayList<>(activeRuns.keySet())) {
            DungeonRun run = activeRuns.get(id);
            if (run == null) continue;
            for (UUID member : run.members()) {
                playerDungeon.remove(member);
                Player player = Bukkit.getPlayer(member);
                Location destination = returnLocations.remove(member);
                if (player != null && destination != null && destination.getWorld() != null) player.teleport(destination);
            }
        }
        activeRuns.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String id = clicked.getItemMeta().getPersistentDataContainer().get(dungeonItemKey, PersistentDataType.STRING);
        if (id == null) return;
        player.closeInventory();
        player.sendMessage("§6§l[Dungeons] §f" + start(player, id));
    }

    private void resetArea(DungeonDefinition definition) {
        World world = definition.origin().getWorld();
        if (world == null) return;
        double radiusSquared = RESET_RADIUS * RESET_RADIUS;
        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (entity instanceof Player) continue;
            if (entity.getLocation().distanceSquared(definition.origin()) <= radiusSquared) entity.remove();
        }
    }

    private void activateDungeonEntities(Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player || entity.getLocation().distanceSquared(origin) > RESET_RADIUS * RESET_RADIUS) continue;
            if (entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) != (byte) 1) continue;
            String role = entity.getPersistentDataContainer().getOrDefault(roleKey, PersistentDataType.STRING, "NORMAL");
            boolean hostile = role.equals("NORMAL") || role.equals("BOSS") || role.equals("ALLY");
            entity.setAI(hostile);
            entity.setInvulnerable(!hostile);
            entity.setSilent(false);
        }
    }

    private void freezeRestoredEntities(Location origin) {
        World world = origin.getWorld();
        if (world == null) return;
        for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Player || entity.getLocation().distanceSquared(origin) > RESET_RADIUS * RESET_RADIUS) continue;
            if (entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) != (byte) 1) continue;
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
        }
    }

    private void loadDefinitions() {
        definitions.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("dungeons");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String template = section.getString(id + ".template");
            String display = section.getString(id + ".display", id);
            String worldName = section.getString(id + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (template == null || world == null) continue;
            Location origin = new Location(world, section.getInt(id + ".x"), section.getInt(id + ".y"), section.getInt(id + ".z"));
            definitions.put(id, new DungeonDefinition(id, template, display, origin));
        }
    }

    private void saveDefinitions() {
        plugin.getConfig().set("dungeons", null);
        for (DungeonDefinition definition : definitions.values()) {
            String path = "dungeons." + definition.id();
            plugin.getConfig().set(path + ".template", definition.template());
            plugin.getConfig().set(path + ".display", definition.displayName());
            plugin.getConfig().set(path + ".world", definition.origin().getWorld().getName());
            plugin.getConfig().set(path + ".x", definition.origin().getBlockX());
            plugin.getConfig().set(path + ".y", definition.origin().getBlockY());
            plugin.getConfig().set(path + ".z", definition.origin().getBlockZ());
        }
        plugin.saveConfig();
    }

    private String normalize(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9_-]{1,40}")) return null;
        return value.toLowerCase(Locale.ROOT);
    }
    private String format(Location location) { return location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(); }

    private record DungeonDefinition(String id, String template, String displayName, Location origin) { }
    private record DungeonRun(String id, UUID leader, String leaderName, List<UUID> members, long startedAt) { }
}
