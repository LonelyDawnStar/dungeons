package kr.minq.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyManager {
    private final Map<UUID, Party> partiesByMember = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    public boolean createParty(Player leader) {
        if (partiesByMember.containsKey(leader.getUniqueId())) {
            return false;
        }
        Party party = new Party(leader.getUniqueId());
        partiesByMember.put(leader.getUniqueId(), party);
        return true;
    }

    public String invite(Player leader, Player target) {
        Party party = partiesByMember.get(leader.getUniqueId());
        if (party == null || !party.leader().equals(leader.getUniqueId())) {
            return "§c파티장만 초대할 수 있습니다.";
        }
        if (leader.equals(target)) {
            return "§c자기 자신은 초대할 수 없습니다.";
        }
        if (partiesByMember.containsKey(target.getUniqueId())) {
            return "§c대상 플레이어는 이미 파티에 속해 있습니다.";
        }
        pendingInvites.put(target.getUniqueId(), leader.getUniqueId());
        target.sendMessage("§6§l[Dungeons] §e" + leader.getName() + "§f님이 파티에 초대했습니다.");
        target.sendMessage("§7수락: §e/dungeon party accept");
        return "§a" + target.getName() + "§f님을 초대했습니다.";
    }

    public String accept(Player player) {
        UUID leaderId = pendingInvites.remove(player.getUniqueId());
        if (leaderId == null) {
            return "§c받은 파티 초대가 없습니다.";
        }
        Party party = partiesByMember.get(leaderId);
        if (party == null) {
            return "§c초대한 파티가 더 이상 존재하지 않습니다.";
        }
        if (partiesByMember.containsKey(player.getUniqueId())) {
            return "§c이미 파티에 속해 있습니다.";
        }
        party.members().add(player.getUniqueId());
        partiesByMember.put(player.getUniqueId(), party);
        broadcast(party, "§e" + player.getName() + "§f님이 파티에 참가했습니다.");
        return "§a파티에 참가했습니다.";
    }

    public String leave(Player player) {
        Party party = partiesByMember.get(player.getUniqueId());
        if (party == null) {
            return "§c파티에 속해 있지 않습니다.";
        }

        if (party.leader().equals(player.getUniqueId())) {
            if (party.members().size() == 1) {
                partiesByMember.remove(player.getUniqueId());
                return "§7파티를 해산했습니다.";
            }
            List<UUID> remaining = new ArrayList<>(party.members());
            remaining.remove(player.getUniqueId());
            UUID newLeader = remaining.get(0);
            party.members().remove(player.getUniqueId());
            party.setLeader(newLeader);
            partiesByMember.remove(player.getUniqueId());
            broadcast(party, "§e" + player.getName() + "§f님이 떠났습니다. 새 파티장: §6" + playerName(newLeader));
            return "§7파티에서 나갔습니다.";
        }

        party.members().remove(player.getUniqueId());
        partiesByMember.remove(player.getUniqueId());
        broadcast(party, "§e" + player.getName() + "§f님이 파티에서 나갔습니다.");
        return "§7파티에서 나갔습니다.";
    }

    public Party getParty(Player player) {
        return partiesByMember.get(player.getUniqueId());
    }

    public boolean isLeader(Player player) {
        Party party = getParty(player);
        return party != null && party.leader().equals(player.getUniqueId());
    }

    public Collection<Player> getOnlineMembers(Party party) {
        List<Player> result = new ArrayList<>();
        for (UUID memberId : party.members()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                result.add(player);
            }
        }
        return result;
    }

    public String describe(Player viewer) {
        Party party = getParty(viewer);
        if (party == null) {
            return "§7현재 파티가 없습니다.";
        }
        List<String> names = new ArrayList<>();
        for (UUID memberId : party.members()) {
            String prefix = party.leader().equals(memberId) ? "§6[파티장] " : "§f";
            names.add(prefix + playerName(memberId));
        }
        return String.join("§7, ", names);
    }

    private void broadcast(Party party, String message) {
        for (Player member : getOnlineMembers(party)) {
            member.sendMessage("§6§l[Dungeons] §f" + message);
        }
    }

    private String playerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();
    }

    public static final class Party {
        private UUID leader;
        private final Set<UUID> members = new LinkedHashSet<>();

        private Party(UUID leader) {
            this.leader = leader;
            this.members.add(leader);
        }

        public UUID leader() {
            return leader;
        }

        private void setLeader(UUID leader) {
            this.leader = leader;
        }

        public Set<UUID> members() {
            return members;
        }

        public Set<UUID> membersView() {
            return Collections.unmodifiableSet(members);
        }
    }
}
