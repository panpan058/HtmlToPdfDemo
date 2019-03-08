package com.ppw.htmltopdfdemo;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.dx.stock.ProxyBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {
    WebView mWebView;
    private WebSettings mSettings;
    private String pdfFilePath;
    private ParcelFileDescriptor descriptor;
    private PageRange[] ranges;
    private PrintDocumentAdapter printAdapter;
    ProgressBar mProgressBar;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mWebView = findViewById(R.id.wv);
        mProgressBar = findViewById(R.id.pb);
        mSettings = mWebView.getSettings();
        mSettings.setAllowContentAccess(true);
        mSettings.setBuiltInZoomControls(false);
        mSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        mSettings.setJavaScriptEnabled(true);

        // 开启Application Cache功能
        mSettings.setAppCacheEnabled(true);

        //设置适配
        mSettings.setUseWideViewPort(true);
        mSettings.setLoadWithOverviewMode(true);
        mSettings.setDomStorageEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        pdfFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.pdf";
        mWebView.setWebChromeClient(new WebChromeClient());
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted (WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished (WebView view, String url) {
                super.onPageFinished(view, url);
                mProgressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading (WebView view, String url) {
                // 返回值是true的时候控制去WebView打开，为false调用系统浏览器或第三方浏览器
//                view.loadUrl(url);
                return true;
            }
        });
//        mWebView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:54.0) Gecko/20100101
// Firefox/54.0");
        mWebView.loadUrl("https://www.baidu.com");

    }

    public void printPDF (View view) {
        boolean hasPermission =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PermissionChecker.PERMISSION_GRANTED;
        if (! hasPermission) {
            if (Build.VERSION.SDK_INT > 22) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
            } else {
                Toast.makeText(MainActivity.this, "请打开读写权限", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        webViewToPdf();
    }

    private void webViewToPdf () {
        mProgressBar.setVisibility(View.VISIBLE);
        //创建DexMaker缓存目录
        try {
            File pdfFile = new File(pdfFilePath);
            if (pdfFile.exists()) {
                pdfFile.delete();
            }
            pdfFile.createNewFile();
            descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_WRITE);
            // 设置打印参数
            PrintAttributes.MediaSize isoA4 = PrintAttributes.MediaSize.ISO_A4;
            PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(isoA4)
                    .setResolution(new PrintAttributes.Resolution("id", Context.PRINT_SERVICE, 500, 500))
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();
            // 计算webview打印需要的页数
            int numberOfPages = (int) ((mWebView.getContentHeight() * 500 / (isoA4.getHeightMils())));
            ranges = new PageRange[]{new PageRange(0, numberOfPages)};
            // 创建pdf文件缓存目录
            // 获取需要打印的webview适配器
            printAdapter = mWebView.createPrintDocumentAdapter();
            // 开始打印
            printAdapter.onStart();
            printAdapter.onLayout(attributes, attributes, new CancellationSignal(),
                    getLayoutResultCallback(new InvocationHandler() {
                        @Override
                        public Object invoke (Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.getName().equals("onLayoutFinished")) {
                                // 监听到内部调用了onLayoutFinished()方法,即打印成功
                                onLayoutSuccess();
                            } else {
                                // 监听到打印失败或者取消了打印
                                Toast.makeText(MainActivity.this, "导出失败,请重试", Toast.LENGTH_SHORT).show();
                            }
                            return null;
                        }
                    }), new Bundle());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onLayoutSuccess () throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            PrintDocumentAdapter.WriteResultCallback callback = getWriteResultCallback(new InvocationHandler() {
                @Override
                public Object invoke (Object o, Method method, Object[] objects) {
                    if (method.getName().equals("onWriteFinished")) {
                        Toast.makeText(MainActivity.this, "导出成功", Toast.LENGTH_SHORT).show();
                        mProgressBar.setVisibility(View.GONE);
                    } else {
                        Toast.makeText(MainActivity.this, "导出失败", Toast.LENGTH_SHORT).show();
                    }
                    return null;
                }
            });
            printAdapter.onWrite(ranges, descriptor, new CancellationSignal(), callback);
        }
    }

    public static PrintDocumentAdapter.LayoutResultCallback getLayoutResultCallback (InvocationHandler invocationHandler) throws IOException {
        return ProxyBuilder.forClass(PrintDocumentAdapter.LayoutResultCallback.class)
                .handler(invocationHandler)
                .build();
    }

    public static PrintDocumentAdapter.WriteResultCallback getWriteResultCallback (InvocationHandler invocationHandler) throws IOException {
        return ProxyBuilder.forClass(PrintDocumentAdapter.WriteResultCallback.class)
                .handler(invocationHandler)
                .build();
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            boolean hasPermission = true;
            for (int hasPer : grantResults) {
                if (hasPer == PermissionChecker.PERMISSION_DENIED) {
                    hasPermission = false;
                    break;
                }
            }
            if (hasPermission) {
                webViewToPdf();
            }
        }
    }
}
