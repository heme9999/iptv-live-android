package com.heme.iptvlive;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateChecker {
    private static final String[] UPDATE_URLS = {
        "https://fastly.jsdelivr.net/gh/" + BuildConfig.GITHUB_REPO + "@main/version.json",
        "https://cdn.jsdelivr.net/gh/" + BuildConfig.GITHUB_REPO + "@main/version.json",
        "https://gcore.jsdelivr.net/gh/" + BuildConfig.GITHUB_REPO + "@main/version.json",
        "https://raw.githubusercontent.com/" + BuildConfig.GITHUB_REPO + "/main/version.json",
        "https://raw.gitmirror.com/" + BuildConfig.GITHUB_REPO + "/main/version.json",
        "https://api.github.com/repos/" + BuildConfig.GITHUB_REPO + "/releases/latest"
    };

    static void check(Activity activity, boolean userInitiated) {
        if (userInitiated) Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            JSONObject release = null;
            Exception lastError = null;
            for (String baseUrl : UPDATE_URLS) {
                try {
                    String fetchUrl = baseUrl.contains("?") ? baseUrl + "&_t=" + System.currentTimeMillis() : baseUrl + "?_t=" + System.currentTimeMillis();
                    HttpURLConnection connection = (HttpURLConnection) new URL(fetchUrl).openConnection();
                    connection.setRequestProperty("Accept", "application/vnd.github+json, application/json, text/plain, */*");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36");
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.setUseCaches(false);
                    connection.setConnectTimeout(4_000);
                    connection.setReadTimeout(6_000);
                    int code = connection.getResponseCode();
                    if (code == 200) {
                        StringBuilder body = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                            String line; while ((line = reader.readLine()) != null) body.append(line);
                        }
                        release = new JSONObject(body.toString());
                        break;
                    }
                } catch (Exception e) {
                    lastError = e;
                }
            }

            try {
                if (release == null) {
                    throw lastError != null ? lastError : new IllegalStateException("所有更新镜像源均不可达");
                }
                int remoteCode = release.has("version_code") ? release.optInt("version_code") : parseVersionCode(release.optString("tag_name"));
                String download = findAsset(release.optJSONArray("assets"));
                if (remoteCode > BuildConfig.VERSION_CODE && download == null) {
                    throw new IllegalStateException("新版本缺少 " + BuildConfig.UPDATE_ASSET);
                } else if (remoteCode > BuildConfig.VERSION_CODE) {
                    final JSONObject finalRelease = release;
                    final String finalDownload = download;
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setTitle("发现新版本 " + finalRelease.optString("tag_name"))
                        .setMessage(finalRelease.optString("body", "建议更新到最新版。"))
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("下载更新", (d, w) -> download(activity, finalDownload))
                        .show());
                } else if (userInitiated) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "已是最新版本 (v" + BuildConfig.VERSION_NAME + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception error) {
                if (userInitiated) activity.runOnUiThread(() -> Toast.makeText(activity, "检查更新失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "update-checker").start();
    }

    private static int parseVersionCode(String tag) {
        String digits = tag.replaceAll("[^0-9.]", "");
        String[] parts = digits.split("\\.");
        int major = parts.length > 0 && !parts[0].isEmpty() ? Integer.parseInt(parts[0]) : 0;
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        return major * 100 + minor * 10 + patch;
    }

    private static String findAsset(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && BuildConfig.UPDATE_ASSET.equals(asset.optString("name"))) return asset.optString("browser_download_url");
        }
        return null;
    }

    private static void download(Activity activity, String url) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, "请允许本应用安装更新，然后再次点击检查更新", Toast.LENGTH_LONG).show();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName())));
            return;
        }
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        String destination = "IPTV-Live-update-" + System.currentTimeMillis() + ".apk";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
            .setTitle("正在下载 " + BuildConfig.UPDATE_ASSET)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destination)
            .setMimeType("application/vnd.android.package-archive");
        long id = manager.enqueue(request);
        new Thread(() -> waitAndInstall(activity, manager, id), "update-download").start();
    }

    private static void waitAndInstall(Activity activity, DownloadManager manager, long id) {
        for (;;) {
            try { Thread.sleep(1_000); } catch (InterruptedException ignored) { return; }
            try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
                if (!cursor.moveToFirst()) return;
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri uri = manager.getUriForDownloadedFile(id);
                    activity.runOnUiThread(() -> activity.startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK)));
                    return;
                }
                if (status == DownloadManager.STATUS_FAILED) return;
            }
        }
    }
}
