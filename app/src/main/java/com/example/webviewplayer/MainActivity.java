package com.example.webviewplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout fullscreenContainer;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private View customView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全屏，隐藏状态栏和标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 创建一个用于全屏播放视频的容器
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(0xFF000000); // 黑色背景
        fullscreenContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreenContainer.setVisibility(View.GONE); // 初始隐藏

        // 创建 WebView
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 将 WebView 和全屏容器添加到根容器
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.addView(webView);
        rootLayout.addView(fullscreenContainer);
        setContentView(rootLayout);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 注入安卓原生接口
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidInterface");
        
        // 实现全屏处理
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;

                // 隐藏 WebView，显示全屏容器
                webView.setVisibility(View.GONE);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(customView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }

                // 显示 WebView，隐藏全屏容器
                webView.setVisibility(View.VISIBLE);
                fullscreenContainer.setVisibility(View.GONE);
                fullscreenContainer.removeView(customView);
                customViewCallback.onCustomViewHidden();
                customView = null;
            }
        });

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

        // 手动触发全屏模式切换
        @JavascriptInterface
        public void toggleFullscreen() {
            runOnUiThread(() -> {
                if (customView != null) {
                    // 如果已经是全屏模式，退出全屏
                    webView.getWebChromeClient().onHideCustomView();
                } else {
                    // 如果是普通模式，通过 JS 触发视频全屏
                    webView.loadUrl("javascript:document.getElementById('video-container').requestFullscreen()");
                }
            });
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

    // 处理后退键：优先退出全屏
    @Override
    public void onBackPressed() {
        if (customView != null) {
            webView.getWebChromeClient().onHideCustomView();
        } else {
            super.onBackPressed();
        }
    }
}
