package com.inseye.serviceclientdemo;

import android.util.Log;

import androidx.annotation.Nullable;

import com.inseye.shared.communication.GazeData;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;

public class GazeDataReader extends Thread implements Closeable {

    private final String TAG = GazeDataReader.class.getName();
    private final DatagramSocket socket;
    private final ByteBuffer byteBuffer;
    private final LinkedBlockingQueue<GazeData> gazeBuffer;
    private final IGazeData gazeInterface;

    public interface IGazeData {
        void nextGazeDataReady(GazeData gazeData);
    }

    public GazeDataReader(int port, @Nullable IGazeData gazeCallback) throws SocketException, UnknownHostException {

        gazeInterface = gazeCallback;
        byte[] array = new byte[GazeData.SERIALIZER.getSizeInBytes()];
        byteBuffer = ByteBuffer.wrap(array, 0, GazeData.SERIALIZER.getSizeInBytes()).order(ByteOrder.LITTLE_ENDIAN);

        InetAddress address = InetAddress.getByName("0.0.0.0");
        socket = new DatagramSocket(null);

        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(address, port));
        Log.i(TAG, "port: " + socket.getLocalPort());
        gazeBuffer = new LinkedBlockingQueue<>(100);
    }

    public LinkedBlockingQueue<GazeData> getGazeBuffer() {
        return gazeBuffer;
    }

    @Override
    public void run() {
        while(!isInterrupted()) {
            GazeData gazeData = new GazeData();
            DatagramPacket datagram = new DatagramPacket(byteBuffer.array(), byteBuffer.capacity());

            try {
                socket.receive(datagram);
                byteBuffer.clear();
                GazeData.SERIALIZER.readFromBuffer(gazeData, byteBuffer);

                if (gazeBuffer.remainingCapacity() == 0)
                    gazeBuffer.poll();
                gazeBuffer.offer(gazeData);
                if(gazeInterface != null)
                    gazeInterface.nextGazeDataReady(gazeData);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    @Override
    public void close() {
        interrupt();
        if(socket != null) socket.close();
    }
}
