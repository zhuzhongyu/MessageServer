package coboo.mina;

import java.nio.ByteBuffer;

public class Bytes {
	/************************************************************
	 * 从buffer 中取出一个long 8字节序列
	 * @return
	 */
	 public static long fetchLong(byte[] buffer, int offset) {
			byte[]ret=new byte[8];
			System.arraycopy(buffer,offset,ret,0,8);
			return ByteBuffer.wrap(ret).getLong();
		}
	/*********************************************************
	 * 从buffer 中取出一个int 4字节序列
	 * @return
	 */
	 public static int fetchInt(byte[] buffer, int offset) {
		byte[]ret=new byte[4];
		System.arraycopy(buffer,offset,ret,0,4);
		return ByteBuffer.wrap(ret).getInt();
	}
}
