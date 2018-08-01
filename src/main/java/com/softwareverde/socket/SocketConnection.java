package com.softwareverde.socket;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SocketConnection {
    private static final Object _nextIdMutex = new Object();
    private static Long _nextId = 0L;

    private final Long _id;
    private final Socket _socket;
    private PrintWriter _out;
    private BufferedReader _in;
    private final List<String> _messages = new ArrayList<String>();
    private volatile Boolean _isClosed = false;

    private Runnable _messageReceivedCallback;
    private Thread _readThread;

    private InputStream _rawInputStream;

    private void _executeMessageReceivedCallback() {
        if (_messageReceivedCallback != null) {
            (new Thread(_messageReceivedCallback)).start();
        }
    }

    /**
     * Internal callback that is executed when a message is received by the client.
     *  Is executed before any external callbacks are received.
     *  Intended for subclass extension.
     */
    protected void _onMessageReceived(final String message) {
        // Nothing.
    }

    /**
     * Internal callback that is executed when the connection is closed by either the client or server,
     *  or if the connection is terminated.
     *  Intended for subclass extension.
     */
    protected void _onSocketClosed() {
        // Nothing.
    }

    public SocketConnection(final Socket socket) {
        synchronized (_nextIdMutex) {
            _id = _nextId;
            _nextId += 1;
        }

        _socket = socket;

        try {
            _out = new PrintWriter(socket.getOutputStream(), true);

            _rawInputStream = socket.getInputStream();
            _in = new BufferedReader(new InputStreamReader(_rawInputStream));
        } catch (final IOException e) { }

        _readThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while ( (! _isClosed) && (! Thread.interrupted()) ) {
                    try {
                        final String message = _in.readLine();
                        if (message == null) {
                            throw new IOException("Remote socket closed the connection.");
                        }

                        synchronized (_messages) {
                            _messages.add(message);

                            _onMessageReceived(message);
                            _executeMessageReceivedCallback();
                        }
                    }
                    catch (final IOException exception) {
                        _isClosed = true;
                        _onSocketClosed();
                    }
                }
            }
        });

        _readThread.start();
    }

    public void setMessageReceivedCallback(final Runnable callback) {
        _messageReceivedCallback = callback;
    }

    synchronized public void write(final String outboundMessage) {
        _out.write(outboundMessage.trim() + "\n");
        _out.flush();
    }

    public String waitForMessage() {
        while (! Thread.interrupted()) {
            final String message;
            synchronized (_messages) {
                message = ( (! _messages.isEmpty()) ? _messages.remove(0) : null );
            }

            if (message != null) {
                return message;
            }

            try { Thread.sleep(100L); } catch (final InterruptedException exception) { break; }
        }

        return null;
    }

    public String waitForMessage(final Long timeout) {
        final Long start = System.currentTimeMillis();

        boolean shouldContinue = true;
        while (shouldContinue) {
            final String message;
            synchronized (_messages) {
                message = ( (! _messages.isEmpty()) ? _messages.remove(0) : null );
            }

            if (message != null) {
                return message;
            }

            try { Thread.sleep( (timeout > 0) ? (timeout / 10L) : (100L) ); } catch (final InterruptedException exception) { break; }

            if (Thread.interrupted()) {
                shouldContinue = false;
            }
            else {
                final Long now = System.currentTimeMillis();
                final Long elapsed = (now - start);
                if ( (timeout >= 0L) && (elapsed > timeout) ) {
                    shouldContinue = false;
                }
            }
        }

        return null;
    }

    /**
     * Retrieves the oldest message from the inbound queue and returns it.
     *  Returns null if there are no pending messages.
     */
    public String popMessage() {
        synchronized (_messages) {
            if (_messages.isEmpty()) { return null; }

            return _messages.remove(0);
        }
    }

    /**
     * Ceases all reads, and closes the socket.
     *  Invoking any write functions after this call throws a runtime exception.
     */
    public void close() {
        _isClosed = true;

        try {
            _rawInputStream.close();
        }
        catch (final Exception exception) { }

        try {
            _readThread.interrupt();
            _readThread.join();
        }
        catch (final InterruptedException e) { }

        try {
            _socket.close();
        }
        catch (final IOException e) { }

        _onSocketClosed();
    }

    /**
     * Returns false if this instance has had its close() function invoked or the socket is no longer connected.
     */
    public Boolean isConnected() {
        if (_out.checkError()) {
            _isClosed = true;
            _onSocketClosed();
        }

        return (! (_isClosed || _socket.isClosed()));
    }

    @Override
    public int hashCode() {
        return (SocketConnection.class.getSimpleName().hashCode() + _id.hashCode());
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) { return false; }
        if (! (obj instanceof SocketConnection)) { return false; }

        final SocketConnection socketConnectionObj = (SocketConnection) obj;
        return _id.equals(socketConnectionObj._id);
    }
}
