package me.evil.ezmail.config;

import me.evil.ezmail.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息文本的读取与发送。
 */
public final class Lang {

    private final Component prefix;
    private final Map<String, String> messages = new HashMap<>();
    private final List<String> help = new ArrayList<>();

    public Lang(FileConfiguration config) {
        this.prefix = Text.parse(config.getString("messages.prefix", ""));

        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (section.isList(key)) {
                    if ("help".equals(key)) {
                        help.addAll(section.getStringList(key));
                    }
                    continue;
                }
                String value = section.getString(key);
                if (value != null) {
                    messages.put(key, value);
                }
            }
        }
    }

    public Component prefix() {
        return prefix;
    }

    public List<String> helpLines() {
        return help;
    }

    public String rawString(String key) {
        String value = messages.get(key);
        return value == null ? key : value;
    }

    public Component raw(String key) {
        return raw(key, Map.of());
    }

    public Component raw(String key, Map<String, String> placeholders) {
        String value = messages.get(key);
        if (value == null) {
            return Component.text("<缺失的消息: " + key + ">", NamedTextColor.RED);
        }
        return Text.format(value, placeholders);
    }

    public Component prefixed(String key, Map<String, String> placeholders) {
        return prefix.append(raw(key, placeholders));
    }

    public Component prefixed(String key) {
        return prefixed(key, Map.of());
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (!messages.containsKey(key)) {
            sender.sendMessage(Component.text("<缺失的消息: " + key + ">", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(prefixed(key, placeholders));
    }

    public void send(CommandSender sender, String key, String... pairs) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            placeholders.put(pairs[i], pairs[i + 1]);
        }
        send(sender, key, placeholders);
    }
}
