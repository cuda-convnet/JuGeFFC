package cn.com.magnity.coresdksample;

import android.Manifest;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


import com.bumptech.glide.Glide;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;

import cn.com.magnity.coresdk.MagDevice;
import cn.com.magnity.coresdk.types.EnumInfo;
import cn.com.magnity.coresdksample.Util.photoUtil;

import static cn.com.magnity.coresdksample.Util.FFCUtil.getFFC;

public class MainActivity extends AppCompatActivity implements MagDevice.ILinkCallback {
    //const
    private static final int START_TIMER_ID = 0;
    private static final int TIMER_INTERVAL = 500;//ms

    private static final int STATUS_IDLE = 0;
    private static final int STATUS_LINK = 1;
    private static final int STATUS_TRANSFER = 2;

    private static final String STATUS_ARGS = "status";
    private static final String USBID_ARGS = "usbid";

    //non-const
    private MagDevice mDev;

    private ArrayList<EnumInfo> mDevices;
    private ArrayList<String> mDeviceStrings;
    private ArrayAdapter mListAdapter;
    private EnumInfo mSelectedDev;
    private ListView mDevList;
    private Button mLinkBtn;
    private Button mPlayBtn;
    private Button mCalibrationBtn;
    private Button mCurrentBtn;
    private Button mFFCBtn;
    private Button mAfterBtn;
    private TextView mTextSelectedDevice;
    private Handler mEnumHandler;
    private Handler mRestoreHandler;
    private Runnable mRestoreRunnable;
    private VideoFragment mVideoFragment;

    private ImageView iv_origin,iv_FFC,iv_after,iv_current;
    private TextView tv_origin,tv_FFC,tv_after,tv_current;



    private int mDegree;//0 - 90, 1 - 180, 2 - 270

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initTemp(savedInstanceState);

        /* init ui */
        initUi();

        initMyUi();

    }

    private void initMyUi() {
        iv_origin= (ImageView) findViewById(R.id.iv_origin);
        iv_FFC= (ImageView) findViewById(R.id.iv_FFC);
        iv_after= (ImageView) findViewById(R.id.iv_after);
        iv_current= (ImageView) findViewById(R.id.iv_current);

        tv_origin=(TextView)findViewById(R.id.tv_origin);
        tv_FFC=(TextView)findViewById(R.id.tv_FFC);
        tv_after=(TextView)findViewById(R.id.tv_after);
        tv_current=(TextView)findViewById(R.id.tv_current);



    }

    private void initTemp(Bundle savedInstanceState) {

        /* global init */
        MagDevice.init(this);

        /* new object */
        mDev = new MagDevice();
        mDevices = new ArrayList<>();
        mDeviceStrings = new ArrayList<>();
        mListAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_expandable_list_item_1, mDeviceStrings);

        /* enum timer handler */
        mEnumHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case START_TIMER_ID:
                        mEnumHandler.removeMessages(START_TIMER_ID);
                        updateDeviceList();
                        mEnumHandler.sendEmptyMessageDelayed(START_TIMER_ID, TIMER_INTERVAL);
                        break;
                }
            }
        };

        /* start timer */
        mEnumHandler.sendEmptyMessage(START_TIMER_ID);

        /* runtime permit */
        Utils.requestRuntimePermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE, 0, R.string.writeSDPermission);

        /* restore parameter */
        if (savedInstanceState != null) {
            final int usbId = savedInstanceState.getInt(USBID_ARGS);
            final int status = savedInstanceState.getInt(STATUS_ARGS);
            mRestoreHandler = new Handler();
            mRestoreRunnable = new Runnable() {
                @Override
                public void run() {
                    restore(usbId, status);
                }
            };

            /* restore after all ui component created */
            /* FIXME */
            mRestoreHandler.postDelayed(mRestoreRunnable, 200);
        }

    }

    private void initUi() {
        mDevList = (ListView)findViewById(R.id.listDev);
        mDevList.setAdapter(mListAdapter);
        mDevList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                EnumInfo dev = mDevices.get(position);
                if (mSelectedDev == null ||
                        mSelectedDev.id != dev.id ||
                        !mDev.isLinked()) {
                    mDev.dislinkCamera();
                    mSelectedDev = dev;
                    mTextSelectedDevice.setText(mSelectedDev.name);
                    updateButtons();
                }
            }
        });

        MagOnClickListener listener = new MagOnClickListener();
        mLinkBtn = (Button)findViewById(R.id.btnLink);
        mLinkBtn.setOnClickListener(listener);
        mPlayBtn = (Button)findViewById(R.id.btnPlay);
        mPlayBtn.setOnClickListener(listener);
        mCalibrationBtn = (Button)findViewById(R.id.btnOrigin);
        mCalibrationBtn.setOnClickListener(listener);
        mCurrentBtn = (Button)findViewById(R.id.btnCunrrent);
        mCurrentBtn.setOnClickListener(listener);
        mFFCBtn = (Button)findViewById(R.id.btnFFC);
        mFFCBtn.setOnClickListener(listener);
        mAfterBtn = (Button)findViewById(R.id.btnafter);
        mAfterBtn.setOnClickListener(listener);
        mTextSelectedDevice = (TextView) findViewById(R.id.tvSelectedName);

        updateButtons();

        FragmentManager fm = getSupportFragmentManager();
        mVideoFragment = (VideoFragment)fm.findFragmentById(R.id.videoLayout);
        if (mVideoFragment == null) {
            mVideoFragment = new VideoFragment();
            fm.beginTransaction().add(R.id.videoLayout, mVideoFragment).commit();
        }



    }

    private void restore(int usbId, int status) {
        /* restore list status */
       MagDevice.getDevices(this, 33596, 1, mDevices);

        mDeviceStrings.clear();
        for (EnumInfo dev : mDevices) {
            if (dev.id == usbId) {
                mSelectedDev = dev;
            }
            mDeviceStrings.add(dev.name);
        }
        if (mSelectedDev == null) {
            return;
        }

        mTextSelectedDevice.setText(mSelectedDev.name);

        /* restore camera status */
        switch (status) {
            case STATUS_IDLE:
                //do nothing
                break;
            case STATUS_LINK:
                mDev.linkCamera(MainActivity.this, mSelectedDev.id, MainActivity.this);
                updateButtons();
                break;
            case STATUS_TRANSFER:
                int r = mDev.linkCamera(MainActivity.this, mSelectedDev.id,
                    new MagDevice.ILinkCallback() {
                        @Override
                        public void linkResult(int result) {
                            if (result == MagDevice.CONN_SUCC) {
                            /* 连接成功 */
                                play();
                            } else if (result == MagDevice.CONN_FAIL) {
                            /* 连接失败 */
                            } else if (result == MagDevice.CONN_DETACHED) {
                            /* 连接失败*/
                            }
                            updateButtons();
                        }
                    });

                if (r == MagDevice.CONN_SUCC) {
                    play();
                }
                updateButtons();
                break;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        /* save parameter for restore when screen rotating */
        int status = STATUS_IDLE;
        if (mDev.isProcessingImage()) {
            status = STATUS_TRANSFER;
        } else if (mDev.isLinked()) {
            status = STATUS_LINK;
        }
        outState.putInt(STATUS_ARGS, status);
        if (mSelectedDev != null) {
            outState.putInt(USBID_ARGS, mSelectedDev.id);
        }
    }

    private void updateDeviceList() {
        MagDevice.getDevices(this, 33596, 1, mDevices);

        mDeviceStrings.clear();
        for (EnumInfo dev : mDevices) {
            mDeviceStrings.add(dev.name);
        }

        mListAdapter.notifyDataSetChanged();
    }

    private void updateButtons() {
        if (mDev.isProcessingImage()) {
            mLinkBtn.setEnabled(false);
            mPlayBtn.setEnabled(false);
            mCalibrationBtn.setEnabled(true);
            mCurrentBtn.setEnabled(true);
            mFFCBtn.setEnabled(true);
            mAfterBtn.setEnabled(true);
        } else if (mDev.isLinked()) {
            mLinkBtn.setEnabled(false);
            mPlayBtn.setEnabled(true);
            mCalibrationBtn.setEnabled(true);
            mCurrentBtn.setEnabled(true);
            mFFCBtn.setEnabled(true);
            mAfterBtn.setEnabled(false);
        } else {
            mLinkBtn.setEnabled(mSelectedDev!=null);
            mPlayBtn.setEnabled(false);
            mCalibrationBtn.setEnabled(false);
            mCurrentBtn.setEnabled(false);
            mFFCBtn.setEnabled(false);
            mAfterBtn.setEnabled(false);
        }
    }

    @Override
    protected void onDestroy() {
        /* remove pending messages */
        mEnumHandler.removeCallbacksAndMessages(null);
        if (mRestoreHandler != null) {
            mRestoreHandler.removeCallbacksAndMessages(null);
            mRestoreRunnable = null;
            mRestoreHandler = null;
        }

        /* disconnect camera when app exited */
        if (mDev.isProcessingImage()) {
            mDev.stopProcessImage();
            mVideoFragment.stopDrawingThread();
        }
        if (mDev.isLinked()) {
            mDev.dislinkCamera();
        }
        mDev = null;
        super.onDestroy();
    }

    private void play() {
        mDev.setColorPalette(MagDevice.ColorPalette.PaletteIronBow);
        if (mDev.startProcessImage(mVideoFragment, 0, 0)) {
            mVideoFragment.startDrawingThread(mDev);
        }
    }

    @Override
    public void linkResult(int result) {
        if (result == MagDevice.CONN_SUCC) {
            /* 连接成功 */
        } else if (result == MagDevice.CONN_FAIL) {
            /* 连接失败 */
        } else if (result == MagDevice.CONN_DETACHED) {
            /* 设备拔出*/
        }
        updateButtons();
    }

    private class MagOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.btnLink:
                    mDev.linkCamera(MainActivity.this, mSelectedDev.id, MainActivity.this);
                    updateButtons();
                    break;
                case R.id.btnPlay:
                    play();
                    updateButtons();
                    break;
                case R.id.btnOrigin://获得原始数据图像。
                   /* mDev.stopProcessImage();
                    mVideoFragment.stopDrawingThread();
                    updateButtons();*/
                    // bitmap= BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher);
                    Origin();
                    Current();


                    break;
                case R.id.btnCunrrent://获得当前视频流图像
                   /* mDev.dislinkCamera();
                    mVideoFragment.stopDrawingThread();
                    mDegree = 0;
                    updateButtons();*/
                   Current();


                    break;
                case R.id.btnFFC://获取FFC图像
                 /*   mDegree++;
                    if (mDegree > 3) {
                        mDegree = 0;
                    }
                    mDev.stopProcessImage();
                    mVideoFragment.stopDrawingThread();
                    mDev.setImageTransform(0, mDegree);
                    play();*/
                    int []temps=Origin();
                    Current();
                    FFC(temps);

                    break;
                case R.id.btnafter:

                    break;
            }
            mDevList.requestFocus();
        }
    }

    private void FFC(int[] temps) {//从原始数据图中计算出FFC校准图
        int []temp;
        temp=getFFC(temps);
        final Bitmap bitmaps;
        bitmaps=photoUtil.CovertToBitMap(temp,0,100000);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmaps.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] bytes=baos.toByteArray();
                Glide.with(MainActivity.this).load(bytes).into(iv_FFC);
            }
        });


    }

    private void Current() {//获得当前图像
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            return;
        }
        File file = Environment.getExternalStorageDirectory();
        if (null == file) {
            return;
        }
        /*file = new File(file, "magnity/mx/media/");*/
        file = new File(file, "CurrentImg");
        if (!file.exists()) {
            file.mkdirs();
        }
        final String paths= System.currentTimeMillis() + ".bmp";
        //final String paths=  "Current.bmp";
        File files=new File(file,paths);
        if(files.exists()){
            files.delete();
            Log.i("delete", "delete: ");
        }
        final String path=file.getAbsolutePath() + File.separator + paths;
        //保存图片
        if (mDev.saveBMP(0, path)) {
            Toast.makeText(MainActivity.this, file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
        }
        final File finalFile = file;
        Runnable runnable=new Runnable() {
            @Override
            public void run() {
                File fileIv = new File(finalFile, paths);
                Glide.with(MainActivity.this).load(fileIv).into(iv_current);
            }
        };
        //加载图片
        myHander hander=new myHander();
        hander.postDelayed(runnable,200);
    }
   private class myHander extends Handler{
       @Override
       public void handleMessage(Message msg) {
           super.handleMessage(msg);

       }
   }
    private int[] Origin() {//获得原始图片
        mDev.lock();
        int[] temps = new int[160*120];
        mDev.getTemperatureData(temps,false,false);
        mDev.unlock();
        final Bitmap bitmap;
        bitmap=photoUtil.CovertToBitMap(temps,0,100);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] bytes=baos.toByteArray();
                Glide.with(MainActivity.this).load(bytes).into(iv_origin);
            }
        });
        return temps;
    }
}
