package android.view.accessibility;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;

public final class AccessibilityNodeInfo {
    public String app = "com.tencent.mm";
    public String role;
    public String text;
    public String description;
    public boolean visible = true;
    public boolean editable;
    public boolean password;
    public final Rect rect;
    public final List<AccessibilityNodeInfo> children = new ArrayList<>();

    public AccessibilityNodeInfo(String role, String text, int x1, int y1, int x2, int y2) {
        this.role = role;
        this.text = text;
        rect = new Rect(x1, y1, x2, y2);
    }

    public String getPackageName() { return app; }
    public String getClassName() { return role; }
    public String getText() { return text; }
    public String getContentDescription() { return description; }
    public int getChildCount() { return children.size(); }
    public AccessibilityNodeInfo getChild(int index) { return children.get(index); }
    public boolean isVisibleToUser() { return visible; }
    public boolean isEditable() { return editable; }
    public boolean isPassword() { return password; }
    public boolean isScrollable() { return false; }
    public Object getCollectionInfo() { return null; }
    public int getInputType() { return 0; }

    public void getBoundsInScreen(Rect area) {
        area.left = rect.left;
        area.top = rect.top;
        area.right = rect.right;
        area.bottom = rect.bottom;
    }
}
