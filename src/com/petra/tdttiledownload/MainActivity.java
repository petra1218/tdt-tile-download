package com.petra.tdttiledownload;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.google.gson.JsonObject;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseStream;
import com.lidroid.xutils.http.client.HttpRequest.HttpMethod;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.lidroid.xutils.view.annotation.event.OnClick;

public class MainActivity extends Activity {
	@ViewInject(R.id.start_download)
	Button startDownload;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

	}

	@OnClick({ R.id.start_download, })
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.start_download:
			break;
		}

	}

	class DownloadThread extends Thread {
		private ResponseStream responseStream;

		@Override
		public void run() {
			HttpUtils httpUtils = new HttpUtils();

			try {
				responseStream = httpUtils
						.sendSync(
								HttpMethod.GET,
								"http://api.tianditu.com/api/api-new/query.do?jsonStr={%22keyWord%22:%22%E5%A4%AA%E5%92%8C%E5%8C%BA%22,%22level%22:%2211%22,%22mapBound%22:%22-180,-90,180,90%22,%22queryType%22:%221%22,%22count%22:%2210%22,%22start%22:%220%22}");
				JSONObject json = new JSONObject(responseStream.toString());
				JSONObject area = json.optJSONObject("area");

				String boundStr = area.optString("bound");

				String[] boundArray = boundStr.split(",");
				double xMin = Double.parseDouble(boundArray[0]);
				double xMax = Double.parseDouble(boundArray[2]);
				double yMin = Double.parseDouble(boundArray[1]);
				double yMax = Double.parseDouble(boundArray[3]);

			} catch (HttpException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}

			super.run();
		}
	}
}
