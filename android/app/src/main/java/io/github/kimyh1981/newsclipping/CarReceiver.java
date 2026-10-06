package io.github.kimyh1981.newsclipping;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.Calendar;

/** 차 블루투스가 연결되면(평일 아침 시간대, 하루 한 번) 뉴스를 읽기 시작하고, 끊기면 멈춘다. */
public class CarReceiver extends BroadcastReceiver {
    private static final String TAG = "newsclipping";

    @Override
    public void onReceive(Context context, Intent intent) {
        @SuppressWarnings("deprecation")
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (device == null) return;
        Prefs prefs = new Prefs(context);
        if (!isCar(device, prefs)) return;

        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) {
            NewsService.stopIfRunning();
            return;
        }
        if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(intent.getAction())) return;
        Calendar now = Calendar.getInstance();
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (!prefs.enabled() || prefs.playedToday()
                || !Rules.inWindow(now.get(Calendar.DAY_OF_WEEK), minute, prefs.start(), prefs.end(), prefs.weekdaysOnly())) {
            Log.i(TAG, "연결됨, 재생 조건 아님");
            return;
        }
        NewsService.start(context, false);
    }

    private static boolean isCar(BluetoothDevice device, Prefs prefs) {
        String chosen = prefs.carAddress();
        if (!chosen.isEmpty()) return chosen.equalsIgnoreCase(device.getAddress());
        try {
            BluetoothClass cls = device.getBluetoothClass();
            return cls != null && cls.getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO;
        } catch (SecurityException e) {
            return false; // 블루투스 권한이 없으면 차를 고를 때까지 기다린다
        }
    }
}
