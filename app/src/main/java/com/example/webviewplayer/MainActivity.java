package com.example.webviewplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void openFilePicker() {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            // 允许选择多个文件（视安卓版本支持情况）
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); 
            startActivityForResult(intent, 1001);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            // 处理单选
            if (data.getData() != null) {
                processAndAdd(data.getData());
            } 
            // 处理多选 (部分文件管理器支持)
            else if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    processAndAdd(data.getClipData().getItemAt(i).getUri());
                }
            }
        }
    }

    private void processAndAdd(Uri uri) {
        String privatePath = copyToInternalStorage(uri);
        if (privatePath != null) {
            webView.post(() -> webView.loadUrl("javascript:addVideoToList('file://" + privatePath + "')"));
        }
    }

    private String copyToInternalStorage(Uri uri) {
        try {
            String fileName = "vid_" + System.currentTimeMillis() + "_" + uri.getLastPathSegment().replaceAll("[^a-zA-Z0-9]", "") + ".mp4";
            File destFile = new File(getFilesDir(), fileName);
            InputStream in = getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(destFile);
            byte[] buf = new byte[1024 * 1024]; // 1MB 缓冲区提高拷贝速度
            int len;
            while ((len = in.read(buf)) > 0) { out.write(buf, 0, len); }
            out.close(); in.close();
            return destFile.getAbsolutePath();
        } catch (Exception e) { return null; }
    }
}
