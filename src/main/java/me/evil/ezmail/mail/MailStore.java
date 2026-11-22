package me.evil.ezmail.mail;

import me.evil.ezmail.Ezmail;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 留言的内存索引与 YAML 持久化。
 * 所有方法都在锁内执行, 因此可以在异步保存线程中安全调用。
 */
public final class MailStore {

    private static final Comparator<Mail> NEWEST_FIRST =
            Comparator.comparingLong(Mail::sentAt).reversed();

    private final Ezmail plugin;
    private final File file;

    private final Map<UUID, Mail> byId = new LinkedHashMap<>();
    private final Map<UUID, List<Mail>> byRecipient = new HashMap<>();
    private final Map<String, List<Mail>> byPendingName = new HashMap<>();
    private final Map<UUID, List<Mail>> bySender = new HashMap<>();

    private boolean dirty;

    public MailStore(Ezmail plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mails.yml");
    }

    public synchronized void load() {
        byId.clear();
        if (!file.exists()) {
            rebuildIndexes();
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> entry : yaml.getMapList("mails")) {
            try {
                Mail mail = fromMap(entry);
                if (mail != null) {
                    byId.put(mail.id(), mail);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("跳过一条无法解析的留言数据: " + exception);
            }
        }
        rebuildIndexes();
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }

        List<Map<String, Object>> list = new ArrayList<>(byId.size());
        for (Mail mail : byId.values()) {
            list.add(toMap(mail));
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", 1);
        yaml.set("mails", list);

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().severe("无法创建插件数据目录: " + parent.getAbsolutePath());
                return;
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存留言数据: " + exception.getMessage());
        }
    }

    public synchronized int size() {
        return byId.size();
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void add(Mail mail) {
        byId.put(mail.id(), mail);
        rebuildIndexes();
        dirty = true;
    }

    public synchronized boolean remove(UUID mailId) {
        if (byId.remove(mailId) == null) {
            return false;
        }
        rebuildIndexes();
        dirty = true;
        return true;
    }

    public synchronized boolean markRead(Mail mail) {
        if (mail.markRead(System.currentTimeMillis())) {
            dirty = true;
            return true;
        }
        return false;
    }

    /**
     * 某个玩家的收件箱 (包含尚未绑定 UUID 的同名留言), 按时间倒序。
     */
    public synchronized List<Mail> inbox(UUID playerId, String playerName) {
        List<Mail> result = new ArrayList<>();
        List<Mail> direct = byRecipient.get(playerId);
        if (direct != null) {
            result.addAll(direct);
        }
        if (playerName != null) {
            List<Mail> pending = byPendingName.get(playerName.toLowerCase(Locale.ROOT));
            if (pending != null) {
                result.addAll(pending);
            }
        }
        result.sort(NEWEST_FIRST);
        return result;
    }

    public synchronized int unreadCount(UUID playerId, String playerName) {
        int count = 0;
        for (Mail mail : inbox(playerId, playerName)) {
            if (!mail.isRead()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 某个玩家发出的留言, 按时间倒序。
     */
    public synchronized List<Mail> sent(UUID senderId) {
        List<Mail> list = new ArrayList<>(bySender.getOrDefault(senderId, List.of()));
        list.sort(NEWEST_FIRST);
        return list;
    }

    /**
     * 服务器上的所有留言, 按时间倒序。
     */
    public synchronized List<Mail> all() {
        List<Mail> list = new ArrayList<>(byId.values());
        list.sort(NEWEST_FIRST);
        return list;
    }

    public synchronized int countSent(UUID senderId) {
        List<Mail> list = bySender.get(senderId);
        return list == null ? 0 : list.size();
    }

    public synchronized int countSentTo(UUID senderId, UUID recipientId, String recipientName) {
        List<Mail> list = bySender.get(senderId);
        if (list == null) {
            return 0;
        }
        int count = 0;
        for (Mail mail : list) {
            if (recipientId != null) {
                if (recipientId.equals(mail.recipientId())) {
                    count++;
                }
            } else if (mail.recipientName() != null && mail.recipientName().equalsIgnoreCase(recipientName)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 删除所有已读超过 autoDeleteMillis 的留言, 返回删除数量。
     */
    public synchronized int purgeExpired(long autoDeleteMillis) {
        if (autoDeleteMillis <= 0L) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int before = byId.size();
        byId.values().removeIf(mail -> mail.isRead() && now - mail.readAt() >= autoDeleteMillis);
        int removed = before - byId.size();
        if (removed > 0) {
            rebuildIndexes();
            dirty = true;
        }
        return removed;
    }

    /**
     * 把收件人从未登录过 (UUID 为空) 且名字匹配的留言绑定到该玩家, 返回绑定数量。
     */
    public synchronized int bindPending(UUID playerId, String playerName) {
        if (playerName == null) {
            return 0;
        }
        List<Mail> pending = byPendingName.get(playerName.toLowerCase(Locale.ROOT));
        if (pending == null || pending.isEmpty()) {
            return 0;
        }

        int bound = 0;
        for (Mail mail : pending) {
            if (mail.recipientId() == null) {
                mail.bindRecipient(playerId);
                bound++;
            }
        }
        if (bound > 0) {
            rebuildIndexes();
            dirty = true;
        }
        return bound;
    }

    private void rebuildIndexes() {
        byRecipient.clear();
        byPendingName.clear();
        bySender.clear();

        for (Mail mail : byId.values()) {
            if (mail.recipientId() != null) {
                byRecipient.computeIfAbsent(mail.recipientId(), key -> new ArrayList<>()).add(mail);
            } else {
                byPendingName.computeIfAbsent(mail.recipientName().toLowerCase(Locale.ROOT),
                        key -> new ArrayList<>()).add(mail);
            }
            bySender.computeIfAbsent(mail.senderId(), key -> new ArrayList<>()).add(mail);
        }
    }

    private Map<String, Object> toMap(Mail mail) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", mail.id().toString());
        map.put("sender", mail.senderId().toString());
        map.put("sender-name", mail.senderName());
        map.put("recipient", mail.recipientId() == null ? "" : mail.recipientId().toString());
        map.put("recipient-name", mail.recipientName());
        map.put("message", mail.message());
        map.put("sent-at", mail.sentAt());
        map.put("read-at", mail.readAt());
        return map;
    }

    private Mail fromMap(Map<?, ?> map) {
        UUID mailId = uuid(map.get("id"));
        UUID senderId = uuid(map.get("sender"));
        if (mailId == null || senderId == null) {
            return null;
        }

        UUID recipientId = uuid(map.get("recipient"));
        String recipientName = string(map.get("recipient-name"));
        String message = string(map.get("message"));
        if (recipientName == null || message == null) {
            return null;
        }

        return new Mail(
                mailId,
                senderId,
                string(map.get("sender-name")),
                recipientId,
                recipientName,
                message,
                number(map.get("sent-at"), 0L),
                number(map.get("read-at"), 0L));
    }

    private static UUID uuid(Object value) {
        String raw = string(value);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long number(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
