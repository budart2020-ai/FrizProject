package net.frizproject;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FrizProjectPlugin extends PluginBase implements CommandExecutor {
    private Config ranks;
    private Config users;
    private Config kits;
    private Config cooldowns;
    private final Set<String> coordinateHud = new HashSet<>();

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        saveResource("ranks.yml", false);
        saveResource("kits.yml", false);
        ranks = new Config(new File(getDataFolder(), "ranks.yml"), Config.YAML);
        users = new Config(new File(getDataFolder(), "users.yml"), Config.YAML);
        kits = new Config(new File(getDataFolder(), "kits.yml"), Config.YAML);
        cooldowns = new Config(new File(getDataFolder(), "kit-cooldowns.yml"), Config.YAML);

        String[] names = {"coords", "stafftp", "tphere", "tpall", "beacon", "staffrank", "rankset", "kit", "kits", "staffchat"};
        for (String name : names) {
            Command command = getCommand(name);
            if (command != null) command.setExecutor(this);
            else getLogger().warning("Команда не зарегистрирована в plugin.yml: " + name);
        }

        getServer().getScheduler().scheduleRepeatingTask(this, () -> {
            for (Player player : getServer().getOnlinePlayers().values()) {
                if (coordinateHud.contains(key(player.getName()))) {
                    player.sendTip(String.format(Locale.US, "§bX: %.1f  Y: %.1f  Z: %.1f §7| §f%s",
                            player.getX(), player.getY(), player.getZ(), player.getLevel().getName()));
                }
            }
        }, 20);
        getLogger().info("FrizProject 1.3.0 enabled.");
    }

    @Override
    public void onDisable() {
        if (users != null) users.save();
        if (cooldowns != null) cooldowns.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "coords" -> { return coords(sender, args); }
            case "stafftp" -> { return staffTp(sender, args); }
            case "tphere" -> { return tpHere(sender, args); }
            case "tpall" -> { return tpAll(sender); }
            case "beacon" -> { return beacon(sender, args); }
            case "staffrank" -> { return setStaffRank(sender, args); }
            case "rankset" -> { return setDonorRank(sender, args); }
            case "kit" -> { return giveKit(sender, args); }
            case "kits" -> { return listKits(sender); }
            case "staffchat" -> { return staffChat(sender, args); }
            default -> { sender.sendMessage("§cНеизвестная команда."); return true; }
        }
    }

    private boolean coords(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Команда доступна игроку в мире."); return true; }
        String k = key(player.getName());
        if (args.length == 0) {
            player.sendMessage(String.format(Locale.US, "§bКоординаты: X %.1f, Y %.1f, Z %.1f §7Мир: §f%s", player.getX(), player.getY(), player.getZ(), player.getLevel().getName()));
            return true;
        }
        if (args[0].equalsIgnoreCase("on")) { coordinateHud.add(k); player.sendMessage("§aПоказ координат включён."); }
        else if (args[0].equalsIgnoreCase("off")) { coordinateHud.remove(k); player.sendMessage("§eПоказ координат выключен."); }
        else player.sendMessage("§eИспользование: /coords [on|off]");
        return true;
    }

    private boolean staffTp(CommandSender sender, String[] args) {
        if (!require(sender, "friz.staff.tp")) return true;
        if (!(sender instanceof Player actor) || args.length < 1) { sender.sendMessage("§eИспользование: /stafftp <игрок>"); return true; }
        Player target = getServer().getPlayer(args[0]);
        if (target == null) { sender.sendMessage("§cИгрок не найден онлайн."); return true; }
        actor.teleport(target.getLocation());
        sender.sendMessage("§aТелепортация к " + target.getName() + ".");
        return true;
    }

    private boolean tpHere(CommandSender sender, String[] args) {
        if (!require(sender, "friz.staff.tphere")) return true;
        if (!(sender instanceof Player actor) || args.length < 1) { sender.sendMessage("§eИспользование: /tphere <игрок>"); return true; }
        Player target = getServer().getPlayer(args[0]);
        if (target == null) { sender.sendMessage("§cИгрок не найден онлайн."); return true; }
        target.teleport(actor.getLocation());
        sender.sendMessage("§aИгрок " + target.getName() + " телепортирован к вам.");
        return true;
    }

    private boolean tpAll(CommandSender sender) {
        if (!require(sender, "friz.staff.tpall")) return true;
        if (!(sender instanceof Player actor)) { sender.sendMessage("Команда доступна игроку."); return true; }
        for (Player target : getServer().getOnlinePlayers().values()) {
            if (!target.equals(actor)) target.teleport(actor.getLocation());
        }
        sender.sendMessage("§aВсе игроки телепортированы к вам.");
        return true;
    }

    private boolean beacon(CommandSender sender, String[] args) {
        if (!require(sender, "friz.creator.beacon")) return true;
        if (!(sender instanceof Player player) || args.length != 1 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage("§eИспользование: /beacon spawn"); return true;
        }
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY()) + 1;
        int z = (int) Math.floor(player.getZ());
        boolean dispatched = getServer().dispatchCommand(getServer().getConsoleSender(), "setblock " + x + " " + y + " " + z + " beacon");
        sender.sendMessage(dispatched ? "§aКоманда установки маяка отправлена. Проверьте, что setblock поддерживается вашей сборкой." : "§cНе удалось выполнить setblock. Поставьте маяк вручную или проверьте поддержку команды.");
        return true;
    }

    private boolean setStaffRank(CommandSender sender, String[] args) {
        if (!require(sender, "friz.staff.rankset")) return true;
        if (args.length < 2) { sender.sendMessage("§eИспользование: /staffrank <игрок> <helper|moderator|administrator|creator|owner>"); return true; }
        String role = args[1].toLowerCase(Locale.ROOT);
        if (!ranks.exists("staff-ranks." + role)) { sender.sendMessage("§cТакой должности нет в ranks.yml."); return true; }
        users.set("users." + key(args[0]) + ".staff-rank", role);
        users.save();
        sender.sendMessage("§aДолжность игрока " + args[0] + " установлена: " + role + ".");
        return true;
    }

    private boolean setDonorRank(CommandSender sender, String[] args) {
        if (!require(sender, "friz.staff.rankset")) return true;
        if (args.length < 2) { sender.sendMessage("§eИспользование: /rankset <игрок> <player|warrior|wanderer|lord|legend|prince|magister|overlord|guardian>"); return true; }
        String rank = args[1].toLowerCase(Locale.ROOT);
        if (!ranks.exists("donor-ranks." + rank)) { sender.sendMessage("§cТакой привилегии нет в ranks.yml."); return true; }
        users.set("users." + key(args[0]) + ".donor-rank", rank);
        users.save();
        sender.sendMessage("§aПривилегия игрока " + args[0] + " установлена: " + rank + ".");
        return true;
    }

    private boolean listKits(CommandSender sender) {
        sender.sendMessage("§6Доступные наборы:");
        for (String kit : kits.getSection("kits").keySet()) {
            sender.sendMessage("§e/kit " + kit + " §7— " + kits.getString("kits." + kit + ".display", kit));
        }
        return true;
    }

    private boolean giveKit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Команда доступна игроку."); return true; }
        if (args.length < 1) { sender.sendMessage("§eИспользование: /kit <название>. Список: /kits"); return true; }
        String kit = args[0].toLowerCase(Locale.ROOT);
        if (!kits.exists("kits." + kit)) { sender.sendMessage("§cТакого набора нет. Используйте /kits."); return true; }
        int required = kits.getInt("kits." + kit + ".rank_weight", 0);
        if (donorWeight(player.getName()) < required && !hasPermission(player, "friz.staff.*")) {
            sender.sendMessage("§cНедостаточная привилегия для этого набора."); return true;
        }
        long now = System.currentTimeMillis() / 1000L;
        long last = cooldowns.getLong("cooldowns." + key(player.getName()) + "." + kit, 0L);
        long cooldown = kits.getLong("cooldown_seconds", 86400L);
        if (now - last < cooldown && !hasPermission(player, "friz.staff.*")) {
            sender.sendMessage("§cНабор будет доступен через " + ((cooldown - (now - last)) / 3600) + " ч."); return true;
        }
        for (String raw : kits.getStringList("kits." + kit + ".commands")) {
            String cmd = raw.replace("%player%", player.getName());
            getServer().dispatchCommand(getServer().getConsoleSender(), cmd);
        }
        cooldowns.set("cooldowns." + key(player.getName()) + "." + kit, now);
        cooldowns.save();
        sender.sendMessage("§aНабор выдан: " + kits.getString("kits." + kit + ".display", kit));
        return true;
    }

    private boolean staffChat(CommandSender sender, String[] args) {
        if (!require(sender, "friz.staff.chat")) return true;
        if (args.length == 0) { sender.sendMessage("§eИспользование: /staffchat <сообщение>"); return true; }
        String message = String.join(" ", args);
        String author = sender.getName();
        for (Player player : getServer().getOnlinePlayers().values()) {
            if (hasPermission(player, "friz.staff.chat")) player.sendMessage("§c[STAFF] §f" + author + ": " + message);
        }
        return true;
    }

    private boolean require(CommandSender sender, String permission) {
        if (!(sender instanceof Player player) || hasPermission(player, permission)) return true;
        sender.sendMessage("§cУ вас нет прав на эту команду.");
        return false;
    }

    private boolean hasPermission(Player player, String permission) {
        String role = users.getString("users." + key(player.getName()) + ".staff-rank", "");
        if (role.equals("creator") || role.equals("owner")) return true;
        if (role.isBlank()) return false;
        for (String node : ranks.getStringList("staff-ranks." + role + ".permissions")) {
            if (node.equals("*") || node.equals(permission) || (node.endsWith(".*") && permission.startsWith(node.substring(0, node.length() - 1)))) return true;
        }
        return false;
    }

    private int donorWeight(String playerName) {
        String rank = users.getString("users." + key(playerName) + ".donor-rank", "player");
        return ranks.getInt("donor-ranks." + rank + ".weight", 0);
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}
