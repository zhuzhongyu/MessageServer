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
import java.util.concurrent.Future;
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
public class AsynchronousMsgServer {
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
		SocketAddress local=new InetSocketAddress(Config.getInstance().server_port);
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

		public static final int BUFFER_SIZE = 1024;
		volatile ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
		private AsynchronousServerSocketChannel serverChannel1;

		/******************************************************************************************************
		 * 
		 * Inner Class to deal  AsynchronousSocketChannel client read data complete
		 *
		 ******************************************************************************************************/
		private final class ClientReadHandler implements CompletionHandler<Integer, Object> {
			private final AsynchronousSocketChannel socket;

			private ClientReadHandler(AsynchronousSocketChannel socketIn) {
				this.socket = socketIn;
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
					buffer.flip();
					if(result>0&&buffer.remaining()>0){
						byte[] head=new byte[1];
						buffer.get(head, 0, 1);
						short type=head[0];
						switch(type){
						case HEART_BEATING:
							if(result!=9)break;
							long icare_id ;
							synchronized(socket){
							icare_id = fetchLong(buffer.array(),1);
							//System.out.println("beating from "+icare_id);
							/*try {
								byte[] array=new byte[]{HEART_BEATING};
								client.write(ByteBuffer.wrap(array)).get();
							} catch (InterruptedException e2) {
								e2.printStackTrace();
							} catch (ExecutionException e2) {
								e2.printStackTrace();
							}*/
						  clients.put(icare_id, socket);
						  if(hasMessage(icare_id)){
								send_stored_msg(socket,icare_id);
							}
						  }
							
							
							break;
						case TEXT_MESSAGE:
							if(result!=BUFFER_SIZE)break;
							synchronized (buffer) {
								byte[] bufferarray = buffer.array();
								long content_total_bytes = fetchLong(bufferarray, 1);
								long message_id = fetchLong(bufferarray, 9);
								long from_icare_id = fetchLong(bufferarray, 17);
								long to_icare_id = fetchLong(bufferarray, 25);
								int sn = fetchInt(bufferarray, 33);
								byte[] str = new byte[bufferarray.length - 36];
								clients.put(from_icare_id, socket);
								System.arraycopy(bufferarray, 37, str, 0, (int) content_total_bytes);
								
								AsynchronousSocketChannel to_socket = clients.get(to_icare_id);
								
								if (to_socket != null) {
									if(to_socket.isOpen()){
										try {
											buffer.flip();
											to_socket.connect(to_socket.getRemoteAddress()).get();
											System.out.println("message: "+new String(str)+" from: " + from_icare_id+socket.getRemoteAddress().toString());
											System.out.println("forward to:  " + to_icare_id + " socket:"	+ to_socket.getRemoteAddress().toString());
											to_socket.write(ByteBuffer.wrap(bufferarray)).get();
										} catch (Exception e) {
											clients.remove(to_icare_id);
										}
									}else{
										try {
											to_socket.connect(to_socket.getRemoteAddress()).get();
											to_socket.write(ByteBuffer.wrap(bufferarray)).get();
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										} catch (ExecutionException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										} catch (IOException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
										
									}
									
									
								} else {
									store_message(to_icare_id, message_id, sn, content_total_bytes, buffer);
								}
							}
							break;
						}
				}
				
			//	}
				//end of switch
				//	System.out.println("get next data");
				buffer.clear();
				socket.read(buffer,null,this);
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
		/**********************************************************
		 * 将存储在映射文件中的用户信息通过socket
		 * 发送给接收端，每条信息固定1024字节，循
		 * 环发送发送完成将文件删除。
		 * @param client
		 * @param icare_id
		 */
		private void send_stored_msg(AsynchronousSocketChannel client, long icare_id) {
			synchronized(client){
				Future<Integer> future=null;
				String[] filenames=getMessageFileNames(icare_id);
				//int length=BUFFER_SIZE;
				for(String filename:filenames){
					try {
						String full_filename=Config.message_base_dir+"/"+icare_id+"/"+filename;
						byte[]bytes=Store.read(full_filename);
						ByteBuffer buff = ByteBuffer.wrap(bytes);
						if(bytes==null)break;
						while (future != null) {
							future.get();
							if(buff.remaining()>0){
								future = client.write(buff);
							}else{
								break;
							}
						}
						Store.removeFile(filename);
					} catch (IOException | InterruptedException | ExecutionException e) {
						e.printStackTrace();
						break;
					}
					
				}

			}
		}
		private String[] getMessageFileNames(long icare_id) {
			File dir=new File(Config.message_base_dir+"/"+icare_id);
			if(dir.exists()){
				if(dir.isDirectory()){
					return dir.list();
				}
			}
			return new String[0];
		}
		/*************************************************************
		 * 根据文件是否存在判断用户（对应icare_id）是
		 * 否有未发出的信息
		 * @param icare_id
		 * @return
		 */
		private boolean hasMessage(long icare_id) {
			String dir=Config.message_base_dir+"/"+icare_id;
			File f=new File(dir);
			String[] files = f.list();
			if(files==null||files.length==0){
				return false;
			}else{
				return true;
			}
		}
		/**************************************************************
		 * 将无法发送的信息暂存到内存映射文件中
		 * @param to_icare_id
		 * @param buffer
		 */
		private void store_message(long to_icare_id,long message_id,int sn,long content_length, ByteBuffer buffer) {
			try {
				File d=new File(Config.message_base_dir+"/"+to_icare_id);
				if(!d.exists())d.mkdirs();
				byte[] array = buffer.array();
				byte[]array2=new byte[(int) content_length+37];
				System.arraycopy(array, 0, array2, 0, array2.length);
				Store.save(getMessageFileName(to_icare_id,message_id,sn), array2);
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
		private String getMessageFileName(long icare_id, long message_id, int sn) {
			String filename=Config.message_base_dir+"/"+icare_id+"/"+message_id+"_"+sn;
			return filename;
		}
		/************************************************************
		 * 从buffer 中取出一个long 8字节序列
		 * @return
		 */
		 private long fetchLong(byte[] buffer, int offset) {
				byte[]ret=new byte[8];
				System.arraycopy(buffer,offset,ret,0,8);
				return ByteBuffer.wrap(ret).getLong();
			}
		/*********************************************************
		 * 从buffer 中取出一个int 4字节序列
		 * @return
		 */
		private int fetchInt(byte[] buffer, int offset) {
			byte[]ret=new byte[4];
			System.arraycopy(buffer,offset,ret,0,4);
			return ByteBuffer.wrap(ret).getInt();
		}
	}
	

/**********************************************
 *  main 
 * @param args
 */
	public static void main(String[] args) {
		try {
			System.out.println("server start");
			new AsynchronousMsgServer().start();
			System.out.println("server end");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
