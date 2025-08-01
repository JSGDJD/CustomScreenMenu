package com.example.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorParser {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND =
            LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION =
            LegacyComponentSerializer.legacySection();

    public static Component parse(String input) {
        if (input == null) return Component.empty();
        if (input.indexOf('<') >= 0) {
            return MINI.deserialize(input);
        }
        return LEGACY_AMPERSAND.deserialize(input);
    }

    public static String toLegacyString(String input) {
        if (input == null) return "";
        Component component = parse(input);
        return LEGACY_SECTION.serialize(component);
    }

    private ColorParser() {}
}