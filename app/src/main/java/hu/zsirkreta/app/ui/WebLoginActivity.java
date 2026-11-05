package hu.zsirkreta.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import hu.zsirkreta.app.R;
import hu.zsirkreta.app.data.Config;

/**
 * Belépés a KRÉTA saját oldalán, beépített böngészőben. Amikor a KRÉTA az engedélyezési kóddal
 * átirányít a redirect címre, a kódot elfogjuk – nem kell kézzel másolni.
 */
public class WebLoginActivity extends AppCompatActivity {
    private WebView web;
    private boolean done = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean sysDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Theme.apply(hu.zsirkreta.app.data.St.prefs, sysDark);
        final String url = getIntent().getStringExtra("url");
        final String verifier = getIntent().getStringExtra("verifier");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.bg);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(sb.left, sb.top, sb.right, Math.max(sb.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        LinearLayout bar = Ui.hbox(this, 8);
        bar.setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        bar.addView(Ui.text(this, "Belépés a KRÉTA-ban", 18, Theme.text, true), Ui.grow());
        bar.addView(Ui.iconButton(this, R.drawable.ic_close, Theme.muted, this::cancel));
        root.addView(bar, Ui.lp(Ui.MATCH, Ui.WRAP));

        FrameLayout body = new FrameLayout(this);
        web = new WebView(this);
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.getProgressDrawable().setTint(Theme.accent);
        body.addView(web, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        body.addView(pb, new FrameLayout.LayoutParams(Ui.MATCH, Ui.dp(3), Gravity.TOP));
        root.addView(body, Ui.lp(Ui.MATCH, 0, 1f));
        setContentView(root);

        // tiszta indulás: a KRÉTA mindig kérje a belépést
        CookieManager.getInstance().removeAllCookies(null);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSaveFormData(false);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return intercept(r.getUrl().toString(), verifier);
            }
            @Override public void onPageStarted(WebView v, String u, Bitmap f) {
                pb.setVisibility(View.VISIBLE);
                intercept(u, verifier);
            }
            @Override public void onPageFinished(WebView v, String u) { pb.setVisibility(View.GONE); }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { pb.setProgress(p); }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack(); else cancel();
            }
        });
        if (b == null) web.loadUrl(url);
        else web.restoreState(b);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    /** Ha a cím a redirect URI: kivesszük a kódot, és visszatérünk. */
    private boolean intercept(String url, String verifier) {
        if (done) return true;
        String redirect = Config.redirectUri();
        String base = redirect.contains("?") ? redirect.substring(0, redirect.indexOf('?')) : redirect;
        if (!url.startsWith(base)) return false;
        Uri u = Uri.parse(url);
        String code = u.getQueryParameter("code");
        done = true;
        Intent res = new Intent();
        if (code != null && !code.isEmpty()) {
            res.putExtra("code", code);
            res.putExtra("verifier", verifier);
            setResult(RESULT_OK, res);
        } else {
            String err = u.getQueryParameter("error_description");
            if (err == null) err = u.getQueryParameter("error");
            res.putExtra("error", err == null ? "A KRÉTA nem adott engedélyezési kódot" : "A KRÉTA hibát jelzett: " + err);
            setResult(RESULT_CANCELED, res);
        }
        finish();
        return true;
    }

    private void cancel() {
        if (done) return;
        done = true;
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
