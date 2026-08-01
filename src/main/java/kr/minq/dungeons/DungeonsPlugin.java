package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Locale;

public final class DungeonsPlugin extends JavaPlugin {
    private PartyManager partyManager;
    private TestDungeonManager dungeonManager;

    @Override
    public void onEnable() {
        partyManager = new PartyManager();
        dungeonManager = new TestDungeonManager(this);
        Bukkit.getPluginManager().registerEvents(dungeonManager, this);
        getLogger().info("Dungeons 0.2.0 enabled for Paper 1.20.1");
    }

    @Override
    public void onDisable() {
        if (dungeonManager != null && dungeonManager.isRunning()) {
            dungeonManager.stop(false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "party" -> handleParty(sender, args);
            case "start" -> handleStart(sender);
            case "stop" -> handleStop(sender);
            case "status" -> handleStatus(sender);
            default -> sender.sendMessage("§c알 수 없는 명령어입니다. /dungeon help");
        }
        return true;
    }

    private void handleParty(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 사용할 수 있습니다.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§6§l[Dungeons] §f파티: " + partyManager.describe(player));
            player.sendMessage("§e/dungeon party create");
            player.sendMessage("§e/dungeon party invite <닉네임>");
            player.sendMessage("§e/dungeon party accept");
            player.sendMessage("§e/dungeon party leave");
            player.sendMessage("§e/dungeon party list");
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> player.sendMessage(partyManager.createParty(player)
                    ? "§6§l[Dungeons] §a파티를 생성했습니다."
                    : "§6§l[Dungeons] §c이미 파티에 속해 있습니다.");
            case "invite" -> {
                if (args.length < 3) {
                    player.sendMessage("§c사용법: /dungeon party invite <닉네임>");
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    player.sendMessage("§c접속 중인 플레이어를 찾을 수 없습니다.");
                    return;
                }
                player.sendMessage("§6§l[Dungeons] §f" + partyManager.invite(player, target));
            }
            case "accept" -> player.sendMessage("§6§l[Dungeons] §f" + partyManager.accept(player));
            case "leave" -> {
                if (dungeonManager.isRunning()) {
                    player.sendMessage("§c던전 진행 중에는 파티를 나갈 수 없습니다.");
                    return;
                }
                player.sendMessage("§6§l[Dungeons] §f" + partyManager.leave(player));
            }
            case "list" -> player.sendMessage("§6§l[Dungeons] §f파티원: " + partyManager.describe(player));
            default -> player.sendMessage("§c알 수 없는 파티 명령어입니다.");
        }
    }

    private void handleStart(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c플레이어만 던전을 시작할 수 있습니다.");
            return;
        }
        if (dungeonManager.isRunning()) {
            player.sendMessage("§c이미 던전이 진행 중입니다.");
            return;
        }

        PartyManager.Party party = partyManager.getParty(player);
        if (party == null) {
            partyManager.createParty(player);
            party = partyManager.getParty(player);
        }
        if (!partyManager.isLeader(player)) {
            player.sendMessage("§c파티장만 던전을 시작할 수 있습니다.");
            return;
        }

        Collection<Player> members = partyManager.getOnlineMembers(party);
        player.sendMessage("§6§l[Dungeons] §f" + dungeonManager.start(members));
    }

    private void handleStop(CommandSender sender) {
        if (!sender.hasPermission("dungeons.admin")) {
            sender.sendMessage("§c관리자만 강제로 종료할 수 있습니다.");
            return;
        }
        sender.sendMessage("§6§l[Dungeons] §f" + dungeonManager.stop(false));
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage("§6§l[Dungeons] §f현재 상태: "
                + (dungeonManager.isRunning() ? "§a진행 중" : "§7대기 중"));
        if (sender instanceof Player player) {
            sender.sendMessage("§6§l[Dungeons] §f파티: " + partyManager.describe(player));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lDungeons 0.2.0 §7- Paper 1.20.1");
        sender.sendMessage("§e/dungeon party §7- 파티 명령어");
        sender.sendMessage("§e/dungeon start §7- 파티와 테스트 던전 시작");
        sender.sendMessage("§e/dungeon stop §7- 관리자 강제 종료");
        sender.sendMessage("§e/dungeon status §7- 현재 상태 확인");
    }
}
