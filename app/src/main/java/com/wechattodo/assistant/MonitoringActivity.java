package com.wechattodo.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.wechattodo.assistant.MonitoringRules.App;
import com.wechattodo.assistant.MonitoringRules.Conversation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MonitoringActivity extends Activity {
    private static final int INK = 0xFF17332B;
    private static final int SUBTLE = 0xFF475D55;
    private static final int GREEN = 0xFF116654;
    private MonitoringSettings settings;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        settings = new MonitoringSettings(this);
        LinearLayout root = column();
        root.setBackgroundColor(0xFFF3F7F5);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(8));
        header.addView(button("返回", view -> finish()));
        TextView heading = label(getString(R.string.group_monitoring), 24, INK);
        heading.setAccessibilityHeading(true);
        header.addView(heading);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout page = column();
        page.setPadding(dp(20), 0, dp(20), dp(20));
        scroll.addView(page);
        add(page, label("这里的开关只控制群消息。开启应用的群监测后，添加要监测的群，群名请与聊天标题一致。"
                + "填写你在群里的昵称，可帮助判断消息是否发给你。",
                14, SUBTLE), 6);
        for (App app : App.values()) add(page, appCard(app), 12);
        setContentView(root);
    }

    private LinearLayout appCard(App app) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(0xFFFFFFFF);
        surface.setCornerRadius(dp(14));
        card.setBackground(surface);

        Switch enabled = new Switch(this);
        enabled.setText(app.label);
        enabled.setTextColor(INK);
        enabled.setTextSize(17);
        enabled.setMinHeight(dp(48));
        enabled.setChecked(settings.groupsEnabled(app));
        enabled.setOnCheckedChangeListener((view, checked) -> settings.setGroupsEnabled(app, checked));
        add(card, enabled, 0);
        Button manage = button("", null);
        manage.setOnClickListener(view -> manage(app, manage));
        updateManage(app, manage);
        add(card, manage, 0);
        return card;
    }

    private void updateManage(App app, Button manage) {
        manage.setText("管理监测群聊（" + groups(app).size() + "）");
        manage.setContentDescription(app.label + " · " + manage.getText());
    }

    private List<Conversation> groups(App app) {
        return settings.conversations(app).stream().filter(Conversation::group).toList();
    }

    private void manage(App app, Button button) {
        List<Conversation> entries = groups(app);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(app.label + "监测群聊")
                .setPositiveButton("添加群聊", (view, which) -> edit(app, button, null))
                .setNegativeButton("关闭", null);
        if (entries.isEmpty()) {
            dialog.setMessage("还没有添加监测群聊。请使用聊天中显示的群名称。");
        } else {
            String[] labels = entries.stream().map(entry -> entry.title()
                    + (entry.aliases().isEmpty() ? "" : "（" + String.join("、", entry.aliases()) + "）")).toArray(String[]::new);
            dialog.setItems(labels, (view, which) -> edit(app, button, entries.get(which)));
        }
        dialog.show();
    }

    private void edit(App app, Button button, Conversation previous) {
        LinearLayout fields = column();
        fields.setPadding(dp(22), dp(6), dp(22), dp(6));
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        title.setHint("群名称（与聊天标题一致）");
        title.setText(previous == null ? "" : previous.title());
        add(fields, title, 0);

        EditText aliases = new EditText(this);
        aliases.setMaxLines(3);
        aliases.setFilters(new InputFilter[]{new InputFilter.LengthFilter(400)});
        aliases.setHint("我在群中的昵称（可填多个，用逗号分隔）");
        aliases.setText(previous == null ? "" : String.join("、", previous.aliases()));
        add(fields, aliases, 5);
        CheckBox shared = new CheckBox(this);
        shared.setText("群里有人和我同名，点名时请先确认");
        shared.setChecked(previous != null && previous.sharedAlias());
        add(fields, shared, 4);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(previous == null ? "添加群聊" : "编辑群聊")
                .setView(fields).setNegativeButton("取消", null).setPositiveButton("保存", null);
        if (previous != null) {
            builder.setNeutralButton("移除", (view, which) -> new AlertDialog.Builder(this)
                    .setTitle("移除此监测群聊？").setMessage(previous.title())
                    .setNegativeButton("取消", null).setPositiveButton("移除", (confirm, action) -> {
                        settings.save(app, previous, null);
                        updateManage(app, button);
                        manage(app, button);
                    }).show());
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(view -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(save -> {
            String name = MonitoringRules.normalize(title.getText().toString());
            if (name.isBlank() || name.contains("\n") || name.contains("\r")) {
                title.setError("请输入一行完整的群名称");
                return;
            }
            if (previous == null || !previous.title().equals(name)) {
                for (Conversation item : settings.conversations(app)) {
                    if (!item.title().equals(name)) continue;
                    title.setError(item.group() ? "此群聊已添加，请返回列表编辑" : "群名与已有私聊同名，请区分名称后再添加");
                    return;
                }
            }
            Set<String> names = new HashSet<>();
            for (String alias : aliases.getText().toString().split("[,，、;；\\r\\n]")) {
                String item = MonitoringRules.normalize(alias);
                if (!item.isBlank()) names.add(item);
            }
            settings.save(app, previous, new Conversation(name, true, names, shared.isChecked()));
            updateManage(app, button);
            dialog.dismiss();
            manage(app, button);
        }));
        dialog.show();
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String text, View.OnClickListener click) {
        Button view = new Button(this, null, android.R.attr.borderlessButtonStyle);
        view.setText(text);
        view.setTextColor(GREEN);
        view.setMinHeight(dp(48));
        view.setOnClickListener(click);
        return view;
    }

    private void add(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMargin);
        parent.addView(child, params);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
