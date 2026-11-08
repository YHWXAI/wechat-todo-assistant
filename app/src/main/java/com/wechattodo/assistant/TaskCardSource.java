package com.wechattodo.assistant;

import com.wechattodo.assistant.MonitoringRules.App;

record TaskCardSource(App app, String title) {
    private static final String SEPARATOR = " · ";

    static TaskCardSource from(TaskStore.Task task) {
        String conversation = task.source();
        if (task.origin() == null && TaskStore.MANUAL_SOURCE.equals(conversation)) {
            return new TaskCardSource(null, conversation);
        }
        if (task.origin() != null) {
            App app = App.fromPackage(task.origin().packageName());
            String title = task.origin().sender();
            if (title == null || title.isBlank()) {
                title = withoutAppPrefix(conversation, app);
            }
            return new TaskCardSource(app, title.isBlank() && app != null ? app.label : title);
        }

        App app = App.WECHAT;
        for (App candidate : App.values()) {
            if (candidate != App.WECHAT && (conversation.equals(candidate.label)
                    || conversation.startsWith(candidate.label + SEPARATOR))) {
                app = candidate;
                break;
            }
        }
        conversation = withoutAppPrefix(conversation, app);
        int sender = conversation.lastIndexOf(SEPARATOR);
        String title = sender < 0 ? conversation : conversation.substring(sender + SEPARATOR.length());
        return new TaskCardSource(app, title.isBlank() ? app.label : title);
    }

    private static String withoutAppPrefix(String source, App app) {
        String prefix = app == null || app == App.WECHAT ? "" : app.label + SEPARATOR;
        return !prefix.isEmpty() && source.startsWith(prefix) ? source.substring(prefix.length()) : source;
    }
}
