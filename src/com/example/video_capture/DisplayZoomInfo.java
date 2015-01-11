package com.example.video_capture;

import java.util.concurrent.locks.ReentrantLock;

import android.os.AsyncTask;
import android.util.Log;
import android.widget.TextView;

public class DisplayZoomInfo extends AsyncTask<Void, Void, String> {

	public static final String TAG = "DisplayZoomInfo";

	private TextView infoZoomTW;
	private double zoomRatio;
	private int infoShowDelayTime = 0;
	private ReentrantLock resourcesAssessLock;
	private Object zoomWait;
	private boolean cancelFlag = false;

	public DisplayZoomInfo(TextView tw, double d, int time) {
		infoZoomTW = tw;
		resourcesAssessLock = new ReentrantLock();

		zoomRatio = d;
		resourcesAssessLock.lock();
		zoomWait = new Object();

		try {
			infoShowDelayTime = time;
		} finally {
			resourcesAssessLock.unlock();
		}

		String str = String.format("%.1f", zoomRatio);
		infoZoomTW.setText(str);
	}

	/**
	 * set the display vale of zoom and how long the ratio will be display
	 * 
	 * @param d
	 *            : display vale of zoom
	 * @param time
	 *            : how long the ratio will be display
	 * 
	 * @return boolean: check if need to restart the thread
	 * 
	 */
	public boolean updateZoomRatio(double d, int time) {
		boolean status = false;
		zoomRatio = d;
		resourcesAssessLock.lock();

		if (infoShowDelayTime <= 0) {
			status = true;
		}

		try {
			infoShowDelayTime = time;
		} finally {
			resourcesAssessLock.unlock();
		}

		String str = String.format("%.1f", zoomRatio);
		infoZoomTW.setText(str);
		infoZoomTW.setVisibility(TextView.VISIBLE);

		synchronized (zoomWait) {
			zoomWait.notify();
		}

		return status;
	}

	public void setTreadExit() {
		resourcesAssessLock.lock();
		cancelFlag = true;
		resourcesAssessLock.unlock();
		synchronized (zoomWait) {
			zoomWait.notify();
		}
	}

	@Override
	protected String doInBackground(Void... params) {
		while (true) {
			resourcesAssessLock.lock();
			if (infoShowDelayTime > 0) {
				Log.d(TAG, "tick:" + infoShowDelayTime);
				infoShowDelayTime--;
				resourcesAssessLock.unlock();
				if (isCancelled() == true) {
					break;
				}
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch
					// block
					e.printStackTrace();
				}
			} else {
				resourcesAssessLock.unlock();
				publishProgress();
				try {
					synchronized (zoomWait) {
						zoomWait.wait();
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (isCancelled() == true) {
					break;
				}
				resourcesAssessLock.lock();
				if (cancelFlag == true) {
					resourcesAssessLock.unlock();
					break;
				}
				resourcesAssessLock.unlock();
			}
		}

		infoZoomTW.setVisibility(TextView.INVISIBLE);
		Log.d(TAG, "Zoom thread exist normally");

		return "TRUE";
	}

	@Override
	protected void onPostExecute(String result) {
		/* When Exec to here, the task is ended */
	}

	@Override
	protected void onPreExecute() {
	}

	@Override
	protected void onProgressUpdate(Void... values) {
		infoZoomTW.setVisibility(TextView.INVISIBLE);
	}
}
