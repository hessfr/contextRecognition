package com.example.tools;


/*
 * Singleton class containing the current status of algorithm, i.e. if we are adapting the model, 
 * waiting for a new model from the server, ...
 * To set the current status: appStatus.getInstance().set(int)
 * To get the current status: appStatus.getInstance().get()
 */
public class appStatus {
	public static final int NORMAL_CLASSIFICATION = 0;
	public static final int MODEL_ADPATION = 1;
	public static final int INIT = 2;
	
	private int status = INIT;
	
	public void set(int s) {
		this.status = s;
	}
	
	public int get() {
		return this.status;
	}
	
	private static final appStatus stat = new appStatus();

	public static appStatus getInstance() {
		return stat;
	}
	
}
