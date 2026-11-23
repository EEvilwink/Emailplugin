package me.evil.ezmail.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 解析后的插件配置。所有字段都是不可变的快照, /ezmail reload 时整体替换。
 */
public record Settings(
        boolean allowSelf,
        boolean allowUnknownRecipients,
        int maxLength,
        long cooldownMillis,
        long autoDeleteMillis,
        long broadcastDeleteMillis,
        int broadcastMaxLength,
        String broadcastConsoleName,
        boolean useClickCallbacks,
        String timeFormat,
        LimitSet defaultLimits,
        List<LimitGroup> limitGroups,
        ReminderSettings reminder,
        Map<String, SoundSpec> sounds,
        GuiSettings inbox,
        GuiSettings sent,
        GuiSettings admin,
        BroadcastGui broadcastGui,
        ItemTemplate filler,
        ItemTemplate previousPage,
        ItemTemplate nextPage,
        ItemTemplate closeButton
) {

    /** 单个权限组的发送数量限制。 */
    public record LimitSet(int total, int perRecipient) {
        public static final LimitSet UNLIMITED = new LimitSet(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /** 一个权限组: 拥有 permission 权限时使用 limits。 */
    public record LimitGroup(String id, String permission, LimitSet limits) {
    }

    /** 未读提醒设置。 */
    public record ReminderSettings(boolean onJoin, int intervalSeconds, SoundSpec sound) {
    }

    private static final GuiSettings DEFAULT_INBOX = new GuiSettings(
            "&8&l收件箱", 6,
            ItemTemplate.of(Material.PAPER, "&7收件箱",
                    List.of("&7未读: &c{unread} &8| &7已读: &a{read} &8| &7合计: &f{total}")),
            ItemTemplate.of(Material.RED_DYE, "&c未读留言",
                    List.of("&7发送者: &f{sender}", "&7发送时间: &f{time}", "&7状态: &c未读", "", "&e左键 &7查看留言")),
            ItemTemplate.of(Material.GREEN_DYE, "&a已读留言",
                    List.of("&7发送者: &f{sender}", "&7发送时间: &f{time}", "&7状态: &a已读", "&7自动删除: &e{countdown}")));

    private static final GuiSettings DEFAULT_SENT = new GuiSettings(
            "&8&l已发送", 6,
            ItemTemplate.of(Material.PAPER, "&7已发送的留言",
                    List.of("&7合计: &f{total} &8| &7对方未读: &c{unread} &8| &7对方已读: &a{read}")),
            ItemTemplate.of(Material.ORANGE_DYE, "&e等待读取",
                    List.of("&7收件人: &f{recipient}", "&7发送时间: &f{time}", "&7状态: &e对方未读", "", "&c右键 &7删除留言")),
            ItemTemplate.of(Material.LIME_DYE, "&a已被读取",
                    List.of("&7收件人: &f{recipient}", "&7发送时间: &f{time}", "&7状态: &a对方已读", "&7自动删除: &e{countdown}")));

    private static final GuiSettings DEFAULT_ADMIN = new GuiSettings(
            "&8&l管理菜单", 6,
            ItemTemplate.of(Material.PAPER, "&7所有留言",
                    List.of("&7合计: &f{total} &8| &7未读: &c{unread} &8| &7已读: &a{read}")),
            ItemTemplate.of(Material.RED_DYE, "&c未读留言",
                    List.of("&7发送者: &f{sender}", "&7收件人: &f{recipient}", "&7发送时间: &f{time}", "&7状态: &c未读")),
            ItemTemplate.of(Material.GREEN_DYE, "&a已读留言",
                    List.of("&7发送者: &f{sender}", "&7收件人: &f{recipient}", "&7发送时间: &f{time}",
                            "&7状态: &a已读", "&7待删除: &e{countdown}")));

    private static final ItemTemplate DEFAULT_FILLER = ItemTemplate.of(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    private static final ItemTemplate DEFAULT_PREVIOUS = ItemTemplate.of(Material.ARROW, "&e上一页", List.of("&7第 &f{page}&7/&f{pages} &7页"));
    private static final ItemTemplate DEFAULT_NEXT = ItemTemplate.of(Material.ARROW, "&e下一页", List.of("&7第 &f{page}&7/&f{pages} &7页"));
    private static final ItemTemplate DEFAULT_CLOSE = ItemTemplate.of(Material.BARRIER, "&c关闭", List.of("&7点击关闭界面"));

    private static final ItemTemplate DEFAULT_BROADCAST_UNREAD = ItemTemplate.of(Material.NETHER_STAR, "&6&l[公告]",
            List.of("&7发送者: &f{sender}", "&7发布时间: &f{time}", "&7状态: &c未读", "", "&e左键 &7查看公告"));
    private static final ItemTemplate DEFAULT_BROADCAST_READ = ItemTemplate.of(Material.PAPER, "&6&l[公告]",
            List.of("&7发送者: &f{sender}", "&7发布时间: &f{time}", "&7状态: &a已读",
                    "&7自动删除: &e{countdown}", "", "&e左键 &7再次查看"));
    private static final ItemTemplate DEFAULT_BROADCAST_ADMIN = ItemTemplate.of(Material.NETHER_STAR, "&6&l[公告]",
            List.of("&7发送者: &f{sender}", "&7接收者: &f{recipient}", "&7发布时间: &f{time}",
                    "&7已读人数: &f{readers}", "&7阅读后删除: &e{delete-after}", "",
                    "&e左键 &7查看内容", "&c右键 &7删除公告"));

    public static Settings load(FileConfiguration config, Logger logger) {
        boolean allowSelf = config.getBoolean("settings.allow-self", false);
        boolean allowUnknownRecipients = config.getBoolean("settings.allow-unknown-recipients", true);
        int maxLength = Math.max(1, config.getInt("settings.max-length", 200));
        long cooldownSeconds = Math.max(0L, config.getLong("settings.cooldown", 5L));
        long autoDeleteSeconds = Math.max(0L, config.getLong("settings.auto-delete-after-read", 604800L));
        boolean useClickCallbacks = config.getBoolean("settings.use-click-callbacks", true);
        String timeFormat = config.getString("settings.time-format", "yyyy-MM-dd HH:mm:ss");

        LimitSet defaultLimits = new LimitSet(
                Math.max(0, config.getInt("limits.default.total", 20)),
                Math.max(0, config.getInt("limits.default.per-recipient", 5)));

        List<LimitGroup> limitGroups = new ArrayList<>();
        ConfigurationSection groupSection = config.getConfigurationSection("limits.groups");
        if (groupSection != null) {
            for (String id : groupSection.getKeys(false)) {
                ConfigurationSection section = groupSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String permission = section.getString("permission", "ezmail.limit." + id);
                LimitSet limits = new LimitSet(
                        Math.max(0, section.getInt("total", defaultLimits.total())),
                        Math.max(0, section.getInt("per-recipient", defaultLimits.perRecipient())));
                limitGroups.add(new LimitGroup(id, permission, limits));
            }
        }

        ReminderSettings reminder = new ReminderSettings(
                config.getBoolean("reminder.on-join", true),
                Math.max(0, config.getInt("reminder.interval", 120)),
                SoundSpec.of(config.getString("reminder.sound", ""),
                        (float) config.getDouble("reminder.volume", 1.0D),
                        (float) config.getDouble("reminder.pitch", 1.0D)));

        Map<String, SoundSpec> sounds = new HashMap<>();
        ConfigurationSection soundSection = config.getConfigurationSection("sounds");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, SoundSpec.from(soundSection.getConfigurationSection(key)));
            }
        }

        ItemTemplate filler = ItemTemplate.from(config.getConfigurationSection("gui.buttons.filler"),
                DEFAULT_FILLER, logger, "gui.buttons.filler");
        ItemTemplate previous = ItemTemplate.from(config.getConfigurationSection("gui.buttons.previous"),
                DEFAULT_PREVIOUS, logger, "gui.buttons.previous");
        ItemTemplate next = ItemTemplate.from(config.getConfigurationSection("gui.buttons.next"),
                DEFAULT_NEXT, logger, "gui.buttons.next");
        ItemTemplate close = ItemTemplate.from(config.getConfigurationSection("gui.buttons.close"),
                DEFAULT_CLOSE, logger, "gui.buttons.close");

        GuiSettings inbox = loadGui(config, "gui.inbox", DEFAULT_INBOX, logger);
        GuiSettings sent = loadGui(config, "gui.sent", DEFAULT_SENT, logger);
        GuiSettings admin = loadGui(config, "gui.admin", DEFAULT_ADMIN, logger);

        long broadcastDeleteSeconds = Math.max(0L, config.getLong("broadcast.default-delete-after-read", 604800L));
        int broadcastMaxLength = Math.max(1, config.getInt("broadcast.max-length", 1000));
        String broadcastConsoleName = config.getString("broadcast.console-name", "服务器");
        if (broadcastConsoleName == null || broadcastConsoleName.isBlank()) {
            broadcastConsoleName = "服务器";
        }
        ConfigurationSection broadcastSection = config.getConfigurationSection("broadcast.gui");
        BroadcastGui broadcastGui = broadcastSection == null
                ? new BroadcastGui(DEFAULT_BROADCAST_UNREAD, DEFAULT_BROADCAST_READ, DEFAULT_BROADCAST_ADMIN)
                : new BroadcastGui(
                        ItemTemplate.from(broadcastSection.getConfigurationSection("unread"),
                                DEFAULT_BROADCAST_UNREAD, logger, "broadcast.gui.unread"),
                        ItemTemplate.from(broadcastSection.getConfigurationSection("read"),
                                DEFAULT_BROADCAST_READ, logger, "broadcast.gui.read"),
                        ItemTemplate.from(broadcastSection.getConfigurationSection("admin"),
                                DEFAULT_BROADCAST_ADMIN, logger, "broadcast.gui.admin"));

        return new Settings(
                allowSelf,
                allowUnknownRecipients,
                maxLength,
                cooldownSeconds * 1000L,
                autoDeleteSeconds * 1000L,
                broadcastDeleteSeconds * 1000L,
                broadcastMaxLength,
                broadcastConsoleName,
                useClickCallbacks,
                timeFormat,
                defaultLimits,
                List.copyOf(limitGroups),
                reminder,
                Map.copyOf(sounds),
                inbox,
                sent,
                admin,
                broadcastGui,
                filler,
                previous,
                next,
                close);
    }

    private static GuiSettings loadGui(FileConfiguration config, String path, GuiSettings fallback, Logger logger) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return fallback;
        }

        String title = section.getString("title", fallback.title());
        int rows = clamp(section.getInt("rows", fallback.rows()), 2, 6);
        ItemTemplate info = ItemTemplate.from(section.getConfigurationSection("info"), fallback.info(), logger, path + ".info");
        ItemTemplate unread = ItemTemplate.from(section.getConfigurationSection("unread"), fallback.unread(), logger, path + ".unread");
        ItemTemplate read = ItemTemplate.from(section.getConfigurationSection("read"), fallback.read(), logger, path + ".read");

        return new GuiSettings(title, rows, info, unread, read);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
