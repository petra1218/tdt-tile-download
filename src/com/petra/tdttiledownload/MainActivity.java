package com.petra.tdttiledownload;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.ViewUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.RequestParams;
import com.lidroid.xutils.http.ResponseStream;
import com.lidroid.xutils.http.client.HttpRequest.HttpMethod;
import com.lidroid.xutils.view.annotation.ViewInject;
import com.lidroid.xutils.view.annotation.event.OnClick;

public class MainActivity extends Activity {
	String LOG_TAG = MainActivity.class.getSimpleName();

	@ViewInject(R.id.start_download)
	Button startDownload;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ViewUtils.inject(this);
	}

	@OnClick({ R.id.start_download, })
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.start_download:
			DownloadThread downloadThread = new DownloadThread();
			downloadThread.start();
			break;
		}

	}

	class DownloadThread extends Thread {
		private ResponseStream responseStream;

		@Override
		public void run() {
			HttpUtils httpUtils = new HttpUtils();
			RequestParams params = new RequestParams();
			params.addBodyParameter(
					"jsonStr",
					"{\"keyWord\":\"海淀区\",\"level\":\"11\",\"mapBound\":\"-180,-90,180,90\",\"queryType\":\"1\",\"count\":\"10\",\"start\":\"0\"}");
			// String url =
			// "http://api.tianditu.com/api/api-new/query.do?jsonStr={\"keyWord\":\"海淀区\",\"level\":\"11\",\"mapBound\":\"-180,-90,180,90\",\"queryType\":\"1\",\"count\":\"10\",\"start\":\"0\"}";
			String url = "http://api.tianditu.com/api/api-new/query.do?";
			try {
				responseStream = httpUtils.sendSync(HttpMethod.POST, url,
						params);
				String responseStr = responseStream.readString();
				Log.v(LOG_TAG, "response string = " + responseStr);
				JSONObject json = new JSONObject(responseStr);
				JSONObject area = json.optJSONObject("area");

				String boundStr = area.optString("bound");

				String[] boundArray = boundStr.split(",");
				Log.v(LOG_TAG,
						boundStr
								+ String.format("{%s, %s, %s, %s}",
										boundArray[0], boundArray[1],
										boundArray[2], boundArray[3]));
				double xMin = Double.parseDouble(boundArray[0]);
				double xMax = Double.parseDouble(boundArray[2]);
				double yMin = Double.parseDouble(boundArray[1]);
				double yMax = Double.parseDouble(boundArray[3]);
				Log.v(LOG_TAG, String.format("bound double = {%f,%f,%f,%f}",
						xMin, yMin, xMax, yMax));

				double[] tlMercator = TileUtils.lonLat2Mercator(xMin, yMin);
				double[] trMercator = TileUtils.lonLat2Mercator(xMax, yMin);
				double[] blMercator = TileUtils.lonLat2Mercator(xMin, yMax);
				double[] brMercator = TileUtils.lonLat2Mercator(xMax, yMax);
				Log.v(LOG_TAG, String.format(
						"bound mercator = {%f,%f},{%f,%f},{%f,%f},{%f,%f}",
						tlMercator[0], tlMercator[1], trMercator[0],
						trMercator[1], blMercator[0], blMercator[1],
						brMercator[0], brMercator[1]));

				int[] tlRcNum = TileUtils.getRowAndColume(tlMercator, 15);
				int[] trRcNum = TileUtils.getRowAndColume(trMercator, 15);
				int[] blRcNum = TileUtils.getRowAndColume(blMercator, 15);
				int[] brRcNum = TileUtils.getRowAndColume(brMercator, 15);
				Log.v(LOG_TAG, String.format(
						"bound mercator = {%d,%d},{%d,%d},{%d,%d},{%d,%d}",
						tlRcNum[0], tlRcNum[1], trRcNum[0], trRcNum[1],
						blRcNum[0], blRcNum[1], brRcNum[0], brRcNum[1]));

			} catch (HttpException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}

			super.run();
		}
	}
}
