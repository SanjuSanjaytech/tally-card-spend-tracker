package com.sanjay.tally;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    static final String PREFS = "tally_widget";
    static final String STATE = "tally_state";
    private static final int IMPORT_BACKUP = 41;
    private static final String APP_URL = "https://sanjusanjaytech.github.io/tally-card-spend-tracker/";
    private WebView webView;
    private ValueCallback<Uri[]> fileChooser;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF4F3FB);

        TextView title = new TextView(this);
        title.setText("Tally  ·  Android widget");
        title.setTextColor(0xFF1D1B3A);
        title.setTextSize(18);
        title.setPadding(dp(18), dp(14), dp(18), dp(4));
        root.addView(title);

        Button importButton = new Button(this);
        importButton.setText("Import a backup from your current Tally");
        importButton.setOnClickListener(v -> chooseBackup());
        root.addView(importButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setSupportZoom(false);
        webView.addJavascriptInterface(new TallyBridge(), "TallyWidget");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                Uri target = request.getUrl();
                boolean isTally = "https".equals(target.getScheme())
                        && "sanjusanjaytech.github.io".equals(target.getHost())
                        && "/tally-card-spend-tracker/".equals(target.getPath());
                return !isTally;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!APP_URL.equals(url)) return;
                String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(STATE, "");
                String js = "(function(){var old=localStorage.getItem('tally');var saved=" + JSONObject.quote(saved)
                        + ";if(!old&&saved){localStorage.setItem('tally',saved);location.reload()}"
                        + "else if(old&&window.TallyWidget)window.TallyWidget.save(old)})()";
                view.evaluateJavascript(js, null);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setNegativeButton("Cancel", (d, w) -> result.cancel()).setOnCancelListener(d -> result.cancel()).show();
                return true;
            }

            @Override public boolean onJsPrompt(WebView view, String url, String message, String value, JsPromptResult result) {
                android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                input.setText(value);
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setView(input)
                        .setPositiveButton("Save", (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton("Cancel", (d, w) -> result.cancel()).setOnCancelListener(d -> result.cancel()).show();
                return true;
            }

            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooser != null) fileChooser.onReceiveValue(null);
                fileChooser = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, IMPORT_BACKUP);
                return true;
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        webView.loadUrl(APP_URL);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }

    private void chooseBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, IMPORT_BACKUP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != IMPORT_BACKUP) return;
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (fileChooser != null) {
            fileChooser.onReceiveValue(uri == null ? null : new Uri[]{uri});
            fileChooser = null;
        }
        if (uri == null) return;
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalArgumentException("Could not open this file");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String json = output.toString("UTF-8");
            JSONObject backup = new JSONObject(json);
            if (!backup.has("tx") || !backup.has("cards")) throw new IllegalArgumentException("This is not a Tally backup");
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(STATE, json).apply();
            if (webView != null) webView.evaluateJavascript(
                    "localStorage.setItem('tally'," + JSONObject.quote(json) + ");location.reload()", null);
            SpendWidgetProvider.refresh(this);
            Toast.makeText(this, "Tally backup imported", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "Could not read that Tally backup", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    public final class TallyBridge {
        @JavascriptInterface public void save(String json) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(STATE, json).apply();
            runOnUiThread(() -> SpendWidgetProvider.refresh(MainActivity.this));
        }

        @JavascriptInterface public void saveFile(String name, String mime, String encoded) {
            runOnUiThread(() -> writeDownload(name, mime, encoded));
        }
    }

    private void writeDownload(String name, String mime, String encoded) {
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name.replaceAll("[^A-Za-z0-9._-]", "_"));
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Tally");
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Could not create download");
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null) throw new IllegalStateException("Could not write download");
                stream.write(bytes);
            }
            Toast.makeText(this, "Saved to Downloads/Tally", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            Toast.makeText(this, "Could not save the file", Toast.LENGTH_LONG).show();
        }
    }
}
