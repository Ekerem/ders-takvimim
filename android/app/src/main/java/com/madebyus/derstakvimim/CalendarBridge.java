package com.madebyus.derstakvimim;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Web sayfasının telefondaki takvime (Google hesabı dahil) ulaştığı köprü.
 * JS tarafında window.AndroidApp olarak görünür. Her yöntem JSON döndürür:
 * {"ok":true,"data":...} ya da {"ok":false,"error":"...","code":"..."}.
 */
public class CalendarBridge {

    static final int PERMISSION_REQUEST = 42;
    // Google Takvim etkinlik renkleri: 10 = Fesleğen (yeşil), 11 = Domates (kırmızı)
    static final String PAID_KEY = "10";
    static final String UNPAID_KEY = "11";
    static final int PAID_ARGB = 0xFF0B8043;
    static final int UNPAID_ARGB = 0xFFD50000;

    private final Activity activity;
    private final ContentResolver resolver;
    private final Map<Long, Boolean> keySupport = new HashMap<>();

    CalendarBridge(Activity activity) {
        this.activity = activity;
        this.resolver = activity.getContentResolver();
    }

    /* ---------- yardımcılar ---------- */

    private static String ok(Object data) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("data", data == null ? JSONObject.NULL : data);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":true,\"data\":null}";
        }
    }

    private static String fail(Exception e) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", false);
            if (e instanceof SecurityException) {
                o.put("code", "perm");
                o.put("error", "Takvim izni yok.");
            } else {
                o.put("code", "native");
                o.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            }
            return o.toString();
        } catch (Exception ignored) {
            return "{\"ok\":false,\"error\":\"Takvim hatası\"}";
        }
    }

    boolean granted() {
        return activity.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private String[] account(long calId) {
        try (Cursor c = resolver.query(ContentUris.withAppendedId(Calendars.CONTENT_URI, calId),
                new String[]{Calendars.ACCOUNT_NAME, Calendars.ACCOUNT_TYPE}, null, null, null)) {
            if (c != null && c.moveToFirst()) return new String[]{c.getString(0), c.getString(1)};
        }
        return null;
    }

    /** Takvim, Google'ın renk anahtarlarını (10 ve 11) tanıyor mu? */
    private boolean supportsKeys(long calId) {
        Boolean cached = keySupport.get(calId);
        if (cached != null) return cached;
        boolean has10 = false, has11 = false;
        String[] acc = account(calId);
        if (acc != null) {
            String sel = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE + "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;
            try (Cursor c = resolver.query(Colors.CONTENT_URI, new String[]{Colors.COLOR_KEY}, sel, acc, null)) {
                while (c != null && c.moveToNext()) {
                    String k = c.getString(0);
                    if (PAID_KEY.equals(k)) has10 = true;
                    if (UNPAID_KEY.equals(k)) has11 = true;
                }
            }
        }
        boolean r = has10 && has11;
        keySupport.put(calId, r);
        return r;
    }

    private void putColor(ContentValues v, long calId, boolean paid) {
        if (supportsKeys(calId)) v.put(Events.EVENT_COLOR_KEY, paid ? PAID_KEY : UNPAID_KEY);
        else v.put(Events.EVENT_COLOR, paid ? PAID_ARGB : UNPAID_ARGB);
    }

    /**
     * Tek bir ders örneğini değiştirir. Örnek tekrar eden bir serinin parçasıysa yalnızca o hafta
     * için bir istisna oluşturulur; serinin diğer haftaları aynen kalır.
     */
    private void applyToInstance(long eventId, long begin, boolean master, ContentValues v) {
        if (master) {
            v.put(Events.ORIGINAL_INSTANCE_TIME, begin);
            if (!v.containsKey(Events.STATUS)) v.put(Events.STATUS, Events.STATUS_CONFIRMED);
            Uri uri = ContentUris.withAppendedId(Events.CONTENT_EXCEPTION_URI, eventId);
            if (resolver.insert(uri, v) == null) throw new IllegalStateException("Takvim bu değişikliği kaydedemedi.");
        } else {
            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
            if (resolver.update(uri, v, null, null) < 1) throw new IllegalStateException("Ders takvimde bulunamadı. Yenileyip tekrar dene.");
        }
    }

    /* ---------- JS'e açık yöntemler ---------- */

    @JavascriptInterface
    public String apkVersion() {
        return BuildConfig.VERSION_NAME;
    }

    @JavascriptInterface
    public boolean hasCalendarPermission() {
        return granted();
    }

    @JavascriptInterface
    public void requestCalendarPermission() {
        activity.runOnUiThread(() -> activity.requestPermissions(
                new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR},
                PERMISSION_REQUEST));
    }

    @JavascriptInterface
    public String calendars() {
        try {
            JSONArray out = new JSONArray();
            String[] proj = {Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                    Calendars.ACCOUNT_TYPE, Calendars.IS_PRIMARY, Calendars.OWNER_ACCOUNT};
            String sel = Calendars.CALENDAR_ACCESS_LEVEL + ">=? AND " + Calendars.VISIBLE + "=1";
            try (Cursor c = resolver.query(Calendars.CONTENT_URI, proj, sel,
                    new String[]{String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR)}, null)) {
                while (c != null && c.moveToNext()) {
                    String acc = c.getString(2) == null ? "" : c.getString(2);
                    String owner = c.getString(5) == null ? "" : c.getString(5);
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0));
                    o.put("name", c.getString(1) == null ? acc : c.getString(1));
                    o.put("account", acc);
                    o.put("type", c.getString(3) == null ? "" : c.getString(3));
                    o.put("primary", (!c.isNull(4) && c.getInt(4) == 1) || (!owner.isEmpty() && owner.equals(acc)));
                    out.put(o);
                }
            }
            return ok(out);
        } catch (Exception e) {
            return fail(e);
        }
    }

    /** from..to aralığındaki tüm etkinlik örnekleri (tekrar edenler açılmış olarak). */
    @JavascriptInterface
    public String events(long calId, long from, long to) {
        try {
            Uri.Builder b = Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(b, from);
            ContentUris.appendId(b, to);
            String[] proj = {Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.TITLE,
                    Instances.DESCRIPTION, Instances.EVENT_COLOR_KEY, Instances.EVENT_COLOR, Instances.RRULE,
                    Instances.ORIGINAL_ID, Instances.STATUS, Instances.ALL_DAY};
            String sel = Instances.CALENDAR_ID + "=?";
            JSONArray out = new JSONArray();
            try (Cursor c = resolver.query(b.build(), proj, sel, new String[]{String.valueOf(calId)}, Instances.BEGIN + " ASC")) {
                while (c != null && c.moveToNext()) {
                    if (!c.isNull(10) && c.getInt(10) == 1) continue;                       // tüm gün
                    if (!c.isNull(9) && c.getInt(9) == Events.STATUS_CANCELED) continue;     // iptal
                    JSONObject o = new JSONObject();
                    o.put("eventId", c.getLong(0));
                    o.put("begin", c.getLong(1));
                    o.put("end", c.getLong(2));
                    o.put("title", c.getString(3) == null ? "" : c.getString(3));
                    o.put("desc", c.getString(4) == null ? "" : c.getString(4));
                    String key = c.getString(5);
                    if (key == null && !c.isNull(6)) {
                        // renk anahtarı yoksa doğrudan renge bak
                        int col = c.getInt(6);
                        if (col == PAID_ARGB) key = PAID_KEY;
                        else if (col == UNPAID_ARGB) key = UNPAID_KEY;
                    }
                    o.put("colorKey", key == null ? JSONObject.NULL : key);
                    String rrule = c.getString(7);
                    o.put("master", rrule != null && !rrule.trim().isEmpty());
                    o.put("originalId", c.isNull(8) ? JSONObject.NULL : (Object) c.getLong(8));
                    out.put(o);
                }
            }
            return ok(out);
        } catch (Exception e) {
            return fail(e);
        }
    }

    @JavascriptInterface
    public String setPaid(long calId, long eventId, long begin, boolean master, boolean paid) {
        try {
            ContentValues v = new ContentValues();
            putColor(v, calId, paid);
            applyToInstance(eventId, begin, master, v);
            return ok(null);
        } catch (Exception e) {
            return fail(e);
        }
    }

    @JavascriptInterface
    public String update(long calId, long eventId, long begin, boolean master, String json) {
        try {
            JSONObject f = new JSONObject(json);
            ContentValues v = new ContentValues();
            putColor(v, calId, f.optBoolean("paid", false));
            if (f.has("title")) v.put(Events.TITLE, f.getString("title"));
            if (f.has("description")) v.put(Events.DESCRIPTION, f.getString("description"));
            if (f.has("startMs") && f.has("endMs")) {
                v.put(Events.DTSTART, f.getLong("startMs"));
                v.put(Events.DTEND, f.getLong("endMs"));
                v.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            }
            applyToInstance(eventId, begin, master, v);
            return ok(null);
        } catch (Exception e) {
            return fail(e);
        }
    }

    /** weeks: 0 = tekrar yok, -1 = süresiz her hafta, n = n hafta */
    @JavascriptInterface
    public String create(long calId, String json) {
        try {
            JSONObject f = new JSONObject(json);
            long start = f.getLong("startMs"), end = f.getLong("endMs");
            int weeks = f.optInt("weeks", 0);
            ContentValues v = new ContentValues();
            v.put(Events.CALENDAR_ID, calId);
            v.put(Events.TITLE, f.getString("title"));
            String desc = f.optString("description", "");
            if (!desc.isEmpty()) v.put(Events.DESCRIPTION, desc);
            v.put(Events.DTSTART, start);
            v.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID());
            if (weeks == 0) {
                v.put(Events.DTEND, end);
            } else {
                v.put(Events.DURATION, "P" + ((end - start) / 1000) + "S");
                v.put(Events.RRULE, weeks < 0 ? "FREQ=WEEKLY" : "FREQ=WEEKLY;COUNT=" + weeks);
            }
            putColor(v, calId, f.optBoolean("paid", false));
            if (resolver.insert(Events.CONTENT_URI, v) == null) throw new IllegalStateException("Ders eklenemedi.");
            return ok(null);
        } catch (Exception e) {
            return fail(e);
        }
    }

    /** Yalnızca bu haftaki dersi siler (seriyse o hafta iptal edilir). */
    @JavascriptInterface
    public String deleteOne(long eventId, long begin, boolean master) {
        try {
            if (master) {
                ContentValues v = new ContentValues();
                v.put(Events.STATUS, Events.STATUS_CANCELED);
                applyToInstance(eventId, begin, true, v);
            } else {
                resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null);
            }
            return ok(null);
        } catch (Exception e) {
            return fail(e);
        }
    }

    /** Tekrar eden serinin tamamını siler. */
    @JavascriptInterface
    public String deleteSeries(long eventId) {
        try {
            resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null);
            return ok(null);
        } catch (Exception e) {
            return fail(e);
        }
    }

    /** Google ile hemen eşitlemeyi ister (değişiklikler bilgisayardaki takvime de geçsin). */
    @JavascriptInterface
    public void sync(long calId) {
        try {
            String[] acc = account(calId);
            if (acc == null || acc[1] == null || CalendarContract.ACCOUNT_TYPE_LOCAL.equals(acc[1])) return;
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            ContentResolver.requestSync(new Account(acc[0], acc[1]), CalendarContract.AUTHORITY, extras);
        } catch (Exception ignored) {
        }
    }
}
