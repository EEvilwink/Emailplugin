package me.evil.ezmail.config;

/**
 * 一个界面的布局配置。
 */
public record GuiSettings(String title, int rows, ItemTemplate info, ItemTemplate unread, ItemTemplate read) {

    /** 界面总格子数。 */
    public int size() {
        return rows * 9;
    }

    /** 可用于显示留言的格子数 (最后一行留给按钮)。 */
    public int contentSize() {
        return (rows - 1) * 9;
    }

    /** 上一页按钮的格子。 */
    public int previousSlot() {
        return size() - 9;
    }

    /** 信息物品的格子。 */
    public int infoSlot() {
        return size() - 6;
    }

    /** 关闭按钮的格子。 */
    public int closeSlot() {
        return size() - 5;
    }

    /** 下一页按钮的格子。 */
    public int nextSlot() {
        return size() - 1;
    }
}
