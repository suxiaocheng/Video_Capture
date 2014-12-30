package com.example.video_capture;

import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * A placeholder fragment containing a simple view.
 */
public class PlaceholderFragment extends Fragment {

	private final static String TAG = "PlaceholderFragment";

	private CameraPreview mPreview;

	/* control button list */
	private Button captureButton;
	private Button captureVideoButton;
	private Button captureButtonI;
	private Button captureVideoButtonI;

	/* control button zoom out and zoom in */
	private Button zoomOutButton;
	private Button zoomInButton;

	/* control display delay capture time */
	private TextView captureDelayTimeTW;

	/* control display how long has been capture */
	private TextView captureTimeTW;

	public Video_Capture_Main capture_main;

	/* camera video capture process state */
	private static final int PROCESS_FREE = 0x00;
	private static final int PROCESS_DELAY = 0x01;
	private static final int PROCESS_CAPTURE = 0x02;

	private int isRecording;

	/* Camera lock use for sync */
	private ReentrantLock captureCameraLock;

	/* Screen wakeup lock */
	protected PowerManager.WakeLock mWakeLock;

	public CameraSetting cameraSetting;

	private CameraOperation cameraOperation;

	public PlaceholderFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		capture_main = (Video_Capture_Main) getActivity();

		isRecording = PROCESS_FREE;
		captureCameraLock = new ReentrantLock();

		cameraSetting = new CameraSetting(getActivity());
	}

	@Override
	public void onResume() {
		cameraOperation = new CameraOperation(mPreview);

		cameraOperation.reGetCameraWithRetry();

		if (cameraSetting.GetScreenLock() == true) {
			GetScreenOnLock();
		}

		super.onResume();
	}

	@Override
	public void onPause() {
		super.onPause();

		ReleaseScreenOnLock();
		CameraOperation.releaseCamera(); // release the camera immediately on
											// pause event
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
		
		preview.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				cameraOperation.focusCameraAgain();
			}
		});

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
				cameraOperation.focusTakePicture();
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
				setButtonStatus(isRecording);
			}

		});

		zoomOutButton = (Button) rootView.findViewById(R.id.zoomOut);
		zoomOutButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				boolean status;

				captureCameraLock.lock();

				status = cameraOperation.zoomCameraOut();
				if (status == false) {
					zoomOutButton.setEnabled(false);
				}
				zoomInButton.setEnabled(true);

				captureCameraLock.unlock();
			}
		});

		zoomInButton = (Button) rootView.findViewById(R.id.zoomIn);
		zoomInButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				boolean status;
				captureCameraLock.lock();

				status = cameraOperation.zoomCameraIn();

				if (status == false) {
					zoomInButton.setEnabled(false);
				}
				zoomOutButton.setEnabled(true);

				captureCameraLock.unlock();
			}
		});

		return rootView;
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
			/* temporary add the auto focus function here */
			int auto_focus_count = 2;

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
				if (--auto_focus_count == 0) {
					auto_focus_count = 10;
					cameraOperation.focusCameraAgain();
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

		CameraOperation.releaseCamera();
		// initialize video camera

		status = cameraOperation.prepareVideoRecorder(cameraSetting
				.GetCaptureVideoQuality());

		if (status == true) {
			// Camera is available and unlocked, MediaRecorder
			// is prepared,
			// now you can start recording
			cameraOperation.mMediaRecorder.start();

			new CameraCaptureOperation(cameraSetting.GetStartCaptureTime())
					.execute(0);

		} else {
			CameraOperation.releaseCamera();
			// prepare didn't work, release the camera
			cameraOperation.releaseMediaRecorder();
			// inform user
			cameraOperation.reGetCameraWithRetry();
		}

		return status;
	}

	void GetScreenOnLock() {
		if (mWakeLock == null) {
			final PowerManager pm = (PowerManager) capture_main
					.getSystemService(Context.POWER_SERVICE);
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
