
package com.petra.tdttiledownload;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    public static final int UPDATE_PROGRESS = 0;

    private static final long EXECUTOR_WAIT_TIME = 30;// 秒
    String LOG_TAG = MainActivity.class.getSimpleName();

    @ViewInject(R.id.start_download)
    Button startDownload;
    @ViewInject(R.id.et_region_name)
    EditText etRegionName;
    @ViewInject(R.id.et_start_level)
    EditText etStartLevel;
    @ViewInject(R.id.et_end_level)
    EditText etEndLevel;
    @ViewInject(R.id.progressBar)
    ProgressBar progress;
    @ViewInject(R.id.progressText)
    TextView progressText;

    long totalTileCount = 0;// 瓦片的个数，一组img+cia为一个。
    volatile long tileCounter = 0;// 已经完成的瓦片的计数器。
    long startTime = 0;
    final ReentrantLock downloadLock = new ReentrantLock();
    private String regionName;
    int startLevel, endLevel;

    private int coreCount;

    BlockingQueue<long[]> tileQueue = new LinkedBlockingQueue<long[]>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViewUtils.inject(this);
        coreCount = Runtime.getRuntime().availableProcessors();
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
        executor = new ThreadPoolExecutor(coreCount * 2,
                coreCount * 3, EXECUTOR_WAIT_TIME, TimeUnit.SECONDS, workQueue);
    }

    Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what)
            {
                case UPDATE_PROGRESS:
                    long currTime = System.currentTimeMillis();
                    long duration = (currTime - startTime) / 1000;
                    long durationM = duration / 60;
                    long durationS = duration % 60;

                    String progressStr = String
                            .format(
                                    "正在下载<%s>的影像，从%d级到%d级，总瓦片数为：%d张，共启动下载子线程：%d，其中%d个线程正在运行，缓存队列中有%d个在等待的事务，已经下载的瓦片数为：%d张。耗时：%d分%d秒。",
                                    regionName, startLevel, endLevel, totalTileCount,
                                    executor.getCorePoolSize(), executor.getActiveCount(),
                                    executor.getQueue().size(), tileCounter, durationM, durationS);
                    progressText.setText(progressStr);
                    int currProgress = (int) ((tileCounter * 1.0) / totalTileCount) / 100;
                    progress.setProgress(currProgress);
                    break;
            }
            // AlertDialog.Builder builder = new
            // AlertDialog.Builder(MainActivity.this);
            // builder.setTitle("下载完成").setMessage((String) msg.obj).show();
            super.handleMessage(msg);
        }
    };

    private ThreadPoolExecutor executor;

    @OnClick({
            R.id.start_download,
    })
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_download:
                regionName = etRegionName.getText().toString();
                startLevel = Integer.parseInt(etStartLevel.getText().toString());
                endLevel = Integer.parseInt(etEndLevel.getText().toString());
                DownloadThread downloadThread = new DownloadThread();
                downloadThread.start();
                break;
        }
    }

    class DownloadThread extends Thread {
        private ResponseStream responseStream;

        @Override
        public void run() {

            try {
                long totalStart = System.currentTimeMillis();
                startTime = totalStart;
                HttpUtils httpUtils = new HttpUtils();
                RequestParams params = new RequestParams();
                String paramBody = String
                        .format("{\"keyWord\":\"%s\",\"level\":\"11\",\"mapBound\":\"-180,-90,180,90\",\"queryType\":\"1\",\"count\":\"10\",\"start\":\"0\"}",
                                regionName);
                params.addBodyParameter("jsonStr", paramBody);
                // String url =
                // "http://api.tianditu.com/api/api-new/query.do?jsonStr={\"keyWord\":\"海淀区\",\"level\":\"11\",\"mapBound\":\"-180,-90,180,90\",\"queryType\":\"1\",\"count\":\"10\",\"start\":\"0\"}";
                String url = "http://api.tianditu.com/api/api-new/query.do?";
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
                for (int i = startLevel; i <= endLevel; i++) {
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
                    totalTileCount += currLevelCount;

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
                                    totalTileCount));
                }

                // 需要分别下载影像瓦片和矢量瓦片
                for (int level : tileMap.keySet())
                {
                    long[] tileBound = tileMap.get(level);
                    for (long colume = tileBound[0]; colume <= tileBound[2]; colume++)
                    {
                        for (long row = tileBound[1]; row <= tileBound[3]; row++)
                        {
                            // executor.execute(new SubDownload(level, colume,
                            // row));
                            // Thread.sleep(5);
                            // 这里使用线程池时崩溃了，崩溃在向线程池缓存中增加runnable到898个时，抛出异常java.lang.OutOfMemoryError:
                            // pthread_create
                            // (stack size 16384 bytes) failed: Try again
                            // pthread_create (stack size 16384 bytes) failed:
                            // Try again
                            // 09-14 00:06:15.913: E/AndroidRuntime(14088):
                            // FATAL EXCEPTION: Thread-898

                            tileQueue.add(new long[] {
                                    level, colume, row
                            });

                        }
                    }
                }

                for (int i = 0; i < coreCount * 3; i++)
                {
                    new SubDownload().start();
                }

                while (!tileQueue.isEmpty())
                {
                    Thread.yield();
                }
                long totalEnd = System.currentTimeMillis();
                long duration = (totalEnd - totalStart) / 1000;
                long durationM = duration / 60;
                long durationS = duration % 60;
                String msgStr = String.format("%s的瓦片下载已经完成,从%d级到%d级，共下载瓦片%d张，下载耗时%d分%d秒",
                        regionName, startLevel, endLevel, totalTileCount * 2,
                        durationM, durationS);
                Message.obtain(handler, 0, msgStr).sendToTarget();
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

    class SubDownload extends Thread {

        private String imgUrl;
        private String ciaUrl;
        private File imgFile;
        private File ciaFile;
        private HttpUtils httpUtils;
        ResponseStream responseStream;
        InputStream input;
        FileOutputStream outPut;
        int readSize = 0;
        int fileLength = 0;
        private static final int CACHE_SIZE = 30 * 1024;
        byte[] fileCache = new byte[CACHE_SIZE];
        long timeImgStart = 0, timeImgEnd = 0, timeCiaStart = 0, timeCiaEnd = 0;

        // public SubDownload(int level, long colume, long row) {
        // imgUrl = TileUtils.makeImgUrl(level, colume, row);
        // ciaUrl = TileUtils.makeCiaUrl(level, colume, row);
        // imgFile = new File(TileUtils.makeImgFile(level, colume, row));
        // ciaFile = new File(TileUtils.makeCiaFile(level, colume, row));
        // httpUtils = new HttpUtils();
        //
        // Log.v(LOG_TAG, "ImgUrl = " + imgUrl);
        // Log.v(LOG_TAG, "CiaUrl = " + ciaUrl);
        // Log.v(LOG_TAG, "imgFile = " + imgFile.getPath());
        // Log.v(LOG_TAG, "ciaFile = " + ciaFile.getPath());
        // }
        public boolean initFile()
        {

            final ReentrantLock lock = downloadLock;
            lock.lock();
            long[] tileParam = tileQueue.poll();
            lock.unlock();
            if (null == tileParam)
            {
                return false;
            }
            long level = tileParam[0];
            long colume = tileParam[1];
            long row = tileParam[2];
            imgUrl = TileUtils.makeImgUrl(level, colume, row);
            ciaUrl = TileUtils.makeCiaUrl(level, colume, row);
            imgFile = new File(TileUtils.makeImgFile(level, colume, row));
            ciaFile = new File(TileUtils.makeCiaFile(level, colume, row));

            Log.v(LOG_TAG, "ImgUrl = " + imgUrl);
            Log.v(LOG_TAG, "CiaUrl = " + ciaUrl);
            Log.v(LOG_TAG, "imgFile = " + imgFile.getPath());
            Log.v(LOG_TAG, "ciaFile = " + ciaFile.getPath());
            return true;
        }

        @Override
        public void run() {

            httpUtils = new HttpUtils();
            while (initFile())
            {
                try
                {
                    do {
                        timeImgStart = System.currentTimeMillis();
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

                        while ((readSize = input.read(fileCache, 0, CACHE_SIZE)) > 0)
                        {// 设置的缓存不够大了
                            outPut.write(fileCache, 0, readSize);
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

                        while ((readSize = input.read(fileCache, 0, CACHE_SIZE)) > 0)
                        {// 设置的缓存不够大了
                            outPut.write(fileCache, 0, readSize);
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

                    final ReentrantLock localLock = downloadLock;
                    localLock.lock();
                    tileCounter++;
                    localLock.unlock();
                    Message.obtain(handler, UPDATE_PROGRESS).sendToTarget();

                } catch (HttpException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
}
