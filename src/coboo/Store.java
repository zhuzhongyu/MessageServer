package coboo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.session.IoSession;

public class Store {
	/*************************************************************
	 * 根据文件是否存在判断用户（对应icare_id）是
	 * 否有未发出的信息
	 * @param icare_id
	 * @return
	 */
	public static boolean hasMessage(long icare_id) {
		String dir=Config.message_base_dir+"/"+icare_id;
		File f=new File(dir);
		String[] files = f.list();
		if(files==null||files.length==0){
			return false;
		}else{
			return true;
		}
	} 
	/**********************************************************
	 * 将存储在映射文件中的用户信息通过socket
	 * 发送给接收端，每条信息固定1024字节，循
	 * 环发送发送完成将文件删除。
	 * @param session
	 * @param icare_id
	 */
	static public void send_stored_msg(final IoSession session,final long icare_id) {
		Thread t=new Thread(new Runnable(){

			@Override
			public void run() {
				String[] filenames=getMessageFileNames(icare_id);
				for(String filename:filenames){
					String full_filename=Config.message_base_dir+"/"+icare_id+"/"+filename;
					try {
						System.out.println("dealing..."+full_filename);
						byte[] bytes=Store.read(full_filename);
						System.out.println("sending ...."+new String(bytes));
						IoBuffer ioBuffer=IoBuffer.allocate(bytes.length);
						ioBuffer.put(bytes);		
						ioBuffer.flip();
						WriteFuture future = session.write(ioBuffer);
						future.awaitUninterruptibly();
						if(future.isWritten()){
							System.out.println("sending ....ok");
							Store.removeFile(full_filename);
						}
						
					} catch (IOException e) {
						e.printStackTrace();
						break;
					}
				
				}
				Store.removeDir(icare_id);
				
			}});	
		t.start();
       return ;
	
	}
	private static void removeDir(long icare_id) {
		String dirname=Config.message_base_dir+"/"+icare_id;
		File f=new File(dirname);
		dirname=f.getAbsolutePath();
    	if(f.delete()){
    		 System.out.println("dir removed");
    	}else{
       	 String osName = System.getProperty("os.name");
       	try {	
       	 if(osName.startsWith("Windows")){
    		//Windows下调用系统命令
    		String [] cmd={"cmd","/c","del",dirname}; 
				Process proc =Runtime.getRuntime().exec(cmd);
    		}else{
    		//Linux下调用系统命令就要改成下面的格式
    		String [] cmd={"/bin/sh","-rf","rm "+dirname}; 
    		Process proc =Runtime.getRuntime().exec(cmd);
    		}
       	} catch (IOException e) {
			e.printStackTrace();
		}
    		
    	}
		
	}
	static String[] getMessageFileNames(long icare_id) {
		File dir=new File(Config.message_base_dir+"/"+icare_id);
		if(dir.exists()){
			if(dir.isDirectory()){
				String[] list = dir.list();
				Comparator<Object> compare=new Comparator<Object>(){

					@Override
					public int compare(Object o1, Object o2) {
					   String s1=(String) o1;
					   String s2=(String) o2;
					   return s1.compareTo(s2);
						
					}} ;
				Arrays.sort(list, compare);
				return list;
			}
		}
		return new String[0];
	}
	/**************************************************************
	 * 将无法发送的信息暂存到内存映射文件中
	 * @param to_icare_id
	 * @param buffer
	 */
	public static void store_message(long to_icare_id,long message_id,int sn,long content_length, byte[] array) {
		try {
			File d=new File(Config.message_base_dir+"/"+to_icare_id);
			if(!d.exists())d.mkdirs();
			Store.save(getMessageFileName(to_icare_id,message_id,sn), array);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/************************************************************
	 * 获取用户缓存信息内存映像文件名字
	 * @param icare_id
	 * @param sn 
	 * @param message_id 
	 * @return
	 */
	static private String getMessageFileName(long icare_id, long message_id, int sn) {
		String filename=Config.message_base_dir+"/"+icare_id+"/"+message_id+"_"+sn;
		return filename;
	}
	
    public static byte[] read(String filename) throws IOException{
    	 RandomAccessFile randomAccessFile = new RandomAccessFile(filename, "rw");
    	 long filesize = randomAccessFile.length();
    	 byte[] dst=null;
    	 FileChannel fc = randomAccessFile.getChannel();
    	 MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_WRITE, 0,filesize);
    	dst=new byte[(int) filesize];
		buffer.get(dst);
	
		randomAccessFile.close();
		return dst;
    }
    public static void save(String filename,byte[]bytes) throws IOException{
    	RandomAccessFile randomAccessFile = new RandomAccessFile(filename, "rw");
		FileChannel fc = randomAccessFile.getChannel();
		MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_WRITE, 0, bytes.length);
        buffer.put(bytes);
        buffer.force();
        randomAccessFile.close();
        fc.close();
    }
    public static void removeFile(String filename) throws IOException{		
    	System.out.println("try to remove file:"+filename);
    	File f=new File(filename);
    	filename=f.getAbsolutePath();
    	if(f.delete()){
    		 System.out.println("deleted");
    	}else{
    		System.out.println("failed to remove:"+filename);
       	 String osName = System.getProperty("os.name");
    
       	 if(osName.startsWith("Windows")){
    		//Windows下调用系统命令
    		String [] cmd={"cmd","/c","del",filename}; 
				Process proc =Runtime.getRuntime().exec(cmd);
    		}else{
    		//Linux下调用系统命令就要改成下面的格式
    		String [] cmd={"/bin/sh","-c","rm "+filename}; 
    		Process proc =Runtime.getRuntime().exec(cmd);
    		}		
    	}
    }
	public static void main(String[] args) throws Exception {
		/*String str="hello world";
		byte[]bytes=str.getBytes(Charset.defaultCharset());
		String filename="d:/ttt.data";
		save(filename,bytes);
		byte[]readBytes=read(filename);
		System.out.println(new String(readBytes));
		removeFile(filename);*/
	}

}
