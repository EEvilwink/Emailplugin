package me.evil.ezmail.mail;

import me.evil.ezmail.Ezmail;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 公告的内存存储与 YAML 持久化 (broadcasts.yml)。
 */
public final class BroadcastStore {

    private static final Comparator<Broadcast> NEWEST_FIRST =
            Comparator.comparingLong(Broadcast::sentAt).reversed();

    private final Ezmail plugin;
    private final File file;
    private final Map<UUID, Broadcast> byId = new LinkedHashMap<>();

    private boolean dirty;

    public BroadcastStore(Ezmail plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "broadcasts.yml");
    }

    public synchronized void load() {
        byId.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> entry : yaml.getMapList("broadcasts")) {
            try {
                Broadcast broadcast = fromMap(entry);
                if (broadcast != null) {
                    byId.put(broadcast.id(), broadcast);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("跳过一条无法解析的公告数据: " + exception);
            }
        }
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }

        List<Map<String, Object>> list = new ArrayList<>(byId.size());
        for (Broadcast broadcast : byId.values()) {
            list.add(toMap(broadcast));
        }

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("format-version", 1);
        yaml.set("broadcasts", list);

        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().severe("无法创建插件数据目录: " + parent.getAbsolutePath());
                return;
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("无法保存公告数据: " + exception.getMessage());
        }
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized int size() {
        return byId.size();
    }

    public synchronized void add(Broadcast broadcast) {
        byId.put(broadcast.id(), broadcast);
        dirty = true;
    }

    public synchronized boolean remove(UUID broadcastId) {
        if (byId.remove(broadcastId) == null) {
            return false;
        }
        dirty = true;
        return true;
    }

    /**
     * 所有公告, 按发布时间倒序。
     */
    public synchronized List<Broadcast> all() {
        List<Broadcast> list = new ArrayList<>(byId.values());
        list.sort(NEWEST_FIRST);
        return list;
    }

    /**
     * 某个玩家还能看到且未读的公告数量。
     */
    public synchronized int unreadCount(UUID playerId) {
        int count = 0;
        for (Broadcast broadcast : byId.values()) {
            if (broadcast.isVisibleTo(playerId) && !broadcast.isReadBy(playerId)) {
                count++;
            }
        }
        return count;
    }

    public synchronized boolean markRead(Broadcast broadcast, UUID playerId) {
        if (broadcast.markRead(playerId, System.currentTimeMillis())) {
            dirty = true;
            return true;
        }
        return false;
    }

    /**
     * 把已到删除时间的公告从对应玩家的收件箱中移除。
     */
    public synchronized int purgeExpired() {
        long now = System.currentTimeMillis();
        int changed = 0;
        for (Broadcast broadcast : byId.values()) {
            changed += broadcast.expire(now);
        }
        if (changed > 0) {
            dirty = true;
        }
        return changed;
    }

    private Map<String, Object> toMap(Broadcast broadcast) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", broadcast.id().toString());
        map.put("sender", broadcast.senderId().toString());
        map.put("sender-name", broadcast.senderName());
        map.put("message", broadcast.message());
        map.put("sent-at", broadcast.sentAt());
        map.put("delete-after-read", broadcast.deleteAfterReadMillis());

        if (broadcast.icon() != null) {
            map.put("icon", broadcast.icon().name());
        }
        if (broadcast.displayName() != null) {
            map.put("name", broadcast.displayName());
        }
        if (broadcast.lore() != null) {
            map.put("lore", broadcast.lore());
        }

        Map<String, Object> players = new LinkedHashMap<>();
        for (Map.Entry<UUID, Broadcast.PlayerState> entry : broadcast.states().entrySet()) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("read-at", entry.getValue().readAt());
            state.put("hidden", entry.getValue().hidden());
            players.put(entry.getKey().toString(), state);
        }
        map.put("players", players);
        return map;
    }

    private Broadcast fromMap(Map<?, ?> map) {
        UUID id = uuid(map.get("id"));
        UUID senderId = uuid(map.get("sender"));
        if (id == null || senderId == null) {
            return null;
        }

        String message = string(map.get("message"));
        if (message == null) {
            return null;
        }

        Material icon = null;
        String rawIcon = string(map.get("icon"));
        if (rawIcon != null && !rawIcon.isBlank()) {
            icon = Material.matchMaterial(rawIcon.trim());
        }

        String displayName = string(map.get("name"));
        List<String> lore = null;
        Object rawLore = map.get("lore");
        if (rawLore instanceof List<?> lines) {
            lore = new ArrayList<>(lines.size());
            for (Object line : lines) {
                lore.add(String.valueOf(line));
            }
        }

        Broadcast broadcast = new Broadcast(
                id,
                senderId,
                string(map.get("sender-name")),
                message,
                number(map.get("sent-at"), 0L),
                number(map.get("delete-after-read"), 0L),
                icon,
                displayName,
                lore);

        Object rawPlayers = map.get("players");
        if (rawPlayers instanceof Map<?, ?> players) {
            for (Map.Entry<?, ?> entry : players.entrySet()) {
                UUID playerId = uuid(entry.getKey());
                if (playerId == null || !(entry.getValue() instanceof Map<?, ?> state)) {
                    continue;
                }
                broadcast.states().put(playerId, new Broadcast.PlayerState(
                        number(state.get("read-at"), 0L),
                        Boolean.TRUE.equals(state.get("hidden"))));
            }
        }

        return broadcast;
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
