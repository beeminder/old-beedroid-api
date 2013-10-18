package com.beeminder.simpleform;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.beeminder.beedroid.api.Session;
import com.beeminder.beedroid.api.Session.SessionError;
import com.beeminder.beedroid.api.Session.SessionException;
import com.beeminder.beedroid.api.Session.SessionState;

public class BeeminderForm extends Activity {

	private static final String TAG = "BeeminderForm";

	public static final int ACTIVITY_BEEMINDER_AUTH = 1;

	TextView mGoalView;
	TextView mTokenView;
	EditText mValueView;
	Button mSubmitButton;

	String mUsername;
	String mGoalSlug;
	String mToken;

	private static final String KEY_USERNAME = "beeminder_username";
	private static final String KEY_GOALSLUG = "beeminder_goalslug";

	SharedPreferences mSP;
	Session mSession;

	private void resetFields() {
		mToken = null;
		mUsername = null;
		mGoalSlug = null;
		mGoalView.setText("Goal URI: none");
		mTokenView.setText("Token: none");
		mValueView.setText("0");
	}

	private void recordCurGoal() {
		SharedPreferences.Editor edit = mSP.edit();
		edit.putString(KEY_USERNAME, mUsername);
		edit.putString(KEY_GOALSLUG, mGoalSlug);
		edit.commit();
	}

	private void clearCurGoal() {
		SharedPreferences.Editor edit = mSP.edit();
		edit.remove(KEY_USERNAME);
		edit.remove(KEY_GOALSLUG);
		edit.commit();
	}

	private class SessionStatusCallback implements Session.StatusCallback {
		@Override
		public void call(Session session, SessionState state) {
			Log.v(TAG, "Beeminder status changed:" + state);

			if (state == SessionState.OPENED) {
				mToken = session.getToken();
				mUsername = session.getUsername();
				mGoalSlug = session.getGoalSlug();
				Log.v(TAG, "Goal = " + mUsername + "/" + mGoalSlug + ", token="
						+ session.getToken());

				mGoalView.setText("Goal URI: " + mUsername + "/" + mGoalSlug);
				mTokenView.setText("Token: " + mToken);

				recordCurGoal();

			} else if (state == SessionState.CLOSED_ON_ERROR) {
				SessionError error = mSession.getError();
				if (error.type == Session.ErrorType.ERROR_UNAUTHORIZED)
					clearCurGoal();
				resetFields();
				Toast.makeText(getBaseContext(),
						"Session closed with error: " + mSession.getError().message,
						Toast.LENGTH_SHORT).show();
			} else if (state == SessionState.CLOSED) {
				resetFields();
			}
		}
	}

	private class PointSubmissionCallback implements Session.SubmissionCallback {
		@Override
		public void call(Session session, int id, String error) {
			Log.v(TAG, "Point submission completed, id=" + id + ", error="
					+ error);
			if (error == null) {
				Toast.makeText(getBaseContext(), "Datapoint submission complete.", Toast.LENGTH_SHORT)
				.show();				
			} else {
				Toast.makeText(getBaseContext(), "Error submitting datapoint: "+error, Toast.LENGTH_SHORT)
				.show();
			}
			mSubmitButton.setEnabled(true);
			mSubmitButton.setText("Submit Point");
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_beeminder_form);

		mGoalView = (TextView) findViewById(R.id.token);
		mTokenView = (TextView) findViewById(R.id.goaluri);
		mValueView = (EditText) findViewById(R.id.value);
		mSubmitButton = (Button) findViewById(R.id.submit);

		resetFields();

		mSP = PreferenceManager
				.getDefaultSharedPreferences(getApplicationContext());
		mUsername = mSP.getString(KEY_USERNAME, null);
		mGoalSlug = mSP.getString(KEY_GOALSLUG, null);

		try {
			mSession = new Session(this);
			mSession.setStatusCallback(new SessionStatusCallback());
			mSession.setSubmissionCallback(new PointSubmissionCallback());

			if (mUsername != null && mGoalSlug != null)
				mSession.openForGoal(mUsername, mGoalSlug);
			else
				resetFields();

		} catch (SessionException e) {
			resetFields();
			Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_SHORT)
					.show();
			clearCurGoal();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		mSession.onActivityResult(requestCode, resultCode, intent);
	}

	public void newSession(View v) {
		try {
			resetFields();
			mSession.close();
			mSession.openForNewGoal();
		} catch (SessionException e) {
			Toast.makeText(getBaseContext(), e.getMessage(), Toast.LENGTH_SHORT)
					.show();
		}
	}

	public void closeSession(View v) {
		clearCurGoal();
		resetFields();
		mSession.close();
	}

	public void submitPoint(View v) {

		float value;

		try {
			value = Float.parseFloat(mValueView.getText().toString());
			mSession.submitPoint(value, 0, null);
			mSubmitButton.setEnabled(false);
			mSubmitButton.setText("Submitting...");
			
		} catch (NumberFormatException e) {
			Toast.makeText(getApplicationContext(), "Invalid value!",
					Toast.LENGTH_SHORT).show();
		} catch (SessionException e) {
			Toast.makeText(getApplicationContext(),
					"Session error:" + e.getMessage(), Toast.LENGTH_SHORT)
					.show();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mSession.close();
	}

}
