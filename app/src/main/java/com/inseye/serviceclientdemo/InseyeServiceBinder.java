package com.inseye.serviceclientdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

public class InseyeServiceBinder {
    private final Context context;
    private ServiceConnection inseyeServiceConnection;
    public InseyeServiceBinder(Context context) {
        this.context = context;
    }

    public void bind(ServiceConnection inseyeServiceConnection) {
        this.inseyeServiceConnection = inseyeServiceConnection;
        Intent serviceIntent = new Intent();
        ComponentName component = new ComponentName(context.getString(com.inseye.shared.R.string.service_package_name), context.getString(com.inseye.shared.R.string.service_class_name));
        serviceIntent.setComponent(component);
        context.bindService(serviceIntent, inseyeServiceConnection, Context.BIND_AUTO_CREATE);
    }
    public void unbind() {
        context.unbindService(inseyeServiceConnection);
    }
}
