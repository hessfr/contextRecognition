package ch.ethz.wearable.contextrecognition.tools;


/*
 * Singleton class containing the current status of algorithm, i.e. if we are adapting the model, 
 * waiting for a new model from the server, ...
 * To set the current status: appStatus.getInstance().set(int)
 * To get the current status: appStatus.getInstance().get()
 */
public class AppStatus {
	public static final int NORMAL_CLASSIFICATION = 0;
	public static final int MODEL_ADPATION = 1;
	public static final int INIT = 2;
	public static final int MODEL_UPDATED = 3;
	public static final int STOP_RECORDING = 4;
	
	
	public static final int BUFFER_NOT_READY = 0;
	public static final int BUFFER_READY = 1;
	
	private int status = INIT;
	private int bufferStatus = BUFFER_NOT_READY;
	
	public void set(int s) {
		this.status = s;
	}
	
	public int get() {
		return this.status;
	}
	
	public void setBufferStatus(int s) {
		this.bufferStatus = s;
	}
	
	public int getBufferStatus() {
		return this.bufferStatus;
	}
	
	private static final AppStatus stat = new AppStatus();

	public static AppStatus getInstance() {
		return stat;
	}
	
}
