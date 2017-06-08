package com.softwareverde.socket;

import com.softwareverde.async.HaltableThread;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class SocketConnection {
    private static final Object _nextIdMutex = new Object();
    private static Long _nextId = 0L;

    public static class ClosedSocketException extends RuntimeException {
        public ClosedSocketException() {
            super("Attempted to access the socket after it has been closed via SocketConnection.close().");
        }
    }

    private final Long _id;
    private final Socket _socket;
    private PrintWriter _out;
    private BufferedReader _in;
    private final List<String> _messages = new ArrayList<String>();
    private Boolean _isClosed = false;

    private Runnable _messageReceivedCallback;
    private HaltableThread _readThread;

    private void _executeMessageReceivedCallback() {
        if (_messageReceivedCallback != null) {
            (new Thread(_messageReceivedCallback)).start();
        }
    }

    /**
     * Internal callback that executed when a message is received by the client.
     *  Is executed before any external callbacks are received.
     *  Primarily created for subclass extension.
     */
    protected void _onMessageReceived(final String message) {
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
            _in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (final IOException e) { }

        _readThread = new HaltableThread(new HaltableThread.ShouldContinueRunnable() {
            @Override
            public Boolean run() {
                try {
                    if (_in.ready()) {
                        final String message = _in.readLine();

                        synchronized (_messages) {
                            _messages.add(message);

                            _onMessageReceived(message);
                            _executeMessageReceivedCallback();
                        }
                    }
                }
                catch (final IOException e) {
                    e.printStackTrace();
                    return false;
                }

                return true;
            }
        });
        _readThread.setSleepTime(200L);

        _readThread.start();
    }

    public void setMessageReceivedCallback(final Runnable callback) {
        _messageReceivedCallback = callback;
    }

    synchronized public void write(final String outboundMessage) {
        if (_isClosed) { throw new ClosedSocketException(); }

        _out.write(outboundMessage.trim() + "\n");
        _out.flush();
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
        _readThread.halt();

        try {
            _socket.close();
        }
        catch (final IOException e) { }

        _isClosed = true;
    }

    /**
     * Returns false if this instance has had its close() function invoked or the socket is no longer connected.
     */
    public Boolean isConnected() {
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
