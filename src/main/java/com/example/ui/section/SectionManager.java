package com.example.ui.section;

import com.example.ui.layout.MenuLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SectionManager {

    private static HashMap<String, Section> sections = new HashMap<String, Section>();

    public void addSection(String key,Section section) {
        sections.put(key, section);
    }

    public Section get(String key) {
        return sections.get(key);
    }

    public boolean has(String key) {
        return sections.containsKey(key);
    }

    public void clear(){
        sections.clear();
    }

    public Set<String> keySet() {
        return sections.keySet();
    }

    public MenuLayout getLayout(String full) {
        if (full == null || !full.contains(":")) return null;
        String[] parts = full.split(":", 2);
        Section section = sections.get(parts[0]);
        return section == null ? null : section.get(parts[1]);
    }

    public Map<String, Section> getAll() {
        return new HashMap<>(sections);
    }
}
