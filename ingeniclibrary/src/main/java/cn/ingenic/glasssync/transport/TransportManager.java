package cn.ingenic.glasssync.transport;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.ingenic.glasssync.DefaultSyncManager;
import cn.ingenic.glasssync.DefaultSyncManager.OnChannelCallBack;
import cn.ingenic.glasssync.Enviroment;
import cn.ingenic.glasssync.LogTag;
import cn.ingenic.glasssync.LogTag.Transport;
import cn.ingenic.glasssync.SyncSerializable;
import cn.ingenic.glasssync.data.ProjoList;
import cn.ingenic.glasssync.transport.ext.TransportManagerClient;
import cn.ingenic.glasssync.transport.ext.TransportManagerServer;

public class TransportManager {

    static final int CHANNEL_CMD = 0x00;
    static final int CHANNEL_SPEICAL = 0x01;
    static final int CHANNEL_DATA = 0x2;
    static final int CHANNEL_FILE = 0x3;

    public static final int CMD = CHANNEL_CMD;
    public static final int SPEICAL = CHANNEL_SPEICAL;
    public static final int DATA = CHANNEL_DATA;
    public static final int FILE = CHANNEL_FILE;
    public static final int CHANNEL_MIN = CMD;

	private MyHandler mHandler;
	protected final Context mContext;
	private FileChannelManager mFileChannelManager;
	private static TransportManager sManager;
	protected OnRetriveCallback mCallback;

	public interface OnRetriveCallback {
		void onRetrive(SyncSerializable serial);
	}
	public void setRetriveCallback(OnRetriveCallback callback) {
		mCallback = callback;
	}
	public static TransportManager init(Context context, String system,
                                        Handler managerHandler) {
	    if (sManager == null) {
			if (LogTag.V) {
				Transport.d("create Manager.");
			}
			if (Enviroment.getDefault().isWatch()) {
				Log.d("TransportManager", "TransportManagerServer");
				sManager = new TransportManagerServer(context, system, managerHandler);
			} else {
				Log.d("TransportManager", "TransportManagerClient");
				sManager = new TransportManagerClient(context, system, managerHandler);
			}
		}
	    return sManager;
	}
	
	private final String mSystemModuleName;
	static String getSystemMoudleName() {
		return sManager.mSystemModuleName;
	}
	
	public static TransportManager getDefault() {
		if (sManager == null) {
			throw new
                    NullPointerException("TransportManager must be inited before getDefault().");
		}
		
		return sManager;
	}
	
	private volatile boolean mTimeoutMsgLock = false;
	private final PowerManager.WakeLock mWakeLock;
	static void sendTimeoutMsg() {
		Handler handler = sManager.mHandler;
		if (handler.hasMessages(MSG_TIME_OUT)) {
			handler.removeMessages(MSG_TIME_OUT);
		}
		
		synchronized (sManager.mWakeLock) {
			if (!sManager.mWakeLock.isHeld()) {
				Transport.i("acquire WakeLock.");
				sManager.mWakeLock.acquire();
			} else {
				Transport.v("WakeLock already acquire.");
			}
		}
		
		if (!sManager.mTimeoutMsgLock) {
			Transport.v("send timeout msg");
			handler.sendMessageDelayed(handler.obtainMessage(MSG_TIME_OUT),
					DefaultSyncManager.TIMEOUT);
		} else {
			Transport.w("Time out msg locked, do not send time out msg.");
		}
	}
	
	private static final int CORE_POOL_SIZE = 5;
    private static final int MAXIMUM_POOL_SIZE = 16;
    private static final int KEEP_ALIVE = 1;
	
	private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(10);
	
	private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "TransportManager #" + mCount.getAndIncrement());
        }
    };

    public static final Executor THREAD_POOL_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                    TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);
	
	static final int MSG_BASE = 0;
	static final int MSG_TIME_OUT = MSG_BASE + 3;
	static final int MSG_FILE_CHANNEL_CLOSE = MSG_BASE + 4;
	
	protected final Handler mMgrHandler;
      //private TransportStateMachine mStateMachine;
	
	protected TransportManager(Context context, String sysetmModuleName, Handler mgrHandler){
		Transport.w("TransportManager created.");
		mContext = context;
		mSystemModuleName = sysetmModuleName;
		mMgrHandler = mgrHandler;
		Transport.w("PowerManager created.");
		PowerManager powerMgr = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
		mWakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"TransportManager");
		mWakeLock.setReferenceCounted(false);
		
		  mHandler = new MyHandler();		
		Transport.w("init() created.");
	}	
	
	protected void init() {
	}
	
	protected final void releaseWakeLock() {
		synchronized (mWakeLock) {
			if (mWakeLock.isHeld()) {
				Transport.i("release WakeLock");
				mWakeLock.release();
			} else {
				Transport.v("WakeLock not locked");
			}
		}
	}
	
	@SuppressLint("HandlerLeak")
	private class MyHandler extends Handler {
		
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_TIME_OUT:
				if (mTimeoutMsgLock) {
					Transport.w("Time out msg locked, do not send msg.");
					return;
				}
				
				releaseWakeLock();
				
				Transport.i("MSG_TIME_OUT msg comes, send disconnect msg");
				prepare("");
//				Message mgrMsg = mMgrHandler.obtainMessage(DefaultSyncManager.MSG_TIME_OUT);
//				mgrMsg.sendToTarget();
				break;
				
			case MSG_FILE_CHANNEL_CLOSE:
				Transport.d("File Channel close, release time out msg Lock");
				mTimeoutMsgLock = false;
				sendTimeoutMsg();
				break;
				
			}
			
		}
	}
	
	public void prepare(String address) {
	}
        public void send(SyncSerializable serializable) {
	}
       public int getPkgEncodeSize(int type) {
	   return 0;
       }	
	public void notifyMgrState(boolean success) {
	    notifyMgrState(success, 0,null);
	}
        public void notifyMgrState(boolean success,String addr) {
	    notifyMgrState(success, 0,addr);
	}
        public void notifyMgrState(boolean success, int arg2,String addr) {
	}
		
	public void sendBondResponse(boolean pass) {
	}
	
	public void request(final ProjoList projoList) {
	}
	
	public void requestSync(ProjoList projoList) {
	}
	
	public void requestUUID(UUID uuid, ProjoList projoList) {
	}
	
	public void sendFile(String module, String name, int length, InputStream in) {
	}
	
	public void retriveFile(String module, String name, int length, String address) {
	}
	
	public void createChannel(UUID uuid, OnChannelCallBack callback) {
	}
	
	public boolean listenChannel(UUID uuid, OnChannelCallBack callback) {
	    return false;
	}
	
	public void destoryChannel(UUID uuid) {
	}
	
	public void closeFileChannel() {
	}
	
}
