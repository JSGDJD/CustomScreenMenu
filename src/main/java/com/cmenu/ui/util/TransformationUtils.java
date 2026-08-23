package com.cmenu.ui.util;

import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class TransformationUtils {

    public static void safeSetTransformation(Display entity, Transformation newTrans) {
        entity.setTransformation(newTrans);
    }

    public static Transformation createTransformation(Vector3f translation, Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
        return new Transformation(translation, leftRotation, scale, rightRotation);
    }

    public static Transformation withLeftRotation(Display entity, Quaternionf newLeftRotation) {
        Transformation old = entity.getTransformation();
        return new Transformation(
            old.getTranslation(),
            newLeftRotation,
            old.getScale(),
            old.getRightRotation()
        );
    }

    public static Transformation withScale(Display entity, Vector3f newScale) {
        Transformation old = entity.getTransformation();
        return new Transformation(
            old.getTranslation(),
            old.getLeftRotation(),
            newScale,
            old.getRightRotation()
        );
    }

    public static Transformation withScale(Display entity, float scale) {
        return withScale(entity, new Vector3f(scale, scale, scale));
    }

    public static Transformation withTranslation(Display entity, Vector3f newTranslation) {
        Transformation old = entity.getTransformation();
        return new Transformation(
            newTranslation,
            old.getLeftRotation(),
            old.getScale(),
            old.getRightRotation()
        );
    }

    public static Transformation withRightRotation(Display entity, Quaternionf newRightRotation) {
        Transformation old = entity.getTransformation();
        return new Transformation(
            old.getTranslation(),
            old.getLeftRotation(),
            old.getScale(),
            newRightRotation
        );
    }
}
