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
    private RoleSettingsManager roleSettingsManager;
    private MobTestToolManager mobTestToolManager;

    @Override
    public void onEnable() {
        partyManager = new PartyManager();
        dungeonManager = new TestDungeonManager(this);
        templateWorldManager = new TemplateWorldManager(this);
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
        getLogger().info("Dungeons role editor enabled for Paper 26.2");
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
            case "create" -> player.sendMessage(templateWorldManager.createOrLoadWorld() == null ? "§c템플릿 월드 생성 실패" : "§a템플릿 월드를 준비했습니다.");
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
        sender.sendMessage("§6§lDungeons §7- Paper 26.2");
        sender.sendMessage("§e전용 막대기 §7- 몹 기본·역할·장비·드롭 설정");
        sender.sendMessage("§e거래인 §7- 우클릭 시 주민 거래 UI 사용");
        sender.sendMessage("§e보상지급인 §7- 설정된 아이템 지급");
        sender.sendMessage("§e/dungeon template wand §7- 편집 도구 일괄 지급");
    }
}
