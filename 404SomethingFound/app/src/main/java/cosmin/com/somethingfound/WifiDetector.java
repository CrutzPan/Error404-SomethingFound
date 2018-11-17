package cosmin.com.somethingfound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class WifiDetector extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        final SharedPreferences.Editor editor = sp.edit();

        int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
        switch (wifiState) {
            case WifiManager.WIFI_STATE_ENABLED:
                FirebaseDatabase db = FirebaseDatabase.getInstance();
                DatabaseReference rf = db.getReference("404test");
                rf.setValue("404-enabled");
                editor.putBoolean(context.getString(R.string.confirm_btn_key), true);
                editor.apply();

                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
                String addr = Formatter.formatIpAddress(dhcpInfo.gateway);

                if (areYouHome(context, addr)) {
                    Intent notifyIntent = new Intent(context, NotificationService.class);
                    context.startService(notifyIntent);
                }

                break;

            case WifiManager.WIFI_STATE_DISABLED:
                FirebaseDatabase db1 = FirebaseDatabase.getInstance();
                DatabaseReference rf1 = db1.getReference("404test");
                rf1.setValue("404-disabled");

                editor.putBoolean(context.getString(R.string.confirm_btn_key), false);
                editor.apply();

                break;
        }
    }

    private boolean areYouHome(Context context, String addr) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(context.getString(R.string.address_key), "").equals(addr) && sp.getBoolean(context.getString(R.string.config_on_key), false);
    }
}
