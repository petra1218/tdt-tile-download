package com.petra.tdttiledownload;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
				double left = Double.parseDouble(boundArray[0]);
				double bottom = Double.parseDouble(boundArray[1]);
				double right = Double.parseDouble(boundArray[2]);
				double top = Double.parseDouble(boundArray[3]);
				Log.v(LOG_TAG, String.format("bound double = {%f,%f,%f,%f}",
						left, bottom, right, top));

				// 存放每一级影像的四个角的瓦片号，左（最小列號）、上（是小行号）、右（最大列号）、下（最大行号）
				Map<Integer, int[]> tileMap = new HashMap<Integer, int[]>();
				long tileCount = 0;
				for (int i = 5; i < 19; i++) {
					int[] tileBound = new int[] {
							TileUtils.getColumeNumBy84(left, i),
							TileUtils.getRowNumBy84(top, i),
							TileUtils.getColumeNumBy84(right, i),
							TileUtils.getRowNumBy84(bottom, i), };
					tileMap.put(i, tileBound);
					int tileXCount = tileBound[2] - tileBound[0];
					int tileYCount = tileBound[3] - tileBound[1];
					int currLevelCount = (0 == tileXCount ? 1 : tileXCount + 1)
							* (0 == tileYCount ? 1 : tileYCount + 1);
					tileCount += currLevelCount;

					Log.v(LOG_TAG,
							String.format(
									"level = %d, tileBound = {%d, %d, %d, %d },currLevelCount = %d, tileCount = %d",
									i, tileBound[0], tileBound[1],
									tileBound[2], tileBound[3], currLevelCount,
									tileCount));
				}
				// 需要分别下载影像瓦片和矢量瓦片
				tileCount *= 2;

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
