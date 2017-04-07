package coboo.mina;

import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;

public class CoMessageCodecFactory implements ProtocolCodecFactory {
    private CoMessageDecoder deCoder;
    private  CoMessageEncoder enCoder;
    public CoMessageCodecFactory(){
    	deCoder=new CoMessageDecoder();
    	enCoder=new CoMessageEncoder();
    }
	@Override
	public ProtocolEncoder getEncoder(IoSession session) throws Exception {
		return enCoder;
	}

	@Override
	public ProtocolDecoder getDecoder(IoSession session) throws Exception {
		return deCoder;
	}

}
