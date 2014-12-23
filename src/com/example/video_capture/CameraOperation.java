package com.example.video_capture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

public class CameraOperation {

	static final String TAG = "CameraOperation";

	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	private Camera mCamera;
	public MediaRecorder mMediaRecorder;

	private static int numOfCamera = 0;

	private CameraPreview mPreview;

	/* reverse for future, and this value can be change */
	private int picturePreviewTime = 0x02;

	public CameraOperation(CameraPreview cp) {
		mPreview = cp;
	}

	public boolean prepareVideoRecorder() {
		String filename;

		mCamera = getCameraInstance();
		mMediaRecorder = new MediaRecorder();

		// Step 1: Unlock and set camera to MediaRecorder
		mCamera.unlock();
		mMediaRecorder.setCamera(mCamera);

		// Step 2: Set sources
		mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

		// Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
		mMediaRecorder.setProfile(CamcorderProfile
				.get(CamcorderProfile.QUALITY_LOW));

		// mMediaRecorder.setProfile(CamcorderProfile
		// .get(CamcorderProfile.QUALITY_HIGH));

		filename = getOutputMediaFile(MEDIA_TYPE_VIDEO).toString();
		if (filename == null) {
			/* Create the file fail */
			return false;
		}
		// Step 4: Set output file
		mMediaRecorder.setOutputFile(filename);

		// Step 5: Set the preview output
		mMediaRecorder.setPreviewDisplay(mPreview.getHolder().getSurface());

		// Step 6: Prepare configured MediaRecorder
		try {
			mMediaRecorder.prepare();
		} catch (IllegalStateException e) {
			Log.d(TAG,
					"IllegalStateException preparing MediaRecorder: "
							+ e.getMessage());
			releaseMediaRecorder();
			return false;
		} catch (IOException e) {
			Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		}
		return true;
	}

	public void releaseMediaRecorder() {
		if (mMediaRecorder != null) {
			mMediaRecorder.reset(); // clear recorder configuration
			mMediaRecorder.release(); // release the recorder object
			mMediaRecorder = null;
			mCamera.lock(); // lock camera for later use
		}
	}

	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static void setCameraDisplayOrientation(Activity activity,
			int cameraId, android.hardware.Camera camera) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
			android.hardware.Camera.getCameraInfo(cameraId, info);
			int rotation = activity.getWindowManager().getDefaultDisplay()
					.getRotation();
			int degrees = 0;
			switch (rotation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
			}

			int result;
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				result = (info.orientation + degrees) % 360;
				result = (360 - result) % 360; // compensate the mirror
			} else { // back-facing
				result = (info.orientation - degrees + 360) % 360;
			}
			camera.setDisplayOrientation(result);
		}
	}

	/* reget the camera handle with retry. Timeout is 2 sec; */
	public boolean reGetCameraWithRetry() {
		int retryCount = 0;
		// reget the camera immediately on pause event
		if (mCamera == null) {
			// Create an instance of Camera
			/* Try get the Camera dump in the UI thread */
			while (mCamera == null) {
				mCamera = getCameraInstance();
				CameraPreview.UpdateCamera(mCamera);
				try {
					// wait(100);
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (++retryCount > 20) {
					Log.d(TAG, "Open Camera fail");
					break;
				}
			}
			if (mCamera != null) {
				try {
					mCamera.setPreviewDisplay(mPreview.getHolder());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				mCamera.setDisplayOrientation(0);
				mCamera.startPreview();
				Log.d(TAG, "onResume sucessfully");
			}
		}
		if (mCamera != null) {
			try {
				getCameraFeatrues(mCamera);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return (mCamera == null) ? false : true;
	}

	/**
	 * Check if this device has a camera
	 * 
	 * @return
	 */
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static boolean checkCameraHardware(Context context) {
		if (context.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_CAMERA)) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				numOfCamera = Camera.getNumberOfCameras();
			} else {
				numOfCamera = 1;
			}
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	/** A safe way to get an instance of the Camera object. */
	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
		}
		return c; // returns null if camera is unavailable
	}

	public void releaseCamera() {
		if (mCamera != null) {
			CameraPreview.UpdateCamera(null);
			mCamera.stopPreview();
			mCamera.release(); // release the camera for other applications
			mCamera = null;
		}
	}

	/** Create a file Uri for saving an image or video */
	public static Uri getOutputMediaFileUri(int type) {
		return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = new File(
				Environment
						.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
				"MyCameraApp");
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d("MyCameraApp", "failed to create directory");
				return null;
			}
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss")
				.format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ "IMG_" + timeStamp + ".jpg");
		} else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator
					+ "VID_" + timeStamp + ".mp4");
		} else {
			return null;
		}

		return mediaFile;
	}

	public void takePicture() {
		// get an image from the camera
		mCamera.takePicture(null, null, mPicture);
		if (picturePreviewTime == 0x0) {
			mCamera.startPreview();
		} else {
			new Thread(new Runnable() {
				public void run() {
					try {
						Thread.sleep(picturePreviewTime * 1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					mCamera.startPreview();
				}
			}).start();
		}
	}

	private PictureCallback mPicture = new PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {

			File pictureFile = getOutputMediaFile(CameraOperation.MEDIA_TYPE_IMAGE);
			if (pictureFile == null) {
				Log.d(TAG,
						"Error creating media file, check storage permissions: \n");
				return;
			}

			try {
				FileOutputStream fos = new FileOutputStream(pictureFile);
				fos.write(data);
				fos.close();
			} catch (FileNotFoundException e) {
				Log.d(TAG, "File not found: " + e.getMessage());
			} catch (IOException e) {
				Log.d(TAG, "Error accessing file: " + e.getMessage());
			}
		}
	};

	public void stopVideoCapture() {
		// stop recording and release camera
		mMediaRecorder.stop(); // stop the recording
		releaseMediaRecorder(); // release the MediaRecorder
								// object
		mCamera.lock(); // take camera access back from
						// MediaRecorder

		releaseCamera();
		reGetCameraWithRetry();
	}

	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}

	/* Checks if external storage is available to at least read */
	public boolean isExternalStorageReadable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)
				|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return true;
		}
		return false;
	}

	public <T> byte[] convertList2String(List<T> dat, String Title) {
		String str = Title;
		int size;
		int count = 0;
		
		if(dat != null){
			size = dat.size();
			for (count = 0; count < size; count++) {
				str += ":";
				str += dat.get(count);
			}
		}
		if(count == 0){
			str += ":Unspport Feature";
		}
		
		str += "\n";

		return str.getBytes();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void getCameraFeatrues(Camera c) throws IOException {
		byte[] string_info;
		FileOutputStream fOut = null;
		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
		// to determine if a camera is on the front or back of the device,
		// and the orientation of the image
		// Camera.getCameraInfo(1, null);
		// } else
		{

			/* Check for externel storage writeable */
			if (isExternalStorageWritable() == false) {
				return;
			}

			// To be safe, you should check that the SDCard is mounted
			// using Environment.getExternalStorageState() before doing this.
			File mediaStorageDir = new File(
					Environment
							.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
					"MyCameraApp");
			// This location works best if you want the created images to be
			// shared
			// between applications and persist after your app has been
			// uninstalled.

			// Create the storage directory if it does not exist
			if (!mediaStorageDir.exists()) {
				if (!mediaStorageDir.mkdirs()) {
					Log.d("MyCameraApp", "failed to create directory");
					return;
				}
			}

			File infoFile;
			infoFile = new File(mediaStorageDir.getPath() + File.separator
					+ "info.txt");

			if (infoFile.exists()) {
				infoFile.delete();
				//return;
			}
			
			/* dump all the information to a file */
			try {
				fOut = new FileOutputStream(infoFile);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			Parameters cameraParameters;
			// get further information about its capabilities
			cameraParameters = c.getParameters();

			List<int[]> previewFpsRange;
			List<Size> videoSizes;

			List<String> focusModes = cameraParameters.getSupportedFocusModes();

			string_info = convertList2String(focusModes, "focusModes");
			fOut.write(string_info);

			if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
				// Autofocus mode is supported
			}
			List<String> antiBanding = cameraParameters
					.getSupportedAntibanding();
			string_info = convertList2String(antiBanding, "antiBanding");
			fOut.write(string_info);
			
			List<String> colorEffects = cameraParameters
					.getSupportedColorEffects();
			string_info = convertList2String(colorEffects, "colorEffects");
			fOut.write(string_info);
			
			List<String> flashModes = cameraParameters.getSupportedFlashModes();
			string_info = convertList2String(flashModes, "flashModes");
			fOut.write(string_info);
			
			List<Size> jpegThumbnailSizes = cameraParameters
					.getSupportedJpegThumbnailSizes();
			string_info = convertList2String(jpegThumbnailSizes, "jpegThumbnailSizes");
			fOut.write(string_info);
			
			List<Integer> pictureFormats = cameraParameters
					.getSupportedPictureFormats();
			string_info = convertList2String(pictureFormats, "pictureFormats");
			fOut.write(string_info);
			
			List<Integer> previewFormats = cameraParameters
					.getSupportedPreviewFormats();
			string_info = convertList2String(previewFormats, "previewFormats");
			fOut.write(string_info);
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				previewFpsRange = cameraParameters
						.getSupportedPreviewFpsRange();
			} else {
				previewFpsRange = null;
			}
			string_info = convertList2String(previewFpsRange, "previewFpsRange");
			fOut.write(string_info);
			
			List<Integer> previewFrameRates = cameraParameters
					.getSupportedPreviewFrameRates();
			string_info = convertList2String(previewFrameRates, "previewFrameRates");
			fOut.write(string_info);
			
			List<Size> previewSizes = cameraParameters
					.getSupportedPreviewSizes();
			string_info = convertList2String(previewSizes, "previewSizes");
			fOut.write(string_info);
			
			List<String> sceneModes = cameraParameters.getSupportedSceneModes();
			string_info = convertList2String(sceneModes, "sceneModes");
			fOut.write(string_info);
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				videoSizes = cameraParameters.getSupportedVideoSizes();
			} else {
				videoSizes = null;
			}
			string_info = convertList2String(videoSizes, "videoSizes");
			fOut.write(string_info);
			
			List<String> witeBalance = cameraParameters
					.getSupportedWhiteBalance();
			string_info = convertList2String(witeBalance, "witeBalance");
			fOut.write(string_info);

			try {
				fOut.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			try {
				fOut.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
