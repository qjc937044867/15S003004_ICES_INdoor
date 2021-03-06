package com.qjc.IndoorNavigation;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.Window;
import com.qjc.AStar.AStar;
import com.qjc.IMU.cap.CapDetector;
import com.qjc.IMU.cap.CapListener;
import com.qjc.IMU.localPoint.LocalisationListener;
import com.qjc.IMU.localPoint.LocalisationManager;
import com.qjc.IMU.step.IStepListener;
import com.qjc.IMU.step.StepActivity;
import com.qjc.IMU.step.StepDetector;
import com.qjc.IndoorNavigation.R;

/**
 * @ClassName: NaviPath
 * @Description: TODO 导航页面Activity
 * @author 锦春
 * @date 2015-4-15 下午3:51:11
 */
public class NaviPath extends StepActivity {
	private String s;
	private int resx;
	private int resy;// res
	private int floor;// res
	///////////////////房间坐标，应从服务器下载，本地解析////////////////////
	private int[] room5x = { 3, 12, 18, 27, 33, 42, 48, 73, 59, 48, 42, 18, 18,
			42, 48, 59, 73, 48, 42, 18, 12, 3, 7, 69, 7, 69 };
	private int[] room5y = { 6, 6, 6, 6, 6, 6, 6, 6, 13, 13, 13, 13, 35, 35,
			35, 35, 35, 42, 42, 42, 42, 42, 42, 13, 13, 35, 35 };
	private int[] room4x = { 3, 12, 18, 42, 48, 73, 59, 48, 42, 18, 18, 42, 48,
			59, 73, 48, 42, 18, 12, 3 };
	private int[] room4y = { 6, 6, 6, 6, 6, 6, 13, 13, 13, 13, 35, 35, 35, 35,
			35, 42, 42, 42, 42, 42, 42, 13 };
	private int[][] mMapView = {};
	
	MapView mapViewcon;// 地图视图
	
	int nbStep = 0;// 步数
	DecimalFormat decimalFormat = new DecimalFormat("##0.00");//构造方法的字符格式这里如果小数不足2位,会以0补足
	private SensorManager m_sensorManager;
	private Sensor m_magnetic;
	private Sensor m_accelerometer;

	CapDetector capDetector;// 方向检测器
	StepDetector stepDetector;// 步数检测器
	LocalisationManager localisationManager;// 位置管理器
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mapViewcon = new MapView(this);
		// 新页面接收数据
		Bundle bundle = this.getIntent().getExtras();
		// 接收name值
		floor = bundle.getInt("floor");
		resx = bundle.getInt("startx");
		resy = bundle.getInt("starty");
		s = bundle.getString("goal");
		System.out.println("resx=" + resx);
		System.out.println("goal=" + s);
		System.out.println("floor=" + floor);
		setContentView(mapViewcon);
		
		m_sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		m_magnetic = m_sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		m_accelerometer = m_sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		// 位置管理器
		localisationManager = new LocalisationManager(this);
		localisationManager.setLocalisationListener(new LocalisationListener() {
			@Override
			public void onNewPosition(float[] oldPosition, float[] newPosition, float stepLengh) {
				//newPosition[0]横坐标；newPosition[1]纵坐标
				String[] newPosit = new String[3];
				newPosit[0] = decimalFormat.format(newPosition[0]);
				newPosit[1] = decimalFormat.format(newPosition[1]);
				newPosit[2] = decimalFormat.format(stepLengh);
				mapViewcon.redraw(Math.round(newPosition[0]), Math.round(newPosition[1]));
			}
		});

		// 方向检测器
		capDetector = localisationManager.getCapDetector();
		capDetector.addHasChangedListener(new CapListener() {
			@Override
			public void hasChanged(float cap, float pitch, float roll) {
			}
		});

		// 脚步检测器
		stepDetector = localisationManager.getStepDetector();
		stepDetector.addStepListener(new IStepListener() {
			@Override
			public void stepDetected(float _stepLength) {
				nbStep++;//步数统计
			}
		});

		stepDetector.setAmplitudeSensibility(2.0f);
	}

	@Override
	// 程序暂停时停止监听器
	protected void onPause() {
		// unregister every sensor
		m_sensorManager.unregisterListener(capDetector, m_accelerometer);
		m_sensorManager.unregisterListener(capDetector, m_magnetic);
		stepDetector.unregisterSensors();
		super.onPause();
	}

	@Override
	// 程序激活时重启监听器
	protected void onResume() {
		// register listener
		m_sensorManager.registerListener(capDetector, m_accelerometer,
				SensorManager.SENSOR_DELAY_UI);
		m_sensorManager.registerListener(capDetector, m_magnetic,
				SensorManager.SENSOR_DELAY_UI);
		stepDetector.registerSensors();
		super.onResume();
	}

	@Override
	// 程序关闭时停止监听器
	protected void onStop() {
		// cancel register
		m_sensorManager.unregisterListener(capDetector, m_accelerometer);
		m_sensorManager.unregisterListener(capDetector, m_magnetic);
		stepDetector.unregisterSensors();
		super.onStop();
	}
	
	public class MapView extends View {

		///////////////////应从服务器下载，本地解析////////////////////
		// tile块的宽高
		public final static int TILE_WIDTH = 24;
		public final static int TILE_HEIGHT = 24;

		// tile块的宽高的数量
		public final static int TILE_WIDTH_COUNT = 49;
		public final static int TILE_HEIGHT_COUNT = 77;
		// 数组元素为0则什么都不画
		public final static int TILE_NULL = 0;
		// 第一层View地图数组
		public int[][] mMapView4 = {// f4
				{ 2, 2, 2, 2, 2, 3, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 1, 2,
						2, 2, 2, 2 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 13,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7,
						7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 31, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 34,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 69, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 70,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 79, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 80,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 16, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 24,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 8, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 2, 2, 2, 2, 2, 35, 64, 64, 64, 64, 64, 64, 64, 64, 1, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3,
						64, 64, 64, 64, 64, 64, 64, 64, 46, 2, 2, 2, 2, 2 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 2, 2, 2, 2, 2, 35, 64, 64, 64, 64, 64, 64, 64, 64, 46, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 35,
						64, 64, 64, 64, 64, 64, 64, 64, 46, 2, 2, 2, 2, 2 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 41, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 43,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 31, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 34,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 69, 7, 8,
						7, 7, 7, 7, 8, 7, 7, 8, 8, 8, 8, 7, 8, 7, 8, 7, 7, 70,
						64, 64, 64, 64, 64, 64, 64, 64, 33, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 33,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 33,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 79, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8, 7, 8, 8, 80,
						64, 64, 64, 64, 64, 64, 64, 64, 33, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 8,
						8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 16, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 24,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 8, 7,
						7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 23,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 2, 2, 2, 2, 2, 43, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 41,
						2, 2, 2, 2, 2 } };
		public int[][] mMapView5 = {// f5
				{ 2, 2, 2, 2, 2, 3, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 1, 2,
						2, 2, 2, 2 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 13,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7,
						7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 31, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 34,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 69, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 70,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 79, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 80,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 16, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 24,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 8, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 2, 2, 2, 2, 2, 35, 64, 64, 64, 64, 64, 64, 64, 64, 1, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3,
						64, 64, 64, 64, 64, 64, 64, 64, 46, 2, 2, 2, 2, 2 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 2, 2, 2, 2, 2, 35, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 2, 2, 2, 2, 2, 35, 64, 64, 64, 64, 64, 64, 64, 64, 46, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 35,
						64, 64, 64, 64, 64, 64, 64, 64, 46, 2, 2, 2, 2, 2 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 64,
						64, 64, 64, 64, 64, 64, 64, 7, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 13,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 23, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 23,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 11,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 41, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 43,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 7, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 31, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 34,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 69, 7, 8,
						7, 7, 7, 7, 8, 7, 7, 8, 8, 8, 8, 7, 8, 7, 8, 7, 7, 70,
						64, 64, 64, 64, 64, 64, 64, 64, 33, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 33,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 33,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 79, 7, 7,
						7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8, 7, 8, 8, 80,
						64, 64, 64, 64, 64, 64, 64, 64, 33, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 71, 7, 8,
						8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 62,
						64, 64, 64, 64, 64, 64, 64, 64, 11, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 13, 64, 64, 64, 64, 64, 64, 64, 64, 16, 2, 2,
						2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 24,
						64, 64, 64, 64, 64, 64, 64, 64, 13, 7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 7, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 8, 7,
						7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 23, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 23,
						7, 7, 7, 7, 8 },
				{ 7, 7, 7, 7, 7, 11, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 11,
						7, 7, 7, 7, 8 },
				{ 2, 2, 2, 2, 2, 43, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64,
						64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 41,
						2, 2, 2, 2, 2 } };

		// 第二层导航线路物理层数组
		public int[][] mMappath = new int[77][49];

		// 地图资源
		Bitmap mBitmap = null;

		// 资源文件
		Resources mResources = null;

		// 画笔
		Paint mPaint = null;

		private final Handler slowDownDrawingHandler;
		/**
		 * The thread that call the redraw
		 */
		// 调用画图线程
		private Thread background;

		// AtomicBoolean原子布尔值管理外部线程的运行
		AtomicBoolean isRunning = new AtomicBoolean(false);
		/** * An atomic boolean to manage the external thread's destruction */
		// AtomicBoolean原子布尔值管理外部线程的暂停
		AtomicBoolean isPausing = new AtomicBoolean(false);
		
		// 横向纵向tile块的数量
		int mWidthTileCount = 0;
		int mHeightTileCount = 0;

		// 横向纵向tile块的数量
		int mBitMapWidth = 0;
		int mBitMapHeight = 0;

		/**
		 * 构造方法
		 * 
		 * @param context
		 */
		@SuppressLint("HandlerLeak")
		public MapView(Context context) {
			super(context);

			mPaint = new Paint();
			mBitmap = ReadBitMap(context, R.drawable.bold);
			mBitMapWidth = mBitmap.getWidth();
			mBitMapHeight = mBitmap.getHeight();
			mWidthTileCount = mBitMapWidth / TILE_WIDTH;
			mHeightTileCount = mBitMapHeight / TILE_HEIGHT;

			slowDownDrawingHandler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					super.handleMessage(msg);
					invalidate();
				}
			};
			background = new Thread(new Runnable() {
				Message myMessage;
				// Overriden Run method
				public void run() {
					try {
						while (isRunning.get()) {
							if (isPausing.get()) {
								Thread.sleep(2000);
							} else {
								// Redraw to have 2 images a second
								Thread.sleep(500);
								// Send the message to the handler (the
								myMessage = slowDownDrawingHandler.obtainMessage();
								slowDownDrawingHandler.sendMessage(myMessage);
							}
						}
					} catch (Throwable t) {
						// just end the background thread
					}
				}
			});
			background.start();
		}

		@Override
		protected void onDraw(Canvas canvas) {
			DrawMap(canvas, mPaint, mBitmap);
			super.onDraw(canvas);
		}

		public void redraw(int x, int y) {
			if((resx-2*y) < 0 || (resx-2*y) >= 49 || (resy+2*x) < 0 || (resy+2*x)  >= 77 ) {
				mMappath[resx][resy] = 52;
			}
			else {
			   mMappath[resx-2*y][resy+2*x] = 52;
			}
			invalidate();			
		}
		
		private void DrawMap(Canvas canvas, Paint paint, Bitmap bitmap) {
			int flag = 0;
			int sx = 0, sy = 6;// temp
			///////////////////应从服务器下载，本地解析////////////////////
			int temp = Integer.valueOf(s).intValue();
			switch (floor) {
			case 4:
				mMapView = mMapView4;
				sx = room4x[temp - 401];
				sy = room4y[temp - 401];
				break;
			case 5:
				mMapView = mMapView5;
				sx = room5x[temp - 501];
				sy = room5y[temp - 501];
				break;
			}
			AStar aStar = new AStar(mMapView, 49, 77);
			for (int x = 0; x < 77; x++)
				for (int y = 0; y < 49; y++)
					if (mMappath[x][y] != 52) 
					mMappath[x][y] = 0;
			flag = aStar.search(resx, resy, sx, sy);
			// 起点横坐标，纵坐标，终点横坐标，纵坐标
			if (flag == -1) {
				System.out.println("数据存在错误！");
			} else if (flag == 0) {
				System.out.println("没有符合要求的路径！");
			} else {
				for (int x = 0; x < 77; x++) {
					for (int y = 0; y < 49; y++) {
						if (mMapView[x][y] == -1) {
							mMappath[x][y] = 51;
						}
					}
				}
			}
			mMappath[resx][resy] = 56;// 起点图标
			mMappath[sx][sy] = 55;
			DrawMaptc(canvas, paint, bitmap);
		}

		/**
		 * 绘制图层
		 */
		private void DrawMaptc(Canvas canvas, Paint paint, Bitmap bitmap) {
			int i, j;
			for (i = 0; i < TILE_HEIGHT_COUNT; i++) {
				for (j = 0; j < TILE_WIDTH_COUNT; j++) {
					int ViewID = mMapView[i][j];
					int PathID = mMappath[i][j];
					// 绘制地图第一层
					if (ViewID > TILE_NULL) {
						DrawMapTile(ViewID, canvas, paint, bitmap, j
								* TILE_WIDTH, i * TILE_HEIGHT);
					}
					// 绘制地图导航路径
					if (PathID > TILE_NULL) {
						DrawMapTile(PathID, canvas, paint, bitmap, j
								* TILE_WIDTH, i * TILE_HEIGHT);
					}
				}
			}
		}

		/**
		 * 根据ID绘制一个tile块
		 * 
		 * @param id
		 * @param canvas
		 * @param paint
		 * @param bitmap
		 */
		private void DrawMapTile(int id, Canvas canvas, Paint paint,
				Bitmap bitmap, int x, int y) {
			// 根据数组中的ID算出在地图资源中的XY 坐标
			// 因为编辑器默认0 所以第一张tile的ID不是0而是1 所以这里 -1
			id--;
			int count = id / mWidthTileCount;
			int bitmapX = (id - (count * mWidthTileCount)) * TILE_WIDTH;
			int bitmapY = count * TILE_HEIGHT;
			DrawClipImage(canvas, paint, bitmap, x, y, bitmapX, bitmapY,
					TILE_WIDTH, TILE_HEIGHT);
		}

		/**
		 * 读取本地资源的图片
		 * 
		 * @param context
		 * @param resId
		 * @return
		 */
		public Bitmap ReadBitMap(Context context, int resId) {
			BitmapFactory.Options opt = new BitmapFactory.Options();
			opt.inPreferredConfig = Bitmap.Config.RGB_565;
			opt.inPurgeable = true;
			opt.inInputShareable = true;
			// 获取资源图片
			InputStream is = context.getResources().openRawResource(resId);
			return BitmapFactory.decodeStream(is, null, opt);
		}

		/**
		 * 绘制图片中的一部分图片
		 * 
		 * @param canvas
		 * @param paint
		 * @param bitmap
		 * @param x
		 * @param y
		 * @param src_x
		 * @param src_y
		 * @param src_width
		 * @param src_Height
		 */
		private void DrawClipImage(Canvas canvas, Paint paint, Bitmap bitmap,
				int x, int y, int src_x, int src_y, int src_xp, int src_yp) {
			canvas.save();
			canvas.clipRect(x, y, x + src_xp, y + src_yp);
			canvas.drawBitmap(bitmap, x - src_x, y - src_y, paint);
			canvas.restore();
		}

	}
}
