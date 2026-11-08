package com.wechattodo.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.wechattodo.assistant.MonitoringRules.App;
import com.wechattodo.assistant.MonitoringRules.Conversation;

final class MonitoringSettings {
    private final SharedPreferences preferences;

    MonitoringSettings(Context context) {
        preferences = context.getSharedPreferences("message_monitoring", Context.MODE_PRIVATE);
    }

    boolean groupsEnabled(App app) {
        return preferences.getBoolean("enabled." + app.name(), app == App.WECHAT);
    }

    void setGroupsEnabled(App app, boolean enabled) {
        preferences.edit().putBoolean("enabled." + app.name(), enabled).apply();
    }

    List<Conversation> conversations(App app) {
        List<Conversation> result = new ArrayList<>();
        for (String item : preferences.getStringSet(key(app), Set.of())) {
            String[] fields = item.split("\n", 4);
            if (fields.length != 4 || fields[1].isBlank()) continue;
            Set<String> aliases = new HashSet<>();
            for (String alias : fields[3].split("\n")) if (!alias.isBlank()) aliases.add(alias);
            result.add(new Conversation(fields[1], fields[0].equals("group"), aliases, fields[2].equals("shared")));
        }
        result.sort(Comparator.comparing(Conversation::title));
        return List.copyOf(result);
    }

    void save(App app, Conversation old, Conversation updated) {
        Set<String> values = new HashSet<>();
        for (Conversation entry : conversations(app)) {
            if ((old == null || !entry.title().equals(old.title()))
                    && (updated == null || !entry.title().equals(updated.title()))) {
                values.add(encode(entry));
            }
        }
        if (updated != null) values.add(encode(updated));
        preferences.edit().putStringSet(key(app), values).apply();
    }

    private static String encode(Conversation entry) {
        return (entry.group() ? "group" : "private") + "\n" + entry.title() + "\n"
                + (entry.sharedAlias() ? "shared" : "unique") + "\n" + String.join("\n", entry.aliases());
    }

    private static String key(App app) {
        return "conversations." + app.name();
    }
}
