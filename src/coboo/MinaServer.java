package coboo;

import java.io.IOException;
import java.net.InetSocketAddress;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import coboo.mina.CoMessageCodecFactory;
import coboo.mina.CoMessageHandler;

public class MinaServer {
	private static final int PORT = Config.getInstance(). server_port;
	public static void main(String[] args) throws IOException {
		IoAcceptor acceptor = new NioSocketAcceptor();
		acceptor.getFilterChain().addLast( "codec", new ProtocolCodecFilter( new CoMessageCodecFactory()));
		acceptor.setHandler( new CoMessageHandler() );
		acceptor.getSessionConfig().setReadBufferSize( Config.BUFFER_SIZE );
		acceptor.getSessionConfig().setIdleTime( IdleStatus.READER_IDLE, 20 );
		acceptor.bind( new InetSocketAddress(PORT) );
		System.out.println("-----------------------------------------------------");
		System.out.println("server start at Port:"+PORT);
		System.out.println("server configure file is:"+Config.getInstance().getConfigFilename());
		System.out.println("-----------------------------------------------------");
	}
}
