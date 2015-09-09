package com.petra.tdttiledownload;

public class TileUtils {

	static String URL = "http://t1.tianditu.cn/DataServer?T=%s";
	// URL = "http://t1.tianditu.cn/DataServer?T=img_w&X=843&Y=388&l=10"

	static String baseImgName = "img_w&X=%d&Y=%d&l=%d";
	String baseCiaName = "cia_w&X=%d&Y=%d&l=%d";

	static String baseTilePath = "/home/petra/python/tile/beijing/%d/%s.map";
	String path = "e:/tmp/cacheMap/img_w&X=843&Y=388&l=10.map";

	// 每张瓦片的像素
	static int pixelPerTile = 256;

	// 实际上是0级时，墨卡托的整体长度显示到一张瓦片上，瓦片的像素是256*256
	// 基础分辨率就是米每像素。
	static double baseResolution = 20037508.3427892 * 2 / pixelPerTile;

	// 墨卡托原点（-20037508.3427892， 20037508.3427892）
	static double[] origin = { -20037508.3427892, 20037508.3427892 };

	// 计算瓦片的列号, 传入WGS84的经度
	static public int getColumeNumBy84(double x, int level) {
		double meterPerTile = getMeterPerTile(level);
		return (int) Math.ceil((lon2Mercator(x) - origin[0]) / meterPerTile);
	}

	// 计算瓦片的行号, 传入WGS84的纬度
	static public int getRowNumBy84(double y, int level) {
		double meterPerTile = getMeterPerTile(level);
		return (int) Math.ceil((origin[1] - lat2Mercator(y)) / meterPerTile);
	}

	private static double getMeterPerTile(int level) {
		double currResolution = baseResolution / Math.pow(2, level);
		double meterPerTile = currResolution * pixelPerTile;
		return meterPerTile;
	}

	private static String makeTileUrl(int level, int colume, int row) {
		String tilename = String.format(baseImgName, colume, row, level);
		return String.format(URL, String.format(baseTilePath, level, tilename));
	}

	// WSG84经度转Mercator横坐标
	static double lon2Mercator(double lon) {
		return lon * 20037508.342789 / 180;
	}

	// WSG84纬度转Mercator纵坐标
	static double lat2Mercator(double lat) {
		double y = Math.log(Math.tan((90 + lat) * Math.PI / 360))
				/ (Math.PI / 180);
		y *= 20037508.34789 / 180;
		return y;
	}
}
