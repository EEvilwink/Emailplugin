package me.evil.ezmail.config;

import me.evil.ezmail.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 配置文件中的一个物品模板 (材质 / 名称 / 描述 / 数量)。
 */
public record ItemTemplate(Material material, String name, List<String> lore, int amount) {

    public static final ItemTemplate EMPTY = new ItemTemplate(Material.AIR, "", List.of(), 1);

    public static ItemTemplate of(Material material, String name, List<String> lore) {
        return new ItemTemplate(material, name, lore == null ? List.of() : List.copyOf(lore), 1);
    }

    public static ItemTemplate from(ConfigurationSection section, ItemTemplate fallback, Logger logger, String path) {
        if (section == null) {
            return fallback;
        }

        Material material = fallback.material();
        String rawMaterial = section.getString("material");
        if (rawMaterial != null && !rawMaterial.isBlank()) {
            Material parsed = Material.matchMaterial(rawMaterial.trim());
            if (parsed == null) {
                logger.warning("配置项 " + path + ".material 中的物品无效: " + rawMaterial + ", 已使用默认物品 " + material);
            } else {
                material = parsed;
            }
        }

        String name = section.isSet("name") ? section.getString("name", "") : fallback.name();
        List<String> lore = section.isSet("lore") ? section.getStringList("lore") : fallback.lore();
        int amount = section.isSet("amount") ? section.getInt("amount", fallback.amount()) : fallback.amount();

        return new ItemTemplate(material, name == null ? "" : name, List.copyOf(lore), Math.max(1, amount));
    }

    public ItemStack build(Map<String, String> placeholders) {
        int maxStack = Math.max(1, material.getMaxStackSize());
        ItemStack item = new ItemStack(material, Math.min(Math.max(1, amount), maxStack));
        if (material.isAir()) {
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        if (name != null && !name.isEmpty()) {
            // 物品名称默认会被客户端渲染成斜体, 这里显式关闭 (配置里写 &o 仍然可以局部斜体)
            meta.displayName(Text.withoutItalic(Text.format(name, placeholders)));
        }

        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(Text.withoutItalic(Text.format(line, placeholders)));
            }
            meta.lore(lines);
        }

        item.setItemMeta(meta);
        return item;
    }
}
