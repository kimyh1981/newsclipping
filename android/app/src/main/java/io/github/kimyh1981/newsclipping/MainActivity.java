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
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 설정 화면: 권한 → 차 블루투스 → 시간대. 설정을 마치면 앱을 닫아도 차에 타면 자동으로 읽는다. */
public class MainActivity extends Activity {
    private static final String SITE = "https://kimyh1981.github.io/newsclipping/";
    private Prefs prefs;
    private TextView status;
    private Button permBtn, batteryBtn, carBtn, timeBtn, sourcesBtn, voiceBtn, rateBtn;
    private TextToSpeech preview; // 목소리·빠르기를 고를 때 미리 들려준다
    private boolean previewReady;
    private Runnable afterPreview;
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
        col.addView(text("차 블루투스가 연결되면 평일 아침에 고른 언론사의 오늘 헤드라인을 읽어 드립니다 (외국 언론은 우리말로 번역). 아래 순서대로 한 번만 설정하면 앱을 닫아도 됩니다.", 15));

        permBtn = button("1. 블루투스·알림 권한 허용", v -> askPermissions());
        batteryBtn = button("2. 배터리 사용 '제한 없음' 허용", v -> askBattery());
        carBtn = button("", v -> pickCar());
        timeBtn = button("", v -> pickTime());
        col.addView(permBtn);
        col.addView(batteryBtn);
        col.addView(carBtn);
        col.addView(timeBtn);
        sourcesBtn = button("", v -> pickSources());
        col.addView(sourcesBtn);
        voiceBtn = button("", v -> withPreview(this::pickVoice));
        rateBtn = button("", v -> withPreview(this::pickRate));
        col.addView(voiceBtn);
        col.addView(rateBtn);

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
    protected void onDestroy() {
        if (preview != null) preview.shutdown();
        super.onDestroy();
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
        Set<String> chosen = prefs.sources();
        sourcesBtn.setText("5. 들을 언론사: " + (chosen == null ? "기본" : chosen.size() + "곳 선택"));
        voiceBtn.setText("6. 목소리: " + (prefs.voice().isEmpty() ? "자동 (가장 자연스러운 음성)" : "직접 고름"));
        rateBtn.setText("7. 말 빠르기: " + Rules.rateLabel(prefs.rate()));
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

    /** 언론사 체크 목록: 오늘 브리핑에 든 언론사 전체를 섹션별로 보여 주고, 고른 것만 읽는다 */
    private void pickSources() {
        sourcesBtn.setEnabled(false);
        sourcesBtn.setText("언론사 목록을 불러오는 중…");
        new Thread(() -> {
            Brief b;
            try {
                b = Brief.parse(NewsService.fetch(BuildConfig.NEWS_URL));
            } catch (Exception e) {
                b = null;
            }
            final Brief brief = b;
            runOnUiThread(() -> {
                sourcesBtn.setEnabled(true);
                refresh();
                if (brief == null) {
                    new AlertDialog.Builder(this).setMessage("언론사 목록을 불러오지 못했습니다. 인터넷 연결을 확인해 주세요.").setPositiveButton("확인", null).show();
                    return;
                }
                showSources(brief);
            });
        }, "news-sources").start();
    }

    private void showSources(Brief b) {
        List<String[]> rows = b.catalog();
        Set<String> chosen = prefs.sources() == null ? b.defaults() : prefs.sources();
        String[] labels = new String[rows.size()];
        boolean[] checked = new boolean[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            labels[i] = r[0] + " · " + r[1] + (r[3].isEmpty() ? "" : " (" + r[3] + ")");
            checked[i] = chosen.contains(r[2]);
        }
        new AlertDialog.Builder(this)
                .setTitle("들을 언론사를 고르세요")
                .setMultiChoiceItems(labels, checked, (d, i, on) -> checked[i] = on)
                .setPositiveButton("저장", (d, w) -> {
                    Set<String> ids = new HashSet<>();
                    for (int i = 0; i < rows.size(); i++) if (checked[i]) ids.add(rows.get(i)[2]);
                    prefs.setSources(ids);
                    refresh();
                })
                .setNeutralButton("기본값으로", (d, w) -> { prefs.resetSources(); refresh(); })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 미리 듣기용 음성 엔진을 한 번만 띄우고, 준비되면 next를 실행한다 */
    private void withPreview(Runnable next) {
        if (previewReady) { next.run(); return; }
        afterPreview = next;
        if (preview != null) return; // 준비 중
        preview = new TextToSpeech(this, st -> runOnUiThread(() -> {
            if (st != TextToSpeech.SUCCESS) {
                preview = null;
                new AlertDialog.Builder(this).setMessage("음성 엔진을 시작하지 못했습니다.").setPositiveButton("확인", null).show();
                return;
            }
            preview.setLanguage(Locale.KOREAN);
            previewReady = true;
            if (afterPreview != null) afterPreview.run();
            afterPreview = null;
        }));
    }

    private void say(Voice v, int rate) {
        if (v != null) preview.setVoice(v);
        preview.setSpeechRate(rate / 100f);
        preview.speak(Voices.SAMPLE, TextToSpeech.QUEUE_FLUSH, null, "preview");
    }

    private void openTtsSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    /** 폰에 든 한국어 음성 목록: 누르면 미리 들려주고, 저장하면 차에서 그 음성으로 읽는다 */
    private void pickVoice() {
        List<Voice> voices = Voices.korean(preview);
        if (voices.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setMessage("고를 수 있는 한국어 음성이 없습니다. 설정의 '텍스트 음성 변환'에서 Google 음성 서비스와 한국어 음성 데이터를 받아 주세요.")
                    .setPositiveButton("설정 열기", (d, w) -> openTtsSettings())
                    .setNegativeButton("닫기", null).show();
            return;
        }
        String[] labels = new String[voices.size() + 1];
        labels[0] = "자동 (가장 자연스러운 음성)";
        int[] choice = {0};
        for (int i = 0; i < voices.size(); i++) {
            labels[i + 1] = Voices.label(voices.get(i), i);
            if (voices.get(i).getName().equals(prefs.voice())) choice[0] = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle("목소리를 고르세요 (누르면 들려 드립니다)")
                .setSingleChoiceItems(labels, choice[0], (d, i) -> {
                    choice[0] = i;
                    say(i == 0 ? Voices.best(voices) : voices.get(i - 1), prefs.rate());
                })
                .setPositiveButton("저장", (d, w) -> {
                    preview.stop();
                    prefs.setVoice(choice[0] == 0 ? "" : voices.get(choice[0] - 1).getName());
                    refresh();
                })
                .setNeutralButton("음성 더 받기", (d, w) -> { preview.stop(); openTtsSettings(); })
                .setNegativeButton("취소", (d, w) -> preview.stop())
                .show();
    }

    private void pickRate() {
        int[] choice = {1};
        for (int i = 0; i < Rules.RATES.length; i++) if (Rules.RATES[i] == prefs.rate()) choice[0] = i;
        List<Voice> voices = Voices.korean(preview);
        Voice current = null;
        for (Voice v : voices) if (v.getName().equals(prefs.voice())) current = v;
        if (current == null) current = Voices.best(voices);
        final Voice voice = current;
        new AlertDialog.Builder(this)
                .setTitle("말 빠르기 (누르면 들려 드립니다)")
                .setSingleChoiceItems(Rules.RATE_LABELS, choice[0], (d, i) -> { choice[0] = i; say(voice, Rules.RATES[i]); })
                .setPositiveButton("저장", (d, w) -> { preview.stop(); prefs.setRate(Rules.RATES[choice[0]]); refresh(); })
                .setNegativeButton("취소", (d, w) -> preview.stop())
                .show();
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
