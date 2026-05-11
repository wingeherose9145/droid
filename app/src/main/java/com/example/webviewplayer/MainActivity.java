package com.example.webviewplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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
        // 允许 WebView 访问应用内部存储的文件
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // 注入 JavaScript 接口，方便网页调用安卓原生功能
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    // JavaScript 接口：当用户点击网页里的“+”号时触发
    public class WebAppInterface {
        @JavascriptInterface
        public void openFilePicker() {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            startActivityForResult(intent, 1001);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            Uri sourceUri = data.getData();
            // 在后台执行私有化拷贝过程
            String privatePath = copyToInternalStorage(sourceUri);
            if (privatePath != null) {
                // 将私有文件路径传回网页播放
                webView.post(() -> webView.loadUrl("javascript:playPrivateVideo('file://" + privatePath + "')"));
            }
        }
    }

    private String copyToInternalStorage(Uri uri) {
        try {
            // 获取文件名
            String fileName = "private_video_" + System.currentTimeMillis() + ".mp4";
            // 定位到 App 私有目录：/data/user/0/com.example.webviewplayer/files/
            File destFile = new File(getFilesDir(), fileName);

            InputStream in = getContentResolver().openInputStream(uri);
            OutputStream out = new FileOutputStream(destFile);
            byte[] buf = new byte[1024 * 8];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            out.close();
            in.close();
            
            return destFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e("Error", "Copy failed", e);
            return null;
        }
    }
}
