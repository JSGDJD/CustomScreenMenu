package com.example.ui.section;

import com.example.ui.layout.MenuLayout;

import java.util.LinkedHashMap;
import java.util.LinkedList;

public class Section {

    public double distance;

    public String world;

    public double cameraX;

    public double cameraY;

    public double cameraZ;
    public float yaw;
    public float pitch;
    public final String permission;

    public LinkedHashMap<String,MenuLayout> layouts;

    public Section(double distance,String world, double cameraX, double cameraY, double cameraZ, float yaw, float pitch, String permission) {
        this.distance = distance;
        this.world = world;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.yaw = yaw;
        this.pitch = pitch;
        this.layouts = new LinkedHashMap<>();
        this.permission = permission;
    }

    public void add(String key,MenuLayout layout) {
        layouts.put(key, layout);
    }

    public MenuLayout get(String key) {
        return layouts.get(key);
    }
}
