package com.heme.iptvlive;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateChecker {
    static void check(Activity activity, boolean userInitiated) {
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(
                    "https://api.github.com/repos/" + BuildConfig.GITHUB_REPO + "/releases/latest").openConnection();
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "IPTV-Live-Android");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                if (connection.getResponseCode() != 200) throw new IllegalStateException("GitHub HTTP " + connection.getResponseCode());
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONObject release = new JSONObject(body.toString());
                int remoteCode = parseVersionCode(release.optString("tag_name"));
                String download = findAsset(release.optJSONArray("assets"));
                if (remoteCode > BuildConfig.VERSION_CODE && download != null) {
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                        .setTitle("发现新版本 " + release.optString("tag_name"))
                        .setMessage(release.optString("body", "建议更新到最新版。"))
                        .setNegativeButton("稍后", null)
                        .setPositiveButton("下载更新", (d, w) -> download(activity, download))
                        .show());
                } else if (userInitiated) {
                    activity.runOnUiThread(() -> Toast.makeText(activity, "已是最新版本", Toast.LENGTH_SHORT).show());
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
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
            .setTitle("正在下载 " + BuildConfig.UPDATE_ASSET)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, BuildConfig.UPDATE_ASSET)
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

