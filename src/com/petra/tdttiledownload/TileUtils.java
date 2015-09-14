
package com.petra.tdttiledownload;

public class TileUtils {

    static String imgURL = "http://t1.tianditu.cn/DataServer?T=img_w&X=%d&Y=%d&l=%d";
    static String ciaURL = "http://t1.tianditu.cn/DataServer?T=cia_w&X=%d&Y=%d&l=%d";
    // URL = "http://t1.tianditu.cn/DataServer?T=img_w&X=843&Y=388&l=10"

    static String baseDir = "/sdcard/zmn/cache/%d";
    static String imgFilePath = baseDir + "/img_w&X=%d&Y=%d&z=%d.map";
    static String ciaFilePath = baseDir + "/cia_w&X=%d&Y=%d&z=%d.map";

    // 每张瓦片的像素
    static int pixelPerTile = 256;

    // 实际上是0级时，墨卡托的整体长度显示到一张瓦片上，瓦片的像素是256*256
    // 基础分辨率就是米每像素。
    static double baseResolution = 20037508.3427892 * 2 / pixelPerTile;

    // 墨卡托原点（-20037508.3427892， 20037508.3427892）
    static double[] origin = {
            -20037508.3427892, 20037508.3427892
    };

    // 计算瓦片的列号, 传入WGS84的经度
    static public int getColumeNumBy84(double lon, int level) {
        double meterPerTile = getMeterPerTile(level);
        double x = lon * 20037508.342789 / 180;
        return (int) Math.floor((x - origin[0]) / meterPerTile);
    }

    // 计算瓦片的行号, 传入WGS84的纬度
    static public int getRowNumBy84(double lat, int level) {
        double meterPerTile = getMeterPerTile(level);
        double y = Math.log(Math.tan((90 + lat) * Math.PI / 360))
                / (Math.PI / 180);
        y *= 20037508.34789 / 180;
        return (int) Math.floor((origin[1] - y) / meterPerTile);
    }

    private static double getMeterPerTile(int level) {
        double currResolution = baseResolution / Math.pow(2, level);
        double meterPerTile = currResolution * pixelPerTile;
        return meterPerTile;
    }

    public static String makeImgUrl(long level, long colume, long row) {
        return String.format(imgURL, colume, row, level);

    }

    public static String makeCiaUrl(long level, long colume, long row) {
        return String.format(ciaURL, colume, row, level);
    }

    public static String makeImgFile(long level, long colume, long row)
    {
        return String.format(imgFilePath, level, colume, row, level);
    }

    public static String makeCiaFile(long level, long colume, long row)
    {
        return String.format(ciaFilePath, level, colume, row, level);
    }

    public static String makeDir(int level)
    {
        return String.format(baseDir, level);
    }

}
