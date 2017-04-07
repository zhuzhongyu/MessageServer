package coboo.mina;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

import coboo.Config;
import coboo.Store;

public class CoMessageHandler extends IoHandlerAdapter {
	public final static byte HEART_BEATING =100;
	public final static byte TEXT_MESSAGE =101;
	private static final byte MESSAGE_ANSWER = 102;
	public final static byte IMAGE_MESSAGE =103;
    public static final byte DATA_MESSAGE = 104;
    public static final byte TEST = 105;
    public static final byte ANSWER_TEST = 106;

	static HashMap<Long,IoSession>clients=new HashMap<Long, IoSession>();
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		cause.printStackTrace();
	}
    /*********************************************************************************************
     * 收到信息
     */
	@Override
	public void messageReceived(IoSession session, Object message) throws Exception {
		final byte[]msgArray=(byte[])message;
		short type=msgArray[0];
		
		switch(type){
		case HEART_BEATING:
			//if(msgArray.length!=9)break;
			long icare_id ;
			icare_id = Bytes.fetchLong(msgArray,1);
			clients.put(icare_id, session);
			if(Store.hasMessage(icare_id)){
				Store.send_stored_msg(session,icare_id);
			}
			break;
		case MESSAGE_ANSWER:
			icare_id = Bytes.fetchLong(msgArray,1);
			IoSession ioSession = clients.get(icare_id);
			synchronized(ioSession){ioSession.notifyAll();}
			System.out.println("got MESSAGE_ANSWER notifyAll");
			break;
		case IMAGE_MESSAGE:
			forwardStoreMsgArray(session, msgArray);
			break;
		case DATA_MESSAGE:
			forwardStoreMsgArray(session, msgArray);
			break;
		case TEXT_MESSAGE:
			forwardStoreMsgArray(session, msgArray);
			break;
		case TEST:
			System.out.println("got TEST from client");
			icare_id = Bytes.fetchLong(msgArray,1);
			clients.put(icare_id, session);
			final byte[] answerMSG = new byte[]{ANSWER_TEST};
			IoBuffer ioBuffer=IoBuffer.allocate(answerMSG.length);
			ioBuffer.put(answerMSG);		
			ioBuffer.flip();
			session.write(ioBuffer);
			System.out.println("TEST replied");
			break;
		}
	}
	/**********************************************************************
	 * forward or store the received bytes array
	 * @param session
	 * @param msgArray
	 */
	private void forwardStoreMsgArray(IoSession session, final byte[] msgArray) {
		final long content_total_bytes = Bytes.fetchLong(msgArray, 1);
		final long message_id = Bytes.fetchLong(msgArray, 9);
		long from_icare_id = Bytes.fetchLong(msgArray, 17);
		final long to_icare_id = Bytes.fetchLong(msgArray, 25);
		final int sn = Bytes.fetchInt(msgArray, 33);
		final byte[] str = new byte[msgArray.length - 36];
		clients.put(from_icare_id, session);
		System.arraycopy(msgArray, 37, str, 0, (int) content_total_bytes);
		final IoSession to_session = clients.get(to_icare_id);
		if (to_session != null&&to_session.isActive()&&to_session.isConnected()) {
				try {
					IoBuffer ioBuffer=IoBuffer.allocate(msgArray.length);
					ioBuffer.put(msgArray);		
					ioBuffer.flip();
					WriteFuture future = to_session.write(ioBuffer);
					Thread t=new Thread(new Runnable(){
						@Override
						public void run() {
							long starttime=System.currentTimeMillis();
							int timeout = 5000;
							try {
								IoSession ioSession = clients.get(to_icare_id);
								synchronized(ioSession){ioSession.wait(timeout);}
								
							} catch (InterruptedException e) {
								storeMessage(msgArray, content_total_bytes, message_id, to_icare_id, sn, str);
							}
							long endtime=System.currentTimeMillis();
							long dur = endtime-starttime;
							if(dur>=timeout){
								System.out.println("no answer in "+dur+"ms");
								storeMessage(msgArray, content_total_bytes, message_id, to_icare_id, sn, str);
							}else{
								System.out.println("get answer in "+dur+"ms");
							}
						}});
					t.start();
					/*future.await(50000);
					Throwable exception = future.getException();
					if(exception!=null){
						storeMessage(msgArray, content_total_bytes, message_id, to_icare_id, sn, str);
					}else{
						System.out.println("message: "+new String(str)+" from id: " + from_icare_id+"addr:"+session.getRemoteAddress().toString()+"to id:" + to_icare_id + "addr:"+ to_session.getRemoteAddress().toString());
					}*/
				} catch (Exception e) {
					storeMessage(msgArray, content_total_bytes, message_id, to_icare_id, sn, str);
				}
		} else {
			storeMessage(msgArray, content_total_bytes, message_id, to_icare_id, sn, str);
		}
	}
	/*********************************************************************
	 * 
	 * @param msgArray
	 * @param content_total_bytes
	 * @param message_id
	 * @param to_icare_id
	 * @param sn
	 * @param str
	 */
	private void storeMessage(byte[] msgArray, long content_total_bytes, long message_id, long to_icare_id, int sn,
			byte[] str) {
		clients.remove(to_icare_id);
		/*Store.store_message(to_icare_id, message_id, sn, content_total_bytes, msgArray);
		System.out.println("message: "+new String(str)+"  stored to disk" );*/
	}
   /*************************************************************************************************
    * 
    */
	@Override
	public void sessionCreated(IoSession session) throws Exception {
	//	System.out.println("(sessionCreated) :"+session.toString());
		super.sessionCreated(session);
	}

	@Override
	public void sessionOpened(IoSession session) throws Exception {
		System.out.println("(sessionOpened) :"+session.toString());
		super.sessionOpened(session);
	}

	@Override
	public void sessionClosed(IoSession session) throws Exception {
		try {
			System.out.println("(sessionClosed) :"+session.toString());
			super.sessionClosed(session);
		} catch (Exception e) {
			session.closeNow();
			e.printStackTrace();
		}
	}

	@Override
	public void messageSent(IoSession session, Object message) throws Exception {
		try {
			System.out.println("(messageSent) :"+session.toString());
			super.messageSent(session, message);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("messageSent exception ");
			byte[]msgArray=(byte[])message;
			long content_total_bytes = Bytes.fetchLong(msgArray, 1);
			long message_id = Bytes.fetchLong(msgArray, 9);
			long from_icare_id = Bytes.fetchLong(msgArray, 17);
			long to_icare_id = Bytes.fetchLong(msgArray, 25);
			int sn = Bytes.fetchInt(msgArray, 33);
			byte[] str = new byte[msgArray.length - 36];
			storeMessage(msgArray, content_total_bytes, message_id, to_icare_id, sn, str);
		}
	}

	@Override
	public void inputClosed(IoSession session) throws Exception {
		try {
			System.out.println("(inputClosed): "+session.toString());
			super.inputClosed(session);
			session.closeOnFlush();
		} catch (Exception e) {
			session.closeNow();
			e.printStackTrace();
		}
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
		//System.out.println("session "+session.toString() +" IDLE " + session.getIdleCount(status)); 
	}

}
