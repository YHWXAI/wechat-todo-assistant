package com.wechattodo.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.function.BooleanSupplier;

import org.json.JSONException;

final class UpdateController {
    private final Activity activity;
    private final TextView status;
    private final Button check;
    private final Button install;
    private final BooleanSupplier settingsVisible;
    private boolean waitingForPermission;
    private boolean checkedOnStart;

    UpdateController(Activity activity, TextView status, Button check, Button install,
                     BooleanSupplier settingsVisible) {
        this.activity = activity;
        this.status = status;
        this.check = check;
        this.install = install;
        this.settingsVisible = settingsVisible;
        check.setOnClickListener(view -> check(true));
        install.setOnClickListener(view -> launchInstaller());
        status.setText(R.string.update_source);
    }

    void onResume() {
        install.setVisibility(UpdateApkProvider.updateFile(activity).isFile() ? View.VISIBLE : View.GONE);
        if (waitingForPermission) {
            waitingForPermission = false;
            if (settingsVisible.getAsBoolean() && activity.getPackageManager().canRequestPackageInstalls()
                    && install.getVisibility() == View.VISIBLE) {
                launchInstaller();
            }
        }
        if (!checkedOnStart) {
            checkedOnStart = true;
            if (install.getVisibility() != View.VISIBLE) {
                check(false);
            }
        }
    }

    void openDownloadPage() {
        try {
            Uri download = Uri.parse(GitHubUpdates.manualDownload(BuildConfig.UPDATE_REPOSITORY).toString());
            activity.startActivity(new Intent(Intent.ACTION_VIEW, download));
        } catch (ActivityNotFoundException unavailable) {
            toast("找不到可打开下载链接的浏览器。");
        }
    }

    private void check(boolean download) {
        String repository = BuildConfig.UPDATE_REPOSITORY;
        setBusy(true);
        status.setText(R.string.checking_update);
        new Thread(() -> {
            try {
                GitHubUpdates.Release release = GitHubUpdates.latest(repository);
                if (release == null) {
                    finish("当前已经是最新发布版本。", false);
                    return;
                }
                if (!download) {
                    finish("发现新版 " + release.version() + "。点击「检查并下载更新」继续。", false);
                    return;
                }
                activity.runOnUiThread(() -> {
                    if (isActive()) {
                        status.setText(activity.getString(R.string.downloading_update, release.version()));
                    }
                });
                GitHubUpdates.download(activity.getApplicationContext(), repository, release);
                finish("新版安装包已下载并通过签名校验。", true);
            } catch (GitHubUpdates.UpdateFailure failure) {
                finish(failure.getMessage(), false);
            } catch (IOException | JSONException | GeneralSecurityException | PackageManager.NameNotFoundException failure) {
                finish("无法连接更新地址。请检查网络，或尝试用浏览器下载。", false);
            }
        }, "github-update").start();
    }

    private void finish(String message, boolean downloaded) {
        activity.runOnUiThread(() -> {
            if (!isActive()) {
                return;
            }
            setBusy(false);
            status.setText(message);
            if (UpdateApkProvider.updateFile(activity).isFile()) {
                install.setVisibility(View.VISIBLE);
            }
            if (downloaded && settingsVisible.getAsBoolean() && activity.hasWindowFocus()) {
                launchInstaller();
            }
        });
    }

    private void launchInstaller() {
        if (!UpdateApkProvider.updateFile(activity).isFile()) {
            install.setVisibility(View.GONE);
            return;
        }
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("允许安装此来源的更新")
                    .setMessage("首次在应用内安装更新，需要在安卓设置里授权。返回后由系统安装界面确认覆盖安装。")
                    .setNegativeButton("稍后", null)
                    .setPositiveButton("打开设置", (dialog, which) -> {
                        try {
                            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())));
                            waitingForPermission = true;
                        } catch (ActivityNotFoundException unavailable) {
                            toast("无法打开安装权限设置。");
                        }
                    }).show();
            return;
        }

        Intent request = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(UpdateApkProvider.updateUri(activity), UpdateApkProvider.MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ResolveInfo handler = activity.getPackageManager().resolveActivity(request,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.MATCH_SYSTEM_ONLY);
        if (handler == null || handler.activityInfo == null) {
            toast("找不到安卓系统安装器。");
            return;
        }
        request.setClassName(handler.activityInfo.packageName, handler.activityInfo.name);
        try {
            activity.startActivity(request);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            toast("安卓系统未能打开安装器，请检查安装设置。");
        }
    }

    private void setBusy(boolean busy) {
        check.setEnabled(!busy);
        install.setEnabled(!busy);
    }

    private boolean isActive() {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    private void toast(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
    }
}
