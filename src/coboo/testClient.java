package coboo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class testClient {

	private AsynchronousSocketChannel client;
	private AsynchronousChannelGroup group;
	private ExecutorService executor;
	private ByteBuffer buffer=ByteBuffer.allocate(1024);
	public void connect(){
		executor=Executors.newFixedThreadPool(80);
		try {
			group=AsynchronousChannelGroup.withThreadPool(executor);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		try {
			client=AsynchronousSocketChannel.open(group);
			client.connect(new InetSocketAddress("rsy.loopoo.cn",2002)).get();

		} catch (IOException | InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
		buffer.clear();
		client.read(buffer, null, new CompletionHandler<Integer, Object>(){

			@Override
			public void completed(Integer result, Object attachment) {
				String content=StandardCharsets.UTF_8.decode(buffer).toString();
				System.out.println("From server"+content);
			}

			@Override
			public void failed(Throwable exc, Object attachment) {
				// TODO Auto-generated method stub

			}});
	}
	
	public static void main(String[] args) {
		while(true){
			System.out.println("input a integer(enter 0 exit):");
			Scanner scanner=new Scanner(System.in);
	        String str=scanner.next();
	        if(str.equals("0"))break;
	        testClient testClient = new testClient();
	        testClient.connect();
			testClient.send(str);
	     
		}
	}

	private void send(String str) {
		try {
			this.client.write(ByteBuffer.wrap(str.getBytes())).get();
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	

}
