package com.beeminder.simpleform;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class BeeminderForm extends Activity {

	private static final String TAG = "BeeminderForm";
	private static final boolean LOCAL_LOGV = true;

	public static final String ACTION_API_AUTHORIZE = "com.beeminder.beeminder.AUTHORIZE";
	public static final String ACTION_API_UNAUTHORIZE = "com.beeminder.beeminder.UNAUTHORIZE";
	public static final String ACTION_API_SUBMITPOINT = "com.beeminder.beeminder.SUBMITPOINT";
	public static final String BEEMINDER_PACKAGE = "com.beeminder.beeminder";

	public static final int ACTIVITY_BEEMINDER_AUTH = 1;

	String mPackageName;
	String mAppName;
	TextView mGoalView;
	TextView mTokenView;
	EditText mValueView;

	boolean mSelectionActive = false;

	String mUsername;
	String mGoalSlug;
	String mToken;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_beeminder_form);

		mReplyTo = new Messenger(new IncomingHandler());

		mGoalView = (TextView) findViewById(R.id.token);
		mTokenView = (TextView) findViewById(R.id.goaluri);
		mValueView = (EditText) findViewById(R.id.value);

		mGoalView.setText("Goal URI: none");
		mTokenView.setText("Token: none");
		mValueView.setText("0");

		try {
			ApplicationInfo ai = getPackageManager().getApplicationInfo(
					getPackageName(), 0);
			mPackageName = getPackageName();
			mAppName = getPackageManager().getApplicationLabel(ai).toString();
		} catch (final NameNotFoundException e) {
			mPackageName = null;
			mAppName = null;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		switch (requestCode) {
		case ACTIVITY_BEEMINDER_AUTH:
			if (resultCode == Activity.RESULT_OK && intent != null) {
				Bundle extras = intent.getExtras();

				mToken = extras.getString("token");
				mUsername = extras.getString("username");
				mGoalSlug = extras.getString("slug");

				mGoalView.setText("Goal URI: " + mUsername + "/" + mGoalSlug);
				mTokenView.setText("Token: " + mToken);
			} else {
				mGoalView.setText("Goal URI: none");
				mTokenView.setText("Token: none");
			}
			mSelectionActive = false;
			break;
		default:
			break;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.beeminder_form, menu);
		return true;
	}

	public void selectGoal(View v) {
		if (mPackageName == null || mAppName == null)
			return;

		if (mSelectionActive)
			return;

		mSelectionActive = true;
		Intent intent = new Intent().setAction(ACTION_API_AUTHORIZE)
				.setPackage(BEEMINDER_PACKAGE)
				.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
				.addCategory(Intent.CATEGORY_DEFAULT)
				.putExtra("protocol_version", "20131017")
				.putExtra("protocol_action", "authorize_dialog")
				.putExtra("application_name", mAppName)
				.putExtra("package_name", mPackageName);
		try {
			startActivityForResult(intent, ACTIVITY_BEEMINDER_AUTH);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(getBaseContext(),
					"Could not find the Beeminder app!", Toast.LENGTH_LONG)
					.show();
		}
	}

	/** Messenger for communicating with the service. */
	private Messenger mService = null;
	/** Flag indicating whether we have called bind on the service. */
	private boolean mBound;
    private Messenger mReplyTo = null;
    
	/** Class for interacting with the main interface of the service. */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mService = new Messenger(service);
			mBound = true;
		}

		public void onServiceDisconnected(ComponentName className) {
			mService = null;
			mBound = false;
		}
	};

	public void submitPoint(View v) {
		if (!mBound)
			return;

		float value;

		try {
			value = Float.parseFloat(mValueView.getText().toString());
		} catch (NumberFormatException e) {
			Toast.makeText(getApplicationContext(), "Invalid value!",
					Toast.LENGTH_SHORT).show();
			return;
		}

		// Create and send a message to the service, using a supported 'what'
		// value
		Message msg = Message.obtain(null, 1, 0, 0);
		Bundle extras = new Bundle();
		extras.putString("pkgname", mPackageName);
		extras.putString("token", mToken);
		extras.putString("username", mUsername);
		extras.putString("slug", mGoalSlug);
		extras.putFloat("value", value);

		msg.setData(extras);
		msg.replyTo = mReplyTo;
		
		try {
			mService.send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Bind to the service

		Intent intent = new Intent().setAction(ACTION_API_SUBMITPOINT)
				.setPackage(BEEMINDER_PACKAGE);
		bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mBound) {
			unbindService(mConnection);
			mBound = false;
		}
	}

	private class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			System.out.println("*****************************************");
			System.out.println("Return successfully received!!!!!!");
			System.out.println("*****************************************");

			int what = msg.what;

			Toast.makeText(BeeminderForm.this.getApplicationContext(),
					"Remote Service replied (" + what + ")", Toast.LENGTH_LONG)
					.show();
		}
	}

}
