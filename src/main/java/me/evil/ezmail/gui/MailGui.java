package me.evil.ezmail.gui;

import me.evil.ezmail.Ezmail;
import me.evil.ezmail.config.GuiSettings;
import me.evil.ezmail.config.ItemTemplate;
import me.evil.ezmail.mail.Broadcast;
import me.evil.ezmail.mail.Mail;
import me.evil.ezmail.util.Text;
import me.evil.ezmail.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一个打开的邮箱界面 (收件箱 / 已发送 / 管理菜单)。
 * 界面只显示发送者、时间、状态等信息, 不显示留言正文, 点击后才会输出到聊天栏。
 * <p>
 * 收件箱中公告会优先显示在最前面, 并且每个玩家单独记录公告的已读状态。
 */
public final class MailGui implements InventoryHolder {

    /**
     * 界面中的一格内容: 要么是一条普通留言, 要么是一条公告。
     */
    private record Entry(Mail mail, Broadcast broadcast) {

        static Entry of(Mail mail) {
            return new Entry(mail, null);
        }

        static Entry of(Broadcast broadcast) {
            return new Entry(null, broadcast);
        }

        boolean isBroadcast() {
            return broadcast != null;
        }
    }

    private final Ezmail plugin;
    private final Player viewer;
    private final GuiType type;
    private final GuiSettings layout;
    private final Inventory inventory;
    private final Map<Integer, Entry> slotEntries = new HashMap<>();

    private int page;
    private int pages;

    public MailGui(Ezmail plugin, Player viewer, GuiType type, int requestedPage) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.type = type;
        this.layout = layoutFor(plugin, type);

        List<Entry> entries = loadEntries();
        int contentSize = layout.contentSize();
        this.pages = Math.max(1, (entries.size() + contentSize - 1) / contentSize);
        this.page = Math.min(Math.max(1, requestedPage), pages);
        this.inventory = Bukkit.createInventory(this, layout.size(), title());

        render(entries);
    }

    /**
     * 把界面展示给玩家。
     */
    public void open() {
        viewer.openInventory(inventory);
        plugin.sound("open").play(viewer);
    }

    /**
     * 重新读取数据并刷新界面内容 (翻页、读取、删除、自动删除后调用)。
     */
    public void refresh() {
        List<Entry> entries = loadEntries();
        int contentSize = layout.contentSize();
        this.pages = Math.max(1, (entries.size() + contentSize - 1) / contentSize);
        if (page > pages) {
            page = pages;
        }
        render(entries);
    }

    /**
     * 处理界面中的一次点击。
     */
    public void handleClick(int slot, boolean rightClick) {
        if (slot == layout.closeSlot()) {
            viewer.closeInventory();
            return;
        }
        if (slot == layout.previousSlot()) {
            navigate(-1);
            return;
        }
        if (slot == layout.nextSlot()) {
            navigate(1);
            return;
        }

        Entry entry = slotEntries.get(slot);
        if (entry == null) {
            return;
        }

        if (entry.isBroadcast()) {
            handleBroadcastClick(entry.broadcast(), rightClick);
            return;
        }

        Mail mail = entry.mail();
        switch (type) {
            case INBOX -> {
                plugin.mailService().readInbox(viewer, mail);
                refresh();
            }
            case SENT -> {
                if (rightClick) {
                    plugin.mailService().delete(viewer, mail, false);
                    refresh();
                } else {
                    plugin.mailService().previewSent(viewer, mail);
                }
            }
            case ADMIN -> {
                if (rightClick) {
                    plugin.mailService().delete(viewer, mail, true);
                    refresh();
                } else {
                    plugin.mailService().previewAdmin(viewer, mail);
                }
            }
        }
    }

    private void handleBroadcastClick(Broadcast broadcast, boolean rightClick) {
        switch (type) {
            case INBOX -> {
                plugin.mailService().readBroadcast(viewer, broadcast);
                refresh();
            }
            case ADMIN -> {
                if (rightClick) {
                    plugin.mailService().deleteBroadcast(viewer, broadcast);
                    refresh();
                } else {
                    plugin.mailService().previewBroadcast(viewer, broadcast);
                }
            }
            // 已发送界面不显示公告
            case SENT -> {
            }
        }
    }

    private void navigate(int delta) {
        int target = Math.min(Math.max(1, page + delta), pages);
        if (target != page) {
            plugin.guiManager().open(viewer, type, target);
        }
    }

    private void render(List<Entry> entries) {
        int size = layout.size();
        ItemStack filler = plugin.settings().filler().build(Map.of());
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }
        slotEntries.clear();

        int start = (page - 1) * layout.contentSize();
        for (int slot = 0; slot < layout.contentSize(); slot++) {
            int index = start + slot;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            slotEntries.put(slot, entry);
            inventory.setItem(slot, buildEntryItem(entry));
        }

        Map<String, String> navigation = Map.of(
                "page", Integer.toString(page),
                "pages", Integer.toString(pages));

        if (page > 1) {
            inventory.setItem(layout.previousSlot(), plugin.settings().previousPage().build(navigation));
        }
        if (page < pages) {
            inventory.setItem(layout.nextSlot(), plugin.settings().nextPage().build(navigation));
        }
        inventory.setItem(layout.closeSlot(), plugin.settings().closeButton().build(navigation));
        inventory.setItem(layout.infoSlot(), layout.info().build(infoPlaceholders(entries)));
    }

    private ItemStack buildEntryItem(Entry entry) {
        if (entry.isBroadcast()) {
            Broadcast broadcast = entry.broadcast();
            boolean read = broadcast.isReadBy(viewer.getUniqueId());
            ItemTemplate template;
            if (type == GuiType.ADMIN) {
                template = plugin.settings().broadcastGui().admin();
            } else {
                template = read
                        ? plugin.settings().broadcastGui().read()
                        : plugin.settings().broadcastGui().unread();
            }
            return buildBroadcastItem(template, broadcast);
        }

        Mail mail = entry.mail();
        ItemTemplate template = mail.isRead() ? layout.read() : layout.unread();
        return template.build(mailPlaceholders(mail));
    }

    /**
     * 公告物品可以按条自定义材质 / 名称 / 描述, 没有自定义时使用配置中的模板。
     */
    private ItemStack buildBroadcastItem(ItemTemplate template, Broadcast broadcast) {
        Material material = broadcast.icon() != null ? broadcast.icon() : template.material();
        String name = broadcast.displayName() != null ? broadcast.displayName() : template.name();
        List<String> lore = broadcast.lore() != null ? broadcast.lore() : template.lore();
        ItemTemplate effective = new ItemTemplate(material, name, lore, template.amount());
        return effective.build(broadcastPlaceholders(broadcast));
    }

    private Map<String, String> infoPlaceholders(List<Entry> entries) {
        int unread = countUnread(entries);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("total", Integer.toString(entries.size()));
        placeholders.put("unread", Integer.toString(unread));
        placeholders.put("read", Integer.toString(entries.size() - unread));
        placeholders.put("page", Integer.toString(page));
        placeholders.put("pages", Integer.toString(pages));
        placeholders.put("auto-delete", autoDeleteText());
        return placeholders;
    }

    private Map<String, String> mailPlaceholders(Mail mail) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", mail.id().toString());
        placeholders.put("sender", mail.senderName());
        placeholders.put("recipient", mail.recipientName());
        placeholders.put("time", TimeUtil.format(mail.sentAt(), plugin.settings().timeFormat()));
        placeholders.put("status", plugin.lang().rawString(mail.isRead() ? "status-read" : "status-unread"));
        placeholders.put("countdown", countdown(mail));
        placeholders.put("page", Integer.toString(page));
        placeholders.put("pages", Integer.toString(pages));
        return placeholders;
    }

    private Map<String, String> broadcastPlaceholders(Broadcast broadcast) {
        UUID playerId = viewer.getUniqueId();
        boolean read = broadcast.isReadBy(playerId);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", broadcast.id().toString());
        placeholders.put("sender", broadcast.senderName());
        placeholders.put("recipient", plugin.lang().rawString("broadcast-recipient"));
        placeholders.put("time", TimeUtil.format(broadcast.sentAt(), plugin.settings().timeFormat()));
        placeholders.put("status", plugin.lang().rawString(read ? "status-read" : "status-unread"));
        placeholders.put("countdown", broadcastCountdown(broadcast));
        placeholders.put("readers", Integer.toString(broadcast.readerCount()));
        placeholders.put("delete-after", broadcastDeleteAfter(broadcast));
        placeholders.put("page", Integer.toString(page));
        placeholders.put("pages", Integer.toString(pages));
        return placeholders;
    }

    private String countdown(Mail mail) {
        long autoDelete = plugin.settings().autoDeleteMillis();
        long deleteAt = mail.deleteAt(autoDelete);
        if (deleteAt <= 0L) {
            return plugin.lang().rawString("countdown-never");
        }
        return TimeUtil.duration(deleteAt - System.currentTimeMillis(), plugin.lang());
    }

    private String broadcastCountdown(Broadcast broadcast) {
        long deleteAt = broadcast.deleteAt(viewer.getUniqueId());
        if (deleteAt <= 0L) {
            return plugin.lang().rawString("countdown-never");
        }
        return TimeUtil.duration(deleteAt - System.currentTimeMillis(), plugin.lang());
    }

    private String broadcastDeleteAfter(Broadcast broadcast) {
        long deleteAfter = broadcast.deleteAfterReadMillis();
        if (deleteAfter <= 0L) {
            return plugin.lang().rawString("countdown-never");
        }
        return TimeUtil.duration(deleteAfter, plugin.lang());
    }

    private String autoDeleteText() {
        long autoDelete = plugin.settings().autoDeleteMillis();
        if (autoDelete <= 0L) {
            return plugin.lang().rawString("countdown-never");
        }
        return TimeUtil.duration(autoDelete, plugin.lang());
    }

    private int countUnread(List<Entry> entries) {
        UUID playerId = viewer.getUniqueId();
        int count = 0;
        for (Entry entry : entries) {
            boolean unread = entry.isBroadcast()
                    ? !entry.broadcast().isReadBy(playerId)
                    : !entry.mail().isRead();
            if (unread) {
                count++;
            }
        }
        return count;
    }

    /**
     * 读取界面要显示的内容。收件箱与管理菜单中公告排在普通留言前面。
     */
    private List<Entry> loadEntries() {
        UUID playerId = viewer.getUniqueId();
        List<Entry> entries = new ArrayList<>();

        switch (type) {
            case INBOX -> {
                for (Broadcast broadcast : plugin.broadcastStore().all()) {
                    if (broadcast.isVisibleTo(playerId)) {
                        entries.add(Entry.of(broadcast));
                    }
                }
                for (Mail mail : plugin.store().inbox(playerId, viewer.getName())) {
                    entries.add(Entry.of(mail));
                }
            }
            case SENT -> {
                for (Mail mail : plugin.store().sent(playerId)) {
                    entries.add(Entry.of(mail));
                }
            }
            case ADMIN -> {
                for (Broadcast broadcast : plugin.broadcastStore().all()) {
                    entries.add(Entry.of(broadcast));
                }
                for (Mail mail : plugin.store().all()) {
                    entries.add(Entry.of(mail));
                }
            }
        }
        return entries;
    }

    private net.kyori.adventure.text.Component title() {
        return Text.format(layout.title(), Map.of(
                "page", Integer.toString(page),
                "pages", Integer.toString(pages)));
    }

    private static GuiSettings layoutFor(Ezmail plugin, GuiType type) {
        return switch (type) {
            case INBOX -> plugin.settings().inbox();
            case SENT -> plugin.settings().sent();
            case ADMIN -> plugin.settings().admin();
        };
    }

    public Player viewer() {
        return viewer;
    }

    public GuiType type() {
        return type;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
