package com.oops.wifichat;

import android.app.Application;

public class ChatApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
	}
	
	public ChatConnection connection;
}
