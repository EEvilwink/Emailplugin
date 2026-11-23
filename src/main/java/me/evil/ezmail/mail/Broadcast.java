package me.evil.ezmail.mail;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全服公告: 所有玩家都能在收件箱中看到, 并且优先显示在收件箱最前面。
 * <p>
 * 与普通留言不同, 公告只有一条, 但每个玩家单独记录"已读时间"和"是否已从自己的收件箱移除":
 * 玩家阅读后开始倒计时, 到时间后只从该玩家的收件箱里消失, 不影响其他玩家。
 * 管理员可以在管理菜单中直接删除, 删除后所有玩家都看不到。
 */
public final class Broadcast {

    /**
     * 单个玩家对某条公告的状态。
     */
    public static final class PlayerState {

        private long readAt;
        private boolean hidden;

        public PlayerState(long readAt, boolean hidden) {
            this.readAt = readAt;
            this.hidden = hidden;
        }

        public long readAt() {
            return readAt;
        }

        public boolean hidden() {
            return hidden;
        }
    }

    private final UUID id;
    private final UUID senderId;
    private final String senderName;
    private final String message;
    private final long sentAt;
    private final long deleteAfterReadMillis;

    /** 以下三项由发送者通过指令单独定制, 为 null 时使用配置文件中的默认外观。 */
    private final Material icon;
    private final String displayName;
    private final List<String> lore;

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public Broadcast(UUID id,
                     UUID senderId,
                     String senderName,
                     String message,
                     long sentAt,
                     long deleteAfterReadMillis,
                     Material icon,
                     String displayName,
                     List<String> lore) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.message = message;
        this.sentAt = sentAt;
        this.deleteAfterReadMillis = Math.max(0L, deleteAfterReadMillis);
        this.icon = icon;
        this.displayName = displayName;
        this.lore = lore;
    }

    public UUID id() {
        return id;
    }

    public UUID senderId() {
        return senderId;
    }

    public String senderName() {
        return senderName;
    }

    public String message() {
        return message;
    }

    public long sentAt() {
        return sentAt;
    }

    public long deleteAfterReadMillis() {
        return deleteAfterReadMillis;
    }

    public Material icon() {
        return icon;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> lore() {
        return lore;
    }

    public Map<UUID, PlayerState> states() {
        return states;
    }

    /**
     * 该公告是否还会显示在这个玩家的收件箱里。
     */
    public boolean isVisibleTo(UUID playerId) {
        PlayerState state = states.get(playerId);
        return state == null || !state.hidden();
    }

    public boolean isReadBy(UUID playerId) {
        PlayerState state = states.get(playerId);
        return state != null && state.readAt() > 0L;
    }

    public long readAt(UUID playerId) {
        PlayerState state = states.get(playerId);
        return state == null ? 0L : state.readAt();
    }

    /**
     * 这个玩家阅读后, 该公告何时从收件箱移除; 未读或未开启自动删除时返回 0。
     */
    public long deleteAt(UUID playerId) {
        long readAt = readAt(playerId);
        if (readAt <= 0L || deleteAfterReadMillis <= 0L) {
            return 0L;
        }
        return readAt + deleteAfterReadMillis;
    }

    /**
     * 标记某个玩家已读, 返回是否发生了变化。
     */
    public boolean markRead(UUID playerId, long now) {
        PlayerState state = states.get(playerId);
        if (state == null) {
            states.put(playerId, new PlayerState(now, false));
            return true;
        }
        if (state.readAt <= 0L) {
            state.readAt = now;
            state.hidden = false;
            return true;
        }
        return false;
    }

    /**
     * 把公告从某个玩家的收件箱中移除。
     */
    public void hideFor(UUID playerId) {
        PlayerState state = states.get(playerId);
        if (state == null) {
            states.put(playerId, new PlayerState(0L, true));
        } else {
            state.hidden = true;
        }
    }

    /**
     * 已读过的玩家数量。
     */
    public int readerCount() {
        int count = 0;
        for (PlayerState state : states.values()) {
            if (state.readAt() > 0L) {
                count++;
            }
        }
        return count;
    }

    /**
     * 处理所有玩家已经到期的状态, 返回被移除的数量。
     */
    public int expire(long now) {
        if (deleteAfterReadMillis <= 0L) {
            return 0;
        }
        int changed = 0;
        for (PlayerState state : states.values()) {
            if (state.readAt() > 0L && !state.hidden && now - state.readAt >= deleteAfterReadMillis) {
                state.hidden = true;
                changed++;
            }
        }
        return changed;
    }
}
