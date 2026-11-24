package com.mrzahidbd.garmentsot.check;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class PhoneCheck extends Instrumentation {
    private Activity screen;
    private WebView web;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override public void onStart() {
        Bundle outcome = new Bundle();
        try {
            String app = getTargetContext().getPackageName();
            screen = startActivitySync(new Intent(Intent.ACTION_MAIN)
                    .setClassName(app, app + ".MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> web = findWeb(screen.getWindow().getDecorView()));
            if (web == null) throw new IllegalStateException("Missing application WebView");
            untilJs("document.readyState==='complete' && !!document.querySelector('#btnToggleNight')");
            untilJs("document.querySelector('h1').textContent.includes('আমার হাজিরা')");
            js("document.querySelector(\"button[onclick='addOtHours(2)']\").click(); document.querySelector('#btnToggleNight').click()");
            js("location.reload()");
            untilJs("document.readyState==='complete' && typeof getMonthlyStats==='function' && getMonthlyStats(activeMonthDate.getFullYear(),activeMonthDate.getMonth()).totalOt===2 && getMonthlyStats(activeMonthDate.getFullYear(),activeMonthDate.getMonth()).totalNight===1");
            progress("Two hours of OT and one night persisted after reload.");
            js("document.querySelector('#nav-settings').click()");
            untilJs("!!document.querySelector(\"button[onclick='exportJsonBackup()']\").offsetWidth");
            String bridge = js("JSON.stringify({save:typeof saveTrackerFile,bridge:typeof window.TrackerFiles,post:typeof (window.TrackerFiles||{}).postMessage,origin:location.origin,name:formatDateStr(new Date()),contentType:document.contentType})");
            progress("Offline home and monthly entries passed. Export bridge: " + bridge);

            IntentFilter filter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);
            filter.addCategory(Intent.CATEGORY_OPENABLE);
            filter.addDataType("application/json");
            ActivityMonitor monitor = addMonitor(filter, null, false);
            js("var button=document.querySelector(\"button[onclick='exportJsonBackup()']\");button.scrollIntoView({block:'center'});setTimeout(() => button.click(),100);true");
            long limit = SystemClock.elapsedRealtime() + 12000;
            String foreground = "";
            while (SystemClock.elapsedRealtime() < limit) {
                AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                if (root != null && root.getPackageName() != null) foreground = root.getPackageName().toString();
                if (monitor.getHits() > 0 && !foreground.isEmpty() && !app.equals(foreground)
                        && !foreground.equals("com.android.systemui")) break;
                SystemClock.sleep(200);
            }
            progress("Document intent hits " + monitor.getHits() + "; foreground " + foreground);
            if (monitor.getHits() == 0 || foreground.isEmpty() || app.equals(foreground)
                    || foreground.equals("com.android.systemui")) {
                throw new IllegalStateException("Document request count " + monitor.getHits()
                        + ", foreground " + foreground + ", " + bridge);
            }
            removeMonitor(monitor);
            outcome.putString("stream", "PASS: Android 16 offline UI, OT and night persistence, and JSON document picker (" + foreground + ").\n");
            finish(Activity.RESULT_OK, outcome);
        } catch (Throwable e) {
            outcome.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e) + "\n");
            finish(Activity.RESULT_CANCELED, outcome);
        }
    }

    private static WebView findWeb(View v) {
        if (v instanceof WebView) return (WebView)v;
        if (v instanceof ViewGroup) {
            ViewGroup g=(ViewGroup)v;
            for (int i=0;i<g.getChildCount();i++) {
                WebView found=findWeb(g.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private String js(String code) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<String> result=new AtomicReference<>();
        runOnMainSync(() -> web.evaluateJavascript(code, value -> {result.set(value);latch.countDown();}));
        if (!latch.await(12, TimeUnit.SECONDS)) throw new IllegalStateException("WebView did not answer: "+code);
        return result.get();
    }

    private void untilJs(String condition) throws Exception {
        long end=SystemClock.elapsedRealtime()+15000;
        while (SystemClock.elapsedRealtime()<end) {
            if ("true".equals(js(condition))) return;
            SystemClock.sleep(200);
        }
        throw new IllegalStateException("Unmet condition: "+condition);
    }

    private void progress(String text) {
        Bundle state=new Bundle();
        state.putString("stream",text+"\n");
        sendStatus(1,state);
    }
}
