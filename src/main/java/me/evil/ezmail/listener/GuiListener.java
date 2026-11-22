package me.evil.ezmail.listener;

import me.evil.ezmail.Ezmail;
import me.evil.ezmail.gui.MailGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * 拦截邮箱界面中的点击 / 拖动 / 关闭。
 */
public final class GuiListener implements Listener {

    private final Ezmail plugin;

    public GuiListener(Ezmail plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MailGui gui)) {
            return;
        }

        // 邮箱界面不允许拿走 / 放入任何物品
        event.setCancelled(true);

        if (event.getClickedInventory() != top) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!gui.viewer().getUniqueId().equals(player.getUniqueId())) {
            return;
        }

        gui.handleClick(event.getRawSlot(), event.isRightClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MailGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MailGui gui && event.getPlayer() instanceof Player player) {
            plugin.guiManager().handleClose(player, gui);
        }
    }
}
