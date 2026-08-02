package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class DungeonsPlugin extends JavaPlugin {
    private PartyManager partyManager;
    private TestDungeonManager dungeonManager;
    private TemplateWorldManager templateWorldManager;
    private CustomMobEditorManager customMobEditorManager;
    private EquipmentDropEditorManager equipmentDropEditorManager;
    private MobTestToolManager mobTestToolManager;

    @Override
    public void onEnable() {
        partyManager = new PartyManager();
        dungeonManager = new TestDungeonManager(this);
        templateWorldManager = new TemplateWorldManager(this);
        equipmentDropEditorManager = new EquipmentDropEditorManager(this);
        customMobEditorManager = new CustomMobEditorManager(this, equipmentDropEditorManager);
        mobTestToolManager = new MobTestToolManager(this);

        Bukkit.getPluginManager().registerEvents(dungeonManager, this);
        Bukkit.getPluginManager().registerEvents(templateWorldManager, this);
        Bukkit.getPluginManager().registerEvents(equipmentDropEditorManager, this);
        Bukkit.getPluginManager().registerEvents(customMobEditorManager, this);
        Bukkit.getPluginManager().registerEvents(mobTestToolManager, this);
        getLogger().info("Dungeons 0.6.0 enabled for Paper 26.2");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null && dungeonManager.isRunning()) dungeonManager.stop(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "party" -> handleParty(sender, args);
            case "templateworld" -> handleTemplateWorld(sender, args);
            case "template" -> handleTemplate(sender, args);
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
            if (sender.hasPermission("dungeons.admin")) roots.addAll(List.of("stop", "templateworld", "template"));
            return filterSuggestions(roots, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "party" -> filterSuggestions(List.of("create", "invite", "accept", "leave", "list"), args[1]);
                case "templateworld" -> sender.hasPermission("dungeons.admin") ? filterSuggestions(List.of("create", "enter", "leave"), args[1]) : List.of();
                case "template" -> sender.hasPermission("dungeons.admin") ? filterSuggestions(List.of("wand", "pos1", "pos2", "info", "clear"), args[1]) : List.of();
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("party") && args[1].equalsIgnoreCase("invite")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> !(sender instanceof Player player) || !name.equalsIgnoreCase(player.getName())).toList();
            return filterSuggestions(names, args[2]);
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
            case "create" -> player.sendMessage(templateWorldManager.createOrLoadWorld() == null ? "§c템플릿 월드 생성에 실패했습니다." : "§6§l[Dungeons] §a템플릿 월드를 생성하거나 불러왔습니다.");
            case "enter" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.enter(player));
            case "leave" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.leave(player));
            default -> player.sendMessage("§c알 수 없는 templateworld 명령어입니다.");
        }
    }

    private void handleTemplate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        if (!player.hasPermission("dungeons.admin")) { player.sendMessage("§c관리자 권한이 필요합니다."); return; }
        if (args.length < 2) { player.sendMessage("§e/dungeon template <wand|pos1|pos2|info|clear>"); return; }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "wand" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.giveWand(player));
            case "pos1" -> player.sendMessage(templateWorldManager.setPosition(player, true));
            case "pos2" -> player.sendMessage(templateWorldManager.setPosition(player, false));
            case "info" -> player.sendMessage(templateWorldManager.info(player));
            case "clear", "clearselection" -> player.sendMessage("§6§l[Dungeons] §f" + templateWorldManager.clearSelection(player));
            default -> player.sendMessage("§c알 수 없는 template 명령어입니다.");
        }
    }

    private void handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        if (args.length < 2) {
            player.sendMessage("§6§l[Dungeons] §f파티: " + partyManager.describe(player));
            player.sendMessage("§e/dungeon party <create|invite|accept|leave|list>");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> player.sendMessage(partyManager.createParty(player) ? "§6§l[Dungeons] §a파티를 생성했습니다." : "§6§l[Dungeons] §c이미 파티에 속해 있습니다.");
            case "invite" -> {
                if (args.length < 3) { player.sendMessage("§c사용법: /dungeon party invite <닉네임>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { player.sendMessage("§c접속 중인 플레이어를 찾을 수 없습니다."); return; }
                player.sendMessage("§6§l[Dungeons] §f" + partyManager.invite(player, target));
            }
            case "accept" -> player.sendMessage("§6§l[Dungeons] §f" + partyManager.accept(player));
            case "leave" -> {
                if (dungeonManager.isRunning()) { player.sendMessage("§c던전 진행 중에는 파티를 나갈 수 없습니다."); return; }
                player.sendMessage("§6§l[Dungeons] §f" + partyManager.leave(player));
            }
            case "list" -> player.sendMessage("§6§l[Dungeons] §f파티원: " + partyManager.describe(player));
            default -> player.sendMessage("§c알 수 없는 파티 명령어입니다.");
        }
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 던전을 시작할 수 있습니다."); return; }
        if (dungeonManager.isRunning()) { player.sendMessage("§c이미 던전이 진행 중입니다."); return; }
        PartyManager.Party party = partyManager.getParty(player);
        if (party == null) { partyManager.createParty(player); party = partyManager.getParty(player); }
        if (!partyManager.isLeader(player)) { player.sendMessage("§c파티장만 던전을 시작할 수 있습니다."); return; }
        player.sendMessage("§6§l[Dungeons] §f" + dungeonManager.start(partyManager.getOnlineMembers(party)));
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("dungeons.admin")) { sender.sendMessage("§c관리자만 강제로 종료할 수 있습니다."); return; }
        sender.sendMessage("§6§l[Dungeons] §f" + dungeonManager.stop(false));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§6§l[Dungeons] §f현재 상태: " + (dungeonManager.isRunning() ? "§a진행 중" : "§7대기 중"));
        if (sender instanceof Player player) sender.sendMessage("§6§l[Dungeons] §f파티: " + partyManager.describe(player));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lDungeons 0.6.0 §7- Paper 26.2");
        sender.sendMessage("§e생성 알 §7- 템플릿 월드에서 프리즈된 프리뷰 몹 생성");
        sender.sendMessage("§e전용 막대기 §7- 몹 능력·장비·드롭 설정 GUI");
        sender.sendMessage("§e전용 블레이즈 막대기 §7- 모든 설정을 포함한 몹 복제");
        sender.sendMessage("§e전용 메아리 조각 §7- 몹 AI 테스트 시작/정지");
        sender.sendMessage("§e/dungeon template wand §7- 템플릿 편집 도구 일괄 지급");
        sender.sendMessage("§e/dungeon party §7- 파티 명령어");
        sender.sendMessage("§e/dungeon start §7- 테스트 던전 시작");
    }
}
