
package com.petra.tdttiledownload;

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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

    @OnClick({
            R.id.start_download,
    })
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_download:
                DownloadThread downloadThread = new DownloadThread();
                downloadThread.start();
                break;
        }
    }

    class DownloadThread extends Thread {
        private static final int CACHE_SIZE = 30 * 1024;
        private ResponseStream responseStream;

        @Override
        public void run() {
            HttpUtils httpUtils = new HttpUtils();
            RequestParams params = new RequestParams();
            params.addBodyParameter(
                    "jsonStr",
                    "{\"keyWord\":\"石景山区\",\"level\":\"11\",\"mapBound\":\"-180,-90,180,90\",\"queryType\":\"1\",\"count\":\"10\",\"start\":\"0\"}");
            // String url =
            // "http://api.tianditu.com/api/api-new/query.do?jsonStr={\"keyWord\":\"海淀区\",\"level\":\"11\",\"mapBound\":\"-180,-90,180,90\",\"queryType\":\"1\",\"count\":\"10\",\"start\":\"0\"}";
            String url = "http://api.tianditu.com/api/api-new/query.do?";
            try {
                double left = 0, bottom = 0, right = 0, top = 0;

                responseStream = httpUtils.sendSync(HttpMethod.POST, url, params);
                String responseStr = responseStream.readString();
                Log.v(LOG_TAG, "response string = " + responseStr);
                JSONObject json = new JSONObject(responseStr);
                JSONObject area = json.optJSONObject("area");

                String boundStr = area.optString("bound");
                if ("".equals(boundStr))
                {// 如果没有明确的给出bound，要自己算
                 // 返回的边界可能是个数组。我们默认算出多个地块的整体外接边界。
                    JSONArray pointsArray = area.getJSONArray("points");

                    double tmp = 0;
                    for (int i = 0; i < pointsArray.length(); i++)
                    {
                        JSONObject regionObject = (JSONObject) pointsArray.get(i);
                        String regionStr = regionObject.optString("region");
                        String[] pointArray = regionStr.split(",");

                        String[] point = pointArray[0].split(" ");

                        left = right = Double.parseDouble(point[0]);
                        top = bottom = Double.parseDouble(point[1]);

                        for (int j = 2; j < pointArray.length; j++)
                        {
                            point = pointArray[j].split(" ");
                            tmp = Double.parseDouble(point[0]);
                            if (tmp < left)
                            {
                                left = tmp;
                            }
                            else if (tmp > right)
                            {
                                right = tmp;
                            }

                            tmp = Double.parseDouble(point[1]);
                            if (tmp < bottom)
                            {
                                bottom = tmp;
                            }
                            else if (tmp > top)
                            {
                                top = tmp;
                            }
                        }
                    }
                }
                else
                {
                    String[] boundArray = boundStr.split(",");
                    Log.v(LOG_TAG,
                            boundStr
                                    + String.format("{%s, %s, %s, %s}",
                                            boundArray[0], boundArray[1],
                                            boundArray[2], boundArray[3]));
                    left = Double.parseDouble(boundArray[0]);
                    bottom = Double.parseDouble(boundArray[1]);
                    right = Double.parseDouble(boundArray[2]);
                    top = Double.parseDouble(boundArray[3]);
                }

                Log.v(LOG_TAG, String.format("bound double = {%f,%f,%f,%f}",
                        left, top, right, bottom));
                // 存放每一级影像的四个角的瓦片号，左（最小列號）、上（是小行号）、右（最大列号）、下（最大行号）
                Map<Integer, long[]> tileMap = new HashMap<Integer, long[]>();
                long tileCount = 0;
                for (int i = 5; i < 19; i++) {
                    long[] tileBound = new long[] {
                            TileUtils.getColumeNumBy84(left, i),
                            TileUtils.getRowNumBy84(top, i),
                            TileUtils.getColumeNumBy84(right, i),
                            TileUtils.getRowNumBy84(bottom, i),
                    };
                    tileMap.put(i, tileBound);
                    long tileXCount = tileBound[2] - tileBound[0];
                    long tileYCount = tileBound[3] - tileBound[1];
                    long currLevelCount = (0 == tileXCount ? 1 : tileXCount + 1)
                            * (0 == tileYCount ? 1 : tileYCount + 1);
                    tileCount += currLevelCount;

                    File levelDir = new File(TileUtils.makeDir(i));
                    if (!levelDir.isDirectory())
                    {
                        levelDir.mkdirs();
                    }

                    Log.v(LOG_TAG,
                            String.format(
                                    "level = %d, tileBound = {%d, %d, %d, %d },currLevelCount = %d, tileCount = %d",
                                    i, tileBound[0], tileBound[1],
                                    tileBound[2], tileBound[3], currLevelCount,
                                    tileCount));
                }

                String imgUrl;
                String ciaUrl;
                ResponseStream responseStream;
                InputStream input;
                FileOutputStream outPut;
                int readSize = 0;
                int fileLength = 0;
                File ciaFile;
                byte[] fileCache = new byte[CACHE_SIZE];
                File imgFile;
                long timeImgStart = 0, timeImgEnd = 0, timeCiaStart = 0, timeCiaEnd = 0;
                // 需要分别下载影像瓦片和矢量瓦片
                for (int level : tileMap.keySet())
                {
                    long[] tileBound = tileMap.get(level);
                    for (long colume = tileBound[0]; colume <= tileBound[2]; colume++)
                    {
                        for (long row = tileBound[1]; row <= tileBound[3]; row++)
                        {
                            imgUrl = TileUtils.makeImgUrl(level, colume, row);
                            ciaUrl = TileUtils.makeCiaUrl(level, colume, row);
                            Log.v(LOG_TAG, "ImgUrl = " + imgUrl);
                            Log.v(LOG_TAG, "CiaUrl = " + ciaUrl);

                            do
                            {
                                timeImgStart = System.currentTimeMillis();
                                imgFile = new File(TileUtils.makeImgFile(level, colume, row));
                                Log.v(LOG_TAG, "imgFile = " + imgFile.getPath());
                                if (imgFile.exists() && imgFile.isFile())
                                {
                                    break;
                                }
                                Log.v(LOG_TAG, "imgFile = " + imgFile.getPath());
                                outPut = new FileOutputStream(imgFile);
                                Log.v(LOG_TAG, "imgFile = " + imgFile.getPath());
                                responseStream = httpUtils.sendSync(HttpMethod.GET, imgUrl);
                                input = responseStream.getBaseStream();
                                readSize = 0;
                                fileLength = 0;

                                while ((readSize = input.read(fileCache, fileLength, CACHE_SIZE)) > 0)
                                {// 设置的缓存不够大了
                                    outPut.write(fileCache, fileLength, readSize);
                                    fileLength += readSize;
                                }

                                Log.v(LOG_TAG, String.format(
                                        "imgFile Entity length = %d write Length = %d",
                                        responseStream.getContentLength(), fileLength));

                                outPut.flush();
                                outPut.close();
                                input.close();
                                timeImgEnd = System.currentTimeMillis();
                            } while (false);

                            do
                            {
                                timeCiaStart = System.currentTimeMillis();
                                ciaFile = new File(TileUtils.makeCiaFile(level, colume, row));
                                Log.v(LOG_TAG, "ciaFile = " + ciaFile.getPath());
                                if (ciaFile.exists() && ciaFile.isFile())
                                {
                                    break;
                                }
                                Log.v(LOG_TAG, "ciaFile = " + ciaFile.getPath());
                                outPut = new FileOutputStream(ciaFile);
                                Log.v(LOG_TAG, "ciaFile = " + ciaFile.getPath());

                                responseStream = httpUtils.sendSync(HttpMethod.GET, ciaUrl);
                                input = responseStream.getBaseStream();

                                readSize = 0;
                                fileLength = 0;

                                while ((readSize = input.read(fileCache, fileLength, CACHE_SIZE)) > 0)
                                {// 设置的缓存不够大了
                                    outPut.write(fileCache, fileLength, readSize);
                                    fileLength += readSize;
                                }
                                Log.v(LOG_TAG, String.format(
                                        "ciaFile Entity length = %d write Length = %d",
                                        responseStream.getContentLength(), fileLength));

                                outPut.flush();
                                outPut.close();
                                input.close();
                                timeCiaEnd = System.currentTimeMillis();
                            } while (false);

                            long timeImg = timeImgEnd - timeImgStart;
                            long timeCia = timeCiaEnd - timeCiaStart;
                            Log.v(LOG_TAG, String.format(
                                    "each tile Time(%d) = ImgTime(%d) + CiaTime(%d)", timeImg
                                            + timeCia, timeImg, timeCia));
                        }
                    }
                }

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
