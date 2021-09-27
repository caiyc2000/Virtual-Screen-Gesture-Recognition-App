package com.android.application;

import android.annotation.SuppressLint;
import android.app.Instrumentation;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import androidx.annotation.Nullable;

import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.formats.proto.LandmarkProto;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.AndroidPacketCreator;
import com.google.mediapipe.framework.Packet;
import com.google.mediapipe.framework.PacketGetter;
import com.google.mediapipe.glutil.EglManager;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloatingVideoService extends Service {
    public static boolean isStarted = false;

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private WindowManager.LayoutParams layoutParamsDrawView;
    private WindowManager.LayoutParams layoutParamsButton;
    private MediaPlayer mediaPlayer;
    private View displayView;
    private static final float MAX_VISIBLE_DEPTH_MM = 750.0f;
    private static final String TAG = "MainActivity";
    private static final String BINARY_GRAPH_NAME = "hand_tracking_mobile_gpu.binarypb";
    private static final String INPUT_VIDEO_STREAM_NAME = "input_video";
    private static final String OUTPUT_VIDEO_STREAM_NAME = "output_video";
    private static final String OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks";
    private static final String OUTPUT_HANDEDNESS_STREAM_NAME ="handedness";
    private static final String INPUT_NUM_HANDS_SIDE_PACKET_NAME = "num_hands";
    private static final int NUM_HANDS = 2;
    //    private static final CameraHelper.CameraFacing CAMERA_FACING = CameraHelper.CameraFacing.FRONT;
    private static final Camera2Helper.CameraFacing CAMERA_FACING = Camera2Helper.CameraFacing.FRONT;
    // processed in a MediaPipe graph, and flips the processed frames back when they are displayed.
    // This is needed because OpenGL represents images assuming the image origin is at the bottom-left
    // corner, whereas MediaPipe in general assumes the image origin is at top-left.
    private static final boolean FLIP_FRAMES_VERTICALLY = true;
    public ImageReader imageReader;

    static {
        // Load all native libraries needed by the app.
        System.loadLibrary("mediapipe_jni");
        System.loadLibrary("opencv_java3");
    }

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private SurfaceTexture previewFrameTexture;
    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private SurfaceView previewDisplayView;
    // Creates and manages an {@link EGLContext}.
    private EglManager eglManager;
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    private FrameProcessor processor;
    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private ExternalTextureConverter converter;
    // ApplicationInfo for retrieving metadata defined in the manifest.
    private ApplicationInfo applicationInfo;
    // Handles camera access via the {@link CameraX} Jetpack support library.
//    private CameraXPreviewHelper cameraHelper;

    short[] depth16Data;
    DrawView drawView;
    float xPointNormLoc;
    float yPointNormLoc;
    float zPointNormLoc;
    private float pointDepth;
    int nearCount;//只用Mediapipe来判断的时候
    int farCount;
    float xClickDisplayPointNormLoc;//点击时、归一化后的x
    float yClickDisplayPointNormLoc;//点击时、归一化后的y
    float f_xClickDisplayPointLoc;//乘以了尺寸
    float f_yClickDisplayPointLoc;
    long startClickTime;
    long stopClickTime;
    private Button button;


    @Override
    public void onCreate() {
        super.onCreate();
        isStarted = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams();
        layoutParamsDrawView = new WindowManager.LayoutParams();
        layoutParamsButton = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            layoutParamsDrawView.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            layoutParamsButton.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
            layoutParamsDrawView.type = WindowManager.LayoutParams.TYPE_PHONE;
            layoutParamsButton.type = WindowManager.LayoutParams.TYPE_PHONE;

        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.width = 320;
        layoutParams.height = 480;
        layoutParams.x = 300;
        layoutParams.y = 300;

        layoutParamsDrawView.format = PixelFormat.RGBA_8888;
        layoutParamsDrawView.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParamsDrawView.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParamsDrawView.width = 200;
        layoutParamsDrawView.height = 200;
        layoutParamsDrawView.x = 0;
        layoutParamsDrawView.y = 0;

        layoutParamsButton.format = PixelFormat.RGBA_8888;
        layoutParamsButton.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParamsButton.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParamsButton.width = 500;
        layoutParamsButton.height = 100;
        layoutParamsButton.x = 300;
        layoutParamsButton.y = 300;

        //获取绝对深度图，但是在这里没有用到
        imageReader = ImageReader.newInstance(640  ,480, ImageFormat.DEPTH16,50);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @SuppressLint("LongLogTag")
            @Override
            public void onImageAvailable(ImageReader reader) {
                //B2.1 接收图片：从ImageReader中读取最近的一张
                Thread t = new Thread(){
                    @Override
                    public void run() {
                        super.run();
                        Image image = null;
                        try{
                            image = reader.acquireLatestImage();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        finally {
                            if (image != null){
                                image.close();
                            }
                        }
                    }
                };
                t.start();
            }
        },null);

        try {
            applicationInfo =
                    getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find application info: " + e);
        }


        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this);
        eglManager = new EglManager(null);
        processor =
                new FrameProcessor(
                        this,
                        eglManager.getNativeContext(),
                        BINARY_GRAPH_NAME,
                        INPUT_VIDEO_STREAM_NAME,
                        OUTPUT_VIDEO_STREAM_NAME);
        processor
                .getVideoSurfaceOutput()
                .setFlipY(FLIP_FRAMES_VERTICALLY);
        AndroidPacketCreator packetCreator = processor.getPacketCreator();
        Map<String, Packet> inputSidePackets = new HashMap<>();
        inputSidePackets.put(INPUT_NUM_HANDS_SIDE_PACKET_NAME, packetCreator.createInt32(NUM_HANDS));
        processor.setInputSidePackets(inputSidePackets);
        previewDisplayView = new SurfaceView(this);
//        setupPreviewDisplayView();

        // To show verbose logging, run:
        // adb shell setprop log.tag.MainActivity VERBOSE
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            processor.addPacketCallback(
                    OUTPUT_LANDMARKS_STREAM_NAME,
                    (packet) -> {
                        Log.v(TAG, "Received multi-hand landmarks packet.");
                        List<LandmarkProto.NormalizedLandmarkList> multiHandLandmarks =
                                PacketGetter.getProtoVector(packet, LandmarkProto.NormalizedLandmarkList.parser());
                        xPointNormLoc = cursorLocation(multiHandLandmarks,0,8).get(0);
                        yPointNormLoc = cursorLocation(multiHandLandmarks,0,8).get(1);
                        zPointNormLoc = cursorLocation(multiHandLandmarks,0,8).get(2);
                        float xDisplayPointNormLoc = (xPointNormLoc - 0.5f) * 1.4f + 0.5f; //要进行缩放
                        float yDisplayPointNormLoc = yPointNormLoc * 0.95f;
                        float f_xDisplayPointLoc =  (1080*xDisplayPointNormLoc);
                        float f_yDisplayPointLoc =  (2148*yDisplayPointNormLoc);
                        layoutParamsDrawView.x = (int)f_xDisplayPointLoc;
                        layoutParamsDrawView.y = (int)f_yDisplayPointLoc;
                        Handler refresh = new Handler(Looper.getMainLooper());
                        refresh.post(new Runnable() {
                            public void run()
                            {
                                windowManager.updateViewLayout(drawView, layoutParamsDrawView);
                            }
                        });
                        this.pointDepth = zPointNormLoc;
                        String clickText = getClickString(pointDepth,xPointNormLoc,yPointNormLoc);
                        Log.v("clickText",clickText);
                    });
        }
        converter =
                new ExternalTextureConverter(
                        eglManager.getContext(), 2);
        converter.setFlipY(FLIP_FRAMES_VERTICALLY);
        converter.setConsumer(processor);
//        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera();
//        }

    }
    Camera2Helper cameraHelper = new Camera2Helper(this);

    private void startCamera() {
        cameraHelper.setOnCameraStartedListener(
                surfaceTexture -> {
                    previewFrameTexture = surfaceTexture;
                    // Make the display view visible to start showing the preview. This triggers the
                    // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
                    previewDisplayView.setVisibility(View.VISIBLE);
                });
        cameraHelper.startCamera( CAMERA_FACING, /*surfaceTexture=*/ new CustomSurfaceTexture(34),imageReader);

    }
    protected Size computeViewSize(int width, int height) {
        return new Size(width, height);
    }

    protected void onPreviewDisplaySurfaceChanged(
            SurfaceHolder holder, int format, int width, int height) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        Size viewSize = computeViewSize(width, height);
        Size displaySize = cameraHelper.computeDisplaySizeFromViewSize(viewSize);
        boolean isCameraRotated = cameraHelper.isCameraRotated();

        // Connect the converter to the camera-preview frames as its input (via
        // previewFrameTexture), and configure the output width and height as the computed
        // display size.
        converter.setSurfaceTextureAndAttachToGLContext(
                previewFrameTexture,
                displaySize.getHeight() , displaySize.getWidth());
//                isCameraRotated ? displaySize.getHeight() : displaySize.getWidth(),
//                isCameraRotated ? displaySize.getWidth() : displaySize.getHeight());
        Log.v("DisplaySize W:",String.valueOf(displaySize.getWidth()+" H:"+String.valueOf(displaySize.getHeight())));

    }

    private void setupPreviewDisplayView() {
        previewDisplayView.setVisibility(View.GONE); //控件隐藏
        if (Settings.canDrawOverlays(this)) {
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            displayView = layoutInflater.inflate(R.layout.video_display, null);
            displayView.setOnTouchListener(new FloatingOnTouchListener());
            ViewGroup viewGroup = displayView.findViewById(R.id.preview_display_layout);
            viewGroup.addView(previewDisplayView);
            drawView = new DrawView(this);
            previewDisplayView
                    .getHolder()
                    .addCallback(
                            new SurfaceHolder.Callback() {

                                @Override
                                public void surfaceCreated(SurfaceHolder holder) {
                                    processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
                                }

                                @Override
                                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                                    onPreviewDisplaySurfaceChanged(holder, format, width, height);
                                }

                                @Override
                                public void surfaceDestroyed(SurfaceHolder holder) {
                                    processor.getVideoSurfaceOutput().setSurface(null);
                                }
                            });
        }
        windowManager.addView(displayView, layoutParams);
        windowManager.addView(drawView,layoutParamsDrawView);
        button = new Button(getApplicationContext());
        button.setBackgroundColor(Color.BLUE);
        button.setText("Floating Window");
    }

    private List<String> handGestureCalculator(List<LandmarkProto.NormalizedLandmarkList> multiHandLandmarks) {
        List <String> gestureList = new ArrayList();
        if (multiHandLandmarks.isEmpty()) {
            gestureList.add("No hand deal");
            return gestureList;
        }
        int handNum = multiHandLandmarks.size();

        boolean thumbIsOpen = false;
        boolean firstFingerIsOpen = false;
        boolean secondFingerIsOpen = false;
        boolean thirdFingerIsOpen = false;
        boolean fourthFingerIsOpen = false;
        for (int count = 0; count < handNum; count++){
            LandmarkProto.NormalizedLandmarkList landmarksPerHand = multiHandLandmarks.get(count); //每一只手
            List <LandmarkProto.NormalizedLandmark> landmarkList = landmarksPerHand.getLandmarkList();
            float pseudoFixKeyPoint = landmarkList.get(2).getX();
            if (pseudoFixKeyPoint < landmarkList.get(9).getX()) {
                if (landmarkList.get(3).getX() < pseudoFixKeyPoint && landmarkList.get(4).getX() < pseudoFixKeyPoint) {
                    thumbIsOpen = true;
                }
            }
            if (pseudoFixKeyPoint > landmarkList.get(9).getX()) {
                if (landmarkList.get(3).getX() > pseudoFixKeyPoint && landmarkList.get(4).getX() > pseudoFixKeyPoint) {
                    thumbIsOpen = true;
                }
            }
            pseudoFixKeyPoint = landmarkList.get(6).getY();
            if (landmarkList.get(7).getY() < pseudoFixKeyPoint && landmarkList.get(8).getY() < landmarkList.get(7).getY()) {
                firstFingerIsOpen = true;
            }
            pseudoFixKeyPoint = landmarkList.get(10).getY();
            if (landmarkList.get(11).getY() < pseudoFixKeyPoint && landmarkList.get(12).getY() < landmarkList.get(11).getY()) {
                secondFingerIsOpen = true;
            }
            pseudoFixKeyPoint = landmarkList.get(14).getY();
            if (landmarkList.get(15).getY() < pseudoFixKeyPoint && landmarkList.get(16).getY() < landmarkList.get(15).getY()) {
                thirdFingerIsOpen = true;
            }
            pseudoFixKeyPoint = landmarkList.get(18).getY();
            if (landmarkList.get(19).getY() < pseudoFixKeyPoint && landmarkList.get(20).getY() < landmarkList.get(19).getY()) {
                fourthFingerIsOpen = true;
            }

            // Hand gesture recognition
            if (thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
                gestureList .add("HOLD"); //modified
            } else if (!thumbIsOpen && firstFingerIsOpen && !secondFingerIsOpen && !thirdFingerIsOpen && !fourthFingerIsOpen) {
                gestureList .add("ONE");
            }  else {
                String info = "thumbIsOpen " + thumbIsOpen + "firstFingerIsOpen" + firstFingerIsOpen
                        + "secondFingerIsOpen" + secondFingerIsOpen +
                        "thirdFingerIsOpen" + thirdFingerIsOpen + "fourthFingerIsOpen" + fourthFingerIsOpen;
                gestureList .add("____");
            }
        }
        return gestureList;
    }

    private List<Float> cursorLocation(List<LandmarkProto.NormalizedLandmarkList> multiHandLandmarks, int HandIndex, int LandmarkIndex)
    {
        List<Float> cursorLocationList = new ArrayList();
        if (multiHandLandmarks.isEmpty()) {
            cursorLocationList.add((float)-0.5);
            cursorLocationList.add((float)-0.5);
            return cursorLocationList;
        }
        LandmarkProto.NormalizedLandmarkList landmarksPerHand = multiHandLandmarks.get(HandIndex); //每一只手
        List <LandmarkProto.NormalizedLandmark> landmarkList = landmarksPerHand.getLandmarkList();
        float xLandmark = landmarkList.get(LandmarkIndex).getX();
        float yLandmark = landmarkList.get(LandmarkIndex).getY();
        float zLandmark = landmarkList.get(LandmarkIndex).getZ();
        cursorLocationList.add(xLandmark);
        cursorLocationList.add(yLandmark);
        cursorLocationList.add(zLandmark);
        return cursorLocationList;
    }

    private String getClickString(float pointDepth, float xPointNormLoc, float yPointNormLoc){
        String click = "";
        int button = -1;
        Log.v("depth",String.valueOf(pointDepth));
        if (pointDepth < -0.7){ //离相机更近
            if(farCount > 0 && nearCount == 0){
                //第一次进入click，将这个地方记录为使用者想要按的地方
                xClickDisplayPointNormLoc = (xPointNormLoc - 0.5f) * 1.4f + 0.5f; //要进行缩放
                yClickDisplayPointNormLoc = yPointNormLoc * 0.95f;
                f_xClickDisplayPointLoc =  (1080*xClickDisplayPointNormLoc);
                f_yClickDisplayPointLoc =  (2148*yClickDisplayPointNormLoc);
                startClickTime = System.currentTimeMillis();
            }
            nearCount ++;
            farCount = 0;
            click = "press";
        }
        else //离相机更远
        {
            click = "leave";
            if(nearCount>0 && farCount == 0) {
                //如果是第一次离开click
                PackageManager packageManager = getPackageManager();
                String packageName = "";
                stopClickTime = System.currentTimeMillis();
                long clickInterval = stopClickTime - startClickTime;
                Log.v("clickInterval",String.valueOf(clickInterval));
                if (clickInterval>50 && clickInterval < 1000){
                    Instrumentation inst=new Instrumentation();
                    exeCmd(getTap((int)f_xClickDisplayPointLoc, (int)f_yClickDisplayPointLoc));
                    if (packageName != ""){
                        Intent launchIntentForPackage = packageManager.getLaunchIntentForPackage(packageName);
                        if (launchIntentForPackage != null)
                            startActivity(launchIntentForPackage);
                        else
                            Toast.makeText(this, "手机未安装该应用", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            farCount ++;
            nearCount = 0;
        }

        return click;
    }

    private static String TAP = "input tap %d %d";
    public static String getTap(int x, int y) {
        return String.format(TAP, x,y);
    }

    public static void exeCmd(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            OutputStream outputStream = process.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(
                    outputStream);
            dataOutputStream.writeBytes(cmd + "\n");
            dataOutputStream.flush();
            dataOutputStream.close();
            outputStream.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    class DrawView extends View {

        public DrawView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Paint paint = new Paint(); //设置一个笔刷大小是3的黄色的画笔
            paint.setColor(Color.RED);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(3);
            // 创建画笔
            float xLoc = (float)15;
            float yLoc = (float)15;
            canvas.drawCircle(xLoc,yLoc,15,paint);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupPreviewDisplayView();
        return super.onStartCommand(intent, flags, startId);
    }

    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;
                    layoutParams.x = layoutParams.x + movedX;
                    layoutParams.y = layoutParams.y + movedY;
                    windowManager.updateViewLayout(view, layoutParams);
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
