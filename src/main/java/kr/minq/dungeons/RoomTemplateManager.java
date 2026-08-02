package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class RoomTemplateManager {
    private static final int FORMAT_VERSION = 1;
    private static final Pattern SAFE_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,40}");
    private static final Set<String> TYPES = Set.of("start", "combat", "elite", "treasure", "shop", "event", "boss", "corridor");

    private final JavaPlugin plugin;
    private final TemplateWorldManager templateWorldManager;
    private final File directory;
    private final List<PdcField> pdcFields = new ArrayList<>();

    public RoomTemplateManager(JavaPlugin plugin, TemplateWorldManager templateWorldManager) {
        this.plugin = plugin;
        this.templateWorldManager = templateWorldManager;
        this.directory = new File(plugin.getDataFolder(), "templates");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("템플릿 폴더를 생성하지 못했습니다: " + directory.getAbsolutePath());
        }
        registerFields();
    }

    public Set<String> supportedTypes() {
        return TYPES;
    }

    public List<String> listNames() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".dtpl"));
        if (files == null) return List.of();
        List<String> names = new ArrayList<>();
        for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 5));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public String save(Player player, String rawName, String rawType) {
        if (!player.getWorld().getName().equals(TemplateWorldManager.WORLD_NAME)) {
            return "§c템플릿 월드에서만 저장할 수 있습니다.";
        }
        String name = normalizeName(rawName);
        String type = rawType.toLowerCase(Locale.ROOT);
        if (name == null) return "§c이름은 영문·숫자·_·-만 사용하며 1~40자로 입력하세요.";
        if (!TYPES.contains(type)) return "§c지원하지 않는 방 타입입니다: §f" + String.join(", ", TYPES);

        TemplateWorldManager.Selection selection = templateWorldManager.getSelection(player);
        if (selection == null || !selection.isComplete()) return "§c먼저 선택 도끼로 두 지점을 지정하세요.";
        Bounds bounds = Bounds.of(selection.getPos1(), selection.getPos2());
        long volume = (long) bounds.sizeX() * bounds.sizeY() * bounds.sizeZ();
        if (volume > 1_000_000L) return "§c선택 영역이 너무 큽니다. 최대 1,000,000블록입니다.";

        File file = file(name);
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            out.writeInt(FORMAT_VERSION);
            writeString(out, name);
            writeString(out, type);
            out.writeInt(bounds.sizeX());
            out.writeInt(bounds.sizeY());
            out.writeInt(bounds.sizeZ());
            out.writeInt((int) volume);

            World world = player.getWorld();
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                        Block block = world.getBlockAt(x, y, z);
                        out.writeInt(x - bounds.minX());
                        out.writeInt(y - bounds.minY());
                        out.writeInt(z - bounds.minZ());
                        writeString(out, block.getBlockData().getAsString());
                        BlockState state = block.getState();
                        if (state instanceof Container container) {
                            out.writeBoolean(true);
                            writeItems(out, container.getInventory().getContents());
                        } else {
                            out.writeBoolean(false);
                        }
                    }
                }
            }

            List<LivingEntity> mobs = world.getLivingEntities().stream()
                    .filter(entity -> entity.getLocation().getX() >= bounds.minX() && entity.getLocation().getX() < bounds.maxX() + 1.0)
                    .filter(entity -> entity.getLocation().getY() >= bounds.minY() && entity.getLocation().getY() < bounds.maxY() + 1.0)
                    .filter(entity -> entity.getLocation().getZ() >= bounds.minZ() && entity.getLocation().getZ() < bounds.maxZ() + 1.0)
                    .filter(this::isPreview)
                    .sorted(Comparator.comparing(entity -> entity.getUniqueId().toString()))
                    .toList();
            out.writeInt(mobs.size());
            for (LivingEntity mob : mobs) writeMob(out, mob, bounds);
            return "§a템플릿 §f" + name + " §a을(를) 저장했습니다. §7[" + type + ", " + volume + "블록, 몹 " + mobs.size() + "마리]";
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("템플릿 저장 실패: " + exception.getMessage());
            return "§c템플릿 저장 중 오류가 발생했습니다.";
        }
    }

    public String paste(Player player, String rawName) {
        String name = normalizeName(rawName);
        if (name == null || !file(name).isFile()) return "§c템플릿을 찾을 수 없습니다.";
        Location origin = player.getLocation().getBlock().getLocation();
        World world = origin.getWorld();
        if (world == null) return "§c월드를 찾을 수 없습니다.";

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file(name))))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) return "§c지원하지 않는 템플릿 파일 버전입니다.";
            String storedName = readString(in);
            String type = readString(in);
            int sizeX = in.readInt();
            int sizeY = in.readInt();
            int sizeZ = in.readInt();
            int blockCount = in.readInt();

            List<PendingContainer> containers = new ArrayList<>();
            for (int i = 0; i < blockCount; i++) {
                int rx = in.readInt();
                int ry = in.readInt();
                int rz = in.readInt();
                String blockDataText = readString(in);
                boolean hasInventory = in.readBoolean();
                ItemStack[] contents = hasInventory ? readItems(in) : null;
                Block target = world.getBlockAt(origin.getBlockX() + rx, origin.getBlockY() + ry, origin.getBlockZ() + rz);
                BlockData data = Bukkit.createBlockData(blockDataText);
                target.setBlockData(data, false);
                if (contents != null) containers.add(new PendingContainer(target.getLocation(), contents));
            }
            for (PendingContainer pending : containers) {
                BlockState state = pending.location().getBlock().getState();
                if (state instanceof Container container) {
                    container.getInventory().setContents(fitContents(pending.contents(), container.getInventory().getSize()));
                    container.update(true, false);
                }
            }

            int mobCount = in.readInt();
            for (int i = 0; i < mobCount; i++) readAndSpawnMob(in, world, origin);
            return "§a템플릿 §f" + storedName + " §a을(를) 현재 위치에 붙여넣었습니다. §7[" + type + ", " + sizeX + "×" + sizeY + "×" + sizeZ + ", 몹 " + mobCount + "마리]";
        } catch (IOException | IllegalArgumentException exception) {
            plugin.getLogger().severe("템플릿 붙여넣기 실패: " + exception.getMessage());
            return "§c템플릿을 불러오는 중 오류가 발생했습니다.";
        }
    }

    public String delete(String rawName) {
        String name = normalizeName(rawName);
        if (name == null) return "§c올바르지 않은 템플릿 이름입니다.";
        File file = file(name);
        if (!file.isFile()) return "§c템플릿을 찾을 수 없습니다.";
        return file.delete() ? "§a템플릿 §f" + name + "§a을(를) 삭제했습니다." : "§c템플릿 파일을 삭제하지 못했습니다.";
    }

    public String info(String rawName) {
        String name = normalizeName(rawName);
        if (name == null || !file(name).isFile()) return "§c템플릿을 찾을 수 없습니다.";
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file(name))))) {
            int version = in.readInt();
            String storedName = readString(in);
            String type = readString(in);
            int x = in.readInt(), y = in.readInt(), z = in.readInt(), blocks = in.readInt();
            for (int i = 0; i < blocks; i++) {
                in.readInt(); in.readInt(); in.readInt(); readString(in);
                if (in.readBoolean()) readItems(in);
            }
            int mobs = in.readInt();
            return "§6§l[템플릿] §f" + storedName + " §7| 타입 §e" + type + " §7| 크기 §a" + x + "×" + y + "×" + z + " §7| 블록 §f" + blocks + " §7| 몹 §f" + mobs + " §8(v" + version + ")";
        } catch (IOException exception) {
            return "§c템플릿 정보를 읽지 못했습니다.";
        }
    }

    private void writeMob(DataOutputStream out, LivingEntity mob, Bounds bounds) throws IOException {
        writeString(out, mob.getType().name());
        Location location = mob.getLocation();
        out.writeDouble(location.getX() - bounds.minX());
        out.writeDouble(location.getY() - bounds.minY());
        out.writeDouble(location.getZ() - bounds.minZ());
        out.writeFloat(location.getYaw());
        out.writeFloat(location.getPitch());
        writeString(out, mob.getCustomName() == null ? "" : mob.getCustomName());
        out.writeInt(pdcFields.size());
        PersistentDataContainer data = mob.getPersistentDataContainer();
        for (PdcField field : pdcFields) field.write(out, data);
        EntityEquipment equipment = mob.getEquipment();
        writeItems(out, equipment == null ? new ItemStack[6] : new ItemStack[]{
                equipment.getHelmet(), equipment.getChestplate(), equipment.getLeggings(), equipment.getBoots(),
                equipment.getItemInMainHand(), equipment.getItemInOffHand()
        });
    }

    private void readAndSpawnMob(DataInputStream in, World world, Location origin) throws IOException {
        String typeName = readString(in);
        double x = in.readDouble(), y = in.readDouble(), z = in.readDouble();
        float yaw = in.readFloat(), pitch = in.readFloat();
        String customName = readString(in);
        EntityType type = EntityType.valueOf(typeName);
        Location location = origin.clone().add(x, y, z);
        location.setYaw(yaw); location.setPitch(pitch);
        Entity raw = world.spawnEntity(location, type);
        if (!(raw instanceof LivingEntity mob)) { raw.remove(); skipMobRemainder(in); return; }
        int fieldCount = in.readInt();
        PersistentDataContainer data = mob.getPersistentDataContainer();
        for (int i = 0; i < fieldCount; i++) {
            String key = readString(in);
            byte typeId = in.readByte();
            boolean present = in.readBoolean();
            PdcField field = fieldByKey(key, typeId);
            if (field == null) skipFieldValue(in, typeId, present);
            else field.readValue(in, data, present);
        }
        ItemStack[] equipmentItems = readItems(in);
        applyPreviewState(mob, customName, equipmentItems);
    }

    private void skipMobRemainder(DataInputStream in) throws IOException {
        int fields = in.readInt();
        for (int i = 0; i < fields; i++) {
            readString(in); byte type = in.readByte(); boolean present = in.readBoolean(); skipFieldValue(in, type, present);
        }
        readItems(in);
    }

    private void applyPreviewState(LivingEntity mob, String customName, ItemStack[] items) {
        if (!customName.isEmpty()) { mob.setCustomName(customName); mob.setCustomNameVisible(true); }
        mob.setAI(false);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        mob.setPersistent(true);
        mob.setCanPickupItems(false);
        mob.setRemoveWhenFarAway(false);
        PersistentDataContainer data = mob.getPersistentDataContainer();
        setAttribute(mob, Attribute.MAX_HEALTH, data.getOrDefault(key("custom_mob_health"), PersistentDataType.DOUBLE, 20.0));
        setAttribute(mob, Attribute.ATTACK_DAMAGE, data.getOrDefault(key("custom_mob_damage"), PersistentDataType.DOUBLE, 4.0));
        setAttribute(mob, Attribute.SCALE, data.getOrDefault(key("custom_mob_scale"), PersistentDataType.DOUBLE, 1.0));
        double max = attributeValue(mob, Attribute.MAX_HEALTH, 20.0);
        mob.setHealth(Math.max(1.0, Math.min(max, max)));
        EntityEquipment equipment = mob.getEquipment();
        if (equipment != null && items.length >= 6) {
            equipment.setHelmet(items[0]); equipment.setChestplate(items[1]); equipment.setLeggings(items[2]); equipment.setBoots(items[3]);
            equipment.setItemInMainHand(items[4]); equipment.setItemInOffHand(items[5]);
            equipment.setHelmetDropChance(0); equipment.setChestplateDropChance(0); equipment.setLeggingsDropChance(0); equipment.setBootsDropChance(0);
            equipment.setItemInMainHandDropChance(0); equipment.setItemInOffHandDropChance(0);
        }
    }

    private void registerFields() {
        addByte("custom_mob_preview"); addDouble("custom_mob_health"); addDouble("custom_mob_damage"); addString("custom_mob_role");
        addDouble("custom_mob_range"); addString("custom_mob_name_color"); addString("custom_mob_ability"); addDouble("custom_mob_scale"); addFloat("custom_mob_yaw");
        addBytes("custom_mob_equipment_data"); addBytes("custom_mob_drop_data");
        addBytes("role_trader_data"); addBytes("role_reward_data"); addByte("role_reward_once");
        addByte("role_boss_bar"); addString("role_boss_color"); addString("role_ally_mode"); addDouble("role_ally_power"); addDouble("role_ally_range");
    }

    private void addByte(String key) { pdcFields.add(PdcField.byteField(key(key))); }
    private void addDouble(String key) { pdcFields.add(PdcField.doubleField(key(key))); }
    private void addFloat(String key) { pdcFields.add(PdcField.floatField(key(key))); }
    private void addString(String key) { pdcFields.add(PdcField.stringField(key(key))); }
    private void addBytes(String key) { pdcFields.add(PdcField.bytesField(key(key))); }
    private NamespacedKey key(String value) { return new NamespacedKey(plugin, value); }
    private PdcField fieldByKey(String key, byte type) { return pdcFields.stream().filter(field -> field.key().getKey().equals(key) && field.typeId() == type).findFirst().orElse(null); }

    private boolean isPreview(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(key("custom_mob_preview"), PersistentDataType.BYTE, (byte) 0) == (byte) 1;
    }

    private File file(String name) { return new File(directory, name + ".dtpl"); }
    private String normalizeName(String value) { return value != null && SAFE_NAME.matcher(value).matches() ? value.toLowerCase(Locale.ROOT) : null; }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length); out.write(bytes);
    }
    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > 16_777_216) throw new IOException("잘못된 문자열 길이");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
    private static void writeItems(DataOutputStream out, ItemStack[] items) throws IOException {
        out.writeInt(items.length);
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) out.writeInt(0);
            else { byte[] bytes = item.serializeAsBytes(); out.writeInt(bytes.length); out.write(bytes); }
        }
    }
    private static ItemStack[] readItems(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 1024) throw new IOException("잘못된 아이템 수");
        ItemStack[] items = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            int length = in.readInt();
            if (length > 0) items[i] = ItemStack.deserializeBytes(in.readNBytes(length));
        }
        return items;
    }
    private static ItemStack[] fitContents(ItemStack[] source, int size) {
        ItemStack[] result = new ItemStack[size];
        System.arraycopy(source, 0, result, 0, Math.min(source.length, size));
        return result;
    }
    private static void setAttribute(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(Math.max(attribute == Attribute.SCALE ? 0.0625 : 0.0, value));
    }
    private static double attributeValue(LivingEntity entity, Attribute attribute, double fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }
    private static void skipFieldValue(DataInputStream in, byte type, boolean present) throws IOException {
        if (!present) return;
        switch (type) {
            case 1 -> in.readByte();
            case 2 -> in.readDouble();
            case 3 -> in.readFloat();
            case 4 -> readString(in);
            case 5 -> { int length = in.readInt(); in.skipNBytes(length); }
            default -> throw new EOFException("알 수 없는 PDC 형식");
        }
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static Bounds of(Location one, Location two) {
            return new Bounds(Math.min(one.getBlockX(), two.getBlockX()), Math.min(one.getBlockY(), two.getBlockY()), Math.min(one.getBlockZ(), two.getBlockZ()),
                    Math.max(one.getBlockX(), two.getBlockX()), Math.max(one.getBlockY(), two.getBlockY()), Math.max(one.getBlockZ(), two.getBlockZ()));
        }
        int sizeX() { return maxX - minX + 1; } int sizeY() { return maxY - minY + 1; } int sizeZ() { return maxZ - minZ + 1; }
    }
    private record PendingContainer(Location location, ItemStack[] contents) { }

    private record PdcField(NamespacedKey key, byte typeId, PersistentDataType<?, ?> type) {
        static PdcField byteField(NamespacedKey key) { return new PdcField(key, (byte) 1, PersistentDataType.BYTE); }
        static PdcField doubleField(NamespacedKey key) { return new PdcField(key, (byte) 2, PersistentDataType.DOUBLE); }
        static PdcField floatField(NamespacedKey key) { return new PdcField(key, (byte) 3, PersistentDataType.FLOAT); }
        static PdcField stringField(NamespacedKey key) { return new PdcField(key, (byte) 4, PersistentDataType.STRING); }
        static PdcField bytesField(NamespacedKey key) { return new PdcField(key, (byte) 5, PersistentDataType.BYTE_ARRAY); }

        @SuppressWarnings("unchecked")
        void write(DataOutputStream out, PersistentDataContainer data) throws IOException {
            writeString(out, key.getKey()); out.writeByte(typeId);
            switch (typeId) {
                case 1 -> { Byte value = data.get(key, PersistentDataType.BYTE); out.writeBoolean(value != null); if (value != null) out.writeByte(value); }
                case 2 -> { Double value = data.get(key, PersistentDataType.DOUBLE); out.writeBoolean(value != null); if (value != null) out.writeDouble(value); }
                case 3 -> { Float value = data.get(key, PersistentDataType.FLOAT); out.writeBoolean(value != null); if (value != null) out.writeFloat(value); }
                case 4 -> { String value = data.get(key, PersistentDataType.STRING); out.writeBoolean(value != null); if (value != null) writeString(out, value); }
                case 5 -> { byte[] value = data.get(key, PersistentDataType.BYTE_ARRAY); out.writeBoolean(value != null); if (value != null) { out.writeInt(value.length); out.write(value); } }
                default -> throw new IOException("알 수 없는 PDC 형식");
            }
        }

        void readValue(DataInputStream in, PersistentDataContainer data, boolean present) throws IOException {
            if (!present) return;
            switch (typeId) {
                case 1 -> data.set(key, PersistentDataType.BYTE, in.readByte());
                case 2 -> data.set(key, PersistentDataType.DOUBLE, in.readDouble());
                case 3 -> data.set(key, PersistentDataType.FLOAT, in.readFloat());
                case 4 -> data.set(key, PersistentDataType.STRING, readString(in));
                case 5 -> { int length = in.readInt(); data.set(key, PersistentDataType.BYTE_ARRAY, in.readNBytes(length)); }
                default -> throw new IOException("알 수 없는 PDC 형식");
            }
        }
    }
}
