import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class UpdateVersionCheck {
    public static void main(String[] args) throws Exception {
        Class<?> updater = Class.forName("com.wechattodo.assistant.GitHubUpdates");
        Method compare = updater.getDeclaredMethod("compareVersions", String.class, String.class);
        compare.setAccessible(true);
        check(compare, "v0.10", "0.2", 1);
        check(compare, "v0.2.1", "0.2", 1);
        check(compare, "v0.2.0", "0.2", 0);
        check(compare, "v0.1", "0.2", -1);
        try {
            compare.invoke(null, "v0.2-rc.1", "0.2");
            throw new AssertionError("Pre-release tag accepted");
        } catch (InvocationTargetException failure) {
            if (!(failure.getCause() instanceof java.io.IOException)) {
                throw failure;
            }
        }
        System.out.println("5 update version checks passed");
    }

    private static void check(Method compare, String remote, String installed, int expected) throws Exception {
        if (!Integer.valueOf(expected).equals(compare.invoke(null, remote, installed))) {
            throw new AssertionError(remote + " compared with " + installed);
        }
    }
}
