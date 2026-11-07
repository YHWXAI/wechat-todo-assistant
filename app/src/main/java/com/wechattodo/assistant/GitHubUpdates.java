package com.wechattodo.assistant;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import org.json.JSONArray;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

final class GitHubUpdates {
    private static final String ASSET = "WeChatTodo-debug.apk";
    private static final int MAX_RELEASE_BYTES = 1024 * 1024;
    private static final int MAX_APK_BYTES = 64 * MAX_RELEASE_BYTES;
    private static final Pattern VERSION = Pattern.compile("v?(\\d{1,6})\\.(\\d{1,6})(?:\\.(\\d{1,6}))?");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final Set<String> DOWNLOAD_HOSTS = Set.of(
            "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com");

    private GitHubUpdates() {}

    static final class UpdateFailure extends IOException {
        UpdateFailure(String message) {
            super(message);
        }
    }

    record Release(String version, long size, String digest) {}

    static Release latest(String repo) throws IOException, JSONException {
        HttpsURLConnection connection = api(repo, "/releases/latest", "application/vnd.github+json");
        try {
            requireSuccess(connection.getResponseCode());
            byte[] json;
            try (InputStream input = connection.getInputStream()) {
                json = input.readNBytes(MAX_RELEASE_BYTES + 1);
            }
            if (json.length > MAX_RELEASE_BYTES) {
                throw new UpdateFailure("发布信息过大。");
            }
            return parseRelease(new JSONObject(new String(json, StandardCharsets.UTF_8)), BuildConfig.VERSION_NAME);
        } finally {
            connection.disconnect();
        }
    }

    static Release parseRelease(JSONObject json, String installed) throws JSONException, IOException {
        String tag = json.getString("tag_name");
        if (compareVersions(tag, installed) <= 0) {
            return null;
        }
        JSONArray assets = json.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (ASSET.equals(asset.optString("name")) && "uploaded".equals(asset.optString("state"))) {
                long size = asset.getLong("size");
                String digest = asset.optString("digest", "");
                if (size > 0 && size <= MAX_APK_BYTES && DIGEST.matcher(digest).matches()) {
                    return new Release(tag, size, digest);
                }
                throw new UpdateFailure("发布的 APK 缺少有效的大小或 SHA-256 校验值。");
            }
        }
        throw new UpdateFailure("新版未附带 " + ASSET + "。");
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
        HttpsURLConnection connection = open(URI.create(
                "https://github.com/" + repo + "/releases/download/" + release.version() + "/" + ASSET));
        try {
            int code = connection.getResponseCode();
            if (code == HttpsURLConnection.HTTP_MOVED_TEMP) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                connection = downloadConnection(location);
                code = connection.getResponseCode();
            }
            requireSuccess(code);
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

    private static HttpsURLConnection api(String repo, String endpoint, String accept) throws IOException {
        HttpsURLConnection connection = open(URI.create("https://api.github.com/repos/" + repo + endpoint));
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        return connection;
    }

    private static HttpsURLConnection downloadConnection(String location) throws IOException {
        URI uri;
        try {
            uri = location == null ? null : URI.create(location);
        } catch (IllegalArgumentException invalid) {
            throw new UpdateFailure("GitHub 返回了无效的下载地址。");
        }
        if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null
                || !DOWNLOAD_HOSTS.contains(uri.getHost()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new UpdateFailure("GitHub 返回了无法验证的下载地址。");
        }
        return open(uri);
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

    private static void requireSuccess(int status) throws IOException {
        if (status == 200) {
            return;
        }
        throw new UpdateFailure(switch (status) {
            case 404 -> "更新仓库还没有可用的正式发布。";
            case 403, 429 -> "GitHub 暂时拒绝了请求，请稍后再试。";
            default -> "GitHub 更新请求失败（HTTP " + status + "）。";
        });
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
