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
	private ExecutorService executor;
	
	public void start() throws IOException{
		executor=Executors.newFixedThreadPool(80);
		group = AsynchronousChannelGroup.withThreadPool(executor);
		SocketAddress local=new InetSocketAddress(2002);
		serverChannel=AsynchronousServerSocketChannel.open(group).bind(local);
		CompletionHandler<AsynchronousSocketChannel, ? super Object> acceptHandler=new AcceptHandler(serverChannel);
		serverChannel.accept(null,acceptHandler);
		try {
			group.awaitTermination(36500, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	/********************************************************************************************
	 * 
	 * @author zzy
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
		public void completed(final AsynchronousSocketChannel socketChannelWithResult, Object attachment) {
			MsgServer.channelList.add(socketChannelWithResult);
			this.serverChannel1.accept(null,this);
			socketChannelWithResult.read(buffer, null, new CompletionHandler<Integer, Object>(){
				@Override
				public void failed(Throwable exc, Object attachment) {	
					System.out.println("read data failed");
				}

				@Override
				public void completed(Integer result, Object attachment) {
					
					buffer.flip();
					String str=StandardCharsets.UTF_8.decode(buffer).toString();
					System.out.println("get string from client"+str);
					for(AsynchronousSocketChannel sc:MsgServer.channelList){
						try {
							sc.getRemoteAddress().toString();
							sc.write(ByteBuffer.wrap(str.getBytes("utf-8"))).get();
						} catch (InterruptedException | ExecutionException | IOException e) {
							e.printStackTrace();
						}
					}
					try {
						socketChannelWithResult.read(buffer,null,this);
					} catch (Exception e) {
						
						e.printStackTrace();
					}
				}
				
			});
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
