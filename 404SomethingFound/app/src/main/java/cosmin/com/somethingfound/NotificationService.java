package cosmin.com.somethingfound;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;

public class NotificationService extends IntentService {

    public NotificationService() {
        super("NotificationService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(getApplicationContext().getString(R.string.notify_channel_key),
                    getApplicationContext().getString(R.string.notify_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), getApplicationContext().getString(R.string.notify_channel_key))
                .setSmallIcon(R.drawable.baseline_assistant_black_24dp)
                .setContentTitle(getApplicationContext().getString(R.string.notif_title))
                .setContentText(getApplicationContext().getString(R.string.notif_text))
                .setContentIntent(contentIntent(getApplicationContext()))
                .setVibrate(new long[]{0, 200, 50, 300})
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(getApplicationContext(), R.color.colorAccent));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int priority;
            priority = NotificationCompat.PRIORITY_DEFAULT;
            notificationBuilder.setPriority(priority);
        }
        notificationManager.notify(10, notificationBuilder.build());

    }

    private PendingIntent contentIntent(Context context) {
        Intent startActivityIntent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 100, startActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
