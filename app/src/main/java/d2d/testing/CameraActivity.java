package d2d.testing;

import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import d2d.testing.helpers.Logger;

import static d2d.testing.net.helpers.IOUtils.getOutputMediaFile;

public class CameraActivity extends AppCompatActivity {
    private static final int MODE_PICTURE = 0;
    private static final int MODE_VIDEO = 1;

    private static final String[] FLASH_OPTIONS = {
            Camera.Parameters.FLASH_MODE_AUTO,
            Camera.Parameters.FLASH_MODE_OFF,
            Camera.Parameters.FLASH_MODE_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mMediaRecorder;

    private int mCurrentFlash;
    private int mCurrentCamera;
    private boolean mVideoMode;
    private boolean mRecording;

    private FloatingActionButton btnSwitchCamera;
    private Toolbar cameraToolbar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        mCurrentFlash = 0;
        mCurrentCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        mVideoMode = false;
        mRecording = false;

        setContentView(R.layout.camera);

        // Create an instance of Camera
        mCamera = getCameraInstance();

        // get Camera parameters
        Camera.Parameters params = mCamera.getParameters();

        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            // Autofocus mode is supported
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        List<String> flashModes = params.getSupportedFlashModes();
        if (flashModes.contains(Camera.Parameters.FLASH_MODE_AUTO)) {
            // Autofocus mode is supported
            params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
        }

        // set Camera parameters
        mCamera.setParameters(params);

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = findViewById(R.id.camera_preview);
        preview.addView(mPreview);
        initialWork();
    }

    public void initialWork() {

        btnSwitchCamera = findViewById(R.id.button_switch_camera);
        cameraToolbar = findViewById(R.id.camera_toolbar);

        setSupportActionBar(cameraToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
        releaseCamera();              // release the camera immediately on pause event
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
            mCamera.lock();           // lock camera for later use
        }
    }

    private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera for other applications
            mCamera = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.camera_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.switch_flash:

                if (mCamera != null) {
                    mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.length;
                    item.setTitle(FLASH_OPTIONS[mCurrentFlash]);
                    item.setIcon(FLASH_ICONS[mCurrentFlash]);

                    Camera.Parameters params = mCamera.getParameters();

                    List<String> flashModes = params.getSupportedFlashModes();
                    if (flashModes.contains(FLASH_OPTIONS[mCurrentFlash])) {
                        params.setFlashMode(FLASH_OPTIONS[mCurrentFlash]);
                    }
                    // set Camera parameters
                    mCamera.setParameters(params);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
            btnSwitchCamera.animate().rotationBy(0).setDuration(100).start();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            //Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
            btnSwitchCamera.animate().rotationBy(90).setDuration(100).start();
        }
    }

    public void onSwitchCamera (View view){
        //if (inPreview) {
        mCamera.stopPreview();
        //}
        //NB: if you don't release the current camera before switching, you app will crash
        releaseCamera();

        //swap the id of the camera to be used
        if (mCurrentCamera == Camera.CameraInfo.CAMERA_FACING_BACK) {
            mCurrentCamera = Camera.CameraInfo.CAMERA_FACING_FRONT;
        } else {
            mCurrentCamera = Camera.CameraInfo.CAMERA_FACING_BACK;
        }
        mCamera = Camera.open(mCurrentCamera);

        //setCameraDisplayOrientation(CameraActivity.this, currentCameraId, camera);
        try {
            mCamera.setPreviewDisplay(mPreview.getHolder());
            mCamera.setDisplayOrientation(90);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCamera.startPreview();
    }

    public void onCapture(View view){
        if(!mVideoMode){
            mCamera.takePicture(null, null, mPicture);
        }else {
            if(mRecording){
                //todo grabar y streaming
                mRecording = false;
            }else{
                mRecording = true;
            }
        }
    }

    public void onSwitchMode(View view){
        if(mRecording)
        {
            //todo estamos grabando!
            mRecording = false;
        }

        mVideoMode = !mVideoMode;
    }

    private boolean prepareVideoRecorder(){

        mCamera = getCameraInstance();
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        //todo output a socket?
        mMediaRecorder.setOutputFile(getOutputMediaFile("VIDEO_RECORD").toString());

        // Step 5: Set the preview output
        mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

        // Step 6: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Logger.d("IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Logger.d("IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile("imagen_camara.jpg");
            if (pictureFile == null){
                Logger.d("Error creating media file, check storage permissions");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Logger.d("File not found: " + e.getMessage());
            } catch (IOException e) {
                Logger.d("Error accessing file: " + e.getMessage());
            }
        }
    };
}
