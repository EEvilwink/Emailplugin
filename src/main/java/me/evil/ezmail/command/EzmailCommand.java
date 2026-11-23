package me.evil.ezmail.command;

import me.evil.ezmail.Ezmail;
import me.evil.ezmail.config.Settings;
import me.evil.ezmail.gui.GuiType;
import me.evil.ezmail.util.Text;
import me.evil.ezmail.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * /ezmail (别名 /em) 指令与其子指令。
 */
public final class EzmailCommand implements CommandExecutor, TabCompleter {

    private final Ezmail plugin;

    public EzmailCommand(Ezmail plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "send" -> handleSend(sender, label, args);
            case "inbox", "open", "read" -> handleInbox(sender);
            case "sent", "outbox" -> handleSent(sender);
            case "broadcast", "bc", "announce" -> handleBroadcast(sender, label, args);
            case "admin" -> handleAdmin(sender);
            case "reload" -> handleReload(sender);
            case "help" -> sendHelp(sender, label);
            default -> plugin.lang().send(sender, "unknown-command", "command", label);
        }
        return true;
    }

    private void handleSend(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("ezmail.send")) {
            plugin.lang().send(player, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.lang().send(player, "usage-send", "command", label);
            return;
        }

        String target = args[1];
        String content = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.mailService().send(player, target, content);
    }

    private void handleInbox(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("ezmail.inbox")) {
            plugin.lang().send(player, "no-permission");
            return;
        }
        if (plugin.store().inbox(player.getUniqueId(), player.getName()).isEmpty()) {
            plugin.lang().send(player, "inbox-empty");
            return;
        }
        plugin.guiManager().open(player, GuiType.INBOX);
    }

    private void handleSent(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("ezmail.sent")) {
            plugin.lang().send(player, "no-permission");
            return;
        }
        if (plugin.store().sent(player.getUniqueId()).isEmpty()) {
            plugin.lang().send(player, "sent-empty");
            return;
        }
        plugin.guiManager().open(player, GuiType.SENT);
    }

    /**
     * /ezmail broadcast [-t 时长] [-i 材质] [-n 名称] [-l 描述] &lt;内容&gt;
     * 玩家与控制台都可以使用。
     */
    private void handleBroadcast(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("ezmail.broadcast")) {
            plugin.lang().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.lang().send(sender, "broadcast-usage", "command", label);
            return;
        }

        Settings settings = plugin.settings();
        long deleteAfter = settings.broadcastDeleteMillis();
        Material icon = null;
        String displayName = null;
        List<String> lore = null;

        int index = 1;
        while (index < args.length && args[index].startsWith("-") && args[index].length() > 1) {
            String token = args[index];
            String flag;
            String value;

            int equals = token.indexOf('=');
            if (equals > 1) {
                flag = token.substring(0, equals);
                if (!isBroadcastFlag(flag)) {
                    break;
                }
                value = token.substring(equals + 1);
                index++;
            } else {
                flag = token;
                if (!isBroadcastFlag(flag)) {
                    // 不是已知选项, 从这里开始当作公告正文
                    break;
                }
                if (index + 1 >= args.length) {
                    plugin.lang().send(sender, "broadcast-usage", "command", label);
                    return;
                }
                int[] next = new int[1];
                value = collectValue(args, index + 1, next);
                index = next[0];
            }

            if (value.isEmpty()) {
                plugin.lang().send(sender, "broadcast-usage", "command", label);
                return;
            }

            switch (flag.toLowerCase(Locale.ROOT)) {
                case "-t", "--time", "-time" -> {
                    Long parsed = TimeUtil.parseDuration(value);
                    if (parsed == null) {
                        plugin.lang().send(sender, "broadcast-invalid-time", "time", value);
                        playError(sender);
                        return;
                    }
                    deleteAfter = parsed;
                }
                case "-i", "--item", "-item" -> {
                    Material material = Material.matchMaterial(value);
                    if (material == null) {
                        plugin.lang().send(sender, "broadcast-invalid-item", "item", value);
                        playError(sender);
                        return;
                    }
                    icon = material;
                }
                case "-n", "--name", "-name" -> displayName = value;
                case "-l", "--lore", "-lore" -> lore = List.of(value.split("\\|"));
                default -> {
                    plugin.lang().send(sender, "broadcast-usage", "command", label);
                    return;
                }
            }
        }

        if (index >= args.length) {
            plugin.lang().send(sender, "broadcast-usage", "command", label);
            return;
        }

        String content = String.join(" ", Arrays.copyOfRange(args, index, args.length));
        plugin.mailService().sendBroadcast(sender, content, deleteAfter, icon, displayName, lore);
    }

    private void playError(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.sound("error").play(player);
        }
    }

    private static boolean isBroadcastFlag(String flag) {
        return switch (flag.toLowerCase(Locale.ROOT)) {
            case "-t", "--time", "-time", "-i", "--item", "-item",
                 "-n", "--name", "-name", "-l", "--lore", "-lore" -> true;
            default -> false;
        };
    }

    /**
     * 读取一个参数值, 支持用英文双引号包裹带空格的内容。
     * Bukkit 不会替我们去掉引号, 所以这里手动拼接被空格拆开的多个参数。
     *
     * @param args  原始参数
     * @param start 值的第一个参数下标
     * @param next  输出: 值之后的第一个参数下标
     */
    private static String collectValue(String[] args, int start, int[] next) {
        String first = args[start];
        if (!first.startsWith("\"")) {
            next[0] = start + 1;
            return first;
        }

        StringBuilder value = new StringBuilder();
        int index = start;
        String current = first.substring(1);
        boolean firstPart = true;

        while (index < args.length) {
            int close = current.indexOf('"');
            String part = close >= 0 ? current.substring(0, close) : current;
            if (!firstPart) {
                value.append(' ');
            }
            value.append(part);
            firstPart = false;

            index++;
            if (close >= 0) {
                break;
            }
            if (index < args.length) {
                current = args[index];
            }
        }

        next[0] = index;
        return value.toString();
    }

    private void handleAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("ezmail.admin")) {
            plugin.lang().send(player, "no-permission");
            return;
        }
        if (plugin.store().all().isEmpty()) {
            plugin.lang().send(player, "admin-empty");
            return;
        }
        plugin.guiManager().open(player, GuiType.ADMIN);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("ezmail.reload")) {
            plugin.lang().send(sender, "no-permission");
            return;
        }
        try {
            plugin.reloadPlugin();
            plugin.lang().send(sender, "reload-success");
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "重载配置时出现异常", exception);
            plugin.lang().send(sender, "reload-failed");
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        for (String line : plugin.lang().helpLines()) {
            sender.sendMessage(Text.format(line, Map.of("command", label)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("ezmail.send")) {
                options.add("send");
            }
            if (sender.hasPermission("ezmail.inbox")) {
                options.add("inbox");
            }
            if (sender.hasPermission("ezmail.sent")) {
                options.add("sent");
            }
            if (sender.hasPermission("ezmail.broadcast")) {
                options.add("broadcast");
            }
            if (sender.hasPermission("ezmail.admin")) {
                options.add("admin");
            }
            if (sender.hasPermission("ezmail.reload")) {
                options.add("reload");
            }
            options.add("help");
            return filter(options, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("send")) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return filter(names, args[1]);
        }

        if (isBroadcast(args[0]) && args[args.length - 1].startsWith("-")) {
            return filter(List.of("-t", "-i", "-n", "-l", "--time", "--item", "--name", "--lore"),
                    args[args.length - 1]);
        }

        return List.of();
    }

    private static boolean isBroadcast(String subCommand) {
        String lower = subCommand.toLowerCase(Locale.ROOT);
        return "broadcast".equals(lower) || "bc".equals(lower) || "announce".equals(lower);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
