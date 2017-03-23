package coboo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Date;

public class Store {
	 /**  
     * 修改文件中的某一部分的数据测试:将字定位置的字母改为大写  
     * @param fName  :要修改的文件名字  
     * @param start:起始字节  
     * @param len:要修改多少个字节  
     * @return :是否修改成功  
     * @throws Exception:文件读写中可能出的错  
* @author  javaFound  
     */  
    private static boolean changeFile(String fName,int start,int len) throws Exception{   
      //创建一个随机读写文件对象   
        java.io.RandomAccessFile raf=new java.io.RandomAccessFile(fName,"rw");   
        long totalLen=raf.length();   
        System.out.println("文件总长字节是: "+totalLen);   
        //打开一个文件通道   
        java.nio.channels.FileChannel channel=raf.getChannel();   
        //映射文件中的某一部分数据以读写模式到内存中   
        java.nio.MappedByteBuffer buffer=  channel.map(FileChannel.MapMode.READ_WRITE, start, len);   
        //示例修改字节   
        for(int i=0;i<len;i++){   
        byte src=   buffer.get(i);   
        buffer.put(i,(byte)(src-31));//修改Buffer中映射的字节的值   
        System.out.println("被改为大写的原始字节是:"+src);   
       }   
        buffer.force();//强制输出,在buffer中的改动生效到文件   
        buffer.clear();   
        channel.close();   
        raf.close();   
        return true;   
    }   
    public static byte[] read(String filename,int offset,int length) throws IOException{
    	 RandomAccessFile randomAccessFile = new RandomAccessFile(filename, "rw");
    	 long filesize = randomAccessFile.length();
    	 FileChannel fc = randomAccessFile.getChannel();
    	 MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_WRITE, offset,filesize);
    	 byte[] dst=new byte[length];
		buffer.get(dst);
		randomAccessFile.close();
		return dst;
    }
    public static void append(String filename,byte[]bytes) throws IOException{
        RandomAccessFile randomAccessFile = new RandomAccessFile(filename, "rw");
        long filesize = randomAccessFile.length();
		FileChannel fc = randomAccessFile.getChannel();
		MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_WRITE, filesize, bytes.length);
        buffer.put(bytes);
        buffer.force();
        randomAccessFile.close();
        fc.close();
    }
    public static void removeFile(String filename){		
    	File f=new File(filename);
    	filename=f.getAbsolutePath();
    	if(f.delete()){
    		 System.out.println("deleted");
    	}else{
       	 String osName = System.getProperty("os.name");
       	try {	
       	 if(osName.startsWith("Windows")){
    		//Windows下调用系统命令
    		String [] cmd={"cmd","/c","del",filename}; 
				Process proc =Runtime.getRuntime().exec(cmd);
    		}else{
    		//Linux下调用系统命令就要改成下面的格式
    		String [] cmd={"/bin/sh","-c","rm "+filename}; 
    		Process proc =Runtime.getRuntime().exec(cmd);
    		}
       	} catch (IOException e) {
			e.printStackTrace();
		}
    		
    	}
    }
	public static void main(String[] args) throws Exception {
		String str="hello world";
		byte[]bytes=str.getBytes(Charset.defaultCharset());
		String filename="d:/ttt.data";
		append(filename,bytes);
		byte[]readBytes=read(filename,0,10);
		System.out.println(new String(readBytes));
		removeFile(filename);
	}

}
