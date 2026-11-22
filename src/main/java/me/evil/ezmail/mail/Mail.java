package me.evil.ezmail.mail;

import java.util.UUID;

/**
 * 一条留言。
 * 收件人 UUID 在对方从未登录过服务器时可能为 null, 此时会在同名玩家上线后绑定。
 */
public final class Mail {

    private final UUID id;
    private final UUID senderId;
    private final String senderName;
    private UUID recipientId;
    private final String recipientName;
    private final String message;
    private final long sentAt;
    private long readAt;

    public Mail(UUID id,
                UUID senderId,
                String senderName,
                UUID recipientId,
                String recipientName,
                String message,
                long sentAt,
                long readAt) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.recipientId = recipientId;
        this.recipientName = recipientName;
        this.message = message;
        this.sentAt = sentAt;
        this.readAt = readAt;
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

    public UUID recipientId() {
        return recipientId;
    }

    public String recipientName() {
        return recipientName;
    }

    public String message() {
        return message;
    }

    public long sentAt() {
        return sentAt;
    }

    public long readAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt > 0L;
    }

    /**
     * 标记为已读, 返回是否发生了变化。
     */
    public boolean markRead(long now) {
        if (readAt > 0L) {
            return false;
        }
        readAt = now;
        return true;
    }

    /**
     * 绑定收件人的 UUID, 用于从未登录过的同名玩家上线时补全。
     */
    public void bindRecipient(UUID uuid) {
        if (recipientId == null && uuid != null) {
            recipientId = uuid;
        }
    }

    /**
     * 该留言在 autoDeleteMillis 毫秒后的删除时间; 未读或未开启自动删除时返回 0。
     */
    public long deleteAt(long autoDeleteMillis) {
        if (!isRead() || autoDeleteMillis <= 0L) {
            return 0L;
        }
        return readAt + autoDeleteMillis;
    }
}
