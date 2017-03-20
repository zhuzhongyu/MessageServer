package coboo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class testClient {
	InetSocketAddress local = new InetSocketAddress("127.0.0.1",28888);
	InetSocketAddress remote = new InetSocketAddress("rsy.loopoo.cn",2002);
//	InetSocketAddress remote = new InetSocketAddress("127.0.0.1",2002);
	private AsynchronousSocketChannel client;
	private AsynchronousChannelGroup group;
	private ExecutorService executor;
	private ByteBuffer buffer=ByteBuffer.allocate(1024);
    /****************************************************************
     * 
     * @param icare_id
     * @return
     */
	private byte[] getHeartBeating(long icare_id){
		byte[]msg=new byte[9];
		byte[]long_bytes=longToByte(icare_id);
		msg[0]=100;
		for(int i=0;i<long_bytes.length;i++)msg[i+1]=long_bytes[i];
		return msg;
	}
	public byte[] getMessage(long from,long to,String msgStr){
		byte[] msg1=msgStr.getBytes();
		byte[] msg=new byte[1+4+8+8+msg1.length];
		msg[0]=1;
		int length=msg.length;
		appendArray(msg,length,1);
		appendArray(msg,from,1+4);
		appendArray(msg,to,1+4+8);
		appendArray(msg,msg1,1+4+8);
		return msg;
	}
	private void appendArray(byte[]array,byte[] append,int offset){
		for(int i=offset;i<8+offset;i++){
			array[i]=append[i-offset];
		}
	}
	private void appendArray(byte[]array,long append,int offset){
		byte[]longarray=longToByte(append);
		for(int i=offset;i<8+offset;i++){
			array[i]=longarray[i-offset];
		}
	}
	private void appendArray(byte[]array,int append,int offset){
		byte[]retarray=intToByte(append);
		for(int i=offset;i<4+offset;i++){
			array[i]=retarray[i-offset];
		}
	}
	
	/************************************************
	 * convert a long to a byte array
	 * @param number
	 * @return
	 */
	 public  byte[] longToByte(long number) { 
		 ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		 buffer.putLong(0, number);
	        return buffer.array();
	   
	    } 
	 /************************************************
		 * convert a int  to a byte array
		 * @param number
		 * @return
		 */
		 public  byte[] intToByte(int number) { 
			 ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
			 buffer.putInt(0, number);
		        return buffer.array();
		    } 
	/**
	 * @throws IOException 
	 * @throws ExecutionException 
	 * @throws InterruptedException **********************************
	 * 
	 */
	public void connect() throws IOException, InterruptedException, ExecutionException{
		executor=Executors.newFixedThreadPool(80);
		try {
			group=AsynchronousChannelGroup.withThreadPool(executor);
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		}
		client=AsynchronousSocketChannel.open(group);
		buffer.clear();
		
		client.connect(remote).get();
		client.read(buffer, null, new CompletionHandler<Integer, Object>(){
			@Override
			public void completed(Integer result, Object attachment) {
				String content=StandardCharsets.UTF_8.decode(buffer).toString();
				System.out.println("From server"+content);
			}
			@Override
			public void failed(Throwable exc, Object attachment) {
				System.out.println("read From server failed");
			}});
		Thread thread=new Thread(new Runnable(){

			@Override
			public void run() {
				while(true){			
					try {
						sendBeating(111);
						System.out.println("send beating");
						group.awaitTermination(10, TimeUnit.SECONDS);
					} catch (InterruptedException | ExecutionException e) {
						System.out.println("reconnecting");
						break;
					}
				}
				/*try {
					connect();
				} catch (IOException e) {
					e.printStackTrace();
				}*/
			}});
		thread.start();
		
	}
	/****************************************
	 * 
	 * @param str
	 */
	private void send(long from,long to,String str) {
		byte[] msg = this.getMessage(from, to, str);
		send(msg);
	}
	private void send(byte[]msg){
		try {
			this.client.write(ByteBuffer.wrap(msg)).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
	public void sendBeating(long icare_id) throws InterruptedException, ExecutionException{
			this.client.write(ByteBuffer.wrap(this.getHeartBeating(icare_id))).get();
	}
	/**********************************************
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		//	while(true){
		System.out.println("input a integer(enter 0 exit):");
		Scanner scanner=new Scanner(System.in);
		String str=scanner.next();
		//      if(str.equals("0"))break;
		testClient testClient = new testClient();
		try {
			testClient.connect();
	//		testClient.send(111,222,str);
		} catch (IOException | InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
}
