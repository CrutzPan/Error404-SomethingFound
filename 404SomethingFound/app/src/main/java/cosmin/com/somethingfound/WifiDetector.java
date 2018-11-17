package cosmin.com.somethingfound;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.util.Log;

public class WifiDetector extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, false)) {
            // Wait for Wifi connection.
            while (!isConnectedToWifi(context));

            Log.d("smthFound", "CONNECTED");

            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            String addr = Formatter.formatIpAddress(dhcpInfo.gateway);

            if (areYouHome(context, addr)) {
                Intent notifyIntent = new Intent(context, NotificationService.class);
                context.startService(notifyIntent);
            }
        }
        else {
            Log.d("smthFound", "DISCONNECTED");
        }
    }

    private boolean isConnectedToWifi(Context context) {
        ConnectivityManager connMng = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMng.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo.isConnected();
    }

    private boolean areYouHome(Context context, String addr) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(context.getString(R.string.address_key), "").equals(addr) && sp.getBoolean(context.getString(R.string.config_on_key), false);
    }
}
