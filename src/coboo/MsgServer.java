package coboo;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/********************************************************************************************
 * 
 * @author zzy
 *
 */
public class MsgServer {
	AsynchronousServerSocketChannel serverChannel;
	AsynchronousChannelGroup group = null;
	static ArrayList<AsynchronousSocketChannel> channelList=new ArrayList<AsynchronousSocketChannel>();
	static HashMap<Long,AsynchronousSocketChannel>clients=new HashMap<Long, AsynchronousSocketChannel>();
	private ExecutorService executor;
	AcceptHandler acceptHandler ;
	public void start() throws IOException{
		executor=Executors.newFixedThreadPool(80);
		group = AsynchronousChannelGroup.withThreadPool(executor);
		SocketAddress local=new InetSocketAddress(2002);
		serverChannel=AsynchronousServerSocketChannel.open(group).bind(local);
		 acceptHandler = new AcceptHandler(serverChannel);
		//CompletionHandler<AsynchronousSocketChannel, ? super Object> acceptHandler=acceptHandler2;
		serverChannel.accept(null,acceptHandler);
		try {
			group.awaitTermination(36500, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/********************************************************************************************
	 *   服务器被成功（失败）连接后该class的方法被调用
	 *
	 *
	 */
	class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Object>{
		 ByteBuffer buffer = ByteBuffer.allocate(1024);
		private AsynchronousServerSocketChannel serverChannel1;
		public AcceptHandler(AsynchronousServerSocketChannel serverChannel) {
			this.serverChannel1=serverChannel;
		}

		@Override
		public void failed(Throwable exc, Object attachment) {	
			System.out.println("connection failed");
		}

		@Override
		public void completed(final AsynchronousSocketChannel client, Object attachment) {
			try {
				System.out.println("get connection from client:"+client.getRemoteAddress().toString());
			} catch (IOException e1) {
				e1.printStackTrace();
				return;
			}
			//MsgServer.channelList.add(client);
			this.serverChannel1.accept(null,this);//keep watching
			client.read(buffer, null, new CompletionHandler<Integer, Object>(){
				private HashMap<Long,String> sending_message=new HashMap();
				@Override
				public void failed(Throwable exc, Object attachment) {	
					System.out.println("read data failed");
				}
                /*****************************************************
                 * 收到数据
                 */
				@Override 
				public void completed(Integer result, Object attachment) {
					//head.flip();
					if(result>0){
						System.out.println("received data from result is: "+result);
						byte[] head=new byte[1];
						buffer.flip();
						buffer.get(head, 0, 1);
						short type=head[0];
						switch(type){
						case 100:
							byte[] icare_id_byte=new byte[8];
							System.out.println("buffer remains "+buffer.remaining());
							//buffer.get(icare_id_byte, 0, 8);
							for (int i = 0; i < 8; i++){
								icare_id_byte[i] = buffer.get();
							}
	                        long icare_id=getLongFromByte(icare_id_byte);
							System.out.println("beating from "+icare_id);
							clients.put(icare_id, client);
							String msg=sending_message.get(icare_id);
							if(msg!=null){
								try {
									client.write(ByteBuffer.wrap(msg.getBytes("utf-8"))).get();
								} catch (UnsupportedEncodingException | InterruptedException | ExecutionException e) {
									clients.remove(icare_id);
									System.out.println("client removed");
									
								} 
							}
							break;
						case 1:
							//message
							int byte_number=buffer.getInt();
							Long from_icare_id=buffer.getLong();
							Long to_icare_id=buffer.getLong();
					//		clients.put(from_icare_id, client);
							AsynchronousSocketChannel rclient = clients.get(to_icare_id);
							String str=StandardCharsets.UTF_8.decode(buffer).toString();
							if(rclient!=null){
								try {
									rclient.write(ByteBuffer.wrap(str.getBytes("utf-8"))).get();
								} catch (UnsupportedEncodingException | InterruptedException | ExecutionException e) {
									clients.remove(to_icare_id);
								}
							}else{
								sending_message.put(to_icare_id,str);
							}
							break;
						}
					}

					//end of switch
				//	System.out.println("get next data");
					buffer.clear();
					client.read(buffer,null,this);
					return;
				}
			});
		}
		private long getLongFromByte(byte[] bytes) {
			ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		    buffer.put(bytes);
		    buffer.flip();//need flip 
		    return buffer.getLong();
		}
	}
	
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
