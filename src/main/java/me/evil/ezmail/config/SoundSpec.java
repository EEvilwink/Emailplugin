package me.evil.ezmail.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 一个可播放的音效配置。
 */
public record SoundSpec(Sound sound, float volume, float pitch) {

    public static final SoundSpec NONE = new SoundSpec(null, 1.0F, 1.0F);

    public void play(Player player) {
        if (sound == null || player == null || !player.isOnline()) {
            return;
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static SoundSpec from(ConfigurationSection section) {
        if (section == null) {
            return NONE;
        }
        String name = section.getString("sound", "");
        if (name == null || name.isBlank()) {
            return NONE;
        }
        Sound sound = find(name);
        if (sound == null) {
            return NONE;
        }
        float volume = (float) section.getDouble("volume", 1.0D);
        float pitch = (float) section.getDouble("pitch", 1.0D);
        return new SoundSpec(sound, volume, pitch);
    }

    public static SoundSpec of(String name, float volume, float pitch) {
        Sound sound = find(name);
        return sound == null ? NONE : new SoundSpec(sound, volume, pitch);
    }

    /**
     * 同时支持 "BLOCK_CHEST_OPEN" 与 "block.chest.open" 两种写法。
     */
    public static Sound find(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String trimmed = name.trim();
        if (trimmed.indexOf('.') >= 0 || trimmed.indexOf(':') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(trimmed.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUNDS.get(key);
        }

        return Constants.BY_CONSTANT.get(trimmed.toUpperCase(Locale.ROOT));
    }

    /**
     * 延迟构建 "BLOCK_CHEST_OPEN -> Sound" 的映射, 避免使用已废弃的 Sound#valueOf。
     */
    private static final class Constants {

        private static final Map<String, Sound> BY_CONSTANT = build();

        private static Map<String, Sound> build() {
            Map<String, Sound> map = new HashMap<>();
            for (Sound sound : Registry.SOUNDS) {
                NamespacedKey key = Registry.SOUNDS.getKey(sound);
                if (key == null) {
                    continue;
                }
                map.put(key.getKey().toUpperCase(Locale.ROOT).replace('.', '_').replace('/', '_'), sound);
            }
            return map;
        }
    }
}
