package kr.minq.dungeons;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SampleRoomGenerator {
    private static final int SIZE_X = 15;
    private static final int SIZE_Y = 8;
    private static final int SIZE_Z = 15;
    private static final int SPACING = 24;

    private final TemplateWorldManager templateWorldManager;
    private final RoomTemplateManager roomTemplateManager;
    private final NamespacedKey previewKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey roleKey;
    private final NamespacedKey rangeKey;
    private final NamespacedKey nameColorKey;
    private final NamespacedKey abilityKey;
    private final NamespacedKey scaleKey;

    public SampleRoomGenerator(JavaPlugin plugin, TemplateWorldManager templateWorldManager,
                               RoomTemplateManager roomTemplateManager) {
        this.templateWorldManager = templateWorldManager;
        this.roomTemplateManager = roomTemplateManager;
        previewKey = new NamespacedKey(plugin, "custom_mob_preview");
        healthKey = new NamespacedKey(plugin, "custom_mob_health");
        damageKey = new NamespacedKey(plugin, "custom_mob_damage");
        roleKey = new NamespacedKey(plugin, "custom_mob_role");
        rangeKey = new NamespacedKey(plugin, "custom_mob_range");
        nameColorKey = new NamespacedKey(plugin, "custom_mob_name_color");
        abilityKey = new NamespacedKey(plugin, "custom_mob_ability");
        scaleKey = new NamespacedKey(plugin, "custom_mob_scale");
    }

    public String createAll(Player player) {
        World world = templateWorldManager.createOrLoadWorld();
        if (world == null) return "§c템플릿 월드를 생성하지 못했습니다.";

        Location original = player.getLocation().clone();
        Map<String, Material> themes = new LinkedHashMap<>();
        themes.put("start", Material.QUARTZ_BRICKS);
        themes.put("combat", Material.STONE_BRICKS);
        themes.put("elite", Material.DEEPSLATE_BRICKS);
        themes.put("treasure", Material.GOLD_BLOCK);
        themes.put("shop", Material.SPRUCE_PLANKS);
        themes.put("event", Material.PURPUR_BLOCK);
        themes.put("boss", Material.NETHER_BRICKS);
        themes.put("corridor", Material.POLISHED_ANDESITE);

        int index = 0;
        for (Map.Entry<String, Material> entry : themes.entrySet()) {
            int baseX = index * SPACING;
            int baseY = 65;
            int baseZ = 40;
            clear(world, baseX, baseY, baseZ);
            buildShell(world, baseX, baseY, baseZ, entry.getValue(), entry.getKey());
            decorate(world, baseX, baseY, baseZ, entry.getKey());
            addSampleMob(world, baseX, baseY, baseZ, entry.getKey());

            player.teleport(new Location(world, baseX, baseY, baseZ));
            templateWorldManager.setPosition(player, true);
            player.teleport(new Location(world, baseX + SIZE_X - 1, baseY + SIZE_Y - 1, baseZ + SIZE_Z - 1));
            templateWorldManager.setPosition(player, false);
            roomTemplateManager.save(player, "sample_" + entry.getKey(), entry.getKey());
            index++;
        }

        player.teleport(new Location(world, 7.5, 66, 47.5));
        return "§a8종의 테스트 방을 건축하고 템플릿으로 저장했습니다. §7(sample_start ~ sample_corridor)";
    }

    private void clear(World world, int x, int y, int z) {
        for (int dx = -1; dx <= SIZE_X; dx++) {
            for (int dy = 0; dy <= SIZE_Y + 1; dy++) {
                for (int dz = -1; dz <= SIZE_Z; dz++) {
                    world.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                }
            }
        }
    }

    private void buildShell(World world, int x, int y, int z, Material theme, String type) {
        Material floor = type.equals("treasure") ? Material.CUT_COPPER : theme;
        for (int dx = 0; dx < SIZE_X; dx++) {
            for (int dz = 0; dz < SIZE_Z; dz++) {
                world.getBlockAt(x + dx, y, z + dz).setType(floor, false);
                world.getBlockAt(x + dx, y + SIZE_Y - 1, z + dz).setType(theme, false);
            }
        }
        for (int dy = 1; dy < SIZE_Y - 1; dy++) {
            for (int d = 0; d < SIZE_X; d++) {
                world.getBlockAt(x + d, y + dy, z).setType(theme, false);
                world.getBlockAt(x + d, y + dy, z + SIZE_Z - 1).setType(theme, false);
            }
            for (int d = 0; d < SIZE_Z; d++) {
                world.getBlockAt(x, y + dy, z + d).setType(theme, false);
                world.getBlockAt(x + SIZE_X - 1, y + dy, z + d).setType(theme, false);
            }
        }
        openDoor(world, x, y, z + SIZE_Z / 2);
        openDoor(world, x + SIZE_X - 1, y, z + SIZE_Z / 2);
        for (int dx = 2; dx < SIZE_X - 2; dx += 4) {
            world.getBlockAt(x + dx, y + SIZE_Y - 2, z + 2).setType(Material.SEA_LANTERN, false);
            world.getBlockAt(x + dx, y + SIZE_Y - 2, z + SIZE_Z - 3).setType(Material.SEA_LANTERN, false);
        }
    }

    private void openDoor(World world, int x, int y, int z) {
        for (int dy = 1; dy <= 3; dy++) {
            for (int dz = -1; dz <= 1; dz++) world.getBlockAt(x, y + dy, z + dz).setType(Material.AIR, false);
        }
    }

    private void decorate(World world, int x, int y, int z, String type) {
        int cx = x + SIZE_X / 2;
        int cz = z + SIZE_Z / 2;
        switch (type) {
            case "start" -> {
                world.getBlockAt(cx, y + 1, cz).setType(Material.LODESTONE, false);
                world.getBlockAt(cx, y + 2, cz).setType(Material.BEACON, false);
            }
            case "combat" -> {
                for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-4, 4})
                    world.getBlockAt(cx + dx, y + 1, cz + dz).setType(Material.IRON_BARS, false);
            }
            case "elite" -> {
                world.getBlockAt(cx, y + 1, cz).setType(Material.CRYING_OBSIDIAN, false);
                world.getBlockAt(cx, y + 2, cz).setType(Material.SOUL_LANTERN, false);
            }
            case "treasure" -> {
                world.getBlockAt(cx, y + 1, cz).setType(Material.CHEST, false);
                if (world.getBlockAt(cx, y + 1, cz).getState() instanceof Chest chest) {
                    chest.getInventory().setItem(13, new ItemStack(Material.DIAMOND, 3));
                    chest.getInventory().setItem(11, new ItemStack(Material.GOLDEN_APPLE, 2));
                }
            }
            case "shop" -> {
                world.getBlockAt(cx - 2, y + 1, cz).setType(Material.BARREL, false);
                world.getBlockAt(cx + 2, y + 1, cz).setType(Material.CRAFTING_TABLE, false);
            }
            case "event" -> {
                world.getBlockAt(cx, y + 1, cz).setType(Material.LECTERN, false);
                world.getBlockAt(cx - 3, y + 1, cz).setType(Material.LEVER, false);
                world.getBlockAt(cx + 3, y + 1, cz).setType(Material.REDSTONE_LAMP, false);
            }
            case "boss" -> {
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3)
                        world.getBlockAt(cx + dx, y + 1, cz + dz).setType(Material.BLACKSTONE, false);
            }
            case "corridor" -> {
                for (int dx = 1; dx < SIZE_X - 1; dx++) {
                    world.getBlockAt(x + dx, y + 1, cz - 2).setType(Material.STONE_BRICK_WALL, false);
                    world.getBlockAt(x + dx, y + 1, cz + 2).setType(Material.STONE_BRICK_WALL, false);
                }
            }
        }
    }

    private void addSampleMob(World world, int x, int y, int z, String type) {
        int cx = x + SIZE_X / 2;
        int cz = z + SIZE_Z / 2;
        switch (type) {
            case "combat" -> makePreview((LivingEntity) world.spawnEntity(new Location(world, cx, y + 1, cz), EntityType.ZOMBIE),
                    "§c훈련용 좀비", "NORMAL", 30, 5, 2.2);
            case "elite" -> makePreview((LivingEntity) world.spawnEntity(new Location(world, cx, y + 1, cz), EntityType.HUSK),
                    "§6정예 사막 전사", "NORMAL", 80, 10, 3.0);
            case "boss" -> makePreview((LivingEntity) world.spawnEntity(new Location(world, cx, y + 1, cz), EntityType.RAVAGER),
                    "§4시험용 파괴수", "BOSS", 250, 18, 3.5);
            case "shop" -> makePreview((LivingEntity) world.spawnEntity(new Location(world, cx, y + 1, cz), EntityType.VILLAGER),
                    "§e시험 상인", "TRADER", 20, 0, 1.0);
            case "treasure" -> makePreview((LivingEntity) world.spawnEntity(new Location(world, cx + 3, y + 1, cz), EntityType.ALLAY),
                    "§b보물 안내자", "REWARD", 20, 0, 1.0);
            default -> { }
        }
    }

    private void makePreview(LivingEntity entity, String name, String role, double health, double damage, double range) {
        entity.setCustomName(name);
        entity.setCustomNameVisible(true);
        entity.setAI(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(true);
        entity.setRemoveWhenFarAway(false);
        entity.getPersistentDataContainer().set(previewKey, PersistentDataType.BYTE, (byte) 1);
        entity.getPersistentDataContainer().set(healthKey, PersistentDataType.DOUBLE, health);
        entity.getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, damage);
        entity.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role);
        entity.getPersistentDataContainer().set(rangeKey, PersistentDataType.DOUBLE, range);
        entity.getPersistentDataContainer().set(nameColorKey, PersistentDataType.STRING, "RED");
        entity.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, "NONE");
        entity.getPersistentDataContainer().set(scaleKey, PersistentDataType.DOUBLE, 1.0);
        setAttribute(entity, Attribute.MAX_HEALTH, health);
        setAttribute(entity, Attribute.ATTACK_DAMAGE, damage);
        setAttribute(entity, Attribute.SCALE, 1.0);
        entity.setHealth(health);
    }

    private void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }
}
