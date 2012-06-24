package edu.cmu.nishith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.objdetect.CascadeClassifier;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.util.Log;
import android.view.SurfaceHolder;

class FdView extends SampleCvViewBase {
    private static final String TAG = "FdView";
    private Mat                 mRgba;
    private Mat                 mGray;

    private int                 filter = 5;
    private int 				tick   = 0;
    Timer revertTimer = new Timer();
    int orignalRingMode                = 0;
    
    Context local_context;

    class revertForwarding extends TimerTask {
    	@Override
    	public void run() {
    		// Timer has run out, cancel call forwarding
//    		String callForwardString = "##21#";    
//    		Intent intentCallForward = new Intent(Intent.ACTION_DIAL); // ACTION_CALL                               
//    		Uri uri2 = Uri.fromParts("tel", callForwardString, "#"); 
//    		intentCallForward.setData(uri2);                                
//    		local_context.startActivity(intentCallForward);
    	}	
    }
    
    private CascadeClassifier   mCascade;

    public FdView(Context context) {
        super(context);

        local_context = context;
        revertTimer = new Timer();
        
        try {
            InputStream is = context.getResources().openRawResource(R.raw.lbpcascade_polycom);
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mCascade = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (mCascade.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mCascade = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

            cascadeFile.delete();
            cascadeDir.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder _holder, int format, int width, int height) {
        super.surfaceChanged(_holder, format, width, height);

        synchronized (this) {
            // initialize Mats before usage
            mGray = new Mat();
            mRgba = new Mat();
        }
    }

    @Override
    protected Bitmap processFrame(VideoCapture capture) {
        capture.retrieve(mRgba, Highgui.CV_CAP_ANDROID_COLOR_FRAME_RGBA);
        capture.retrieve(mGray, Highgui.CV_CAP_ANDROID_GREY_FRAME);

        if (mCascade != null) {
            int height = mGray.rows();
            int faceSize = Math.round(height * FdActivity.minFaceSize);
            List<Rect> faces = new LinkedList<Rect>();
            mCascade.detectMultiScale(mGray, faces, 1.1, 2, 2 // TODO: objdetect.CV_HAAR_SCALE_IMAGE
                    , new Size(faceSize, faceSize));

//            if(faces.isEmpty() && tick > 0) {
//            	tick = 0;
//            }
            	
            if (!faces.isEmpty()) {
            	tick++;
            	if (tick > filter) {
            		tick = 0;
            		AudioManager ringer = (AudioManager) local_context.getSystemService(Context.AUDIO_SERVICE);
            		orignalRingMode = ringer.getRingerMode();
            		ringer.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
            		
            		// Detected camera, setup call forwarding
            		String callForwardString = "**21*6503365866#";    
            		Intent intentCallForward = new Intent(Intent.ACTION_DIAL); // ACTION_CALL                               
            		Uri uri2 = Uri.fromParts("tel", callForwardString, "#"); 
            		intentCallForward.setData(uri2);                                
            		local_context.startActivity(intentCallForward); 
            		
            		revertTimer.cancel();
            		revertTimer = new Timer();
            		TimerTask revert = new revertForwarding();
            		revertTimer.schedule(revert, 5000);
            	}
            }
            
            for (Rect r : faces)
                Core.rectangle(mRgba, r.tl(), r.br(), new Scalar(0, 255, 0, 255), 3);
        }

        Bitmap bmp = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);

        if (Utils.matToBitmap(mRgba, bmp))
            return bmp;

        bmp.recycle();
        return null;
    }

    @Override
    public void run() {
        super.run();

        synchronized (this) {
            // Explicitly deallocate Mats
            if (mRgba != null)
                mRgba.release();
            if (mGray != null)
                mGray.release();

            mRgba = null;
            mGray = null;
        }
    }
}

