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

	// 计算瓦片的行列号，返回下载瓦片的行列号
	static public int[] getRowAndColume(double[] xy, int level) {
		// '输入墨卡托坐标x，y，和需要计算的级别，返回包含当前坐标的瓦片的行列号'
		double currResolution = baseResolution / Math.pow(2, level);
		double meterPerTile = currResolution * pixelPerTile;
		int colume = (int) Math.ceil((xy[0] - origin[0]) / meterPerTile);
		int row = (int) Math.ceil((origin[1] - xy[1]) / meterPerTile);
		return new int[] { row, colume };
	}

	private static String makeTileUrl(int level, double colume, double row) {
		String tilename = String.format(baseImgName, colume, row, level);
		return String.format(URL, String.format(baseTilePath, level, tilename));
	}

	static public double[] lonLat2Mercator(double lon, double lat) {
		double x = lon * 20037508.342789 / 180;
		double y = Math.log(Math.tan((90 + lat) * Math.PI / 360))
				/ (Math.PI / 180);
		y *= 20037508.34789 / 180;
		return new double[] { x, y };
	}
}
