package com.softwareverde.socket.server;

import com.softwareverde.socket.SocketConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class SocketServer {
    public interface SocketEventCallback {
        void onConnect(SocketConnection socketConnection);
        void onDisconnect(SocketConnection socketConnection);
    }

    protected final Integer _PORT;
    protected ServerSocket _serverSocket;

    protected final List<SocketConnection> _connections = new ArrayList<SocketConnection>();

    protected Long _nextConnectionId = 0L;
    protected volatile Boolean _shouldContinue = true;
    protected Thread _serverThread = null;

    protected SocketEventCallback _socketEventCallback = null;

    protected static final Long _purgeEveryCount = 20L;
    protected void _purgeDisconnectedConnections() {
        synchronized (_connections) {
            Integer i = 0;
            for (final SocketConnection connection : _connections) {
                if (!connection.isConnected()) {
                    _connections.remove(i.intValue());
                    System.out.println("Purging disconnected socket: " + i);

                    _onDisconnect(connection);
                }
                i += 1;
            }
        }
    }

    protected void _onConnect(final SocketConnection socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;

        if (socketEventCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onConnect(socketConnection);
                }
            })).start();
        }
    }

    protected void _onDisconnect(final SocketConnection socketConnection) {
        final SocketEventCallback socketEventCallback = _socketEventCallback;

        if (socketEventCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    socketEventCallback.onDisconnect(socketConnection);
                }
            })).start();
        }
    }

    public SocketServer(final Integer port) {
        _PORT = port;
    }

    public void setSocketEventCallback(final SocketEventCallback socketEventCallback) {
        _socketEventCallback = socketEventCallback;
    }

    public void start() {
        _shouldContinue = true;

        try {
            _serverSocket = new ServerSocket(_PORT);

            _serverThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        while (_shouldContinue) {
                            if (_serverSocket == null) { return; }

                            final SocketConnection connection = new SocketConnection(_serverSocket.accept());

                            final Boolean shouldPurgeConnections = (_nextConnectionId % _purgeEveryCount == 0L);
                            if (shouldPurgeConnections) {
                                _purgeDisconnectedConnections();
                            }

                            synchronized (_connections) {
                                _connections.add(connection);
                                _nextConnectionId += 1L;
                            }

                            _onConnect(connection);
                        }
                    }
                    catch (final IOException exception) { }
                }
            });
            _serverThread.start();

        }
        catch (final IOException exception) { }
    }

    public void stop() {
        _shouldContinue = false;

        if (_serverSocket != null) {
            try {
                _serverSocket.close();
            }
            catch (final IOException e) { }
        }

        _serverSocket = null;

        try {
            if (_serverThread != null) {
                _serverThread.join();
            }
        }
        catch (final Exception exception) { }
    }
}
