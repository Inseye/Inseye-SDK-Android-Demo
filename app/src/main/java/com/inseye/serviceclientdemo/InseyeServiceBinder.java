package com.inseye.serviceclientdemo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;

import com.inseye.shared.communication.ISharedService;

public class InseyeServiceBinder {
    private final Context context;
    private IServiceBindCallback inseyeServiceConnection;

    private boolean isConnected = false;

    public boolean isConnected() { return isConnected; }

    public interface IServiceBindCallback {
        void serviceConnected(ISharedService service);
        void serviceDisconnected();
        void serviceError(Exception e);

    }
    public InseyeServiceBinder(Context context) {
        this.context = context;
    }

    public void bind(@NonNull IServiceBindCallback inseyeServiceConnection) {
        this.inseyeServiceConnection = inseyeServiceConnection;
        Intent serviceIntent = new Intent();
        ComponentName component = new ComponentName(context.getString(com.inseye.shared.R.string.service_package_name), context.getString(com.inseye.shared.R.string.service_class_name));
        serviceIntent.setComponent(component);
        boolean serviceExist = context.bindService(serviceIntent, internalConnection, Context.BIND_AUTO_CREATE);
        if(!serviceExist){
            inseyeServiceConnection.serviceError(new Exception("inseye service is not present in system"));
        }
    }

    private final ServiceConnection internalConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            ISharedService inseyeServiceClient = ISharedService.Stub.asInterface(iBinder);
            isConnected = true;
            inseyeServiceConnection.serviceConnected(inseyeServiceClient);
        }
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isConnected = false;
            inseyeServiceConnection.serviceDisconnected();
        }
    };
    public void unbind() {
        context.unbindService(internalConnection);
    }

}
