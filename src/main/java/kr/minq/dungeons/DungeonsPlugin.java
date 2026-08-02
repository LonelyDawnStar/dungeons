package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class DungeonsPlugin extends JavaPlugin {
    private PartyManager partyManager;
    private TestDungeonManager dungeonManager;
    private TemplateWorldManager templateWorldManager;
    private RoomTemplateManager roomTemplateManager;
    private DungeonGenerationManager generationManager;
    private SampleRoomGenerator sampleRoomGenerator;
    private CustomMobEditorManager customMobEditorManager;
    private EquipmentDropEditorManager equipmentDropEditorManager;
    private RoleSettingsManager roleSettingsManager;
    private MobTestToolManager mobTestToolManager;

    @Override
    public void onEnable() {
        partyManager = new PartyManager();
        dungeonManager = new TestDungeonManager(this);
        templateWorldManager = new TemplateWorldManager(this);
        roomTemplateManager = new RoomTemplateManager(this, templateWorldManager);
        generationManager = new DungeonGenerationManager(this, roomTemplateManager);
        sampleRoomGenerator = new SampleRoomGenerator(this, templateWorldManager, roomTemplateManager);
        equipmentDropEditorManager = new EquipmentDropEditorManager(this);
        roleSettingsManager = new RoleSettingsManager(this);
        customMobEditorManager = new CustomMobEditorManager(this, equipmentDropEditorManager, roleSettingsManager);
        mobTestToolManager = new MobTestToolManager(this);

        Bukkit.getPluginManager().registerEvents(dungeonManager, this);
        Bukkit.getPluginManager().registerEvents(templateWorldManager, this);
        Bukkit.getPluginManager().registerEvents(equipmentDropEditorManager, this);
        Bukkit.getPluginManager().registerEvents(customMobEditorManager, this);
        Bukkit.getPluginManager().registerEvents(roleSettingsManager, this);
        Bukkit.getPluginManager().registerEvents(mobTestToolManager, this);
        getLogger().info("Dungeons 0.8.0 enabled for Paper 26.2");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null && dungeonManager.isRunning()) dungeonManager.stop(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "party" -> handleParty(sender, args);
            case "templateworld" -> handleTemplateWorld(sender, args);
            case "template" -> handleTemplate(sender, args);
            case "generate" -> handleGenerate(sender, args);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "status" -> handleStatus(sender);
            default -> sender.sendMessage("§c알 수 없는 명령어입니다. /dungeon help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) return List.of();
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of("help", "party", "start", "status"));
            if (sender.hasPermission("dungeons.admin")) roots.addAll(List.of("stop", "templateworld", "template", "generate"));
            return filterSuggestions(roots, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "party" -> filterSuggestions(List.of("create", "invite", "accept", "leave", "list"), args[1]);
                case "templateworld" -> sender.hasPermission("dungeons.admin")
                        ? filterSuggestions(List.of("create", "enter", "leave"), args[1]) : List.of();
                case "template" -> sender.hasPermission("dungeons.admin")
                        ? filterSuggestions(List.of("wand", "pos1", "pos2", "info", "clear", "save", "list", "delete", "paste", "samples"), args[1]) : List.of();
                case "generate" -> sender.hasPermission("dungeons.admin")
                        ? filterSuggestions(List.of("random", "custom"), args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("party") && args[1].equalsIgnoreCase("invite")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> !(sender instanceof Player player) || !name.equalsIgnoreCase(player.getName())).toList();
            return filterSuggestions(names, args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("template")) {
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "delete", "paste", "info" -> filterSuggestions(roomTemplateManager.listNames(), args[2]);
                default -> List.of();
            };
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("template") && args[1].equalsIgnoreCase("save")) {
            return filterSuggestions(roomTemplateManager.supportedTypes(), args[3]);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("generate") && args[1].equalsIgnoreCase("custom")) {
            return filterSuggestions(generationManager.templateNames(), args[args.length - 1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("generate") && args[1].equalsIgnoreCase("random")) {
            return filterSuggestions(List.of("5", "7", "10"), args[2]);
        }
        return List.of();
    }

    private List<String> filterSuggestions(Collection<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void handleTemplateWorld(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        if (!player.hasPermission("dungeons.admin")) { player.sendMessage("§c관리자 권한이 필요합니다."); return; }
        if (args.length < 2) { player.sendMessage("§e/dungeon templateworld <create|enter|leave>"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> player.sendMessage(templateWorldManager.createOrLoadWorld() == null ? "§c템플릿 월드 생성 실패" : "§a템플릿 월드를 준비했습니다.");
            case "enter" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.enter(player));
            case "leave" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.leave(player));
            default -> player.sendMessage("§c알 수 없는 templateworld 명령어입니다.");
        }
    }

    private void handleTemplate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        if (!player.hasPermission("dungeons.admin")) { player.sendMessage("§c관리자 권한이 필요합니다."); return; }
        if (args.length < 2) {
            player.sendMessage("§e/dungeon template <wand|pos1|pos2|info|clear|save|list|delete|paste|samples>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "wand" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.giveWand(player));
            case "pos1" -> player.sendMessage(templateWorldManager.setPosition(player, true));
            case "pos2" -> player.sendMessage(templateWorldManager.setPosition(player, false));
            case "clear", "clearselection" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.clearSelection(player));
            case "samples" -> player.sendMessage("§6§l[Dungeons] §f" + sampleRoomGenerator.createAll(player));
            case "save" -> {
                if (args.length < 4) { player.sendMessage("§c사용법: /dungeon template save <이름> <타입>"); return; }
                player.sendMessage("§6§l[Dungeons] §f" + roomTemplateManager.save(player, args[2], args[3]));
            }
            case "list" -> {
                List<String> names = roomTemplateManager.listNames();
                player.sendMessage(names.isEmpty() ? "§7저장된 템플릿이 없습니다." : "§6§l[템플릿 목록] §f" + String.join("§7, §f", names));
            }
            case "delete" -> {
                if (args.length < 3) { player.sendMessage("§c사용법: /dungeon template delete <이름>"); return; }
                player.sendMessage("§6§l[Dungeons] §f" + roomTemplateManager.delete(args[2]));
            }
            case "paste", "load" -> {
                if (args.length < 3) { player.sendMessage("§c사용법: /dungeon template paste <이름>"); return; }
                player.sendMessage("§6§l[Dungeons] §f" + roomTemplateManager.paste(player, args[2]));
            }
            case "info" -> {
                if (args.length >= 3) player.sendMessage(roomTemplateManager.info(args[2]));
                else player.sendMessage(templateWorldManager.info(player));
            }
            default -> player.sendMessage("§c알 수 없는 template 명령어입니다.");
        }
    }

    private void handleGenerate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 던전을 생성할 수 있습니다."); return; }
        if (!player.hasPermission("dungeons.admin")) { player.sendMessage("§c관리자 권한이 필요합니다."); return; }
        if (args.length < 2) {
            player.sendMessage("§e/dungeon generate random [방 개수]");
            player.sendMessage("§e/dungeon generate custom <템플릿1> <템플릿2> ...");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "random" -> {
                int rooms = 7;
                if (args.length >= 3) {
                    try { rooms = Integer.parseInt(args[2]); }
                    catch (NumberFormatException exception) { player.sendMessage("§c방 개수는 숫자로 입력하세요."); return; }
                }
                player.sendMessage("§6§l[Dungeons] §f" + generationManager.generateRandom(player, rooms));
            }
            case "custom", "manual" -> {
                if (args.length < 3) { player.sendMessage("§c템플릿 이름을 순서대로 입력하세요."); return; }
                player.sendMessage("§6§l[Dungeons] §f" + generationManager.generateCustom(player, Arrays.asList(args).subList(2, args.length)));
            }
            default -> player.sendMessage("§c사용법: /dungeon generate <random|custom>");
        }
    }

    private void handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        if (args.length < 2) { player.sendMessage("§6§l[Dungeons] §f파티: " + partyManager.describe(player)); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> player.sendMessage(partyManager.createParty(player) ? "§a파티를 생성했습니다." : "§c이미 파티에 속해 있습니다.");
            case "invite" -> {
                if (args.length < 3) { player.sendMessage("§c사용법: /dungeon party invite <닉네임>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                player.sendMessage(target == null ? "§c플레이어를 찾을 수 없습니다." : partyManager.invite(player, target));
            }
            case "accept" -> player.sendMessage(partyManager.accept(player));
            case "leave" -> player.sendMessage(dungeonManager.isRunning() ? "§c던전 중에는 나갈 수 없습니다." : partyManager.leave(player));
            case "list" -> player.sendMessage("§f파티원: " + partyManager.describe(player));
            default -> player.sendMessage("§c알 수 없는 파티 명령어입니다.");
        }
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        if (dungeonManager.isRunning()) { player.sendMessage("§c이미 던전이 진행 중입니다."); return; }
        PartyManager.Party party = partyManager.getParty(player);
        if (party == null) { partyManager.createParty(player); party = partyManager.getParty(player); }
        if (!partyManager.isLeader(player)) { player.sendMessage("§c파티장만 시작할 수 있습니다."); return; }
        player.sendMessage(dungeonManager.start(partyManager.getOnlineMembers(party)));
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("dungeons.admin")) { sender.sendMessage("§c관리자 권한이 필요합니다."); return; }
        sender.sendMessage(dungeonManager.stop(false));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§6§l[Dungeons] §f현재 상태: " + (dungeonManager.isRunning() ? "§a진행 중" : "§7대기 중"));
        if (sender instanceof Player player) sender.sendMessage("§f파티: " + partyManager.describe(player));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lDungeons 0.8.0 §7- Paper 26.2");
        sender.sendMessage("§e/dungeon template samples §7- 8종 기본 테스트 방 생성·저장");
        sender.sendMessage("§e/dungeon generate random [방 수] §7- 타입 기반 랜덤 던전 생성");
        sender.sendMessage("§e/dungeon generate custom <이름...> §7- 입력한 순서대로 던전 생성");
        sender.sendMessage("§e/dungeon template save <이름> <타입> §7- 선택 영역 저장");
        sender.sendMessage("§e/dungeon template list §7- 저장된 방 목록");
        sender.sendMessage("§e/dungeon template wand §7- 편집 도구 일괄 지급");
    }
}
