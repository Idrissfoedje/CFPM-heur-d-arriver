package com.gbeauto237.pointage;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_FILE_CHOOSER = 1002;
    private WebView web;
    private PermissionRequest pendingPermissionRequest = null;
    private ValueCallback<Uri[]> pendingFileCallback = null;

    private boolean hasCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (pendingPermissionRequest != null) {
                if (granted) {
                    pendingPermissionRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingPermissionRequest.deny();
                }
                pendingPermissionRequest = null;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_CHOOSER) {
            if (pendingFileCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    } else if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    }
                }
                pendingFileCallback.onReceiveValue(results);
                pendingFileCallback = null;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);

        if (!hasCameraPermission()) {
            requestCamera();
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    Uri url = request.getUrl();
                    if ("appassets.androidplatform.net".equals(url.getHost())) {
                        String path = url.getPath();
                        if (path != null && path.startsWith("/assets/")) {
                            String assetPath = path.substring("/assets/".length());
                            try {
                                InputStream is = getAssets().open(assetPath);
                                String mime = "text/html";
                                if (assetPath.endsWith(".js")) mime = "application/javascript";
                                else if (assetPath.endsWith(".css")) mime = "text/css";
                                else if (assetPath.endsWith(".png")) mime = "image/png";
                                else if (assetPath.endsWith(".jpg") || assetPath.endsWith(".jpeg")) mime = "image/jpeg";
                                else if (assetPath.endsWith(".svg")) mime = "image/svg+xml";
                                return new WebResourceResponse(mime, "UTF-8", is);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean wantsCamera = false;
                        for (String r : request.getResources()) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) {
                                wantsCamera = true;
                                break;
                            }
                        }
                        if (!wantsCamera) {
                            request.deny();
                            return;
                        }
                        if (hasCameraPermission()) {
                            request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                        } else {
                            pendingPermissionRequest = request;
                            requestCamera();
                        }
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                String[] acceptTypes = params != null ? params.getAcceptTypes() : null;
                boolean isImage = false;
                if (acceptTypes != null) {
                    for (String t : acceptTypes) {
                        if (t != null && t.startsWith("image/")) {
                            isImage = true;
                            break;
                        }
                    }
                }
                if (isImage) {
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Utilisez la caméra de l'application.", Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                try {
                    Intent intent = params != null ? params.createIntent() : new Intent(Intent.ACTION_GET_CONTENT);
                    startActivityForResult(intent, REQUEST_FILE_CHOOSER);
                    return true;
                } catch (Exception e) {
                    pendingFileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { result.confirm(); }
                    })
                    .setNegativeButton("Annuler", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { result.cancel(); }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface d) { result.cancel(); }
                    })
                    .show();
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                    .setMessage(message)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { result.confirm(); }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        public void onCancel(DialogInterface d) { result.cancel(); }
                    })
                    .show();
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class Bridge {
        @JavascriptInterface
        public boolean saveCsv(final String fileName, final String content) {
            return saveFile(fileName, content, "text/csv");
        }

        @JavascriptInterface
        public boolean saveFile(final String fileName, final String content, final String mimeType) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, fileName);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(content.getBytes("UTF-8"));
                fos.flush();
                fos.close();

                android.media.MediaScannerConnection.scanFile(
                    MainActivity.this,
                    new String[]{file.getAbsolutePath()},
                    new String[]{mimeType != null ? mimeType : "*/*"},
                    null
                );

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Exporté dans Téléchargements : " + fileName, Toast.LENGTH_LONG).show();
                    }
                });
                return true;
            } catch (Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Export impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
                return false;
            }
        }

        @JavascriptInterface
        public boolean saveHistoryBackup(final String jsonContent) {
            try {
                File internalFile = new File(getFilesDir(), "pointage_cfpm_sauvegarde.json");
                FileOutputStream fosInt = new FileOutputStream(internalFile);
                fosInt.write(jsonContent.getBytes("UTF-8"));
                fosInt.flush();
                fosInt.close();

                File extDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!extDir.exists()) {
                    extDir.mkdirs();
                }
                File extFile = new File(extDir, "pointage_cfpm_sauvegarde_annuelle.json");
                FileOutputStream fosExt = new FileOutputStream(extFile);
                fosExt.write(jsonContent.getBytes("UTF-8"));
                fosExt.flush();
                fosExt.close();

                android.media.MediaScannerConnection.scanFile(
                    MainActivity.this,
                    new String[]{extFile.getAbsolutePath()},
                    new String[]{"application/json"},
                    null
                );
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String readHistoryBackup() {
            try {
                File extDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File extFile = new File(extDir, "pointage_cfpm_sauvegarde_annuelle.json");
                if (extFile.exists() && extFile.length() > 0) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(extFile);
                    byte[] data = new byte[(int) extFile.length()];
                    fis.read(data);
                    fis.close();
                    return new String(data, "UTF-8");
                }

                File internalFile = new File(getFilesDir(), "pointage_cfpm_sauvegarde.json");
                if (internalFile.exists() && internalFile.length() > 0) {
                    java.io.FileInputStream fis = new java.io.FileInputStream(internalFile);
                    byte[] data = new byte[(int) internalFile.length()];
                    fis.read(data);
                    fis.close();
                    return new String(data, "UTF-8");
                }
            } catch (Exception ignored) {
            }
            return "";
        }
    }
}
