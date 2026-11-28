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
            untilJs("!document.querySelector('a[href*=learning]')");
            // Use the same persisted fields as 1.0.1. The old record must survive a fresh launch.
            js("localStorage.setItem('garments_ot_logs_v2', JSON.stringify({'2022-03-01':{ot:3,night:true}})); localStorage.setItem('garments_ot_settings_v2', JSON.stringify({workerName:'পুরনো রেকর্ড',otRate:80}));location.reload()");
            untilJs("typeof attendanceLogs!=='undefined' && attendanceLogs['2022-03-01'] && appSettings.otRate===80");
            js("document.querySelector(\"button[onclick='addOtHours(2)']\").click();document.querySelector(\"button[onclick='adjustSelectedOt(0.5)']\").click();document.querySelector('#btnToggleNight').click()");
            js("document.querySelector('#nav-payslip').click();document.querySelector('#paidOtHours').value='১:৩০';document.querySelector('#paidOtMoney').value='১০০';document.querySelector('#paidOtForm button[type=submit]').click()");
            untilJs("document.querySelector('#paidOtResult').textContent.includes('১ ঘণ্টা কম') && document.querySelector('#paidOtResult').textContent.includes('৳১০০ কম')");
            js("document.querySelector('button[aria-label=\"আগের মাস\"]').click()");
            untilJs("document.querySelector('#paidOtHours').value==='' && document.querySelector('#clearPaidOt').hidden");
            js("document.querySelector('button[aria-label=\"পরের মাস\"]').click();location.reload()");
            untilJs("typeof getMonthlyStats==='function' && getMonthlyStats(activeMonthDate.getFullYear(),activeMonthDate.getMonth()).totalOt===2.5 && getMonthlyStats(activeMonthDate.getFullYear(),activeMonthDate.getMonth()).totalNight===1 && monthlyPayslips[payslipMonth(activeMonthDate)]?.paidMinutes===90 && attendanceLogs['2022-03-01'].ot===3");
            progress("Legacy data, half-hour entry, Bengali hours and money, monthly separation and persisted payslip comparison passed.");
            js("document.querySelector('#nav-home').click();var dateField=document.querySelector('#entryDateInput');dateField.value='2024-01-31';dateField.dispatchEvent(new Event('change'));document.querySelector('#shiftDetails').open=true");
            js("document.querySelector('#shiftStart').value='22:00';document.querySelector('#shiftEnd').value='07:17';document.querySelector('#shiftBreak').value='৩০';document.querySelector('#shiftRegular').value='৮';document.querySelector('#shiftRegular').dispatchEvent(new Event('input',{bubbles:true}))");
            untilJs("document.querySelector('#shiftResult').textContent.includes('৪৭ মিনিট') && document.querySelector('#shiftBreakdown').textContent.includes('পরের দিন')");
            js("document.querySelector('#saveShift').click();document.querySelector('#saveShift').click()");
            untilJs("getMonthlyStats(2024,0).totalOtMinutes===47 && !attendanceLogs['2024-02-01'] && attendanceLogs['2024-01-31'].shift.breakMinutes===30 && document.querySelector('#sumOtHours').textContent==='৪৭ মিনিট'");
            js("document.querySelector(\"button[onclick='adjustSelectedOt(0.5)']\").click()");
            untilJs("attendanceLogs['2024-01-31'].otMinutes===77 && document.querySelector('#shiftSaved').textContent.includes('হাতে ঠিক করা')");
            js("document.querySelector('#saveShift').click();location.reload()");
            untilJs("typeof attendanceLogs!=='undefined' && attendanceLogs['2024-01-31']?.otMinutes===47 && attendanceLogs['2024-01-31'].shift.end==='07:17'");
            js("editDateFromCalendar('2024-01-31')");
            untilJs("document.querySelector('#shiftEnd').value==='07:17' && document.querySelector('#otCheckRecorded').textContent==='৪৭ মিনিট'");
            progress("Overnight month boundary, exact minutes, repeated save, manual adjustment and persisted shift passed.");
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
            outcome.putString("stream", "PASS: Android 16 offline shift OT, exact minutes, payslip comparison, existing records, and JSON document picker (" + foreground + ").\n");
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
