package kr.minq.dungeons;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class DungeonsPlugin extends JavaPlugin {
    private boolean dungeonRunning;

    @Override
    public void onEnable() {
        getLogger().info("Dungeons 0.1.0 enabled for Paper 1.20.1");
    }

    @Override
    public void onDisable() {
        dungeonRunning = false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dungeon")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§6§lDungeons §7명령어");
            sender.sendMessage("§e/dungeon start §7- 테스트 던전 시작");
            sender.sendMessage("§e/dungeon stop §7- 던전 종료");
            sender.sendMessage("§e/dungeon status §7- 현재 상태 확인");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!sender.hasPermission("dungeons.admin")) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (dungeonRunning) {
                    sender.sendMessage("§c이미 던전이 진행 중입니다.");
                    return true;
                }
                dungeonRunning = true;
                sender.getServer().broadcastMessage("§6§l[Dungeons] §f테스트 던전이 시작되었습니다.");
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("dungeons.admin")) {
                    sender.sendMessage("§c권한이 없습니다.");
                    return true;
                }
                if (!dungeonRunning) {
                    sender.sendMessage("§c진행 중인 던전이 없습니다.");
                    return true;
                }
                dungeonRunning = false;
                sender.getServer().broadcastMessage("§6§l[Dungeons] §f던전이 종료되었습니다.");
                return true;
            }
            case "status" -> {
                sender.sendMessage("§6§l[Dungeons] §f현재 상태: " + (dungeonRunning ? "§a진행 중" : "§7대기 중"));
                return true;
            }
            default -> {
                sender.sendMessage("§c알 수 없는 명령어입니다. /dungeon help");
                return true;
            }
        }
    }
}
