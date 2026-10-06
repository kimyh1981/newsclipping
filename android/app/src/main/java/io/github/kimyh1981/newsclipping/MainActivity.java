package io.github.kimyh1981.newsclipping;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 설정 화면: 권한 → 차 블루투스 → 시간대. 설정을 마치면 앱을 닫아도 차에 타면 자동으로 읽는다. */
public class MainActivity extends Activity {
    private static final String SITE = "https://kimyh1981.github.io/newsclipping/";
    private Prefs prefs;
    private TextView status;
    private Button permBtn, batteryBtn, carBtn, timeBtn;
    private CheckBox enabledBox, weekdaysBox;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        NewsService.ensureChannel(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        col.setPadding(pad, pad, pad, pad);

        TextView title = text("뉴스클리핑", 24);
        title.setGravity(Gravity.START);
        col.addView(title);
        col.addView(text("차 블루투스가 연결되면 평일 아침에 오늘의 주요 신문·농업·비료·국제/경제 헤드라인을 읽어 드립니다. 아래 순서대로 한 번만 설정하면 앱을 닫아도 됩니다.", 15));

        permBtn = button("1. 블루투스·알림 권한 허용", v -> askPermissions());
        batteryBtn = button("2. 배터리 사용 '제한 없음' 허용", v -> askBattery());
        carBtn = button("", v -> pickCar());
        timeBtn = button("", v -> pickTime());
        col.addView(permBtn);
        col.addView(batteryBtn);
        col.addView(carBtn);
        col.addView(timeBtn);

        weekdaysBox = new CheckBox(this);
        weekdaysBox.setText("평일(월~금)에만 · 공휴일은 서버가 알아서 건너뜁니다");
        weekdaysBox.setOnCheckedChangeListener((x, on) -> { prefs.setWeekdaysOnly(on); refresh(); });
        col.addView(weekdaysBox);
        enabledBox = new CheckBox(this);
        enabledBox.setText("차에 타면 자동으로 읽기");
        enabledBox.setOnCheckedChangeListener((x, on) -> { prefs.setEnabled(on); refresh(); });
        col.addView(enabledBox);

        col.addView(button("▶ 지금 들어보기", v -> NewsService.start(this, true)));
        col.addView(button("■ 멈춤", v -> NewsService.stopIfRunning()));
        col.addView(button("오늘 기사 목록 보기 (웹)", v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SITE)))));

        status = text("", 14);
        status.setPadding(0, dp(16), 0, 0);
        col.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        boolean perms = hasBluetoothPermission() && hasNotifyPermission();
        permBtn.setText((perms ? "✓ " : "") + "1. 블루투스·알림 권한 허용");
        batteryBtn.setText((batteryOk() ? "✓ " : "") + "2. 배터리 사용 '제한 없음' 허용");
        String car = prefs.carAddress().isEmpty() ? "자동 (오디오 블루투스 기기 모두)" : prefs.carName();
        carBtn.setText("3. 차 블루투스: " + car);
        timeBtn.setText("4. 재생 시간: " + Rules.hhmm(prefs.start()) + " ~ " + Rules.hhmm(prefs.end()));
        weekdaysBox.setChecked(prefs.weekdaysOnly());
        enabledBox.setChecked(prefs.enabled());
        String last = prefs.lastPlayed();
        status.setText("버전 " + BuildConfig.VERSION_NAME
                + (last.isEmpty() ? "" : "\n마지막 자동 재생: " + last)
                + (perms && batteryOk() ? "" : "\n1·2번을 허용해야 차에서 자동으로 시작합니다."));
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotifyPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean batteryOk() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    private void askPermissions() {
        List<String> want = new ArrayList<>();
        if (!hasBluetoothPermission()) want.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (!hasNotifyPermission()) want.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!want.isEmpty()) requestPermissions(want.toArray(new String[0]), 1);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refresh();
    }

    @android.annotation.SuppressLint("BatteryLife")
    private void askBattery() {
        if (batteryOk()) return;
        // 배터리 최적화 예외여야 Android 12 이상에서 차 연결 순간 백그라운드에서 읽기를 시작할 수 있다
        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
    }

    private void pickCar() {
        if (!hasBluetoothPermission()) { askPermissions(); return; }
        BluetoothAdapter adapter = getSystemService(BluetoothManager.class).getAdapter();
        List<String> names = new ArrayList<>();
        List<String> addrs = new ArrayList<>();
        names.add("자동 (오디오 블루투스 기기 모두)");
        addrs.add("");
        try {
            Set<BluetoothDevice> bonded = adapter == null ? null : adapter.getBondedDevices();
            if (bonded != null) for (BluetoothDevice d : bonded) {
                names.add(d.getName() == null ? d.getAddress() : d.getName());
                addrs.add(d.getAddress());
            }
        } catch (SecurityException e) {
            askPermissions();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("차 블루투스를 고르세요")
                .setItems(names.toArray(new String[0]), (d, i) -> { prefs.setCar(addrs.get(i), i == 0 ? "" : names.get(i)); refresh(); })
                .show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (v1, h1, m1) ->
                new TimePickerDialog(this, (v2, h2, m2) -> { prefs.setWindow(h1 * 60 + m1, h2 * 60 + m2); refresh(); },
                        prefs.end() / 60, prefs.end() % 60, true) {{ setTitle("끝나는 시각"); }}.show(),
                prefs.start() / 60, prefs.start() % 60, true) {{ setTitle("시작 시각"); }}.show();
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setAllCaps(false);
        btn.setOnClickListener(l);
        return btn;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
