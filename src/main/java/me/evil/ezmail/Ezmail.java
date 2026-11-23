package me.evil.ezmail;

import me.evil.ezmail.command.EzmailCommand;
import me.evil.ezmail.config.Lang;
import me.evil.ezmail.config.Settings;
import me.evil.ezmail.config.SoundSpec;
import me.evil.ezmail.gui.GuiManager;
import me.evil.ezmail.listener.GuiListener;
import me.evil.ezmail.listener.PlayerListener;
import me.evil.ezmail.mail.BroadcastStore;
import me.evil.ezmail.mail.MailService;
import me.evil.ezmail.mail.MailStore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Ezmail 主类。
 */
public final class Ezmail extends JavaPlugin {

    private final Map<String, BukkitTask> tasks = new HashMap<>();

    private Settings settings;
    private Lang lang;
    private MailStore store;
    private BroadcastStore broadcastStore;
    private MailService mailService;
    private GuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        applyDefaults();
        this.lang = new Lang(getConfig());
        this.settings = Settings.load(getConfig(), getLogger());

        this.store = new MailStore(this);
        this.store.load();

        this.broadcastStore = new BroadcastStore(this);
        this.broadcastStore.load();

        this.mailService = new MailService(this);
        this.guiManager = new GuiManager(this);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        EzmailCommand executor = new EzmailCommand(this);
        PluginCommand command = getCommand("ezmail");
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().severe("Unable to register the /ezmail command, please check plugin.yml.");
        }

        scheduleTasks();
        getLogger().info("Ezmail enabled with " + store.size() + " stored mail(s) and "
                + broadcastStore.size() + " broadcast(s).");
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (store != null) {
            store.save();
        }
        if (broadcastStore != null) {
            broadcastStore.save();
        }
    }

    /**
     * 重载配置。已打开的界面会被关闭, 玩家重新打开即可使用新配置。
     */
    public void reloadPlugin() {
        reloadConfig();
        applyDefaults();
        this.lang = new Lang(getConfig());
        this.settings = Settings.load(getConfig(), getLogger());
        this.guiManager.closeAll();
        scheduleTasks();
    }

    public Settings settings() {
        return settings;
    }

    public Lang lang() {
        return lang;
    }

    public MailStore store() {
        return store;
    }

    public BroadcastStore broadcastStore() {
        return broadcastStore;
    }

    public MailService mailService() {
        return mailService;
    }

    public GuiManager guiManager() {
        return guiManager;
    }

    public SoundSpec sound(String key) {
        return settings.sounds().getOrDefault(key, SoundSpec.NONE);
    }

    /**
     * 把 jar 内自带的 config.yml 设为默认值, 这样玩家删掉配置项时也能正常读取。
     */
    private void applyDefaults() {
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                return;
            }
            FileConfiguration config = getConfig();
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
        } catch (IOException exception) {
            getLogger().warning("无法读取默认配置: " + exception.getMessage());
        }
    }

    private void scheduleTasks() {
        cancelTasks();

        int reminderInterval = settings.reminder().intervalSeconds();
        if (reminderInterval > 0) {
            long period = reminderInterval * 20L;
            tasks.put("reminder", getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    mailService.notifyUnread(player);
                }
            }, period, period));
        }

        tasks.put("purge", getServer().getScheduler().runTaskTimer(this, () -> {
            store.purgeExpired(settings.autoDeleteMillis());
            broadcastStore.purgeExpired();
        }, 20L * 60L, 20L * 60L));

        tasks.put("save", getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (store.isDirty()) {
                store.save();
            }
            if (broadcastStore.isDirty()) {
                broadcastStore.save();
            }
        }, 20L * 30L, 20L * 30L));

        tasks.put("refresh", getServer().getScheduler().runTaskTimer(this,
                () -> guiManager.refreshAll(),
                20L * 5L, 20L * 5L));
    }

    private void cancelTasks() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }
}
