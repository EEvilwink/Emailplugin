package me.evil.ezmail.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文本工具: 同时支持 &amp; 颜色代码与 MiniMessage, 并提供 {占位符} 替换。
 */
public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /**
     * 解析一段配置文本: 含有 '&lt;' 时按 MiniMessage 解析, 否则按 &amp; 颜色代码解析。
     */
    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        try {
            if (raw.indexOf('<') >= 0) {
                return MINI_MESSAGE.deserialize(raw);
            }
            return LEGACY.deserialize(raw);
        } catch (RuntimeException exception) {
            return Component.text(raw);
        }
    }

    /**
     * 解析文本并替换其中的 {key} 占位符。
     * 占位符的值以纯文本插入, 不会再次被解析为颜色代码, 也不会被二次替换。
     */
    public static Component format(String raw, Map<String, String> placeholders) {
        Component parsed = parse(raw);
        if (placeholders == null || placeholders.isEmpty()) {
            return parsed;
        }

        StringBuilder regex = new StringBuilder("\\{(");
        boolean first = true;
        for (String key : placeholders.keySet()) {
            if (!first) {
                regex.append('|');
            }
            first = false;
            regex.append(Pattern.quote(key));
        }
        regex.append(")\\}");

        Pattern pattern = Pattern.compile(regex.toString());
        return parsed.replaceText(TextReplacementConfig.builder()
                .match(pattern)
                .replacement((match, builder) -> {
                    String value = placeholders.get(match.group(1));
                    return Component.text(value == null ? "" : value);
                })
                .build());
    }

    /**
     * 解析文本并替换占位符, 占位符以 key, value 成对传入。
     */
    public static Component format(String raw, String... pairs) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            placeholders.put(pairs[i], pairs[i + 1]);
        }
        return format(raw, placeholders);
    }

    /**
     * 取消默认斜体。
     * <p>
     * Minecraft 客户端会把"自定义名称"的物品名称 / 描述渲染成斜体 (原版改名物品也是这样)。
     * 这里套一层 italic=false 的父组件, 子组件没有显式设置斜体时就会继承"不斜体";
     * 如果配置里显式写了 {@code &o} 或 {@code <italic>}, 对应片段仍然会正常显示为斜体。
     */
    public static Component withoutItalic(Component component) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(component);
    }
}
