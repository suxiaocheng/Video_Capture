package com.example.video_capture;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class CameraSetting {

	private static final String TAG = "CameraSetting";

	private static final String PREF_SCREEN_ALWAYS_ON = "screen_always_on";
	private static final String PREF_DELAY_CAPTURE_TIME = "delay_capture_time";
	private static final String PREF_START_CAPTURE_TIME = "start_capture_time";
	private static final String PREF_VIDEO_QUALITY = "capture_video_quality";
	private static final Boolean DEBUG_MODE = true;

	Context context;
	SharedPreferences preference;

	public CameraSetting(Context con) {
		this.context = con;
		preference = PreferenceManager.getDefaultSharedPreferences(context);
		
		if(preference.edit().clear().commit() == false){
			Log.d(TAG, "Fail to clear the preference");
		}
		
		PreferenceManager.setDefaultValues(context, R.xml.pref_general,
				DEBUG_MODE);
	}

	public int GetCaptureVideoQuality() {
		int tmp = preference.getInt(PREF_VIDEO_QUALITY, 1);

		return tmp;
	}

	public int GetDelayCaptureTime() {
		if (preference == null) {
			Log.d(TAG, "preference null");
			return 20;
		}
		String tmp = preference.getString(PREF_DELAY_CAPTURE_TIME, "20");

		return Integer.parseInt(tmp);
	}

	public int GetStartCaptureTime() {
		if (preference == null) {
			Log.d(TAG, "preference null");
			return 40;
		}
		String tmp = preference.getString(PREF_START_CAPTURE_TIME, "40");

		return Integer.parseInt(tmp);
	}

	public boolean GetScreenLock() {
		if (preference == null) {
			Log.d(TAG, "preference null");
			return false;
		}
		boolean tmp = preference.getBoolean(PREF_SCREEN_ALWAYS_ON, true);

		return tmp;
	}

	public void ResetCameraSetting() {
		if (preference == null) {
			Log.d(TAG, "preference null");
			return;
		}
		PreferenceManager.setDefaultValues(context, R.xml.pref_general, false);
	}
}
