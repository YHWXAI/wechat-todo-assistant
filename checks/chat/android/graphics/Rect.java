package android.graphics;

public final class Rect {
    public int left, top, right, bottom;

    public Rect() {}

    public Rect(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int width() { return right - left; }
    public int height() { return bottom - top; }
    public int centerX() { return (left + right) / 2; }
    public int centerY() { return (top + bottom) / 2; }
    public boolean isEmpty() { return width() <= 0 || height() <= 0; }

    public boolean contains(Rect other) {
        return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom;
    }

    public boolean intersect(Rect other) {
        if (left >= other.right || other.left >= right || top >= other.bottom || other.top >= bottom) {
            return false;
        }
        left = Math.max(left, other.left);
        top = Math.max(top, other.top);
        right = Math.min(right, other.right);
        bottom = Math.min(bottom, other.bottom);
        return true;
    }
}
