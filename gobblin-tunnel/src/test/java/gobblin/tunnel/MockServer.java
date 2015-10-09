package gobblin.tunnel;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * A convenient-to-use TCP server class to implement mock test servers.
 *
 * @author kkandekar@linkedin.com
 */
abstract class MockServer {
  protected static final Logger LOG = LoggerFactory.getLogger(MockServer.class);

  volatile boolean _serverRunning = true;
  ServerSocket _server;
  Set<EasyThread> _threads = Collections.synchronizedSet(new HashSet<EasyThread>());
  List<Socket> _sockets = new Vector<Socket>();
  int _serverSocketPort;
  int numConnects = 0;  //only counted for proxy connects

  static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
    }
  }

  public MockServer start() throws IOException {
    _server = new ServerSocket();
    _server.setSoTimeout(5000);
    _server.bind(new InetSocketAddress("localhost", 0));
    _serverSocketPort = _server.getLocalPort();
    _threads.add(new EasyThread() {
      @Override
      void runQuietly() throws Exception {
        runServer();
      }
    }.startThread());
    return this;
  }

  // accept thread
  public void runServer() {
    while (_serverRunning) {
      try {
        final Socket clientSocket = _server.accept();
        numConnects++;
        LOG.info("Accepted connection on " + getServerSocketPort());
        // client handler thread
        _threads.add(new EasyThread() {
          @Override
          void runQuietly() throws Exception {
            try {
              addSocket(clientSocket);
              handleClientSocket(clientSocket);
            } catch (IOException e) {
              e.printStackTrace();
              stopServer();
            }
          }
        }.startThread());
      } catch (IOException ignored) {
      }
    }
    try {
      _server.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  int getNumConnects() {
    return numConnects;
  }

  boolean isServerRunning() {
    return _serverRunning;
  }

  int getServerSocketPort() {
    return _serverSocketPort;
  }

  // need to keep track of socket because interrupting thread is not working
  void addSocket(Socket socket) {
    _sockets.add(socket);
  }

  abstract void handleClientSocket(Socket socket) throws IOException;

  public void stopServer() {
    _serverRunning = false;
    IOUtils.closeQuietly(_server);
    for (Socket socket : _sockets) {
      IOUtils.closeQuietly(socket);
    }
    for (EasyThread thread : _threads) {
      thread.interrupt();
    }
  }
}
