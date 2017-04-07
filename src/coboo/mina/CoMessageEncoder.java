package coboo.mina;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;

import coboo.Config;

public class CoMessageEncoder implements ProtocolEncoder{

	@Override
	public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
		byte[] array=(byte[]) message;
		/*IoBuffer bf=IoBuffer.allocate(Config.BUFFER_SIZE);
		bf.setAutoExpand(true);
		bf.put(array);
		bf.flip();*/
		out.write(array);
	}

	@Override
	public void dispose(IoSession session) throws Exception {
		// TODO Auto-generated method stub
		
	}

}
