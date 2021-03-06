package cn.ingenic.glasssync;

import android.util.Log;

import java.io.InputStream;

import cn.ingenic.glasssync.transport.ext.ProLogTag;
import cn.ingenic.glasssync.transport.ext.TransportManagerExtConstants;

public class FileInputSyncSerializable implements SyncSerializable {

	private final SyncDescriptor mDes;
	private final String mName;
	private final int mLen;
	private final InputStream mIn;
        private final String mPath;

	public FileInputSyncSerializable(SyncDescriptor des, String name, int len,
                                     InputStream in) {
		if (des == null || name == null || in == null || len < 0) {
			throw new IllegalArgumentException("Invalid args.");
		}
		des.mPri = TransportManagerExtConstants.FILE;
		mDes = des;
		mName = name;
		mLen = len;
		mIn = in;
		mPath = null;
	}

	public FileInputSyncSerializable(SyncDescriptor des, String name, int len,
                                     InputStream in, String path) {
		if (des == null || name == null || in == null || len < 0 || path == null) {
			throw new IllegalArgumentException("Invalid args.");
		}
		des.mPri = TransportManagerExtConstants.FILE;
		mDes = des;
		mName = name;
		mLen = len;
		mIn = in;
		mPath = path;
	}

	@Override
	public SyncDescriptor getDescriptor() {
		return mDes;
	}

	@Override
	public byte[] getDatas(int pos, int len) {
		Log.e(ProLogTag.TAG,
				"FileInputSyncSerializable do not support getDatas(int pos, int len)");
		return null;
	}

	@Override
	public int getLength() {
		return mLen;
	}

	public InputStream getInputStream() {
		return mIn;
	}

	public String getName() {
		return mName;
	}

    public String getPath() {
	return mPath;
    }
}
