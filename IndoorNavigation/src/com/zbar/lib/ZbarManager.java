package com.zbar.lib;

/**
 * @ClassName: ZbarManager 
 * @Description: TODO ZBAR����
 * @author ���� 
 * @date 2015-3-23 ����4:07:33
 */
public class ZbarManager {

	static {
		System.loadLibrary("zbar");
	}

	public native String decode(byte[] data, int width, int height, boolean isCrop, int x, int y, int cwidth, int cheight);
}

