package cosmin.com.somethingfound;

import android.Manifest;
import android.animation.Animator;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.format.Formatter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import cosmin.com.somethingfound.camera.Camera2;
import cosmin.com.somethingfound.camera.SafeFaceDetector;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity implements Camera2.OnCamera {

    private WifiDetector mWifiDetector;
    private Camera2 mCamera2;
    private FrameLayout frLay, infoScreen;
    private FloatingActionButton fabCLose;
    private TextView homeIpAddrInfo;

    private ArrayList<Bitmap> mBitmaps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mWifiDetector = new WifiDetector();
        registerWifiDetector();

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = sp.edit();

        frLay = findViewById(R.id.set_home_frame);
        infoScreen = findViewById(R.id.info_screen_layout);
        fabCLose = findViewById(R.id.close_session);
        homeIpAddrInfo = findViewById(R.id.ip_addr_info);

        if (sp.getBoolean(getString(R.string.config_on_key), false)) {
            infoScreen.setVisibility(View.VISIBLE);
            frLay.setVisibility(View.GONE);
            homeIpAddrInfo.setText("Home IP:   " + sp.getString(getString(R.string.address_key), ""));
        } else {
            infoScreen.setVisibility(View.GONE);
            frLay.setVisibility(View.VISIBLE);
        }

        frLay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
                String addr = Formatter.formatIpAddress(dhcpInfo.gateway);

                homeIpAddrInfo.setText("Home IP:   " + addr);

                editor.putString(getString(R.string.address_key), addr);
                editor.putBoolean(getString(R.string.config_on_key), true);
                editor.apply();

                Intent notifyIntent = new Intent(getApplicationContext(), NotificationService.class);
                startService(notifyIntent);

                revealAnimation();
            }
        });

        fabCLose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putBoolean(getString(R.string.config_on_key), false);
                editor.apply();

                reverseRevealAnimation();
            }
        });

        if (checkCameraPermission()) {
            cameraOperations();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mCamera2.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCamera2.pause();
    }

    @Override
    protected void onDestroy() {
        unregisterWifiDetector();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 2006 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraOperations();
        }
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 2006);
            return false;
        }
        return true;
    }

    private void registerWifiDetector() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        registerReceiver(mWifiDetector, intentFilter);
    }

    private void unregisterWifiDetector() {
        unregisterReceiver(mWifiDetector);
    }

    private void cameraOperations() {
        mCamera2 = new Camera2(getApplicationContext());
        mCamera2.setOnCameraCapturedListener(this);
        mCamera2.openCamera();
    }

    @Override
    public void onCameraCaptured(Bitmap imageBitmap, int maxNumber) {
        mBitmaps.add(imageBitmap);
        if (mBitmaps.size() >= maxNumber) {
            int trues = 0;
            int falses = 0;
            for (Bitmap bitmapTemp : mBitmaps) {
                if (computeFaces(bitmapTemp)) {
                    trues++;
                    Log.d("smthFound", "HAPPY!!!!");
                } else {
                    falses++;
                    Log.d("smthFound", "nooo...");
                }
            }
        }
    }

    private boolean computeFaces(Bitmap bitmap){

        FaceDetector detector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .build();

        // This is a temporary workaround for a bug in the face detector with respect to operating
        // on very small images.  This will be fixed in a future release.  But in the near term, use
        // of the SafeFaceDetector class will patch the issue.
        Detector<Face> safeDetector = new SafeFaceDetector(detector);

        // Create a frame from the bitmap and run face detection on the frame.
        Frame frame = new Frame.Builder().setBitmap(bitmap).build();
        SparseArray<Face> faces = safeDetector.detect(frame);

        int i = 0;
        while(i < faces.size()){
            Face face = faces.valueAt(i);

            if (face.getIsSmilingProbability() > 0.1f) {
                safeDetector.release();
                return true;
            }
            i++;
        }
        safeDetector.release();
        return false;
    }

    private void revealAnimation() {
        if (Build.VERSION.SDK_INT >= 21) {

            final ScaleAnimation scale = new ScaleAnimation(
                    1f, 0f, 1f, 0f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(300);
            scale.setInterpolator(new DecelerateInterpolator());
            frLay.startAnimation(scale);

            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int centerX = displayMetrics.widthPixels / 2;
            int centerY = displayMetrics.heightPixels / 2;
            float initRadius = (float) Math.sqrt(displayMetrics.widthPixels * displayMetrics.widthPixels
                    + displayMetrics.heightPixels * displayMetrics.heightPixels);

            final Animator animator = ViewAnimationUtils.createCircularReveal(infoScreen, centerX, centerY, 0, initRadius);
            animator.setDuration(800);
            animator.setInterpolator(new DecelerateInterpolator());

            scale.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) { }

                @Override
                public void onAnimationEnd(Animation animation) {
                    frLay.setVisibility(View.GONE);
                    infoScreen.setVisibility(View.VISIBLE);
                    animator.start();
                }

                @Override
                public void onAnimationRepeat(Animation animation) { }
            });
        } else {
            frLay.setVisibility(View.GONE);
            infoScreen.setVisibility(View.VISIBLE);
        }
    }

    private void reverseRevealAnimation() {
        if (Build.VERSION.SDK_INT >= 21) {

            final ScaleAnimation scale = new ScaleAnimation(
                    0f, 1f, 0f, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(300);
            scale.setInterpolator(new DecelerateInterpolator());

            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int centerX = displayMetrics.widthPixels / 2;
            int centerY = displayMetrics.heightPixels / 2;
            float initRadius = (float) Math.sqrt(displayMetrics.widthPixels * displayMetrics.widthPixels
                    + displayMetrics.heightPixels * displayMetrics.heightPixels);

            final Animator animator = ViewAnimationUtils.createCircularReveal(infoScreen, centerX, centerY, initRadius, 0);
            animator.setDuration(800);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.start();

            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) { }

                @Override
                public void onAnimationEnd(Animator animation) {
                    infoScreen.setVisibility(View.GONE);
                    frLay.setVisibility(View.VISIBLE);
                    frLay.startAnimation(scale);
                }

                @Override
                public void onAnimationCancel(Animator animation) { }

                @Override
                public void onAnimationRepeat(Animator animation) { }
            });
        } else {
            infoScreen.setVisibility(View.GONE);
            frLay.setVisibility(View.VISIBLE);
        }
    }
}
