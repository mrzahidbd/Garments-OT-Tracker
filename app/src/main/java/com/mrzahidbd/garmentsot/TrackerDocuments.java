package com.mrzahidbd.garmentsot;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

final class TrackerDocuments {
    private static final int SAVE = 710;
    private static final int OPEN = 711;
    private static final String STATE_FILE = "pendingTrackerExport";
    private final Activity activity;
    private File pendingExport;
    private ValueCallback<Uri[]> pendingImport;

    TrackerDocuments(Activity activity, Bundle state) {
        this.activity = activity;
        if (state != null) {
            String name = state.getString(STATE_FILE);
            if (name != null && name.matches("ot-export-[A-Za-z0-9_-]+\\.tmp")) {
                File cached = new File(activity.getCacheDir(), name);
                if (cached.isFile()) pendingExport = cached;
            }
        }
    }

    void attach(WebView view, String origin) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        WebViewCompat.addWebMessageListener(view, "TrackerFiles", Collections.singleton(origin),
                (source, message, sourceOrigin, mainFrame, reply) -> {
                    if (mainFrame && "https".equals(sourceOrigin.getScheme())
                            && Uri.parse(origin).getAuthority().equals(sourceOrigin.getAuthority())) {
                        prepareExport(message.getData());
                    }
                });
    }

    private void prepareExport(String message) {
        if (pendingExport != null || message == null) return;
        try {
            JSONObject request = new JSONObject(message);
            String name = request.getString("name");
            if (!name.matches("[a-z0-9_-]{1,90}\\.(json|csv)")) return;
            byte[] data = request.getString("contents").getBytes(StandardCharsets.UTF_8);
            pendingExport = File.createTempFile("ot-export-", ".tmp", activity.getCacheDir());
            try (FileOutputStream file = new FileOutputStream(pendingExport)) {
                file.write(data);
            }
            Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(name.endsWith(".csv") ? "text/csv" : "application/json")
                    .putExtra(Intent.EXTRA_TITLE, name);
            activity.startActivityForResult(create, SAVE);
        } catch (IOException | JSONException | ActivityNotFoundException failure) {
            discardExport();
            notifyUser("ফাইল সংরক্ষণ করা যায়নি");
        }
    }

    boolean openBackup(ValueCallback<Uri[]> callback) {
        if (pendingImport != null) pendingImport.onReceiveValue(null);
        pendingImport = callback;
        Intent open = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        open.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "text/json", "text/plain", "application/octet-stream"});
        try {
            activity.startActivityForResult(open, OPEN);
        } catch (ActivityNotFoundException missing) {
            cancelImport();
            notifyUser("ফাইল বেছে নেওয়া যায়নি");
        }
        return true;
    }

    void onResult(int request, int result, Intent intent) {
        Uri uri = result == Activity.RESULT_OK && intent != null ? intent.getData() : null;
        if (uri != null && !"content".equals(uri.getScheme())) uri = null;
        if (request == OPEN && pendingImport != null) {
            ValueCallback<Uri[]> callback = pendingImport;
            pendingImport = null;
            callback.onReceiveValue(uri == null ? null : new Uri[]{uri});
        } else if (request == SAVE) {
            try {
                if (uri == null) return;
                if (pendingExport == null) throw new IOException("Missing pending export");
                try (FileInputStream src = new FileInputStream(pendingExport);
                     OutputStream dest = activity.getContentResolver().openOutputStream(uri, "wt")) {
                    if (dest == null) throw new IOException("No document output stream");
                    byte[] buf = new byte[8192];
                    int count;
                    while ((count = src.read(buf)) != -1) dest.write(buf, 0, count);
                }
                notifyUser("ফাইল সংরক্ষণ হয়েছে");
            } catch (IOException | SecurityException failure) {
                notifyUser("ফাইল সংরক্ষণ করা যায়নি");
            } finally {
                discardExport();
            }
        }
    }

    void saveState(Bundle state) {
        if (pendingExport != null) state.putString(STATE_FILE, pendingExport.getName());
    }

    void close(boolean finishing) {
        cancelImport();
        if (finishing) discardExport();
    }

    private void cancelImport() {
        if (pendingImport != null) pendingImport.onReceiveValue(null);
        pendingImport = null;
    }

    private void discardExport() {
        if (pendingExport != null) pendingExport.delete();
        pendingExport = null;
    }

    private void notifyUser(String text) {
        Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
    }
}
