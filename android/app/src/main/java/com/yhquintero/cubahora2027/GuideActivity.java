package com.yhquintero.cubahora2027;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Guía del proyecto incluida dentro del APK.
 *
 * <p>Se carga desde {@code file:///android_asset/site/index.html}. Las opciones
 * de red están bloqueadas: la guía no puede hacer peticiones externas. Los
 * enlaces externos se abren en el navegador del sistema.</p>
 */
public class GuideActivity extends Activity {

    private static final String GUIDE_URL = "file:///android_asset/site/index.html";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setLoadsImagesAutomatically(true);
        if (Build.VERSION.SDK_INT >= 16) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }
        });

        webView.loadUrl(GUIDE_URL);
        setContentView(webView);
    }

    private boolean handleUrl(String url) {
        if (url == null) {
            return true;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(this, "No hay una aplicación para abrir ese enlace.",
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (scheme.equals("file")) {
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith(".md") || path.endsWith(".reg")) {
                Toast.makeText(this, "Este archivo es para Windows. Su versión publicada está "
                        + "en el repositorio del proyecto.", Toast.LENGTH_LONG).show();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
