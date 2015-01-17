package com.example.video_capture;

import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.graphics.Color;
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
import android.widget.ProgressBar;
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

	/* display the information of the zoom ratio */
	private TextView zoomInfoTW;

	/* display zoom info class */
	private DisplayZoomInfo diplayZoomInfo;

	/* progress bar use for indicate the busy state of getting the camera */
	private ProgressBar prgressBarGettingCamera;

	public Video_Capture_Main capture_main;

	/* camera video capture process state */
	private static final int PROCESS_FREE = 0x00;
	private static final int PROCESS_DELAY_VIDEO = 0x01;
	private static final int PROCESS_DELAY_PICTURE = 0x02;
	private static final int PROCESS_CAPTURE_VIDEO = 0x03;
	private static final int PROCESS_STARTED = 0x04;
	private static final int PROCESS_CAPTURE_PIC_CONTINUS = 0x05;

	/* camera taken type : picture or video */
	private static final int CAPTURE_TYPE_VIDEO = 0x00;
	private static final int CAPTURE_TYPE_PICTURE = 0x01;

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

		isRecording = PROCESS_STARTED;
		captureCameraLock = new ReentrantLock();

		cameraSetting = new CameraSetting(getActivity());
	}

	@Override
	public void onResume() {
		super.onResume();

		Log.d(TAG, "onResume");

		diplayZoomInfo = new DisplayZoomInfo(zoomInfoTW, 0, 0);
		diplayZoomInfo.execute();

		cameraOperation = new CameraOperation(mPreview);

		reGetCameraWithRetry();

		if (cameraSetting.GetScreenLock() == true) {
			GetScreenOnLock();
		}
	}

	public void killCurrentActivity() {
		Log.d(TAG, "killCurrentActivity");

		diplayZoomInfo.setTreadExit();

		captureCameraLock.lock();
		if (isRecording == PROCESS_FREE) {
			// release the camera immediately on pause event
			CameraOperation.releaseCamera();
		} else if (isRecording == PROCESS_DELAY_VIDEO) {

		} else if (isRecording == PROCESS_DELAY_PICTURE) {

		} else if (isRecording == PROCESS_CAPTURE_VIDEO) {
			cameraOperation.stopVideoCapture();
			isRecording = PROCESS_FREE;
		} else if (isRecording == PROCESS_CAPTURE_PIC_CONTINUS) {
			isRecording = PROCESS_FREE;
		}
		captureCameraLock.unlock();

		capture_main.finish();
	}

	@Override
	public void onPause() {
		Log.d(TAG, "onPause");

		diplayZoomInfo.setTreadExit();

		captureCameraLock.lock();
		if (isRecording == PROCESS_FREE) {
			// release the camera immediately on pause event
			CameraOperation.releaseCamera();
		} else if (isRecording == PROCESS_DELAY_VIDEO) {

		} else if (isRecording == PROCESS_DELAY_PICTURE) {

		} else if (isRecording == PROCESS_CAPTURE_VIDEO) {
			cameraOperation.stopVideoCapture();
			isRecording = PROCESS_FREE;
		} else if (isRecording == PROCESS_CAPTURE_PIC_CONTINUS) {
			isRecording = PROCESS_FREE;
		}
		captureCameraLock.unlock();

		super.onPause();
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

		zoomInfoTW = (TextView) rootView.findViewById(R.id.zoom_info);

		captureVideoButtonI = (Button) rootView.findViewById(R.id.button2);
		captureButtonI = (Button) rootView.findViewById(R.id.button1);

		prgressBarGettingCamera = (ProgressBar) rootView
				.findViewById(R.id.loadingProgress);

		// Add a listener to the Capture button
		captureButton = (Button) rootView.findViewById(R.id.button_capture);
		captureButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (captureCameraLock.tryLock() == false) {
					return;
				}
				if (isRecording == PROCESS_FREE) {
					if (cameraSetting.GetScreenLock() == false) {
						GetScreenOnLock();
					}

					int delay_time = cameraSetting.GetDelayCaptureTime();
					if (delay_time > 0) {
						isRecording = PROCESS_DELAY_PICTURE;
						CameraDelayOperation asyncTask = new CameraDelayOperation();
						asyncTask.execute(delay_time);

						setButtonStatus(isRecording);
					} else {
						cameraOperation.focusTakePicture();
					}
				} else if (isRecording == PROCESS_CAPTURE_PIC_CONTINUS) {
					isRecording = PROCESS_FREE;
					setButtonStatus(isRecording);
				} else if (isRecording == PROCESS_DELAY_PICTURE) {
					isRecording = PROCESS_FREE;
					setButtonStatus(isRecording);
				} else {
					/* should never run here */
					Log.e(TAG, "Error click on the capture button");
				}
				captureCameraLock.unlock();
				// cameraOperation.focusTakePicture();
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
				if (isRecording == PROCESS_CAPTURE_VIDEO) {
					isRecording = PROCESS_FREE;

					cameraOperation.stopVideoCapture();
					reGetCameraWithRetry();

				} else if (isRecording == PROCESS_FREE) {
					if (cameraSetting.GetScreenLock() == false) {
						GetScreenOnLock();
					}

					int delay_time = cameraSetting.GetDelayCaptureTime();
					if (delay_time > 0) {
						isRecording = PROCESS_DELAY_VIDEO;
						CameraDelayOperation asyncTask = new CameraDelayOperation();
						asyncTask.execute(delay_time);
					} else {
						isRecording = PROCESS_CAPTURE_VIDEO;
						if (startVideoRecording() == false) {
							isRecording = PROCESS_FREE;
						}
					}
				} else if (isRecording == PROCESS_DELAY_VIDEO) {
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

				diplayZoomInfo.updateZoomRatio(
						cameraOperation.getCurrentzoomIndex() / 100.0, 3);
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

				diplayZoomInfo.updateZoomRatio(
						cameraOperation.getCurrentzoomIndex() / 100.0, 3);
			}
		});

		isRecording = PROCESS_STARTED;
		setButtonStatus(isRecording);

		return rootView;
	}

	private void setButtonStatus(int status) {

		Log.d(TAG, "Button status:" + status);

		if (status != PROCESS_STARTED) {
			zoomInButton.setEnabled(true);
			zoomOutButton.setEnabled(true);
			prgressBarGettingCamera.setVisibility(View.INVISIBLE);
		} else {
			zoomInButton.setEnabled(false);
			zoomOutButton.setEnabled(false);
			prgressBarGettingCamera.setVisibility(View.VISIBLE);
		}

		/* Reset the UI to the right state */
		if (status == PROCESS_FREE) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(true);
			captureButtonI.setEnabled(true);

			captureButton.setEnabled(true);
			captureButton.setText("Capture Picture");

			captureVideoButton.setEnabled(true);
			captureVideoButton.setText("Capture Video");

			/* Release the screen lock */
			if (cameraSetting.GetScreenLock() == false) {
				ReleaseScreenOnLock();
			}

		} else if (status == PROCESS_CAPTURE_VIDEO) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(false);
			captureButtonI.setEnabled(false);
			captureButton.setEnabled(false);

			captureVideoButton.setEnabled(true);
			captureVideoButton.setText("Capture Stop");
		} else if (status == PROCESS_DELAY_VIDEO) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(false);
			captureButtonI.setEnabled(false);
			captureButton.setEnabled(false);

			captureVideoButton.setEnabled(true);
			captureVideoButton.setText("Capture Cancel");
		} else if (status == PROCESS_DELAY_PICTURE) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(false);
			captureButtonI.setEnabled(false);
			captureVideoButton.setEnabled(false);

			captureButton.setEnabled(true);
			captureButton.setText("Capture Cancel");
		} else if (status == PROCESS_STARTED) {
			/* Just started, disable all button */
			captureVideoButtonI.setEnabled(false);
			captureButtonI.setEnabled(false);
			captureButton.setEnabled(false);
			captureVideoButtonI.setEnabled(false);
		} else if (status == PROCESS_CAPTURE_PIC_CONTINUS) {
			/* Enable other button */
			captureVideoButtonI.setEnabled(false);
			captureButtonI.setEnabled(false);
			captureVideoButton.setEnabled(false);

			captureButton.setEnabled(true);
			captureButton.setText("Capture stop");
		} else {
			Log.d(TAG, "setButtonStatus: Unknow status");
		}
	}

	/* reget the camera handle with async task */
	public boolean reGetCameraWithRetry() {
		int delay_time = 50;

		isRecording = PROCESS_STARTED;
		setButtonStatus(isRecording);

		if (cameraOperation.reGetCameraWithRetry() == false) {
			Log.d(TAG, "reGetCameraWithRetry fail, camera instance is null");
			capture_main.finish();
		}

		/* reset the picture quality */
		cameraOperation.setCapturePictureSize(cameraSetting
				.GetCapturePictureQuality());

		Log.d(TAG,
				"Picture quality:" + cameraSetting.GetCapturePictureQuality());

		isRecording = PROCESS_FREE;
		setButtonStatus(isRecording);

		return true;
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
			if (isRecording == PROCESS_DELAY_VIDEO) {
				if (isRecording != PROCESS_FREE) {
					status = startVideoRecording();
				}

				if (status == false) {
					isRecording = PROCESS_FREE;
				}
			} else if (isRecording == PROCESS_DELAY_PICTURE) {
				if (isRecording != PROCESS_FREE) {
					isRecording = PROCESS_CAPTURE_PIC_CONTINUS;
				}
				new CameraPictureContinusCaptureOperation(
						cameraSetting.GetStartCaptureTime()).execute(0);
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

	private class CameraPictureContinusCaptureOperation extends
			AsyncTask<Integer, Integer, String> {

		private Time startupTime;
		private Time currentTime;
		private int captureTime;
		private int pictrueCount = 0;

		public CameraPictureContinusCaptureOperation(int cp) {
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
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch
					// block
					e.printStackTrace();
				}
				if (isRecording == PROCESS_FREE) {
					break;
				}
				if (cameraOperation.checkForAvailStatus() == true) {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch
						// block
						e.printStackTrace();
					}
					if (isRecording == PROCESS_FREE) {
						break;
					}
					cameraOperation.takePictureContinus(true);
				}
			} while ((captureTime == 0) || (captureTime > time_interval));

			/* wait until the last take picture action over */
			while (cameraOperation.checkForAvailStatus() == false) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch
					// block
					e.printStackTrace();
				}
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch
				// block
				e.printStackTrace();
			}

			/* post operation, put the camera into preview state */
			cameraOperation.putCameraPreviewState();

			return "Executed";
		}

		@Override
		protected void onPostExecute(String result) {
			captureTimeTW.setVisibility(TextView.INVISIBLE);
			captureCameraLock.lock();
			if (isRecording != PROCESS_FREE) {
				isRecording = PROCESS_FREE;

				setButtonStatus(isRecording);
			}
			captureCameraLock.unlock();

			killCurrentActivity();
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
					if (cameraSetting.GetVideoAutofocusEnable() == true) {
						cameraOperation.focusCameraAgain();
					}
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
				reGetCameraWithRetry();

				setButtonStatus(isRecording);
			}
			captureCameraLock.unlock();

			killCurrentActivity();
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
			reGetCameraWithRetry();
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
