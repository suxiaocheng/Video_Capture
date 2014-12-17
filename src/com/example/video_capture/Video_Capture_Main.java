package com.example.video_capture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class Video_Capture_Main extends ActionBarActivity {
	public static final String TAG = "Video_Capture_Main";

	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 200;

	private CameraPreview mPreview;

	private boolean isRecording;

	/* control button list */
	private Button captureButton;
	private Button captureVideoButton;
	private Button captureButtonI;
	private Button captureVideoButtonI;

	/* Screen wakeup lock */
	protected PowerManager.WakeLock mWakeLock;

	private CameraOperation cameraOperation;

	private Uri fileUri;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video__capture__main);
		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment(this))
					.commit();
		}
		if (CameraOperation.checkCameraHardware(this) == false) {
			Toast.makeText(this,
					"The Target hardware didn't have any camera\n",
					Toast.LENGTH_LONG).show();
			finish();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.video__capture__main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public class PlaceholderFragment extends Fragment {

		Video_Capture_Main capture_main;

		public PlaceholderFragment(Video_Capture_Main main) {
			capture_main = main;
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			Log.d(TAG, "onCreateView");
			View rootView = inflater.inflate(
					R.layout.fragment_video__capture__main, container, false);

			// Create our Preview view and set it as the content of our
			// activity.
			mPreview = new CameraPreview(capture_main);
			FrameLayout preview = (FrameLayout) rootView
					.findViewById(R.id.camera_preview);
			preview.addView(mPreview);

			captureVideoButtonI = (Button) rootView.findViewById(R.id.button2);
			captureButtonI = (Button) rootView.findViewById(R.id.button1);

			// Add a listener to the Capture button
			captureButton = (Button) rootView.findViewById(R.id.button_capture);
			captureButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					cameraOperation.takePicture();
				}
			});

			// Add a listener to the Capture button
			captureVideoButton = (Button) rootView
					.findViewById(R.id.button_capture_video);
			captureVideoButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (isRecording) {
						cameraOperation.stopVideoCapture();

						isRecording = false;

						// inform the user that recording has stopped
						// setCaptureButtonText("Capture");
						captureVideoButton.setText("Capture Video");

						/* Enable other button */
						captureVideoButtonI.setEnabled(true);
						captureButtonI.setEnabled(true);
						captureButton.setEnabled(true);

						ReleaseScreenOnLock();
					} else {
						cameraOperation.releaseCamera();
						// initialize video camera
						if (cameraOperation.prepareVideoRecorder()) {
							/* Disable other button */
							captureVideoButtonI.setEnabled(false);
							captureButtonI.setEnabled(false);
							captureButton.setEnabled(false);
							GetScreenOnLock();

							// Camera is available and unlocked, MediaRecorder
							// is prepared,
							// now you can start recording
							cameraOperation.mMediaRecorder.start();

							// inform the user that recording has started
							// setCaptureButtonText("Stop");
							captureVideoButton.setText("Capture Stop");
							isRecording = true;

						} else {
							cameraOperation.releaseCamera();
							// prepare didn't work, release the camera
							cameraOperation.releaseMediaRecorder();
							// inform user
							cameraOperation.reGetCameraWithRetry();
						}
					}
				}
			});

			return rootView;
		}
	}

	/** Called when the user touches the button */
	public void startInternelCameraCapturePicture(View view) {

		cameraOperation.releaseCamera(); // release the camera immediately on
											// pause event

		// create Intent to take a picture and return control to the calling
		// application
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

		fileUri = CameraOperation
				.getOutputMediaFileUri(CameraOperation.MEDIA_TYPE_IMAGE); // create
																			// a
																			// file
																			// to
		// save the image
		intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri); // set the image file
															// name

		// start the image capture Intent
		startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);

	}

	public void startInternelCameraCaptureVideo(View view) {

		cameraOperation.releaseCamera(); // release the camera immediately on
											// pause event

		// create new Intent
		Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);

		fileUri = CameraOperation
				.getOutputMediaFileUri(CameraOperation.MEDIA_TYPE_VIDEO); // create
																			// a
																			// file
																			// to
		// save the video
		intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri); // set the image file
															// name

		intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1); // set the video
															// image quality to
															// high

		// start the Video Capture Intent
		startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				// Image captured and saved to fileUri specified in the Intent
				Toast.makeText(
						this,
						"Image saved to:\n"
								+ ((data != null) ? data.getData() : "unknow"),
						Toast.LENGTH_LONG).show();
			} else if (resultCode == RESULT_CANCELED) {
				// User cancelled the image capture
			} else {
				// Image capture failed, advise user
			}
		}

		if (requestCode == CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE) {
			if (resultCode == RESULT_OK) {
				// Video captured and saved to fileUri specified in the Intent
				Toast.makeText(this, "Video saved to:\n" + data.getData(),
						Toast.LENGTH_LONG).show();
			} else if (resultCode == RESULT_CANCELED) {
				// User cancelled the video capture
			} else {
				// Video capture failed, advise user
			}
		}
	}

	@Override
	protected void onResume() {
		Log.d(TAG, "onResume");

		cameraOperation = new CameraOperation(mPreview);

		cameraOperation.reGetCameraWithRetry();

		super.onResume();
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();
		cameraOperation.releaseCamera(); // release the camera immediately on
											// pause event
	}

	void GetScreenOnLock() {
		if (mWakeLock == null) {
			final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK,
					"My Tag");
			this.mWakeLock.acquire();
		}
	}

	void ReleaseScreenOnLock() {
		if (mWakeLock != null) {
			this.mWakeLock.release();
			mWakeLock = null;
		}
	}
}
