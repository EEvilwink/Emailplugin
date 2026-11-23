package me.evil.ezmail.config;

/**
 * 公告在界面中的外观配置。
 * 发送公告时可以用指令参数单独覆盖物品材质 / 名称 / 描述。
 */
public record BroadcastGui(ItemTemplate unread, ItemTemplate read, ItemTemplate admin) {
}
