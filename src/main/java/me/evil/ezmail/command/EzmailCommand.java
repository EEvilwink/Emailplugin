package me.evil.ezmail.command;

import me.evil.ezmail.Ezmail;
import me.evil.ezmail.gui.GuiType;
import me.evil.ezmail.util.Text;
import org.bukkit.Bukkit;
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

        return List.of();
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
