package ch.ethz.wearable.contextrecognition.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import ch.ethz.wearable.contextrecognition.communication.CompressAndSendData;

import com.example.contextrecognition.R;

public class UploadActivity extends Activity {

	private static final String TAG = "UploadActivity";

	Button uploadButton;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_upload);

		Log.d(TAG, "onCreate");
		
		addListenerOnButton();

	}
	
	public void addListenerOnButton() {
		
		uploadButton = (Button) findViewById(R.id.uploadButton);
		 
		uploadButton.setOnClickListener(new OnClickListener() {
			 
			@Override
			public void onClick(View arg0) {
 
				// initiate the transfer of the raw audio data to the server:
				Intent i = new Intent(getBaseContext(), CompressAndSendData.class);
				getBaseContext().startService(i);

			}
 
		});
		
	}
}
