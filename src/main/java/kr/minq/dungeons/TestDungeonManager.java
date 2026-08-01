package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TestDungeonManager implements Listener {
    private static final String WORLD_NAME = "dungeons_test";
    private static final Location ROOM_CENTER = new Location(null, 0.5, 81.0, 0.5);

    private final JavaPlugin plugin;
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Set<UUID> dungeonPlayers = new HashSet<>();
    private final Set<UUID> dungeonMobs = new HashSet<>();
    private BossBar bossBar;
    private boolean running;
    private int totalMobs;

    public TestDungeonManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    public String start(Collection<Player> players) {
        if (running) {
            return "§c이미 테스트 던전이 진행 중입니다.";
        }
        if (players.isEmpty()) {
            return "§c입장할 온라인 플레이어가 없습니다.";
        }

        World world = prepareWorld();
        buildRoom(world);
        clearOldEntities(world);

        ROOM_CENTER.setWorld(world);
        running = true;
        dungeonPlayers.clear();
        dungeonMobs.clear();
        returnLocations.clear();

        for (Player player : players) {
            returnLocations.put(player.getUniqueId(), player.getLocation().clone());
            dungeonPlayers.add(player.getUniqueId());
            player.teleport(ROOM_CENTER.clone().add(0, 0, 5));
            player.sendTitle("§6§lTEST DUNGEON", "§f모든 몬스터를 처치하세요", 10, 60, 20);
        }

        spawnWave(world);
        createBossBar(players);
        Bukkit.broadcastMessage("§6§l[Dungeons] §f테스트 던전이 시작되었습니다. §7(몬스터 " + totalMobs + "마리)");
        return "§a테스트 던전을 시작했습니다.";
    }

    public String stop(boolean cleared) {
        if (!running) {
            return "§c진행 중인 던전이 없습니다.";
        }

        running = false;
        for (UUID mobId : new HashSet<>(dungeonMobs)) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null) entity.remove();
        }
        dungeonMobs.clear();

        for (UUID playerId : new HashSet<>(dungeonPlayers)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) continue;
            Location back = returnLocations.get(playerId);
            if (back != null) player.teleport(back);
            if (cleared) {
                player.sendTitle("§a§lDUNGEON CLEAR", "§f첫 전투방을 정복했습니다!", 10, 60, 20);
            } else {
                player.sendTitle("§c§lDUNGEON END", "§7던전이 종료되었습니다", 10, 40, 20);
            }
        }

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        dungeonPlayers.clear();
        returnLocations.clear();
        Bukkit.broadcastMessage(cleared
                ? "§6§l[Dungeons] §a테스트 던전 클리어!"
                : "§6§l[Dungeons] §f테스트 던전이 종료되었습니다.");
        return cleared ? "§a던전을 클리어했습니다." : "§7던전을 종료했습니다.";
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!running || !dungeonMobs.remove(event.getEntity().getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        updateBossBar();
        if (dungeonMobs.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> stop(true), 40L);
        }
    }

    private World prepareWorld() {
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) {
            WorldCreator creator = new WorldCreator(WORLD_NAME);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            world = creator.createWorld();
        }
        if (world == null) {
            throw new IllegalStateException("테스트 던전 월드를 생성하지 못했습니다.");
        }
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setTime(18000L);
        return world;
    }

    private void buildRoom(World world) {
        int centerX = 0;
        int floorY = 80;
        int centerZ = 0;
        int radius = 8;
        int height = 7;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.getBlockAt(centerX + x, floorY, centerZ + z).setType(Material.DEEPSLATE_BRICKS, false);
                world.getBlockAt(centerX + x, floorY + height, centerZ + z).setType(Material.DEEPSLATE_TILES, false);
                for (int y = 1; y < height; y++) {
                    boolean wall = Math.abs(x) == radius || Math.abs(z) == radius;
                    world.getBlockAt(centerX + x, floorY + y, centerZ + z)
                            .setType(wall ? Material.POLISHED_DEEPSLATE : Material.AIR, false);
                }
            }
        }

        world.getBlockAt(0, 81, 0).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(6, 81, 6).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(-6, 81, 6).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(6, 81, -6).setType(Material.SOUL_LANTERN, false);
        world.getBlockAt(-6, 81, -6).setType(Material.SOUL_LANTERN, false);
    }

    private void clearOldEntities(World world) {
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) entity.remove();
        }
    }

    private void spawnWave(World world) {
        Location[] spawns = {
                new Location(world, -5.5, 81, -4.5),
                new Location(world, 0.5, 81, -5.5),
                new Location(world, 5.5, 81, -4.5),
                new Location(world, -5.5, 81, 0.5),
                new Location(world, 5.5, 81, 0.5),
                new Location(world, 0.5, 81, 4.5)
        };

        for (Location spawn : spawns) {
            LivingEntity zombie = (LivingEntity) world.spawnEntity(spawn, EntityType.ZOMBIE);
            zombie.setCustomName("§7던전 좀비");
            zombie.setCustomNameVisible(true);
            dungeonMobs.add(zombie.getUniqueId());
        }

        LivingEntity elite = (LivingEntity) world.spawnEntity(new Location(world, 0.5, 81, -1.5), EntityType.HUSK);
        elite.setCustomName("§c§l엘리트 사형집행자");
        elite.setCustomNameVisible(true);
        if (elite.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            elite.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(60.0);
            elite.setHealth(60.0);
        }
        if (elite.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            elite.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(8.0);
        }
        dungeonMobs.add(elite.getUniqueId());
        totalMobs = dungeonMobs.size();
    }

    private void createBossBar(Collection<Player> players) {
        bossBar = Bukkit.createBossBar("§6남은 몬스터: §f" + dungeonMobs.size(), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        for (Player player : players) bossBar.addPlayer(player);
        bossBar.setProgress(1.0);
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        bossBar.setTitle("§6남은 몬스터: §f" + dungeonMobs.size());
        bossBar.setProgress(totalMobs <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, dungeonMobs.size() / (double) totalMobs)));
    }
}
