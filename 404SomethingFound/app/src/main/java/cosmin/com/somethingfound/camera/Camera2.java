package cosmin.com.somethingfound.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.*;

public class Camera2 {

    public interface OnCamera {
        void onCameraCaptured(Bitmap imageBitmap, int currentNumber, int maxNumber);
    }
    private OnCamera cameraCallback;
    public void setOnCameraCapturedListener(OnCamera l) {
        this.cameraCallback = l;
    }

    private Context context;
    private int currentNumber;
    private int pictureNumber = 10;
    private int FPS;
    private long waitTime = System.currentTimeMillis();
    private Handler handler;
    private HandlerThread thread;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private CaptureRequest captureRequest;
    private CaptureRequest.Builder captureRequestBuilder;
    private CameraCaptureSession captureSession;
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }
    };

    private ImageReader imageReader;
    private ImageReader.OnImageAvailableListener imageReaderListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image rawImage = reader.acquireNextImage();

            Image.Plane[] planes = rawImage.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            buffer.rewind();
            byte[] imageData = new byte[buffer.capacity()];
            buffer.get(imageData);

            Bitmap imageBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            cameraCallback.onCameraCaptured(imageBitmap, currentNumber, pictureNumber);
            currentNumber++;

            if (rawImage != null) {
                rawImage.close();
            }
            fpsCount();
        }
    };
    private Size imageSize;

    public Camera2(Context context) {
        this.context = context;
        setupCamera(500, 640);
    }

    public void resume() {
        startThread();
    }

    public void pause() {
        closeCamera();
        closeThread();
    }

    private void startThread() {
        thread = new HandlerThread("Camera2404");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    private void closeThread() {
        if (thread != null) {
            thread.quitSafely();
            try {
                thread.join();
                thread = null;
                handler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                // ONLY BY TESTING - FRONT
//                if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT)
//                    continue;
                cameraId = id;

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
                imageSize = getOptimalSize(outputSizes, width, height);
                imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(imageReaderListener, handler);
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void openCamera() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            cameraManager.openCamera(cameraId, cameraDeviceCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        Surface renderSurface = imageReader.getSurface();
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_STATE_ACTIVE_SCAN);
            captureRequestBuilder.addTarget(renderSurface);

            cameraDevice.createCaptureSession(Arrays.asList(renderSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null)
                                return;
                            try {
                                captureRequest = captureRequestBuilder.build();
                                captureSession = session;
                                currentNumber = 0;
                                for (int i = 0; i < pictureNumber; i++) {
                                    captureSession.capture(captureRequest, captureCallback, handler);
                                }
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(context, "Failed to configure camera preview.", Toast.LENGTH_LONG).show();
                        }
                    }, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader !=  null) {
            imageReader.close();
            imageReader = null;
        }
    }

    private Size getOptimalSize(Size[] mapSizes, int width, int height) {
        List<Size> bestSizes = new ArrayList<>();
        for (Size size: mapSizes) {
            if (width > height) {
                if (size.getWidth() > width && size.getHeight() > height)
                    bestSizes.add(size);
            }
            else {
                if (size.getWidth() > height && size.getHeight() > width)
                    bestSizes.add(size);
            }
        }

        if (bestSizes.size() > 0)
            return Collections.min(bestSizes, new SizeComparator());
        return mapSizes[0];
    }

    private class SizeComparator implements Comparator<Size> {

        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum(o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight());
        }
    }

    private void fpsCount() {
        FPS++;
        long currentTime = System.currentTimeMillis();
        if ((currentTime - waitTime) / 1000 > 1) {
            Log.d("smthFound", "FPS: " + FPS + " (camera2)");
            waitTime = System.currentTimeMillis();
            FPS = 0;
        }
    }
}
