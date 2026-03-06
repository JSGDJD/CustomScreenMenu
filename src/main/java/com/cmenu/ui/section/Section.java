package com.cmenu.ui.section;

import com.cmenu.ui.layout.MenuLayout;

import java.util.LinkedHashMap;
import java.util.List;

public class Section {

    public String key;
    public final List<String> autoCommands;
    public final List<Long> autoCommandDelays;
    public final boolean autoCommandsEnabled;
    public double distance;

    public String world;

    public double cameraX;

    public double cameraY;

    public double cameraZ;
    public float yaw;
    public float pitch;
    public final String permission;

    public LinkedHashMap<String,MenuLayout> layouts;

    public Section(double distance,String world, double cameraX, double cameraY, double cameraZ, float yaw, float pitch, String permission, List<String> autoCommands, List<Long> autoCommandDelays, boolean autoCommandsEnabled) {
        this.distance = distance;
        this.world = world;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.layouts = new LinkedHashMap<>();
        this.permission = permission;
        this.autoCommands = autoCommands;
        this.autoCommandDelays = autoCommandDelays;
        this.autoCommandsEnabled = autoCommandsEnabled;
    }

    public void add(String key,MenuLayout layout) {
        layouts.put(key, layout);
    }

    public MenuLayout get(String key) {
        return layouts.get(key);
    }
}
