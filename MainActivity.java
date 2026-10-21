package com.sade.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Ultra sadə launcher: yalnız mətn siyahısı, saat və axtarış.
 * İkonlar yüklənmir -> az RAM, donma yoxdur.
 */
public class MainActivity extends Activity {

    private static final class App {
        final String label;
        final String lower;
        final String pkg;
        final String cls;

        App(String label, String pkg, String cls) {
            this.label = label;
            this.lower = label.toLowerCase(Locale.ROOT);
            this.pkg = pkg;
            this.cls = cls;
        }
    }

    private final ArrayList<App> all = new ArrayList<>();
    private final ArrayList<App> shown = new ArrayList<>();
    private BaseAdapter adapter;
    private ListView list;
    private EditText search;
    private float density;
    private int iconPx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService iconPool = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap> iconCache = new LruCache<>(80);

    private static final class Holder {
        ImageView icon;
        TextView text;
        String key;
    }

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        iconPx = dp(40);

        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(24), dp(20), 0);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        clock.setGravity(Gravity.START);
        root.addView(clock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d MMMM");
        date.setFormat12Hour("EEEE, d MMMM");
        date.setTextColor(0xFF9E9E9E);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        root.addView(date, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        search = new EditText(this);
        search.setHint("Axtar...");
        search.setHintTextColor(0xFF666666);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setBackgroundColor(0xFF1A1A1A);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(16);
        sp.bottomMargin = dp(8);
        root.addView(search, sp);

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(android.R.color.transparent);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int p) { return shown.get(p); }
            @Override public long getItemId(int p) { return p; }

            @Override
            public View getView(int p, View v, ViewGroup parent) {
                Holder h;
                if (v == null) {
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(4), dp(7), dp(4), dp(7));
                    h = new Holder();
                    h.icon = new ImageView(MainActivity.this);
                    row.addView(h.icon, new LinearLayout.LayoutParams(iconPx, iconPx));
                    h.text = new TextView(MainActivity.this);
                    h.text.setTextColor(Color.WHITE);
                    h.text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                    h.text.setSingleLine(true);
                    LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    tp.leftMargin = dp(16);
                    row.addView(h.text, tp);
                    row.setTag(h);
                    v = row;
                } else {
                    h = (Holder) v.getTag();
                }
                final App a = shown.get(p);
                h.text.setText(a.label);
                h.key = a.pkg + "/" + a.cls;
                Bitmap bmp = iconCache.get(h.key);
                if (bmp != null) {
                    h.icon.setImageBitmap(bmp);
                } else {
                    h.icon.setImageDrawable(null);
                    loadIcon(a, h);
                }
                return v;
            }
        };
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> a, View v, int p, long id) {
                launch(shown.get(p));
            }
        });

        // uzun basma -> tətbiq məlumatı (silmək / dayandırmaq üçün)
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> a, View v, int p, long id) {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + shown.get(p).pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception ignored) { }
                return true;
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { filter(e.toString()); }
        });

        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, android.view.KeyEvent e) {
                if (!shown.isEmpty()) launch(shown.get(0));
                return true;
            }
        });

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        loadApps();
    }

    private void loadApps() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                PackageManager pm = getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> res = pm.queryIntentActivities(main, 0);
                final ArrayList<App> tmp = new ArrayList<>(res.size());
                for (ResolveInfo r : res) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;
                    tmp.add(new App(String.valueOf(r.loadLabel(pm)), pkg, r.activityInfo.name));
                }
                Collections.sort(tmp, new Comparator<App>() {
                    @Override public int compare(App a, App b) { return a.lower.compareTo(b.lower); }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        all.clear();
                        all.addAll(tmp);
                        filter(search.getText().toString());
                    }
                });
            }
        }).start();
    }

    private void loadIcon(final App a, final Holder h) {
        final String key = a.pkg + "/" + a.cls;
        iconPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Drawable d = getPackageManager().getActivityIcon(new ComponentName(a.pkg, a.cls));
                    final Bitmap b = Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(b);
                    d.setBounds(0, 0, iconPx, iconPx);
                    d.draw(c);
                    iconCache.put(key, b);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (key.equals(h.key)) h.icon.setImageBitmap(b);
                        }
                    });
                } catch (Throwable ignored) { }
            }
        });
    }

    private void filter(String q) {
        q = q.trim().toLowerCase(Locale.ROOT);
        shown.clear();
        if (q.isEmpty()) {
            shown.addAll(all);
        } else {
            for (App a : all) {
                if (a.lower.contains(q)) shown.add(a);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(a.pkg, a.cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(i); } catch (Exception ignored) { }
        search.setText("");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home düyməsi: axtarışı sıfırla, siyahını yuxarı qaytar
        search.setText("");
        search.clearFocus();
        list.setSelection(0);
    }

    @Override
    public void onBackPressed() {
        // Launcher-dən çıxmaq olmaz; sadəcə sıfırla
        search.setText("");
        list.setSelection(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
        iconPool.shutdownNow();
    }

    private int dp(int v) {
        return Math.round(v * density);
    }
}
