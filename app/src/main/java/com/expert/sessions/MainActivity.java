package com.expert.sessions;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.DownloadListener;
import android.widget.Toast;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setDefaultTextEncodingName("UTF-8");

        webView.addJavascriptInterface(new BackupBridge(), "AndroidBackup");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                try {
                    startActivityForResult(Intent.createChooser(intent, "اختيار النسخة الاحتياطية"), FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "لا يوجد تطبيق لاختيار الملفات", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.setTitle("تنزيل ملف");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "expert_sessions_backup.json");
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this, "بدأ التنزيل", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "تعذر تنزيل الملف", Toast.LENGTH_LONG).show();
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private class BackupBridge {
        @JavascriptInterface
        public void saveBackup(String json, String filename) {
            runOnUiThread(() -> {
                try {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("تعذر إنشاء مجلد التنزيلات");
                    String safeName = filename.replaceAll("[^\\p{L}\\p{N}._-]", "_");
                    File out = new File(dir, safeName);
                    try (FileOutputStream stream = new FileOutputStream(out)) {
                        stream.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
                        stream.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                    Toast.makeText(MainActivity.this, "تم حفظ النسخة في مجلد Downloads", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "فشل حفظ النسخة الاحتياطية: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] results = (resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
                    ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
