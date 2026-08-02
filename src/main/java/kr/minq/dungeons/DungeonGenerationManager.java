package kr.minq.dungeons;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class DungeonGenerationManager {
    private static final int ROOM_GAP = 5;
    private static final int MAX_ROOMS = 30;

    private final JavaPlugin plugin;
    private final RoomTemplateManager templates;
    private final File templateDirectory;
    private final NamespacedKey previewKey;
    private final NamespacedKey roleKey;

    public DungeonGenerationManager(JavaPlugin plugin, RoomTemplateManager templates) {
        this.plugin = plugin;
        this.templates = templates;
        this.templateDirectory = new File(plugin.getDataFolder(), "templates");
        this.previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        this.roleKey = new NamespacedKey(plugin, "custom_mob_role");
    }

    public String generateRandom(Player player, int requestedRooms) {
        int roomCount = Math.max(2, Math.min(MAX_ROOMS, requestedRooms));
        List<TemplateMeta> all = readAllMetadata();
        List<TemplateMeta> starts = byType(all, "start");
        List<TemplateMeta> bosses = byType(all, "boss");
        List<TemplateMeta> middles = all.stream()
                .filter(meta -> !meta.type().equals("start") && !meta.type().equals("boss"))
                .toList();

        if (starts.isEmpty()) return "§cstart 타입 템플릿이 없습니다.";
        if (bosses.isEmpty()) return "§cboss 타입 템플릿이 없습니다.";
        if (roomCount > 2 && middles.isEmpty()) return "§c중간 방으로 사용할 템플릿이 없습니다.";

        List<String> sequence = new ArrayList<>();
        sequence.add(random(starts).name());
        for (int i = 0; i < roomCount - 2; i++) sequence.add(random(middles).name());
        sequence.add(random(bosses).name());
        return generateCustom(player, sequence);
    }

    public String generateCustom(Player player, List<String> names) {
        if (names.size() < 1) return "§c템플릿 이름을 한 개 이상 입력하세요.";
        if (names.size() > MAX_ROOMS) return "§c한 번에 최대 " + MAX_ROOMS + "개의 방만 생성할 수 있습니다.";

        List<TemplateMeta> rooms = new ArrayList<>();
        for (String raw : names) {
            TemplateMeta meta = readMetadata(raw);
            if (meta == null) return "§c템플릿을 찾거나 읽을 수 없습니다: §f" + raw;
            rooms.add(meta);
        }

        Location original = player.getLocation().clone();
        Location dungeonOrigin = original.getBlock().getLocation();
        World world = dungeonOrigin.getWorld();
        if (world == null) return "§c현재 월드를 찾을 수 없습니다.";

        int cursorX = dungeonOrigin.getBlockX();
        List<String> placed = new ArrayList<>();
        for (int index = 0; index < rooms.size(); index++) {
            TemplateMeta room = rooms.get(index);
            Location roomOrigin = new Location(world, cursorX, dungeonOrigin.getBlockY(), dungeonOrigin.getBlockZ());
            player.teleport(roomOrigin.clone().add(0.5, 1.0, 0.5));
            String result = templates.paste(player, room.name());
            if (!result.startsWith("§a")) {
                player.teleport(original);
                return "§c던전 생성 중 실패했습니다: §f" + room.name() + " §7- " + result;
            }
            activateGeneratedMobs(roomOrigin, room);
            placed.add(room.name());

            if (index < rooms.size() - 1) {
                TemplateMeta next = rooms.get(index + 1);
                int bridgeStart = cursorX + room.sizeX();
                int nextOriginX = bridgeStart + ROOM_GAP;
                buildBridge(world, bridgeStart, nextOriginX, dungeonOrigin.getBlockY(),
                        dungeonOrigin.getBlockZ(), room.sizeZ(), next.sizeZ());
                cursorX = nextOriginX;
            }
        }

        Location entrance = dungeonOrigin.clone().add(0.5, 1.0, 0.5);
        player.teleport(entrance);
        player.setGameMode(GameMode.ADVENTURE);
        return "§a던전을 생성했습니다. §7방 " + rooms.size() + "개: §f" + String.join(" §8→ §f", placed);
    }

    public List<String> templateNames() {
        return templates.listNames();
    }

    private void buildBridge(World world, int fromX, int toX, int y, int originZ, int currentDepth, int nextDepth) {
        int centerZ = originZ + Math.max(0, Math.min(currentDepth, nextDepth) / 2);
        for (int x = fromX; x < toX; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x, y, centerZ + dz).setType(Material.POLISHED_DEEPSLATE, false);
                world.getBlockAt(x, y + 1, centerZ + dz).setType(Material.AIR, false);
                world.getBlockAt(x, y + 2, centerZ + dz).setType(Material.AIR, false);
                world.getBlockAt(x, y + 3, centerZ + dz).setType(Material.AIR, false);
            }
            world.getBlockAt(x, y + 1, centerZ - 2).setType(Material.DEEPSLATE_BRICKS, false);
            world.getBlockAt(x, y + 1, centerZ + 2).setType(Material.DEEPSLATE_BRICKS, false);
        }
    }

    private void activateGeneratedMobs(Location origin, TemplateMeta room) {
        World world = origin.getWorld();
        if (world == null) return;
        double minX = origin.getX(), minY = origin.getY(), minZ = origin.getZ();
        double maxX = minX + room.sizeX(), maxY = minY + room.sizeY(), maxZ = minZ + room.sizeZ();
        for (LivingEntity entity : world.getLivingEntities()) {
            Location location = entity.getLocation();
            if (location.getX() < minX || location.getX() >= maxX
                    || location.getY() < minY || location.getY() >= maxY
                    || location.getZ() < minZ || location.getZ() >= maxZ) continue;
            if (entity.getPersistentDataContainer().getOrDefault(previewKey, PersistentDataType.BYTE, (byte) 0) != (byte) 1) continue;

            String role = entity.getPersistentDataContainer().getOrDefault(roleKey, PersistentDataType.STRING, "NORMAL");
            entity.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 0);
            boolean hostile = role.equals("NORMAL") || role.equals("BOSS");
            entity.setAI(hostile || role.equals("ALLY"));
            entity.setInvulnerable(!hostile);
            entity.setSilent(false);
            if (!hostile && entity instanceof Mob mob) mob.setTarget(null);
        }
    }

    private List<TemplateMeta> readAllMetadata() {
        List<TemplateMeta> result = new ArrayList<>();
        for (String name : templates.listNames()) {
            TemplateMeta meta = readMetadata(name);
            if (meta != null) result.add(meta);
        }
        return result;
    }

    private List<TemplateMeta> byType(List<TemplateMeta> values, String type) {
        return values.stream().filter(meta -> meta.type().equals(type)).toList();
    }

    private TemplateMeta random(List<TemplateMeta> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private TemplateMeta readMetadata(String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT);
        File file = new File(templateDirectory, name + ".dtpl");
        if (!file.isFile()) return null;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            int version = input.readInt();
            if (version != 1) return null;
            String storedName = readString(input);
            String type = readString(input).toLowerCase(Locale.ROOT);
            int sizeX = input.readInt();
            int sizeY = input.readInt();
            int sizeZ = input.readInt();
            input.readInt();
            return new TemplateMeta(storedName, type, sizeX, sizeY, sizeZ);
        } catch (IOException exception) {
            plugin.getLogger().warning("템플릿 메타데이터 읽기 실패: " + name + " - " + exception.getMessage());
            return null;
        }
    }

    private String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 1_000_000) throw new IOException("잘못된 문자열 길이");
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }

    private record TemplateMeta(String name, String type, int sizeX, int sizeY, int sizeZ) { }
}
