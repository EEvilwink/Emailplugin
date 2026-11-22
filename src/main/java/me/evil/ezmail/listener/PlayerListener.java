package me.evil.ezmail.listener;

import me.evil.ezmail.Ezmail;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家上线 / 下线: 补全离线留言的收件人、发送未读提醒、清理界面记录。
 */
public final class PlayerListener implements Listener {

    private final Ezmail plugin;

    public PlayerListener(Ezmail plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.store().bindPending(player.getUniqueId(), player.getName());

        if (!plugin.settings().reminder().onJoin() || plugin.store().unreadCount(player.getUniqueId(), player.getName()) <= 0) {
            return;
        }

        // 稍等一下, 等玩家真正进入世界后再提示, 避免被登录信息刷掉
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.mailService().notifyUnread(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.guiManager().close(event.getPlayer());
        plugin.mailService().clearCooldown(event.getPlayer().getUniqueId());
    }
}
