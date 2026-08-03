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
    private RoomTemplateManager roomTemplateManager;
    private CompleteDungeonManager completeDungeonManager;
    private SampleRoomGenerator sampleRoomGenerator;
    private CustomMobEditorManager customMobEditorManager;
    private EquipmentDropEditorManager equipmentDropEditorManager;
    private RoleSettingsManager roleSettingsManager;
    private MobTestToolManager mobTestToolManager;
    private MobAiEditorManager mobAiEditorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        partyManager = new PartyManager();
        dungeonManager = new TestDungeonManager(this);
        templateWorldManager = new TemplateWorldManager(this);
        roomTemplateManager = new RoomTemplateManager(this, templateWorldManager);
        completeDungeonManager = new CompleteDungeonManager(this, roomTemplateManager, partyManager);
        sampleRoomGenerator = new SampleRoomGenerator(this, templateWorldManager, roomTemplateManager);
        equipmentDropEditorManager = new EquipmentDropEditorManager(this);
        roleSettingsManager = new RoleSettingsManager(this);
        customMobEditorManager = new CustomMobEditorManager(this, equipmentDropEditorManager, roleSettingsManager);
        mobTestToolManager = new MobTestToolManager(this);
        mobAiEditorManager = new MobAiEditorManager(this);

        Bukkit.getPluginManager().registerEvents(dungeonManager, this);
        Bukkit.getPluginManager().registerEvents(templateWorldManager, this);
        Bukkit.getPluginManager().registerEvents(completeDungeonManager, this);
        Bukkit.getPluginManager().registerEvents(equipmentDropEditorManager, this);
        Bukkit.getPluginManager().registerEvents(customMobEditorManager, this);
        Bukkit.getPluginManager().registerEvents(roleSettingsManager, this);
        Bukkit.getPluginManager().registerEvents(mobTestToolManager, this);
        Bukkit.getPluginManager().registerEvents(mobAiEditorManager, this);
        getLogger().info("Dungeons 1.0.0 custom mob AI editor enabled for Paper 26.2");
    }

    @Override
    public void onDisable() {
        if (completeDungeonManager != null) completeDungeonManager.shutdown();
        if (dungeonManager != null && dungeonManager.isRunning()) dungeonManager.stop(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player player)) sender.sendMessage("§c플레이어만 메뉴를 열 수 있습니다.");
            else completeDungeonManager.openMenu(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> sendHelp(sender);
            case "party" -> handleParty(sender, args);
            case "templateworld" -> handleTemplateWorld(sender, args);
            case "template" -> handleTemplate(sender, args);
            case "mobai", "ai" -> handleMobAi(sender);
            case "register" -> handleRegister(sender, args);
            case "unregister" -> handleUnregister(sender, args);
            case "challenge" -> handleChallenge(sender, args);
            case "finish", "complete" -> handleFinish(sender, false);
            case "abandon", "leave" -> handleFinish(sender, true);
            case "status" -> sender.sendMessage("§6§l[Dungeons] §f" + completeDungeonManager.status());
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            default -> sender.sendMessage("§c알 수 없는 명령어입니다. /dungeon help");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) return List.of();
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of("menu", "help", "party", "challenge", "finish", "abandon", "status"));
            if (sender.hasPermission("dungeons.admin")) roots.addAll(List.of("mobai", "register", "unregister", "templateworld", "template", "start", "stop"));
            return filterSuggestions(roots, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "party" -> filterSuggestions(List.of("create", "invite", "accept", "leave", "list"), args[1]);
                case "templateworld" -> sender.hasPermission("dungeons.admin") ? filterSuggestions(List.of("create", "enter", "leave"), args[1]) : List.of();
                case "template" -> sender.hasPermission("dungeons.admin")
                        ? filterSuggestions(List.of("wand", "pos1", "pos2", "info", "clear", "save", "list", "delete", "paste", "samples"), args[1]) : List.of();
                case "challenge", "unregister" -> filterSuggestions(completeDungeonManager.ids(), args[1]);
                case "register" -> List.of("<던전ID>");
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("register")) return filterSuggestions(roomTemplateManager.listNames(), args[2]);
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
        return List.of();
    }

    private List<String> filterSuggestions(Collection<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void handleMobAi(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 AI 편집기를 열 수 있습니다."); return; }
        player.sendMessage("§6§l[Dungeons] §f" + mobAiEditorManager.openForLookedAtMob(player));
    }

    private void handleRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 등록할 수 있습니다."); return; }
        if (!player.hasPermission("dungeons.admin")) { player.sendMessage("§c관리자 권한이 필요합니다."); return; }
        if (args.length < 3) { player.sendMessage("§c사용법: /dungeon register <던전ID> <템플릿이름>"); return; }
        player.sendMessage("§6§l[Dungeons] §f" + completeDungeonManager.register(player, args[1], args[2]));
    }

    private void handleUnregister(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dungeons.admin")) { sender.sendMessage("§c관리자 권한이 필요합니다."); return; }
        if (args.length < 2) { sender.sendMessage("§c사용법: /dungeon unregister <던전ID>"); return; }
        sender.sendMessage("§6§l[Dungeons] §f" + completeDungeonManager.unregister(args[1]));
    }

    private void handleChallenge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 도전할 수 있습니다."); return; }
        if (args.length < 2) { completeDungeonManager.openMenu(player); return; }
        player.sendMessage("§6§l[Dungeons] §f" + completeDungeonManager.start(player, args[1]));
    }

    private void handleFinish(CommandSender sender, boolean abandoned) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        player.sendMessage("§6§l[Dungeons] §f" + completeDungeonManager.finish(player, abandoned));
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
        if (args.length < 2) { player.sendMessage("§e/dungeon template <wand|save|list|delete|paste|info>"); return; }
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
            case "leave" -> player.sendMessage(partyManager.leave(player));
            case "list" -> player.sendMessage("§f파티원: " + partyManager.describe(player));
            default -> player.sendMessage("§c알 수 없는 파티 명령어입니다.");
        }
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§c플레이어만 사용할 수 있습니다."); return; }
        PartyManager.Party party = partyManager.getParty(player);
        if (party == null) { partyManager.createParty(player); party = partyManager.getParty(player); }
        if (!partyManager.isLeader(player)) { player.sendMessage("§c파티장만 시작할 수 있습니다."); return; }
        player.sendMessage(dungeonManager.start(partyManager.getOnlineMembers(party)));
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("dungeons.admin")) { sender.sendMessage("§c관리자 권한이 필요합니다."); return; }
        sender.sendMessage(dungeonManager.stop(false));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lDungeons 1.0.0 §7- 커스텀 몬스터 AI");
        sender.sendMessage("§e/dungeon mobai §7- 바라보는 프리뷰 몹의 AI 편집 GUI");
        sender.sendMessage("§e/dungeon §7- 던전 선택 GUI");
        sender.sendMessage("§e/dungeon challenge <ID> §7- 등록된 던전 도전");
        sender.sendMessage("§e/dungeon finish §7- 던전 완료·초기화");
        sender.sendMessage("§e/dungeon abandon §7- 도전 포기·초기화");
        sender.sendMessage("§e/dungeon register <ID> <템플릿> §7- 현재 위치에 고정 플레이 던전 등록");
        sender.sendMessage("§e/dungeon unregister <ID> §7- 던전 등록 해제");
        sender.sendMessage("§e/dungeon template wand §7- 금 도끼와 몹 편집 도구 지급");
    }
}
