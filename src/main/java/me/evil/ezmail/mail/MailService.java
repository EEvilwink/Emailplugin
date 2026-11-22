package me.evil.ezmail.mail;

import me.evil.ezmail.Ezmail;
import me.evil.ezmail.config.Lang;
import me.evil.ezmail.config.Settings;
import me.evil.ezmail.gui.GuiType;
import me.evil.ezmail.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 留言的业务逻辑: 发送校验、读取、删除、未读提醒。
 */
public final class MailService {

    private final Ezmail plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public MailService(Ezmail plugin) {
        this.plugin = plugin;
    }

    /**
     * 发送一条留言, 所有提示都会直接发给发送者。
     */
    public boolean send(Player sender, String targetName, String content) {
        Lang lang = plugin.lang();
        Settings settings = plugin.settings();

        if (!sender.hasPermission("ezmail.send")) {
            lang.send(sender, "no-permission");
            plugin.sound("error").play(sender);
            return false;
        }

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            lang.send(sender, "empty-content");
            plugin.sound("error").play(sender);
            return false;
        }
        if (trimmed.length() > settings.maxLength()) {
            lang.send(sender, "too-long", Map.of("max", Integer.toString(settings.maxLength())));
            plugin.sound("error").play(sender);
            return false;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        UUID recipientId = null;
        String recipientName = targetName;

        if (online != null) {
            recipientId = online.getUniqueId();
            recipientName = online.getName();
        } else {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(targetName);
            if (cached != null) {
                recipientId = cached.getUniqueId();
                String name = cached.getName();
                if (name != null && !name.isBlank()) {
                    recipientName = name;
                }
            } else if (!settings.allowUnknownRecipients()) {
                lang.send(sender, "player-not-found", Map.of("player", targetName));
                plugin.sound("error").play(sender);
                return false;
            }
        }

        boolean self = recipientId != null
                ? recipientId.equals(sender.getUniqueId())
                : recipientName.equalsIgnoreCase(sender.getName());
        if (self && !settings.allowSelf()) {
            lang.send(sender, "cannot-send-self");
            plugin.sound("error").play(sender);
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldown = settings.cooldownMillis();
        if (cooldown > 0L && !sender.hasPermission("ezmail.bypass.cooldown")) {
            long remaining = cooldownUntil.getOrDefault(sender.getUniqueId(), 0L) - now;
            if (remaining > 0L) {
                lang.send(sender, "cooldown", Map.of("seconds", Long.toString((remaining + 999L) / 1000L)));
                plugin.sound("error").play(sender);
                return false;
            }
        }

        Settings.LimitSet limits = limitsFor(sender);
        int sentCount = plugin.store().countSent(sender.getUniqueId());
        if (sentCount >= limits.total()) {
            lang.send(sender, "limit-total", Map.of(
                    "current", Integer.toString(sentCount),
                    "limit", Integer.toString(limits.total())));
            plugin.sound("error").play(sender);
            return false;
        }

        int sentToRecipient = plugin.store().countSentTo(sender.getUniqueId(), recipientId, recipientName);
        if (sentToRecipient >= limits.perRecipient()) {
            lang.send(sender, "limit-recipient", Map.of(
                    "player", recipientName,
                    "limit", Integer.toString(limits.perRecipient())));
            plugin.sound("error").play(sender);
            return false;
        }

        Mail mail = new Mail(UUID.randomUUID(), sender.getUniqueId(), sender.getName(),
                recipientId, recipientName, trimmed, now, 0L);
        plugin.store().add(mail);

        if (cooldown > 0L) {
            cooldownUntil.put(sender.getUniqueId(), now + cooldown);
        }

        lang.send(sender, "send-success", Map.of("player", recipientName));
        plugin.sound("send").play(sender);

        if (online != null) {
            notifyUnread(online);
        }
        return true;
    }

    /**
     * 打开收件箱中的留言: 标记为已读并把内容输出到聊天栏。
     */
    public void readInbox(Player reader, Mail mail) {
        boolean wasUnread = !mail.isRead();
        plugin.store().markRead(mail);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player_sender", mail.senderName());
        placeholders.put("email", mail.message());
        placeholders.put("time", TimeUtil.format(mail.sentAt(), plugin.settings().timeFormat()));
        reader.sendMessage(plugin.lang().raw("mail-format", placeholders));

        if (wasUnread) {
            plugin.sound("read").play(reader);
        }
    }

    /**
     * 发送方查看自己发出的留言内容 (不会改变已读状态)。
     */
    public void previewSent(Player sender, Mail mail) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player_recipient", mail.recipientName());
        placeholders.put("email", mail.message());
        placeholders.put("time", TimeUtil.format(mail.sentAt(), plugin.settings().timeFormat()));
        sender.sendMessage(plugin.lang().raw("sent-format", placeholders));
    }

    /**
     * 管理员查看任意留言内容 (不会改变已读状态)。
     */
    public void previewAdmin(Player admin, Mail mail) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("sender", mail.senderName());
        placeholders.put("recipient", mail.recipientName());
        placeholders.put("email", mail.message());
        placeholders.put("time", TimeUtil.format(mail.sentAt(), plugin.settings().timeFormat()));
        admin.sendMessage(plugin.lang().raw("admin-format", placeholders));
    }

    /**
     * 删除留言, 删除后收件人将不再收到该留言。
     */
    public void delete(Player actor, Mail mail, boolean admin) {
        plugin.store().remove(mail.id());

        if (admin) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("sender", mail.senderName());
            placeholders.put("recipient", mail.recipientName());
            plugin.lang().send(actor, "admin-delete-success", placeholders);
        } else {
            plugin.lang().send(actor, "delete-success");
        }
        plugin.sound("delete").play(actor);
    }

    /**
     * 向玩家发送未读提醒 (可点击打开收件箱)。
     */
    public void notifyUnread(Player player) {
        int unread = plugin.store().unreadCount(player.getUniqueId(), player.getName());
        if (unread <= 0) {
            return;
        }

        Component message = plugin.lang()
                .prefixed("notify-unread", Map.of("count", Integer.toString(unread)))
                .clickEvent(inboxClick())
                .hoverEvent(HoverEvent.showText(plugin.lang().raw("notify-hover")));
        player.sendMessage(message);
        plugin.settings().reminder().sound().play(player);
    }

    /**
     * 玩家离线时清理冷却记录, 避免长期运行后累积。
     */
    public void clearCooldown(UUID playerId) {
        cooldownUntil.remove(playerId);
    }

    /**
     * 计算玩家的发送数量限制 (取所有已拥有权限组中的最大值)。
     */
    public Settings.LimitSet limitsFor(Player player) {
        if (player.hasPermission("ezmail.bypass.limit")) {
            return Settings.LimitSet.UNLIMITED;
        }

        Settings settings = plugin.settings();
        Settings.LimitSet limits = settings.defaultLimits();
        for (Settings.LimitGroup group : settings.limitGroups()) {
            if (!player.hasPermission(group.permission())) {
                continue;
            }
            limits = new Settings.LimitSet(
                    Math.max(limits.total(), group.limits().total()),
                    Math.max(limits.perRecipient(), group.limits().perRecipient()));
        }
        return limits;
    }

    private ClickEvent<?> inboxClick() {
        Settings settings = plugin.settings();
        if (!settings.useClickCallbacks()) {
            return ClickEvent.runCommand("/ezmail inbox");
        }

        return ClickEvent.callback(audience -> {
            if (audience instanceof Player player) {
                plugin.guiManager().open(player, GuiType.INBOX);
            }
        }, ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(Duration.ofHours(12))
                .build());
    }
}
