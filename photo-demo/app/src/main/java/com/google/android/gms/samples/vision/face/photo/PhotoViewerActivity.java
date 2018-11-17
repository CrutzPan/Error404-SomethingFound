/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gms.samples.vision.face.photo;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;
import android.widget.Toast;


import com.google.android.gms.samples.vision.face.patch.SafeFaceDetector;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Demonstrates basic usage of the GMS vision face detector by running face landmark detection on a
 * photo and displaying the photo with associated landmarks in the UI.
 */


public class PhotoViewerActivity extends Activity {
    private static final String TAG = "PhotoViewerActivity";

    static final int REQUEST_IMAGE_CAPTURE = 1;

    TextView text;

    Set<Bitmap> bitmaps = new HashSet<>();

    private void doTrueAction(){
        text.setText("Computation was true");
    }

    private void doFalseAction(){
        text.setText("Computation was false");
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


        if (!safeDetector.isOperational()) {
            Log.w(TAG, "Face detector dependencies are not yet available.");

            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Toast.makeText(this, R.string.low_storage_error, Toast.LENGTH_LONG).show();
                Log.w(TAG, getString(R.string.low_storage_error));
            }
        }

        int i = 0;
        while(i < faces.size()){
            Face face = faces.valueAt(i);

            if (face.getIsSmilingProbability() > 0.3f) {
                safeDetector.release();
                return true;
            }
            i++;
        }
        safeDetector.release();
        return false;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap bitmap = (Bitmap) extras.get("data");


            Matrix matrix = new Matrix();
            matrix.postRotate(-90);
            bitmap =  Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmaps.add(bitmap);

            if(bitmaps.size() >= 2){
                int trues = 0;
                int falses = 0;
                for (Bitmap bitmapTemp : bitmaps) {
                    if (computeFaces(bitmapTemp))
                        trues++;
                    else
                        falses++;
                }
                if (trues > falses)
                    doTrueAction();
                else
                    doFalseAction();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);
        text = (TextView) findViewById(R.id.textView3);

        for (int i = 0; i <= 1; i++)
            dispatchTakePictureIntent();


        //InputStream stream = getResources().openRawResource(R.raw.face);
        //Bitmap bitmap = BitmapFactory.decodeStream(stream);


    }
}
