package com.wechattodo.assistant;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

final class GitHubUpdates {
    private static final String ASSET = "WeChatTodo-debug.apk";
    private static final int MAX_MANIFEST_BYTES = 4096;
    private static final int MAX_APK_BYTES = 64 * 1024 * 1024;
    private static final Pattern VERSION = Pattern.compile("v?(\\d{1,6})\\.(\\d{1,6})(?:\\.(\\d{1,6}))?");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private GitHubUpdates() {}

    static final class UpdateFailure extends IOException {
        UpdateFailure(String message) {
            super(message);
        }
    }

    record Release(String version, long size, String digest) {}

    static Release latest(String repo) throws IOException, JSONException {
        // The version manifest changes in place, while each version's APK has an immutable URL.
        URI manifest = URI.create(channelFile(repo, "updates.json") + "?check=" + System.currentTimeMillis());
        HttpsURLConnection connection = open(manifest);
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
        try {
            requireSuccess(connection.getResponseCode(), "读取版本信息");
            byte[] json;
            try (InputStream input = connection.getInputStream()) {
                json = input.readNBytes(MAX_MANIFEST_BYTES + 1);
            }
            if (json.length > MAX_MANIFEST_BYTES) {
                throw new UpdateFailure("版本信息超过大小限制。");
            }
            return parseRelease(new JSONObject(new String(json, StandardCharsets.UTF_8)), BuildConfig.VERSION_NAME);
        } finally {
            connection.disconnect();
        }
    }

    static Release parseRelease(JSONObject json, String installed) throws JSONException, IOException {
        String tag = json.getString("version");
        if (!tag.startsWith("v")) {
            throw new UpdateFailure("版本信息缺少版本标签。");
        }
        if (compareVersions(tag, installed) <= 0) {
            return null;
        }
        long size = json.getLong("size");
        String digest = json.getString("digest");
        if (size <= 0 || size > MAX_APK_BYTES || !DIGEST.matcher(digest).matches()) {
            throw new UpdateFailure("发布的 APK 缺少有效的大小或 SHA-256 校验值。");
        }
        return new Release(tag, size, digest);
    }

    static void download(Context context, String repo, Release release)
            throws IOException, GeneralSecurityException, PackageManager.NameNotFoundException {
        File output = UpdateApkProvider.updateFile(context);
        File temp = File.createTempFile("update-", ".apk", context.getCacheDir());
        try {
            copyAsset(repo, release, temp);
            PackageInfo apk = verifiedUpdate(context, temp);
            if (apk == null || compareVersions(apk.versionName, release.version()) != 0) {
                throw new UpdateFailure("安装包的应用名、签名或版本与发布信息不符。");
            }
            Files.move(temp.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    private static void copyAsset(String repo, Release release, File temp)
            throws IOException, GeneralSecurityException {
        HttpsURLConnection connection = open(channelFile(repo, release.version() + "/" + ASSET));
        try {
            requireSuccess(connection.getResponseCode(), "下载安装包");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long length = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    length += count;
                    if (length > release.size()) {
                        throw new UpdateFailure("下载的安装包大小与发布信息不符。");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            StringBuilder actual = new StringBuilder("sha256:");
            for (byte b : digest.digest()) {
                actual.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            if (length != release.size() || !release.digest().contentEquals(actual)) {
                throw new UpdateFailure("下载的安装包校验失败。");
            }
        } finally {
            connection.disconnect();
        }
    }

    static PackageInfo verifiedUpdate(Context context, File file) throws PackageManager.NameNotFoundException {
        if (!file.isFile()) {
            return null;
        }
        PackageManager manager = context.getPackageManager();
        PackageManager.PackageInfoFlags flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        PackageInfo apk = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (apk == null || !installed.packageName.equals(apk.packageName)
                || apk.getLongVersionCode() <= installed.getLongVersionCode()
                || apk.signingInfo == null || installed.signingInfo == null) {
            return null;
        }
        Signature[] installedSigners = installed.signingInfo.getApkContentsSigners();
        Signature[] newSigners = apk.signingInfo.getApkContentsSigners();
        return installedSigners.length == 1 && Arrays.equals(installedSigners, newSigners) ? apk : null;
    }

    static URI manualDownload(String repo) {
        return channelFile(repo, ASSET);
    }

    private static URI channelFile(String repo, String path) {
        return URI.create("https://raw.githubusercontent.com/" + repo + "/updates/" + path);
    }

    private static HttpsURLConnection open(URI uri) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "WeChatTodo-Android");
        return connection;
    }

    private static void requireSuccess(int status, String step) throws IOException {
        if (status == 200) {
            return;
        }
        throw new UpdateFailure(step + "失败（HTTP " + status + "）。可尝试用浏览器下载。");
    }

    private static int compareVersions(String first, String second) throws IOException {
        Matcher left = VERSION.matcher(first == null ? "" : first);
        Matcher right = VERSION.matcher(second == null ? "" : second);
        if (!left.matches() || !right.matches()) {
            throw new UpdateFailure("版本标签应为 v0.2 或 v0.2.1 这样的格式。");
        }
        for (int i = 1; i <= 3; i++) {
            int a = left.group(i) == null ? 0 : Integer.parseInt(left.group(i));
            int b = right.group(i) == null ? 0 : Integer.parseInt(right.group(i));
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }
}
