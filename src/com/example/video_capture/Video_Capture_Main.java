package com.example.video_capture;

import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Video_Capture_Main extends ActionBarActivity {
	public static final String TAG = "Video_Capture_Main";

	private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
	private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 200;

	/* camera video capture process state */
	private static final int PROCESS_FREE = 0x00;
	private static final int PROCESS_DELAY = 0x01;
	private static final int PROCESS_CAPTURE = 0x02;

	private CameraPreview mPreview;

	private int isRecording;

	/* control button list */
	private Button captureButton;
	private Button captureVideoButton;
	private Button captureButtonI;
	private Button captureVideoButtonI;

	/* control display delay capture time */
	private TextView captureDelayTimeTW;

	/* control display how long has been capture */
	private TextView captureTimeTW;

	/* Camera lock use for sync */
	private ReentrantLock captureCameraLock;

	/* Screen wakeup lock */
	protected PowerManager.WakeLock mWakeLock;

	public CameraSetting cameraSetting;

	private CameraOperation cameraOperation;

	private Uri fileUri;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// remove title
		// requestWindowFeature(Window.FEATURE_NO_TITLE);
		// getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
		// WindowManager.LayoutParams.FLAG_FULLSCREEN);

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

		cameraSetting = new CameraSetting(this.getBaseContext());

		isRecording = PROCESS_FREE;
		captureCameraLock = new ReentrantLock();
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
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public class PlaceholderFragment extends Fragment {

		Video_Capture_Main capture_main;

		public PlaceholderFragment() {
			Log.d(TAG, "capture_main:" + capture_main == null ? "NULL" : "OK");
		}

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

			captureDelayTimeTW = (TextView) rootView
					.findViewById(R.id.capture_info_delaytime);

			captureTimeTW = (TextView) rootView.findViewById(R.id.capture_info);

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
					if (captureCameraLock.tryLock() == false) {
						return;
					}
					if (isRecording == PROCESS_CAPTURE) {
						isRecording = PROCESS_FREE;

						cameraOperation.stopVideoCapture();

					} else if (isRecording == PROCESS_FREE) {
						if (cameraSetting.GetScreenLock() == false) {
							GetScreenOnLock();
						}

						int delay_time = cameraSetting.GetDelayCaptureTime();
						if (delay_time > 0) {
							isRecording = PROCESS_DELAY;
							CameraDelayOperation asyncTask = new CameraDelayOperation();
							asyncTask.execute(delay_time);
						} else {
							isRecording = PROCESS_CAPTURE;
							if (startVideoRecording() == false) {
								isRecording = PROCESS_FREE;
							}
						}
					} else if (isRecording == PROCESS_DELAY) {
						isRecording = PROCESS_FREE;
					}

					captureCameraLock.unlock();
					capture_main.setButtonStatus(isRecording);
				}

			});

			return rootView;
		}
	}

	private void setButtonStatus(int status) {
		/* Reset the UI to the right state */
		if (status == PROCESS_FREE) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(true);
			captureButtonI.setEnabled(true);
			captureButton.setEnabled(true);

			captureVideoButton.setText("Capture Video");

			/* Release the screen lock */
			if (cameraSetting.GetScreenLock() == false) {
				ReleaseScreenOnLock();
			}

		} else if (status == PROCESS_CAPTURE) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(false);
			captureButtonI.setEnabled(false);
			captureButton.setEnabled(false);

			captureVideoButton.setText("Capture Stop");
		} else if (status == PROCESS_DELAY) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(false);
			captureButtonI.setEnabled(false);
			captureButton.setEnabled(false);

			captureVideoButton.setText("Capture Cancel");
		} else {
			Log.d(TAG, "setButtonStatus: Unknow status");
		}
	}

	private class CameraDelayOperation extends
			AsyncTask<Integer, Integer, String> {

		private Time startupTime;
		private Time currentTime;
		private int delayTime;

		public CameraDelayOperation() {
			startupTime = new Time();
			currentTime = new Time();

			startupTime.setToNow();
		}

		@Override
		protected String doInBackground(Integer... params) {
			int time_interval;

			delayTime = params[0].intValue();

			do {
				currentTime.setToNow();
				time_interval = delayTime
						- (int) ((currentTime.toMillis(true) - startupTime
								.toMillis(true)) / 1000);

				publishProgress(time_interval);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch
					// block
					e.printStackTrace();
				}
				if (isRecording == PROCESS_FREE) {
					break;
				}
			} while (time_interval > 0);
			return "Executed";
		}

		@Override
		protected void onPostExecute(String result) {
			boolean status = false;

			captureCameraLock.lock();
			captureDelayTimeTW.setVisibility(TextView.INVISIBLE);
			if (isRecording != PROCESS_FREE) {
				status = startVideoRecording();
			}

			if (status == false) {
				isRecording = PROCESS_FREE;
			}

			setButtonStatus(isRecording);

			captureCameraLock.unlock();
		}

		@Override
		protected void onPreExecute() {
			captureCameraLock.lock();

			captureDelayTimeTW.setText(null);
			captureDelayTimeTW.setVisibility(TextView.VISIBLE);
			captureDelayTimeTW.setTextColor(Color.rgb(255, 0, 0));

			captureCameraLock.unlock();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			String str = new String();
			Time tmp = new Time();
			tmp.hour = values[0].intValue() / 3600;
			tmp.minute = (values[0].intValue() - tmp.hour * 3600) / 60;
			tmp.second = values[0].intValue() - tmp.hour * 3600 - tmp.minute
					* 60;

			str = String.format("Capture after:%4d:%2d:%2d", tmp.hour,
					tmp.minute, tmp.second);
			captureDelayTimeTW.setText(str);
		}
	}

	private class CameraCaptureOperation extends
			AsyncTask<Integer, Integer, String> {

		private Time startupTime;
		private Time currentTime;
		private int captureTime;

		public CameraCaptureOperation(int cp) {
			startupTime = new Time();
			currentTime = new Time();
			startupTime.setToNow();

			captureTime = cp;
		}

		@Override
		protected String doInBackground(Integer... params) {
			int time_interval;

			do {
				currentTime.setToNow();
				time_interval = (int) ((currentTime.toMillis(true) - startupTime
						.toMillis(true)) / 1000);
				publishProgress(time_interval);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch
					// block
					e.printStackTrace();
				}
				if (isRecording == PROCESS_FREE) {
					break;
				}
			} while ((captureTime == 0) || (captureTime > time_interval));

			return "Executed";
		}

		@Override
		protected void onPostExecute(String result) {
			captureTimeTW.setVisibility(TextView.INVISIBLE);
			captureCameraLock.lock();
			if (isRecording != PROCESS_FREE) {
				isRecording = PROCESS_FREE;

				cameraOperation.stopVideoCapture();

				setButtonStatus(isRecording);
			}
			captureCameraLock.unlock();
		}

		@Override
		protected void onPreExecute() {
			captureTimeTW.setText(null);
			captureTimeTW.setVisibility(TextView.VISIBLE);
			captureTimeTW.setTextColor(Color.rgb(255, 0, 0));
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			String str = new String();
			Time tmp = new Time();
			tmp.hour = values[0].intValue() / 3600;
			tmp.minute = (values[0].intValue() - tmp.hour * 3600) / 60;
			tmp.second = values[0].intValue() - tmp.hour * 3600 - tmp.minute
					* 60;

			str = String.format("Capture Time:%4d:%2d:%2d", tmp.hour,
					tmp.minute, tmp.second);
			captureTimeTW.setText(str);
		}
	}

	public boolean startVideoRecording() {
		boolean status = false;

		cameraOperation.releaseCamera();
		// initialize video camera

		status = cameraOperation.prepareVideoRecorder();

		if (status == true) {
			// Camera is available and unlocked, MediaRecorder
			// is prepared,
			// now you can start recording
			cameraOperation.mMediaRecorder.start();

			new CameraCaptureOperation(cameraSetting.GetStartCaptureTime())
					.execute(0);

		} else {
			cameraOperation.releaseCamera();
			// prepare didn't work, release the camera
			cameraOperation.releaseMediaRecorder();
			// inform user
			cameraOperation.reGetCameraWithRetry();
		}

		return status;
	}

	/** Called when the user touches the button */
	public void startInternelCameraCapturePicture(View view) {

		cameraOperation.releaseCamera(); // release the camera immediately on
											// pause event

		// create Intent to take a picture and return control to the calling
		// application
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

		// create a file to save the image
		fileUri = CameraOperation
				.getOutputMediaFileUri(CameraOperation.MEDIA_TYPE_IMAGE);

		// set the image file name
		intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

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

		if (cameraSetting.GetScreenLock() == true) {
			GetScreenOnLock();
		}

		super.onResume();
	}

	@Override
	protected void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();

		ReleaseScreenOnLock();
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
