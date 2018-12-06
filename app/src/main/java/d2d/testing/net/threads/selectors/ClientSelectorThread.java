package d2d.testing.net.threads.selectors;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import d2d.testing.MainActivity;
import d2d.testing.net.events.ChangeRequest;
import d2d.testing.net.threads.workers.ClientWorker;

public class ClientSelectorThread implements Runnable{

    private static final int PORT = 3462;
    private static final int BUFF_SIZE = 8192;
    private boolean enabled = true;

    private MainActivity mainActivity;

    private SocketChannel mSocket;
    private InetAddress mInetAddress;
    private InetSocketAddress mInetSocketAddress;

    // The selector we'll be monitoring
    private Selector selector;
    private ClientWorker mWorker; //TODO convert to array??

    // The buffer into which we'll read data when it's available
    private ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFF_SIZE);

    // A list of ChangeRequest instances and Data/socket map
    private final List mPendingChangeRequests = new LinkedList();
    private final Map mPendingData = new HashMap();

    public ClientSelectorThread(InetAddress address, MainActivity mainActivity) throws IOException {
        this.mainActivity = mainActivity;
        mInetAddress = address;
        mInetSocketAddress = new InetSocketAddress(mInetAddress.getHostAddress(),PORT);
        //mSocket = new Socket(mInetAddress, PORT);
        this.mWorker = new ClientWorker();
        new Thread(mWorker).start();
        this.selector = this.initSelector();
    }

    private Selector initSelector() throws IOException {
        // Create a new selector
        return SelectorProvider.provider().openSelector();
    }

    public MainActivity getMainActivity(){
        return this.mainActivity;
    }

    @Override
    public void run() {
        try {
             this.mSocket = this.initiateConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (enabled) {
            try {
                this.processChangeRequests();

                // Wait for an event one of the registered channels
                this.selector.select();

                // Iterate over the set of keys for which events are available
                Iterator selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = (SelectionKey) selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    // Check what event is available and deal with it
                    if (key.isConnectable()) {
                        this.finishConnection(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    } else if (key.isWritable()) {
                        this.write(key);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processChangeRequests() throws ClosedChannelException {
        // Process any pending changes
        synchronized (this.mPendingChangeRequests) {
            Iterator changes = this.mPendingChangeRequests.iterator();
            while (changes.hasNext()) {
                ChangeRequest change = (ChangeRequest) changes.next();
                switch (change.type) {
                    case ChangeRequest.CHANGEOPS:
                        SelectionKey key = change.socket.keyFor(this.selector);
                        key.interestOps(change.ops);
                        break;
                    case ChangeRequest.REGISTER:
                        change.socket.register(this.selector, change.ops);
                        break;
                }
            }
            this.mPendingChangeRequests.clear();
        }
    }

    private void read(SelectionKey key) throws IOException {
        int numRead;
        SocketChannel socketChannel = (SocketChannel) key.channel();
        this.mReadBuffer.clear();   //Clear out our read buffer

        try {
            numRead = socketChannel.read(this.mReadBuffer); // Attempt to read off the channel
        } catch (IOException e) {
            key.cancel();       // Forced to close the connection, cancel key and close the channel.
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            key.cancel();       // Remote entity shut the socket down cleanly. Do the same
            socketChannel.close();
            Log.d("ServerSelector","ServerSelector: client closed connection...");
            return;
        }

        // Hand the data off to our worker thread
        this.mWorker.addData(this, socketChannel, this.mReadBuffer.array(), numRead);
    }

    public void send(byte[] data) {
        synchronized(this.mPendingChangeRequests) {
            this.mPendingChangeRequests.add(new ChangeRequest(mSocket, ChangeRequest.CHANGEOPS, SelectionKey.OP_WRITE));

            synchronized (this.mPendingData) {  // And queue the data we want written
                List queue = (List) this.mPendingData.get(mSocket);
                if (queue == null) {
                    queue = new ArrayList();
                    this.mPendingData.put(mSocket, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }

        // Finally, wake up our selecting thread so it can make the required changes
        this.selector.wakeup();
    }

    private void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        synchronized (this.mPendingData) {
            List queue = (List) this.mPendingData.get(socketChannel);

            while (!queue.isEmpty()) {                  // Write until there's not more data ...
                ByteBuffer buf = (ByteBuffer) queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() > 0) {              // ... or the socket's buffer fills up
                    break;
                }
                queue.remove(0);
            }

            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);  // We wrote away all data, switch back to READING
            }
        }
    }

    private SocketChannel initiateConnection() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);             // Create a non-blocking socket channel


        socketChannel.connect(mInetSocketAddress);          // Connection establishment


        synchronized(this.mPendingChangeRequests) {         // Queue a channel registration
            this.mPendingChangeRequests.add(new ChangeRequest(socketChannel, ChangeRequest.REGISTER, SelectionKey.OP_CONNECT));
        }

        return socketChannel;
    }

    private void finishConnection(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        try {
            socketChannel.finishConnect(); //Finish connecting.
            //negociar algo sobre la conexion?? donde ??
        } catch (IOException e) {

            System.out.println(e);
            key.cancel();               // Cancel the channel's registration with our selector
            return;
        }

        key.interestOps(SelectionKey.OP_READ);  // Register an interest in reading till send
    }
}

