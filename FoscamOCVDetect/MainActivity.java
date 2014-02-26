package com.example.foscamdetect;

/*******************************************************************************************
 * Modifed by Han Dong
 * 12/15/2013
 * 
 * This is a sample application draws contours around objects that are moving in a 
 * camera scene. The data is from a Foscam IP Camera. OpenCV libraries are used to
 * process the scene and generate a contour around it. 
 * 
 *******************************************************************************************/
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.BackgroundSubtractorMOG;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.Menu;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MainActivity extends Activity implements OnSeekBarChangeListener  
{
	static ImageView image_view;
	Bitmap bitmap, myBitmap32, resultBitmap;
	
	private Mat mGray;
	private Mat mRgb;
	private Mat mFGMask;
	private List<MatOfPoint> contours;
	private BackgroundSubtractorMOG sub;
	
	private double lRate = 0.5;
	
	private SeekBar sb;
	
	static Bitmap globalImage;
	
	final String TAG = "Hello World";
	boolean start = false;
	
	//this is needed for opencv libraries to work
	private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
		   switch (status) {
		       case LoaderCallbackInterface.SUCCESS:
		       {
		      Log.i(TAG, "OpenCV loaded successfully");
		       } break;
		       default:
		       {
		      super.onManagerConnected(status);
		       } break;
		   }
		    }
		};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//this function sets up a background task that gets called every 0.5 seconds to retrieve the
		//latest frame from the camera
		callAsynchronousTask();

		//sets up the image view in the layout
		image_view = (ImageView) findViewById(R.id.image);
		

		//sets up seek bar to adjust learning rate
		sb = (SeekBar)findViewById(R.id.seekBar1);
		sb.setProgress(5);
		sb.setMax(10);
		sb.setOnSeekBarChangeListener(this);
	}

	//this function gets called everytime a new frame is retrieved from the performBackgroundTask.execute function
	public void updateImage()
	{
		//start up counter that initializes instances needed in the code
		// NOTE: I couldn't initialize the OpenCV Mat structures anywhere else.
		//       It was giving me linking errors, it has a strange connection
		//       to the background task that retrieves the image frames. 
		//       This is the only way I found that works.
		if(!start)
		{
			mRgb = new Mat();
			mFGMask = new Mat();
			mGray = new Mat();
			contours = new ArrayList<MatOfPoint>();
			sub = new BackgroundSubtractorMOG();
			
			start = true;
		}
		
		contours.clear();
		
		//this converts the bitmap retrieved from the frame into a Mat format for OpenCV
		mGray = new Mat ( globalImage.getHeight(), globalImage.getWidth(), CvType.CV_8U, new Scalar(4));
		myBitmap32 = globalImage.copy(Bitmap.Config.ARGB_8888, true);
		Utils.bitmapToMat(myBitmap32, mGray);

		//this function converts the gray frame into the correct RGB format for the BackgroundSubtractorMOG apply function
		//sub.apply is quite sensitive, if I don't convert it from RGB2GRAY and then GRAY2RGB it causes some errors
		Imgproc.cvtColor(mGray, mGray, Imgproc.COLOR_RGB2GRAY);
		Imgproc.cvtColor(mGray, mRgb, Imgproc.COLOR_GRAY2RGB); 

		//apply detects objects moving and produces a foreground mask
		sub.apply(mRgb, mFGMask, lRate); 

		//drawing contours around the objects by first called findContours and then calling drawContours
		//RETR_EXTERNAL retrieves only external contours
		//CHAIN_APPROX_NONE detects all pixels for each contour
		Imgproc.findContours(mFGMask, contours, new Mat(), Imgproc.RETR_EXTERNAL , Imgproc.CHAIN_APPROX_NONE);

		//draws all the contours in red with thickness of 2
		Imgproc.drawContours(mRgb, contours, -1, new Scalar(255, 0, 0), 2);

		//Then convert the processed Mat to back to Bitmap
		resultBitmap = Bitmap.createBitmap(mRgb.cols(),  mRgb.rows(), Bitmap.Config.ARGB_8888);
		Utils.matToBitmap(mRgb, resultBitmap);
		
		//set the bitmap with the contours drawn on it
		image_view.setImageBitmap(resultBitmap);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	//this sets up a timer for an asynchronous task in the background
	public void callAsynchronousTask() 
	{
	    final Handler handler = new Handler();
	    Timer timer = new Timer();
	    
	    //this is the task itself
	    TimerTask doAsynchronousTask = new TimerTask() {       
	        @Override
	        public void run() {
	            handler.post(new Runnable() {
	                public void run() {       
	                    try {
	                        // PerformBackgroundTask this class is the class that extends AsynchTask 
	                		DownloadImageTask performBackgroundTask = new DownloadImageTask();
	                		
	                		//this function retrieves an image from the URL
	                		//the URL is using the cgi scripts provided by the Foscam camera and returns the current frame in the camera
	                		//as a jpeg
	                        performBackgroundTask.execute("http://example.myfoscam.org/cgi-bin/CGIProxy.fcgi?usr=usrpassword&pwd=password&cmd=snapPicture2");
	                    } catch (Exception e) {
	                        // TODO Auto-generated catch block
	                    }
	                }
	            });
	        }
	    };
	    
	    //this timer schedules it to execute every 500 ms
	    timer.schedule(doAsynchronousTask, 0, 500);
	}
	
	// This function is necessary to make OpenCV libraries work
	public void onResume()
	{
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mOpenCVCallBack);
	}
	
	//private class handling retrieving image frame from camera
	private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> 
	{
		public DownloadImageTask()
		{
		}

		//this gets called every 500 ms
		protected Bitmap doInBackground(String... urls)
		{
			String urldisplay = urls[0];
			Bitmap mIcon11 = null;

			try 
			{
				//opens a stream to the URL and decodes it into a bitmap
				InputStream in = new java.net.URL(urldisplay).openStream();
				mIcon11 = BitmapFactory.decodeStream(in);
		        
			} catch (Exception e) {
				Log.e("Error", e.getMessage());
				e.printStackTrace();
			}
			
			return mIcon11;
		}

		// This function gets called after the bitmap is generated
		protected void onPostExecute(Bitmap result) 
		{
			//it puts the bitmap into a global data structure
			globalImage = result;
			
			//calls updateImage which uses OpenCV to draw the contours
			updateImage();
		}
	}

	// Updates the lRate from the seek bar
	public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
		lRate = (double) arg1 / 10.0;
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar arg0) {
		// TODO Auto-generated method stub
		
	}   
}
