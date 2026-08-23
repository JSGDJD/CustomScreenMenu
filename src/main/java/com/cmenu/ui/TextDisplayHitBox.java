package com.cmenu.ui;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 把 TextDisplay 的本地包围盒按变换矩阵转成世界 OBB，
 * 再判断点是否在内（含 0.2 格外延，可调）。
 */
public final class TextDisplayHitBox {

    /** 本地默认宽高；TextDisplay 1 行时大约 0.2 格高，宽度按字符数线性估算 */
    private static final float LOCAL_HEIGHT = 0.2f;
    private static final float LOCAL_WIDTH_PER_CHAR = 0.12f;

    /**
     * 主入口：光标是否在文本的"真实"范围内
     */
    public static boolean isInside(TextDisplay display, Location cursor) {
        if (!display.isValid()) return false;

        // 1. 估算本地尺寸（宽=字符数*系数，高=单行固定值）
        String text = display.getText() != null ? display.getText() : "";
        // 过滤颜色代码以获得更准确的宽度估算
        String strippedText = text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
        float localWidth = Math.max(0.1f, strippedText.length() * LOCAL_WIDTH_PER_CHAR);
        float localHeight = LOCAL_HEIGHT;
        float localDepth = 0.05f; // 给一点深度，用于 3D 空间碰撞检测

        // 2. 取变换矩阵并求逆
        Transformation tr = display.getTransformation();
        Matrix4f worldMatrix = matrixFromTransformation(display, tr);
        Matrix4f invMatrix = new Matrix4f(worldMatrix).invert();

        // 3. 将世界坐标转换到本地空间
        Vector4f worldPos = new Vector4f((float) cursor.getX(), (float) cursor.getY(), (float) cursor.getZ(), 1.0f);
        Vector4f localPos = new Vector4f();
        invMatrix.transform(worldPos, localPos);

        // 4. 容错范围应随缩放比例调整
        Vector3f scale = tr.getScale();
        float avgScale = (scale.x + scale.y + scale.z) / 3.0f;
        float expansion = (float) Math.max(0.01, 0.05 * avgScale);

        // 5. 在本地空间进行 AABB 检测 (OBB 检测的等效实现)
        float hw = localWidth / 2f + expansion / Math.max(0.1f, scale.x);
        float hh = localHeight / 2f + expansion / Math.max(0.1f, scale.y);
        float hd = localDepth / 2f + expansion / Math.max(0.1f, scale.z);

        return Math.abs(localPos.x) <= hw &&
               Math.abs(localPos.y) <= hh &&
               Math.abs(localPos.z) <= hd;
    }

    /* ---------------- 内部工具 ---------------- */

    private static Matrix4f matrixFromTransformation(TextDisplay td, Transformation tr) {
        // 使用克隆的向量避免修改原始 Transformation
        Vector3f translation = new Vector3f(tr.getTranslation());
        org.joml.Quaternionf leftRot = tr.getLeftRotation();
        Vector3f scale = tr.getScale();

        // 获取TextDisplay的位置作为平移的基础
        Location location = td.getLocation();
        translation.add((float)location.getX(), (float)location.getY(), (float)location.getZ());

        // 世界矩阵 = T * R * S
        return new Matrix4f()
                .translate(translation)
                .rotate(leftRot)
                .scale(scale);
    }

    private TextDisplayHitBox() {}
}