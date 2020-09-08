package com.example.textreconization;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;


public class TextScannerActivity extends CameraActivity {

    private enum ScreenOrientation {
        PORTRAIT(90),
        REVERSE_PORTRAIT(-90),
        LANDSCAPE(0),
        REVERSE_LANDSCAPE(180);
        int rotation;

        ScreenOrientation(int rotationRequired) {
            this.rotation = rotationRequired;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        image = false;
    }

    public static final String ARG_SOURCE_SCREEN = "ARG_SOURCE_SCREEN";
    public ScreenOrientation currentScreenOrientation;
    public MyOrientationEventListener mOrientationListener;

    public ImageView takePicture;
    private TextureView cameraTextureView;
    private DisplayMetrics displayMetrics;

    private ImageCapture imageCapture;
    private Integer itemPosition;
    private String screenName = "";

    @Override
    void hasPermissions() {
        startCamera();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        initUI();
        super.onCreate(savedInstanceState);
        if (getIntent().getExtras() != null) {
            screenName = getIntent().getStringExtra(ARG_SOURCE_SCREEN);
        }
    }

    private void initUI() {
        if (getIntent() != null) {
            itemPosition = getIntent().getIntExtra("position", -1);
        }
        setContentView(R.layout.layout_text_scanner);
    }

    private void startCamera() {
        cameraTextureView = findViewById(R.id.cameraTextureView);
        takePicture = findViewById(R.id.takePicture);
        cameraTextureView.post(new CameraStartRunnable());
    }

    private void handleStartOperations() {

        int width = cameraTextureView.getWidth() >= 1080 ? 1080 : cameraTextureView.getWidth();
        int height = cameraTextureView.getHeight() >= 1920 ? 1920 : cameraTextureView.getHeight();

        Rational aspectRatio = new Rational(width, height);
        Size screen = new Size(width, height);

        PreviewConfig previewConfig = new PreviewConfig.Builder()
                .build();
        Preview preview = new Preview(previewConfig);

        preview.setOnPreviewOutputUpdateListener(
                new Preview.OnPreviewOutputUpdateListener() {
                    //to update the surface texture we  have to destroy it first then re-add it
                    @Override
                    public void onUpdated(Preview.PreviewOutput output) {
                        handleOnPreviewUpdate(output);
                    }
                });

        ImageCaptureConfig imageCaptureConfig = new ImageCaptureConfig.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(cameraTextureView.getDisplay().getRotation())
                .setTargetResolution(screen)
                .setFlashMode(FlashMode.AUTO)
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .build();

        imageCapture = new ImageCapture(imageCaptureConfig);

        // might get used in Future
        ImageAnalysisConfig imageAnalysisConfig = new ImageAnalysisConfig.Builder().build();
        ImageAnalysis imageAnalysis = new ImageAnalysis(imageAnalysisConfig);
        imageAnalysis.setAnalyzer(new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(ImageProxy image, int rotationDegrees) {
//                processImage(image, rotationDegrees);
            }
        });
        CameraX.unbindAll();
        CameraX.bindToLifecycle(this, preview, imageCapture, imageAnalysis);
        takePicture.setOnClickListener(new OnCameraImageClickListener());
    }

    private void handleOnPreviewUpdate(Preview.PreviewOutput output) {
        ViewGroup parent = (ViewGroup) cameraTextureView.getParent();
        parent.removeView(cameraTextureView);
        parent.addView(cameraTextureView, 0);
        cameraTextureView.setSurfaceTexture(output.getSurfaceTexture());
        updateTransform();
    }

    private void handleOnTakePictureEvent() {
        imageCapture.takePicture(new ImageCapture.OnImageCapturedListener() {
            @Override
            public void onCaptureSuccess(ImageProxy image, int rotationDegrees) {
                handleOnCameraCaptureSuccess(image);
                super.onCaptureSuccess(image, rotationDegrees);
            }

            @Override
            public void onError(ImageCapture.UseCaseError useCaseError, String message, @Nullable Throwable cause) {
                super.onError(useCaseError, message, cause);
            }
        });
    }

    private Bitmap convertImageProxyToBitmap(ImageProxy image) {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byteBuffer.rewind();
        byte[] bytes = new byte[byteBuffer.capacity()];
        byteBuffer.get(bytes);
        byte[] clonedBytes = bytes.clone();
        return BitmapFactory.decodeByteArray(clonedBytes, 0, clonedBytes.length);
    }

    public static Bitmap bitMap = null;

    public void handleOnCameraCaptureSuccess(ImageProxy imageProxy) {
        processImage(imageProxy, currentScreenOrientation.rotation);
    }

    Boolean image = false;

    private void processImage(ImageProxy imageProxy, int rotation) {
        image = true;
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, rotation);

            TextRecognizer recognizer = TextRecognition.getClient();

            Task<Text> result =
                    recognizer.process(image)
                            .addOnSuccessListener(new OnSuccessListener<Text>() {
                                @Override
                                public void onSuccess(Text visionText) {
                                    if (visionText.getText() != null && !visionText.getText().isEmpty()) {
                                        if (visionText.getText() != null && !visionText.getText().isEmpty()) {
                                            ArrayList<String> list = new ArrayList();
                                            for (int i = 0; i < visionText.getTextBlocks().size(); i++) {
                                                if (visionText.getTextBlocks().get(i).getLines().size() > 1) {
                                                    for (int j = 0; j < visionText.getTextBlocks().get(i).getLines().size(); j++) {
                                                        String a;
                                                        a = (visionText.getTextBlocks().get(i).getLines().get(j).getCornerPoints()[0].y + "" +
                                                                visionText.getTextBlocks().get(i).getLines().get(j).getCornerPoints()[0].y + "");
                                                        list.add(a + "__" + visionText.getTextBlocks().get(i).getLines().get(j).getText());
                                                    }
                                                    continue;
                                                } else {
                                                    String a;
                                                    a = (visionText.getTextBlocks().get(i).getCornerPoints()[0].y + "" +
                                                            visionText.getTextBlocks().get(i).getCornerPoints()[0].y + "");

                                                    list.add(a + "__" + visionText.getTextBlocks().get(i).getText());
                                                    Collections.sort(list);
                                                }
                                            }
                                            Collections.reverse(list);
                                            ArrayList<String> newList = new ArrayList();

                                            for (int i = 0; i < list.size(); i++) {
                                                newList.add(list.get(i).split("__")[1]);
                                            }

                                            String finalText = "";
                                            for (int i = 0; i < newList.size(); i = i + 2) {
                                                finalText = finalText + newList.get(i) + "->" + ((i + 1) < newList.size() ? newList.get(i + 1) : "");
                                                finalText += "\n\n";
                                            }
                                            DialogBix dialoh = new DialogBix(finalText);
                                            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                                            Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
                                            if (prev != null) {
                                                ft.remove(prev);
                                            }
                                            ft.addToBackStack(null);
                                            dialoh.show(ft, "dialog");
                                        }
                                    } else {
                                        Toast.makeText(TextScannerActivity.this, "Nothing recognized", Toast.LENGTH_LONG).show();
                                    }
                                }
                            })
                            .addOnFailureListener(
                                    new OnFailureListener() {
                                        @Override
                                        public void onFailure(@NonNull Exception e) {
                                            // Task failed with an exception
                                            // ...
                                        }
                                    });
        }
    }

    private void startViewCropImageActivity(Bitmap centerCropImage) {
        String finalPath = saveBitmapToInternalStorage(centerCropImage);
        if (!TextUtils.isEmpty(finalPath)) {
            Intent intent = new Intent(this, CropPreviewActivity.class);
            intent.putExtra("position", itemPosition);
            intent.putExtra("PHOTO_PATH", finalPath);
            intent.putExtra(ARG_SOURCE_SCREEN, screenName);
            intent.putExtra("IS_RETAKE_ALLOWED", true);
            startActivity(intent);
        }
    }

//        Image mediaImage = imageProxy.getImage();
//        if (mediaImage != null) {
//            InputImage image =
//                    InputImage.fromMediaImage(mediaImage, 90);
//
//            TextRecognizer recognizer = TextRecognition.getClient();
//
//            Task<Text> result =
//                    recognizer.process(image)
//                            .addOnSuccessListener(new OnSuccessListener<Text>() {
//                                @Override
//                                public void onSuccess(Text visionText) {
//                                    Toast.makeText(TextScannerActivity.this, visionText.getText(), Toast.LENGTH_LONG).show();
//                                }
//                            })
//                            .addOnFailureListener(
//                                    new OnFailureListener() {
//                                        @Override
//                                        public void onFailure(@NonNull Exception e) {
//                                            // Task failed with an exception
//                                            // ...
//                                        }
//                                    });
//
//
//        }

    private String saveBitmapToInternalStorage(Bitmap bitmap) {
        ContextWrapper wrapper = new ContextWrapper(getApplicationContext());
        File file = wrapper.getDir("InspectionImages", MODE_PRIVATE);
        // Create a file to save the image
        file = new File(file, "JPEG_" + System.currentTimeMillis() + ".jpg");
        try {
            OutputStream stream = null;
            stream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.flush();
            stream.close();
        } catch (IOException e) // Catch the exception
        {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }

    class OnCameraImageClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.takePicture:
                    handleOnTakePictureEvent();
                    break;
            }
        }
    }

    // Required when device is being rotated.
    private void updateTransform() {
        Matrix mx = new Matrix();

        int width = cameraTextureView.getWidth() >= 1080 ? 1080 : cameraTextureView.getWidth();
        int height = cameraTextureView.getHeight() >= 1920 ? 1920 : cameraTextureView.getHeight();

        float w = width;
        float h = height;

        float cX = w / 2f;
        float cY = h / 2f;

        int rotationDgr;
        int rotation = (int) cameraTextureView.getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
                rotationDgr = 0;
                break;
            case Surface.ROTATION_90:
                rotationDgr = 90;
                break;
            case Surface.ROTATION_180:
                rotationDgr = 180;
                break;
            case Surface.ROTATION_270:
                rotationDgr = 270;
                break;
            default:
                return;
        }

        mx.postRotate((float) rotationDgr, cX, cY);
        cameraTextureView.setTransform(mx);
    }

    Bitmap imageToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inSampleSize = 6;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    public Bitmap rotateBitmap(Bitmap source) {
        float angle = currentScreenOrientation.rotation;
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    class CameraStartRunnable implements Runnable {
        @Override
        public void run() {
            setUpOrientationChangeListeners();
            handleStartOperations();
            updateScannerView();
        }
    }

    class MyOrientationEventListener extends OrientationEventListener {
        public MyOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (orientation <= 45) {
                currentScreenOrientation = ScreenOrientation.PORTRAIT;
            } else if (orientation <= 135) {
                currentScreenOrientation = ScreenOrientation.REVERSE_LANDSCAPE;
            } else if (orientation <= 225) {
                // For Reverse Portrait we set portrait
                currentScreenOrientation = ScreenOrientation.PORTRAIT;
            } else if (orientation <= 315) {
                currentScreenOrientation = ScreenOrientation.LANDSCAPE;
            } else {
                currentScreenOrientation = ScreenOrientation.PORTRAIT;
            }

            cameraTextureView.post(new Runnable() {
                @Override
                public void run() {
                    updateScannerView();
                }
            });
        }
    }

    private void updateScannerView() {
        Display display = cameraTextureView.getDisplay();
        if (display != null) {
            //  Fetching real Display Metrics for CameraView  i.e Texture View
            displayMetrics = new DisplayMetrics();
            Display textureDisplay = cameraTextureView.getDisplay();
            textureDisplay.getRealMetrics(displayMetrics);
            if (currentScreenOrientation == ScreenOrientation.PORTRAIT) {
//                textViewWarning.setVisibility(View.VISIBLE);
            } else {
//                textViewWarning.setVisibility(View.GONE);
            }
        }
    }

    private void setUpOrientationChangeListeners() {
        mOrientationListener = new MyOrientationEventListener(this);
        if (mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
    }

    class TextBloccks {
        int sortBasisY = 0;
        int initialYPoint = 0;
        ArrayList<Text.TextBlock> lines = new ArrayList();
        String whloleText = "";
    }
}

