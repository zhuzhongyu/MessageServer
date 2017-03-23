package coboo;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/************************************************************************************************************************************
 *                                       信息服务器
 * 该服务器启动后监控本地端口（默认2002）的信息，信息被接收后根据情况进行本地的缓存，或者
 * 发送给对应的客户端连接。每个用户都用icare_id进行标识。
 * 信息包分成两种：1心跳 heartbeating  2 信息message.
 * heartbeating信息包格式：{type (1byte)} 包类别100
 *                                         {icare_id (8bytes)}
 * message信息包格式：      {type (1byte)}               //包类别101
 *                                         {total_bytes(8 bytes)}   //总字节数，每条信息如果大于1024会被拆分成多个包发送，
 *                                                                            该数值代表该信息（不包括头信息）总字节数
 *                                         {from_icare_id(8bytes)} //发送方icare_id
 *                                         {to_icare_id(8bytes)}    //接收方icare_id
 *                                         {sn(4bytes)}                //该信息包在message中的序号
 *                      
 *                       
 *                       @author zzy@coboo.com
 *                       @version 0.01
 *
 ************************************************************************************************************************************/
public class MsgServer {
	public final static byte TEXT_MESSAGE =101;
	public final static byte HEART_BEATING =100;
	AsynchronousServerSocketChannel serverChannel;
	AsynchronousChannelGroup group = null;
	static ArrayList<AsynchronousSocketChannel> channelList=new ArrayList<AsynchronousSocketChannel>();
	static HashMap<Long,AsynchronousSocketChannel>clients=new HashMap<Long, AsynchronousSocketChannel>();
	private ExecutorService executor;
	ServerAcceptHandler acceptHandler ;
	public void start() throws IOException{
		executor=Executors.newFixedThreadPool(80);
		group = AsynchronousChannelGroup.withThreadPool(executor);
		SocketAddress local=new InetSocketAddress(Config.server_port);
		serverChannel=AsynchronousServerSocketChannel.open(group).bind(local);
		acceptHandler = new ServerAcceptHandler(serverChannel);
		serverChannel.accept(null,acceptHandler);
		try {
			group.awaitTermination(36500, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/*******************************************************************************************************************
	 *   服务器被成功（失败）连接后该class的方法被调用
	 *
	 *
	 ********************************************************************************************************************/
	class ServerAcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Object>{

		volatile ByteBuffer buffer = ByteBuffer.allocate(1024);
		private AsynchronousServerSocketChannel serverChannel1;

		/******************************************************************************************************
		 * 
		 * Inner Class to deal  AsynchronousSocketChannel client read data complete
		 *
		 ******************************************************************************************************/
		private final class ClientReadHandler implements CompletionHandler<Integer, Object> {
			private final AsynchronousSocketChannel client;

			private ClientReadHandler(AsynchronousSocketChannel client) {
				this.client = client;
			}

			@Override
			public void failed(Throwable exc, Object attachment) {	
				System.out.println("read data failed");
			}

			/*****************************************************
			 * 收到数据
			 */
			@Override 
			public  void completed(Integer result, Object attachment) {
				synchronized(buffer){
					buffer.flip();
					if(result>0&&buffer.remaining()>0){
						System.out.println("received data from result is: "+result);
						byte[] head=new byte[1];
						
						//System.out.println(buffer.remaining());
						buffer.get(head, 0, 1);
						short type=head[0];
						switch(type){
						case HEART_BEATING:
							if(result!=9)break;
							long icare_id = fetchLong(1);
							System.out.println("beating from "+icare_id);
							try {
								byte[] array=new byte[]{HEART_BEATING};
								client.write(ByteBuffer.wrap(array)).get();
							} catch (InterruptedException e2) {
								e2.printStackTrace();
							} catch (ExecutionException e2) {
								e2.printStackTrace();
							}
/*							
							try {
								System.out.println("answer beating "+client.getLocalAddress().toString()+client.getRemoteAddress().toString()+client.toString());
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
*/							clients.put(icare_id, client);
							
							if(hasMessage(icare_id)){
								send_stored_msg(client,icare_id);
							}
							break;
						case TEXT_MESSAGE:
							System.out.println("get a message");
							if(result!=1024)break;
							int   content_total_bytes=fetchInt(1);
							/*long message_id=fetchInt();
							 int sequence_number=fetchInt();   */ 
							long from_icare_id=fetchLong(17);
							clients.put(from_icare_id, client);
							long to_icare_id=fetchLong(25);
							byte[]bufferarray=buffer.array();
							byte[]str=new byte[bufferarray.length-36];
							System.arraycopy(bufferarray, 37, str, 0,content_total_bytes);		
							System.out.println(new String(str));
							System.out.println("message sender: "+to_icare_id);
							AsynchronousSocketChannel rclient = clients.get(to_icare_id);
							if(rclient!=null){
								try {
									buffer.flip();
									System.out.println("forward message to: "+to_icare_id+" socket:"+rclient.toString());
									rclient.write(ByteBuffer.wrap(bufferarray)).get();
								} catch (Exception e) {
									clients.remove(to_icare_id);
								}
							}else{
								cache_message_package(to_icare_id,buffer);
							}
							break;
						}
				}
				
				}
				//end of switch
				//	System.out.println("get next data");
				buffer.clear();
				client.read(buffer,null,this);
				return;
			}
		}
		/***********************************************************
		 * ServerAcceptHandler constructor
		 * @param serverChannel
		 */
		public ServerAcceptHandler(AsynchronousServerSocketChannel serverChannel) {
			this.serverChannel1=serverChannel;
		}
        /***********************************************************
         * ServerAcceptHandler failed
         */
		@Override
		public void failed(Throwable exc, Object attachment) {	
			System.out.println("connection failed");
		}
        /**************************************************************
         * ServerAcceptHandler get connection
         */
		@Override
		public void completed(final AsynchronousSocketChannel client, Object attachment) {
			try {
				System.out.println("get connection from client:"+client.getRemoteAddress().toString());
			} catch (IOException e1) {
				e1.printStackTrace();
				return;
			}
			this.serverChannel1.accept(null,this);//keep watching
			client.read(buffer, null, new ClientReadHandler(client));
		}
		/***************************************************
		 * 将字节数组转换成long
		 * @param bytes
		 * @return
		 */
		private long getLongFromByte(byte[] bytes) {
			ByteBuffer buffer1 = ByteBuffer.allocate(Long.BYTES);
			buffer1.put(bytes);
			buffer1.flip();//need flip 
			return buffer1.getLong();
		}
		/******************************************************
		 * 将字节数组转换成int
		 * @param bytes
		 * @return
		 */
		private int getIntFromByte(byte[] bytes) {
			ByteBuffer buffer1 = ByteBuffer.allocate(Integer.BYTES);
			buffer1.put(bytes);
			buffer1.flip();//need flip 
			return buffer.getInt();
		}
		/**********************************************************
		 * 将存储在映射文件中的用户信息通过socket
		 * 发送给接收端，每条信息固定1024字节，循
		 * 环发送发送完成将文件删除。
		 * @param client
		 * @param icare_id
		 */
		private void send_stored_msg(AsynchronousSocketChannel client, long icare_id) {
			String filename=getMessageFileName(icare_id);
			int length=1024;
			for(int i=0;;i++){
				try {
					byte[]bytes=Store.read(filename, i*1024, length);
					if(bytes==null)break;
					client.write(ByteBuffer.wrap(bytes));
				} catch (IOException e) {
					e.printStackTrace();
				}
				Store.removeFile(filename);
			}
		}
		/*************************************************************
		 * 根据文件是否存在判断用户（对应icare_id）是
		 * 否有未发出的信息
		 * @param icare_id
		 * @return
		 */
		private boolean hasMessage(long icare_id) {
			String filename=getMessageFileName(icare_id);
			File f=new File(filename);
			return f.exists();
		}
		/**************************************************************
		 * 将无法发送的信息暂存到内存映射文件中
		 * @param to_icare_id
		 * @param buffer
		 */
		private void cache_message_package(long to_icare_id, ByteBuffer buffer) {
			try {
				Store.append(getMessageFileName(to_icare_id), buffer.array());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		/************************************************************
		 * 获取用户缓存信息内存映像文件名字
		 * @param icare_id
		 * @return
		 */
		private String getMessageFileName(long icare_id) {
			String filename=Config.message_base_dir+"/"+icare_id;
			return filename;
		}
		/************************************************************
		 * 从buffer 中取出一个long 8字节序列
		 * @return
		 */
		private long fetchLong(int offset) {
			byte[] long_byte=new byte[8];
			System.out.println("buffer remains "+buffer.remaining());
			buffer.position(offset);
			for (int i = 0; i < 8; i++){
				long_byte[i] = buffer.get();
			}
			long value=getLongFromByte(long_byte);
			return value;
		}
		/*********************************************************
		 * 从buffer 中取出一个int 4字节序列
		 * @return
		 */
		private int fetchInt(int offset) {
			byte[] int_byte=new byte[4];
			buffer.position(offset);
			for (int i = 0; i < 4; i++){
				int_byte[i] = buffer.get();
			}
			int number=getIntFromByte(int_byte);
			return number;
		}
	}
/**********************************************
 *  main 
 * @param args
 */
	public static void main(String[] args) {
		try {
			System.out.println("server start");
			new MsgServer().start();
			System.out.println("server end");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
