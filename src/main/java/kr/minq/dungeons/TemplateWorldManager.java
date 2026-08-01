package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TemplateWorldManager implements Listener {
    public static final String WORLD_NAME = "dungeon_templates";

    private final JavaPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public TemplateWorldManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "template_wand");
    }

    public World createOrLoadWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            configureWorld(existing);
            return existing;
        }

        WorldCreator creator = new WorldCreator(WORLD_NAME)
                .type(WorldType.FLAT)
                .generateStructures(false);
        World world = creator.createWorld();
        if (world != null) {
            configureWorld(world);
            world.setSpawnLocation(0, 65, 0);
        }
        return world;
    }

    private void configureWorld(World world) {
        world.setAutoSave(true);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
    }

    public String enter(Player player) {
        World world = createOrLoadWorld();
        if (world == null) {
            return "§c템플릿 월드를 생성하지 못했습니다.";
        }
        if (!player.getWorld().getName().equals(WORLD_NAME)) {
            returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
        player.teleport(world.getSpawnLocation().clone().add(0.5, 1.0, 0.5));
        player.sendTitle("§6템플릿 월드", "§f방을 건축하고 선택 도끼로 영역을 지정하세요", 10, 60, 20);
        return "§a템플릿 월드로 이동했습니다.";
    }

    public String leave(Player player) {
        Location destination = returnLocations.remove(player.getUniqueId());
        if (destination == null || destination.getWorld() == null) {
            World fallback = Bukkit.getWorlds().stream()
                    .filter(world -> !world.getName().equals(WORLD_NAME))
                    .findFirst()
                    .orElse(null);
            if (fallback == null) {
                return "§c돌아갈 월드를 찾지 못했습니다.";
            }
            destination = fallback.getSpawnLocation();
        }
        player.teleport(destination);
        return "§a원래 위치로 돌아왔습니다.";
    }

    public String giveWand(Player player) {
        if (!player.getWorld().getName().equals(WORLD_NAME)) {
            return "§c템플릿 월드 안에서만 선택 도끼를 받을 수 있습니다.";
        }
        ItemStack wand = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§6§l던전 템플릿 선택 도끼");
        meta.setLore(java.util.List.of(
                "§e좌클릭 §7- 첫 번째 지점",
                "§e우클릭 §7- 두 번째 지점"
        ));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        return "§a선택 도끼를 지급했습니다.";
    }

    public String setPosition(Player player, boolean first) {
        if (!player.getWorld().getName().equals(WORLD_NAME)) {
            return "§c템플릿 월드 안에서만 영역을 지정할 수 있습니다.";
        }
        Location location = player.getLocation().getBlock().getLocation();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (first) selection.pos1 = location;
        else selection.pos2 = location;
        return describePosition(first, location) + describeSize(selection);
    }

    public String info(Player player) {
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.pos1 == null || selection.pos2 == null) {
            return "§c두 지점을 모두 지정하지 않았습니다.";
        }
        int minX = Math.min(selection.pos1.getBlockX(), selection.pos2.getBlockX());
        int minY = Math.min(selection.pos1.getBlockY(), selection.pos2.getBlockY());
        int minZ = Math.min(selection.pos1.getBlockZ(), selection.pos2.getBlockZ());
        int maxX = Math.max(selection.pos1.getBlockX(), selection.pos2.getBlockX());
        int maxY = Math.max(selection.pos1.getBlockY(), selection.pos2.getBlockY());
        int maxZ = Math.max(selection.pos1.getBlockZ(), selection.pos2.getBlockZ());
        long sizeX = maxX - minX + 1L;
        long sizeY = maxY - minY + 1L;
        long sizeZ = maxZ - minZ + 1L;
        long volume = sizeX * sizeY * sizeZ;
        return "§6§l[선택 영역] §f"
                + "최소 " + minX + ", " + minY + ", " + minZ
                + " §7/ §f최대 " + maxX + ", " + maxY + ", " + maxZ
                + " §7/ §e크기 " + sizeX + "×" + sizeY + "×" + sizeZ
                + " §7(" + volume + "블록)";
    }

    public String clearSelection(Player player) {
        selections.remove(player.getUniqueId());
        return "§a선택 영역을 초기화했습니다.";
    }

    public Selection getSelection(Player player) {
        return selections.get(player.getUniqueId());
    }

    @EventHandler
    public void onWandUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Block clicked = event.getClickedBlock();
        if (item == null || clicked == null || !isWand(item)) return;
        if (!player.hasPermission("dungeons.admin")) return;
        if (!player.getWorld().getName().equals(WORLD_NAME)) {
            player.sendMessage("§c템플릿 월드 안에서만 사용할 수 있습니다.");
            return;
        }

        boolean first;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) first = true;
        else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) first = false;
        else return;

        event.setCancelled(true);
        Location location = clicked.getLocation();
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (first) selection.pos1 = location;
        else selection.pos2 = location;
        player.sendMessage(describePosition(first, location) + describeSize(selection));
    }

    private boolean isWand(ItemStack item) {
        if (item.getType() != Material.WOODEN_AXE || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(wandKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private String describePosition(boolean first, Location location) {
        return "§6§l[Dungeons] §e" + (first ? "1번" : "2번") + " 지점 설정: §f"
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private String describeSize(Selection selection) {
        if (selection.pos1 == null || selection.pos2 == null) return "";
        int x = Math.abs(selection.pos1.getBlockX() - selection.pos2.getBlockX()) + 1;
        int y = Math.abs(selection.pos1.getBlockY() - selection.pos2.getBlockY()) + 1;
        int z = Math.abs(selection.pos1.getBlockZ() - selection.pos2.getBlockZ()) + 1;
        return " §7| §a" + x + "×" + y + "×" + z;
    }

    public static final class Selection {
        private Location pos1;
        private Location pos2;

        public Location getPos1() {
            return pos1 == null ? null : pos1.clone();
        }

        public Location getPos2() {
            return pos2 == null ? null : pos2.clone();
        }

        public boolean isComplete() {
            return pos1 != null && pos2 != null;
        }
    }
}
