package me.evil.ezmail.gui;

import me.evil.ezmail.Ezmail;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 管理每个玩家当前打开的邮箱界面。
 */
public final class GuiManager {

    private final Ezmail plugin;
    private final Map<UUID, MailGui> open = new HashMap<>();

    public GuiManager(Ezmail plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, GuiType type) {
        open(player, type, 1);
    }

    public void open(Player player, GuiType type, int page) {
        MailGui gui = new MailGui(plugin, player, type, page);
        // openInventory 会先关闭旧界面并触发 InventoryCloseEvent, 因此新界面要在之后登记
        gui.open();
        open.put(player.getUniqueId(), gui);
    }

    /**
     * 玩家关闭界面时移除记录 (只有同一个界面实例才会被移除)。
     */
    public void handleClose(Player player, MailGui gui) {
        open.remove(player.getUniqueId(), gui);
    }

    public void close(Player player) {
        open.remove(player.getUniqueId());
    }

    /**
     * 关闭所有界面 (重载配置时使用, 避免新旧布局不一致)。
     */
    public void closeAll() {
        // 关闭界面会触发 InventoryCloseEvent 并修改 open, 因此先复制一份
        for (Map.Entry<UUID, MailGui> entry : new ArrayList<>(open.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory().getHolder() == entry.getValue()) {
                player.closeInventory();
            }
        }
        open.clear();
    }

    /**
     * 定时刷新所有已打开界面, 顺便清理已经失效的记录。
     */
    public void refreshAll() {
        Iterator<Map.Entry<UUID, MailGui>> iterator = open.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MailGui> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            MailGui gui = entry.getValue();

            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof MailGui current) || current != gui) {
                iterator.remove();
                continue;
            }
            gui.refresh();
        }
    }
}
