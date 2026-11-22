package me.evil.ezmail.gui;

import me.evil.ezmail.Ezmail;
import me.evil.ezmail.config.GuiSettings;
import me.evil.ezmail.config.ItemTemplate;
import me.evil.ezmail.mail.Mail;
import me.evil.ezmail.util.Text;
import me.evil.ezmail.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个打开的邮箱界面 (收件箱 / 已发送 / 管理菜单)。
 * 界面只显示发送者、时间、状态等信息, 不显示留言正文, 点击后才会输出到聊天栏。
 */
public final class MailGui implements InventoryHolder {

    private final Ezmail plugin;
    private final Player viewer;
    private final GuiType type;
    private final GuiSettings layout;
    private final Inventory inventory;
    private final Map<Integer, Mail> slotMails = new HashMap<>();

    private int page;
    private int pages;

    public MailGui(Ezmail plugin, Player viewer, GuiType type, int requestedPage) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.type = type;
        this.layout = layoutFor(plugin, type);

        List<Mail> mails = loadMails();
        int contentSize = layout.contentSize();
        this.pages = Math.max(1, (mails.size() + contentSize - 1) / contentSize);
        this.page = Math.min(Math.max(1, requestedPage), pages);
        this.inventory = Bukkit.createInventory(this, layout.size(), title());

        render(mails);
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
        List<Mail> mails = loadMails();
        int contentSize = layout.contentSize();
        this.pages = Math.max(1, (mails.size() + contentSize - 1) / contentSize);
        if (page > pages) {
            page = pages;
        }
        render(mails);
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

        Mail mail = slotMails.get(slot);
        if (mail == null) {
            return;
        }

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

    private void navigate(int delta) {
        int target = Math.min(Math.max(1, page + delta), pages);
        if (target != page) {
            plugin.guiManager().open(viewer, type, target);
        }
    }

    private void render(List<Mail> mails) {
        int size = layout.size();
        ItemStack filler = plugin.settings().filler().build(Map.of());
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }
        slotMails.clear();

        int start = (page - 1) * layout.contentSize();
        for (int slot = 0; slot < layout.contentSize(); slot++) {
            int index = start + slot;
            if (index >= mails.size()) {
                break;
            }
            Mail mail = mails.get(index);
            slotMails.put(slot, mail);

            ItemTemplate template = mail.isRead() ? layout.read() : layout.unread();
            inventory.setItem(slot, template.build(mailPlaceholders(mail)));
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
        inventory.setItem(layout.infoSlot(), layout.info().build(infoPlaceholders(mails)));
    }

    private Map<String, String> infoPlaceholders(List<Mail> mails) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("total", Integer.toString(mails.size()));
        placeholders.put("unread", Integer.toString(countUnread(mails)));
        placeholders.put("read", Integer.toString(mails.size() - countUnread(mails)));
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

    private String countdown(Mail mail) {
        long autoDelete = plugin.settings().autoDeleteMillis();
        long deleteAt = mail.deleteAt(autoDelete);
        if (deleteAt <= 0L) {
            return plugin.lang().rawString("countdown-never");
        }
        return TimeUtil.duration(deleteAt - System.currentTimeMillis(), plugin.lang());
    }

    private String autoDeleteText() {
        long autoDelete = plugin.settings().autoDeleteMillis();
        if (autoDelete <= 0L) {
            return plugin.lang().rawString("countdown-never");
        }
        return TimeUtil.duration(autoDelete, plugin.lang());
    }

    private static int countUnread(List<Mail> mails) {
        int count = 0;
        for (Mail mail : mails) {
            if (!mail.isRead()) {
                count++;
            }
        }
        return count;
    }

    private List<Mail> loadMails() {
        return switch (type) {
            case INBOX -> plugin.store().inbox(viewer.getUniqueId(), viewer.getName());
            case SENT -> plugin.store().sent(viewer.getUniqueId());
            case ADMIN -> plugin.store().all();
        };
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
