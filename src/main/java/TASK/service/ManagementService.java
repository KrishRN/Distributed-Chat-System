package TASK.service;

import TASK.server.ServerState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManagementService implements Runnable {

    private static final Logger logger = LogManager.getLogger(ManagementService.class);
    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    private ServerState serverState = ServerState.getInstance();

    public ManagementService(int port, int poolSize) throws IOException {
        ServerSocketFactory serversocketfactory =
                (ServerSocketFactory) ServerSocketFactory.getDefault();

        serverSocket = (ServerSocket) serversocketfactory.createServerSocket(port);
        pool = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public void run() {
        try {

            logger.info("Server listening peer on management port "+ serverSocket.getLocalPort() +" for a connection...");

            while (!serverState.isStopRunning()) {
                pool.execute(new ManagementConnection((Socket) serverSocket.accept()));
            }

            pool.shutdown();
        } catch (IOException ex) {
            pool.shutdown();
        } finally {
            pool.shutdownNow();
        }
    }

}
