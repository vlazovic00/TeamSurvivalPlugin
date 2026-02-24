package me.chinq.teamsurvival.command;

import me.chinq.teamsurvival.model.HomeLocation;
import me.chinq.teamsurvival.model.Team;
import me.chinq.teamsurvival.service.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class TimCommand implements CommandExecutor, TabCompleter {

    private final Settings settings;
    private final MessageService msg;

    private final TeamService teamService;
    private final PersistenceService persistence;

    private final TeamChatService teamChat;
    private final CombatTagService combat;
    private final TeleportService teleports;

    private final InviteService invites;
    private final TpaService tpa;

    private final BountyService bountyService;
    private final ShopService shopService;

    public TimCommand(
            Settings settings,
            MessageService msg,
            TeamService teamService,
            PersistenceService persistence,
            TeamChatService teamChat,
            CombatTagService combat,
            TeleportService teleports,
            InviteService invites,
            TpaService tpa,
            BountyService bountyService,
            ShopService shopService
    ) {
        this.settings = settings;
        this.msg = msg;
        this.teamService = teamService;
        this.persistence = persistence;
        this.teamChat = teamChat;
        this.combat = combat;
        this.teleports = teleports;
        this.invites = invites;
        this.tpa = tpa;

        this.bountyService = bountyService;
        this.shopService = shopService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            msg.sendPrefixed(sender, "errors.onlyPlayers");
            return true;
        }

        String sub = (args.length == 0) ? "help" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help", "pomoc", "pomoć" -> {
                sendHelp(p);
                return true;
            }
            case "napravi" -> {
                handleCreate(p, args);
                return true;
            }
            case "pozovi" -> {
                handleInvite(p, args);
                return true;
            }
            case "prihvati" -> {
                invites.acceptInvite(p);
                return true;
            }
            case "odbij" -> {
                invites.declineInvite(p);
                return true;
            }
            case "napusti" -> {
                handleLeave(p);
                return true;
            }
            case "info" -> {
                handleInfo(p);
                return true;
            }
            case "sethome" -> {
                handleSetHome(p);
                return true;
            }
            case "home" -> {
                handleHomeTeleport(p);
                return true;
            }
            case "chat" -> {
                handleChatToggle(p);
                return true;
            }

            case "ping" -> {
                handlePing(p);
                return true;
            }
            case "tpa" -> {
                handleTpa(p, args);
                return true;
            }
            case "tpaccept" -> {
                tpa.accept(p);
                return true;
            }
            case "tpdeny" -> {
                tpa.deny(p);
                return true;
            }
            case "izbaci" -> {
                handleKick(p, args);
                return true;
            }

            // ===== economy =====
            case "coins", "bal", "novac" -> {
                Team t = teamService.getTeamOf(p.getUniqueId());
                if (t == null) {
                    msg.sendPrefixed(p, "errors.noTeam");
                    return true;
                }
                p.sendMessage(org.bukkit.ChatColor.GREEN + "Team coins: " + org.bukkit.ChatColor.GOLD + t.getCoins());
                return true;
            }

            // /tim coinset <team> <amount> (admin)
            case "coinset" -> {
                handleCoinSet(p, args);
                return true;
            }

            // /tim bounty <igrac> <amount>
            case "bounty" -> {
                handleBounty(p, args);
                return true;
            }

            // /tim shop
            case "shop" -> {
                // ShopService itself blocks non-team players, but we can short-circuit too
                Team t = teamService.getTeamOf(p.getUniqueId());
                if (t == null) {
                    msg.sendPrefixed(p, "errors.noTeam");
                    return true;
                }
                shopService.openShop(p);
                return true;
            }

            default -> {
                msg.sendPrefixed(p, "errors.unknownSubcommand", Map.of("sub", sub));
                sendHelp(p);
                return true;
            }
        }
    }

    private void sendHelp(Player p) {
        String ff = settings.friendlyFireEnabled ? "ON" : "OFF";
        List<String> lines = msg.list("help.lines");
        msg.sendRaw(p, msg.colorize(msg.applyPlaceholders(msg.get("help.header"), Map.of(
                "max", String.valueOf(settings.maxTeamSize),
                "friendly_fire", ff
        ))));
        for (String line : lines) {
            msg.sendRaw(p, msg.colorize(msg.applyPlaceholders(line, Map.of(
                    "max", String.valueOf(settings.maxTeamSize),
                    "friendly_fire", ff
            ))));
        }

        msg.sendRaw(p, msg.colorize("&8&l----------------"));
        msg.sendRaw(p, msg.colorize("&7/tim coins &8- &fprikaz coins tima"));
        msg.sendRaw(p, msg.colorize("&7/tim bounty <igrac> <amount> &8- &fpostavi bounty (skida coins timu)"));
        msg.sendRaw(p, msg.colorize("&7/tim shop &8- &fshop (kupi iteme / prodaj glave)"));
        msg.sendRaw(p, msg.colorize("&7/tim coinset <team> <amount> &8- &f(admin) set coins timu"));
    }

    private void handleCreate(Player p, String[] args) {
        if (args.length < 2) {
            msg.sendPrefixed(p, "usage.create");
            return;
        }
        if (teamService.getTeamOf(p.getUniqueId()) != null) {
            msg.sendPrefixed(p, "team.alreadyInTeam");
            return;
        }

        String name = args[1];
        if (!settings.teamName.isValid(name)) {
            msg.sendPrefixed(p, "team.invalidName", Map.of(
                    "min", String.valueOf(settings.teamName.minLen),
                    "max", String.valueOf(settings.teamName.maxLen)
            ));
            return;
        }
        if (teamService.getTeamByName(name) != null) {
            msg.sendPrefixed(p, "team.nameTaken");
            return;
        }

        Team created = teamService.createTeam(p.getUniqueId(), name);
        msg.sendPrefixed(p, "team.created", Map.of("team", created.getName(), "id", created.getId()));

        // NOTE: starting coins are handled inside TeamService via StarterCoinsService anti-abuse.
        persistence.requestSaveDebounced();
    }

    private void handleInvite(Player p, String[] args) {
        if (args.length < 2) {
            msg.sendPrefixed(p, "usage.invite");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.sendPrefixed(p, "errors.playerNotOnline", Map.of("player", args[1]));
            return;
        }
        invites.sendInvite(p, target);
    }

    private void handleLeave(Player p) {
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "team.notInTeam");
            return;
        }

        TeamService.LeaveOutcome out = teamService.leaveTeam(p.getUniqueId());
        if (out.disbanded) {
            msg.sendPrefixed(p, "team.disbanded", Map.of("team", out.teamName));
        } else if (out.newLeader != null) {
            String newLeaderName = nameOrShort(out.newLeader);
            msg.sendPrefixed(p, "team.leftAsLeader", Map.of("team", out.teamName, "newLeader", newLeaderName));
            Team t = teamService.getTeamById(out.teamId);
            if (t != null) broadcastToTeam(t, "team.newLeader", Map.of("leader", newLeaderName));
        } else {
            msg.sendPrefixed(p, "team.left", Map.of("team", out.teamName));
        }

        teamChat.setEnabled(p.getUniqueId(), false);
        persistence.requestSaveDebounced();
    }

    private void handleInfo(Player p) {
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "team.notInTeam");
            return;
        }

        String leaderName = nameOrShort(team.getLeader());
        String members = team.getMembers().stream()
                .map(this::nameOrShort)
                .collect(Collectors.joining(", "));

        HomeLocation home = team.getHome();
        String homeText;
        if (home == null) {
            homeText = msg.get("team.info.homeNotSet");
        } else {
            homeText = msg.applyPlaceholders(msg.get("team.info.homeSet"), Map.of(
                    "world", home.world(),
                    "x", formatInt(home.x()),
                    "y", formatInt(home.y()),
                    "z", formatInt(home.z())
            ));
        }

        msg.sendRaw(p, msg.colorize(msg.applyPlaceholders(msg.get("team.info.header"), Map.of(
                "team", team.getName(),
                "id", team.getId()
        ))));
        msg.sendRaw(p, msg.colorize(msg.applyPlaceholders(msg.get("team.info.leader"), Map.of("leader", leaderName))));
        msg.sendRaw(p, msg.colorize(msg.applyPlaceholders(msg.get("team.info.members"), Map.of(
                "size", String.valueOf(team.getMembers().size()),
                "max", String.valueOf(settings.maxTeamSize),
                "members", members
        ))));
        msg.sendRaw(p, msg.colorize(homeText));
        msg.sendRaw(p, msg.colorize("&7Coins: &6" + team.getCoins()));
    }

    private void handleSetHome(Player p) {
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "team.notInTeam");
            return;
        }
        if (!team.isLeader(p.getUniqueId())) {
            msg.sendPrefixed(p, "team.notLeader");
            return;
        }
        if (!settings.isWorldAllowed(p.getWorld().getName())) {
            msg.sendPrefixed(p, "home.worldNotAllowed", Map.of("world", p.getWorld().getName()));
            return;
        }

        long rem = teamService.getHomeSetCooldownRemainingSeconds(team.getId());
        if (rem > 0) {
            msg.sendPrefixed(p, "home.setCooldown", Map.of("seconds", String.valueOf(rem)));
            return;
        }

        Location loc = p.getLocation();
        double x = loc.getBlockX() + 0.5;
        double z = loc.getBlockZ() + 0.5;
        double y = loc.getY();

        HomeLocation home = new HomeLocation(
                loc.getWorld().getName(),
                x, y, z,
                loc.getYaw(), loc.getPitch()
        );
        teamService.setHome(team.getId(), home);
        teamService.recordHomeSet(team.getId());

        msg.sendPrefixed(p, "home.set", Map.of(
                "world", home.world(),
                "x", formatInt(home.x()),
                "y", formatInt(home.y()),
                "z", formatInt(home.z()),
                "cooldown", String.valueOf(settings.homeCooldownSeconds)
        ));
        persistence.requestSaveDebounced();
    }

    private void handleHomeTeleport(Player p) {
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "team.notInTeam");
            return;
        }
        if (team.getHome() == null) {
            msg.sendPrefixed(p, "home.notSet");
            return;
        }
        if (combat.isTagged(p.getUniqueId())) {
            msg.sendPrefixed(p, "combat.blockTeleport", Map.of("seconds", String.valueOf(combat.remainingSeconds(p.getUniqueId()))));
            return;
        }

        long cd = teleports.cooldownRemainingSeconds(p.getUniqueId(), "home");
        if (cd > 0) {
            msg.sendPrefixed(p, "cooldown.home", Map.of("seconds", String.valueOf(cd)));
            return;
        }

        World w = Bukkit.getWorld(team.getHome().world());
        if (w == null) {
            msg.sendPrefixed(p, "home.invalidWorld", Map.of("world", team.getHome().world()));
            return;
        }
        if (!settings.isWorldAllowed(w.getName())) {
            msg.sendPrefixed(p, "home.worldNotAllowed", Map.of("world", w.getName()));
            return;
        }

        Location dest = team.getHome().toBukkitLocation(w);

        if (teleports.isInWarmup(p.getUniqueId())) {
            msg.sendPrefixed(p, "warmup.already");
            return;
        }

        msg.sendPrefixed(p, "warmup.startHome", Map.of("seconds", String.valueOf(settings.homeWarmupSeconds)));

        boolean started = teleports.startWarmup(
                p,
                settings.homeWarmupSeconds,
                () -> {
                    if (!p.isOnline()) return;
                    if (combat.isTagged(p.getUniqueId())) {
                        msg.sendPrefixed(p, "warmup.cancelCombat");
                        return;
                    }
                    p.teleport(dest);
                    teleports.setCooldownSeconds(p.getUniqueId(), "home", settings.homeTeleportCooldownSeconds);
                    msg.sendPrefixed(p, "home.teleported", Map.of(
                            "cooldown", String.valueOf(settings.homeTeleportCooldownSeconds)
                    ));
                },
                reason -> {
                    switch (reason) {
                        case MOVE -> msg.sendPrefixed(p, "warmup.cancelMove");
                        case DAMAGE -> msg.sendPrefixed(p, "warmup.cancelDamage");
                        case COMBAT -> msg.sendPrefixed(p, "warmup.cancelCombat");
                        default -> { /* silent */ }
                    }
                }
        );

        if (!started) {
            msg.sendPrefixed(p, "warmup.already");
        }
    }

    private void handleChatToggle(Player p) {
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "team.notInTeam");
            return;
        }
        boolean now = teamChat.toggle(p.getUniqueId());
        msg.sendPrefixed(p, now ? "chat.enabled" : "chat.disabled");
    }

    private void handlePing(Player p) {
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "team.notInTeam");
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("player", p.getName());

        if (settings.showCoordinatesInPing) {
            Location l = p.getLocation();
            ph.put("world", l.getWorld().getName());
            ph.put("x", String.valueOf(l.getBlockX()));
            ph.put("y", String.valueOf(l.getBlockY()));
            ph.put("z", String.valueOf(l.getBlockZ()));
            broadcastToTeam(team, "ping.withCoords", ph);
        } else {
            broadcastToTeam(team, "ping.noCoords", ph);
        }
    }

    private void handleTpa(Player p, String[] args) {
        if (args.length < 2) {
            msg.sendPrefixed(p, "usage.tpa");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg.sendPrefixed(p, "errors.playerNotOnline", Map.of("player", args[1]));
            return;
        }
        tpa.request(p, target);
    }

    private void handleKick(Player p, String[] args) {
        if (args.length < 2) {
            msg.sendPrefixed(p, "usage.kick");
            return;
        }
        Team team = teamService.getTeamOf(p.getUniqueId());
        if (team == null) {
            msg.sendPrefixed(p, "team.notInTeam");
            return;
        }
        if (!team.isLeader(p.getUniqueId())) {
            msg.sendPrefixed(p, "team.notLeader");
            return;
        }

        String name = args[1];
        UUID targetUuid;

        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            targetUuid = online.getUniqueId();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(name);
            if (off.getName() == null && !off.hasPlayedBefore()) {
                msg.sendPrefixed(p, "errors.playerUnknown", Map.of("player", name));
                return;
            }
            targetUuid = off.getUniqueId();
        }

        if (targetUuid.equals(p.getUniqueId())) {
            msg.sendPrefixed(p, "kick.cantKickSelf");
            return;
        }
        if (!team.hasMember(targetUuid)) {
            msg.sendPrefixed(p, "kick.notInYourTeam", Map.of("player", name));
            return;
        }
        if (team.getLeader().equals(targetUuid)) {
            msg.sendPrefixed(p, "kick.cantKickLeader");
            return;
        }

        teamService.kickMember(team.getId(), targetUuid);

        String targetName = nameOrShort(targetUuid);
        msg.sendPrefixed(p, "kick.kicked", Map.of("player", targetName));

        Player kickedOnline = Bukkit.getPlayer(targetUuid);
        if (kickedOnline != null) {
            msg.sendPrefixed(kickedOnline, "kick.youWereKicked", Map.of("team", team.getName()));
            teamChat.setEnabled(kickedOnline.getUniqueId(), false);
        }

        persistence.requestSaveDebounced();
    }

    private void handleBounty(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage(org.bukkit.ChatColor.RED + "Koristi: /tim bounty <igrac> <amount>");
            return;
        }

        String targetName = args[1];

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (Exception ex) {
            p.sendMessage(org.bukkit.ChatColor.RED + "Amount mora biti broj.");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getName() == null && !target.hasPlayedBefore()) {
            msg.sendPrefixed(p, "errors.playerUnknown", Map.of("player", targetName));
            return;
        }

        BountyService.PlaceOutcome out = bountyService.placeBounty(p, target, amount);

        switch (out) {
            case OK -> p.sendMessage(org.bukkit.ChatColor.GREEN + "Postavio si bounty " + org.bukkit.ChatColor.GOLD + amount
                    + org.bukkit.ChatColor.GREEN + " na " + org.bukkit.ChatColor.AQUA + target.getName());
            case NO_TEAM -> msg.sendPrefixed(p, "errors.noTeam");
            case SAME_TEAM -> p.sendMessage(org.bukkit.ChatColor.RED + "Ne možeš staviti bounty na igrača iz svog tima.");
            case SELF -> p.sendMessage(org.bukkit.ChatColor.RED + "Ne možeš staviti bounty na sebe.");
            case TOO_SMALL -> p.sendMessage(org.bukkit.ChatColor.RED + "Bounty je premali.");
            case TOO_LARGE -> p.sendMessage(org.bukkit.ChatColor.RED + "Bounty je prevelik.");
            case NOT_ENOUGH_COINS -> p.sendMessage(org.bukkit.ChatColor.RED + "Tvoj tim nema dovoljno coins.");
            default -> p.sendMessage(org.bukkit.ChatColor.RED + "Greška.");
        }

        persistence.requestSaveDebounced();
    }

    // /tim coinset <team> <amount>
    private void handleCoinSet(Player p, String[] args) {
        if (!p.hasPermission("teamsurvival.coinset")) {
            p.sendMessage("§cNemaš dozvolu.");
            return;
        }
        if (args.length < 3) {
            p.sendMessage("§cKoristi: /tim coinset <team> <amount>");
            return;
        }

        String teamName = args[1];
        Team team = teamService.getTeamByName(teamName);
        if (team == null) {
            p.sendMessage("§cTim ne postoji.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (Exception e) {
            p.sendMessage("§cAmount mora biti broj.");
            return;
        }
        if (amount < 0) amount = 0;

        team.setCoins(amount);
        persistence.requestSaveDebounced();

        p.sendMessage("§aTeam §e" + team.getName() + " §asada ima §6" + amount + " coins.");
    }

    private void broadcastToTeam(Team team, String key, Map<String, String> placeholders) {
        for (UUID u : team.getMembers()) {
            Player pl = Bukkit.getPlayer(u);
            if (pl != null) msg.sendPrefixed(pl, key, placeholders);
        }
    }

    private String nameOrShort(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
        if (off.getName() != null) return off.getName();
        String s = uuid.toString().replace("-", "");
        return s.substring(0, 8);
    }

    private String formatInt(double v) {
        return String.valueOf((int) Math.floor(v));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player p)) return Collections.emptyList();

        if (args.length == 1) {
            return filterPrefix(List.of(
                    "help",
                    "napravi",
                    "pozovi",
                    "prihvati",
                    "odbij",
                    "napusti",
                    "info",
                    "sethome",
                    "home",
                    "chat",
                    "ping",
                    "tpa",
                    "tpaccept",
                    "tpdeny",
                    "izbaci",
                    "coins",
                    "coinset",
                    "bounty",
                    "shop"
            ), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (sub.equals("pozovi") || sub.equals("tpa") || sub.equals("bounty")) {
                return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
            }

            if (sub.equals("izbaci")) {
                Team t = teamService.getTeamOf(p.getUniqueId());
                if (t == null) return Collections.emptyList();
                return filterPrefix(
                        t.getMembers().stream()
                                .filter(u -> !u.equals(p.getUniqueId()))
                                .map(this::nameOrShort)
                                .distinct()
                                .toList(),
                        args[1]
                );
            }

            if (sub.equals("coinset")) {
                return filterPrefix(
                        teamService.getAllTeams().stream().map(Team::getName).toList(),
                        args[1]
                );
            }
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String pr = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(pr))
                .sorted()
                .collect(Collectors.toList());
    }
}