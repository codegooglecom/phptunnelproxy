package ptp.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.apache.log4j.Logger;

import ptp.Config;
import ptp.net.mp.MethodProcesser;

public class LocalProxyServer {

	private static Logger log = Logger.getLogger(LocalProxyServer.class);

	int localProxyPort;
	int localProxyTimeOut;

	boolean isStopped = false;

	ServerSocket localProxyServerSocket;

	public LocalProxyServer() {
		localProxyPort = Integer.parseInt(Config.getIns().getValue(
				"ptp.local.proxy.port", "8887"));
		localProxyTimeOut = 1000;
	}

	public void startService() throws Exception {
		try {
			localProxyServerSocket = new ServerSocket(localProxyPort);
		} catch (IOException e) {
			log.error("create local proxy server socket failed", e);
			throw e;
		}
		log.info("local proxy server started on port: " + localProxyPort);

		localProxyServerSocket.setSoTimeout(1000);

		while (!isStopped) {
			Socket browserSocket = null;
			try {
				browserSocket = localProxyServerSocket.accept();
			} catch (SocketTimeoutException ste) {
				continue;
			}
			if (browserSocket != null) {
				log.info("visit from browser: "
						+ browserSocket.getInetAddress().getHostAddress() + " "
						+ browserSocket.getPort());
				Thread localProxyProcessThread = new Thread(
						new LocalProxyProcessThread(browserSocket));
				localProxyProcessThread.start();
				log.info("current thread count: " + Thread.activeCount());
			}
		}
		localProxyServerSocket.close();
		log.info("stop local proxy server");
	}

	public void stopService() {
		isStopped = true;
	}

}

class LocalProxyProcessThread implements Runnable {
	private static Logger log = Logger.getLogger(LocalProxyProcessThread.class);

	Socket browserSocket;

	public LocalProxyProcessThread(Socket browserSocket) {
		this.browserSocket = browserSocket;
	}

	@Override
	public void run() {
		process();
		try {
			browserSocket.close();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}

	private void process() {
		InputStream inFromBrowser = null;
		OutputStream outToBrowser = null;
		try {
			inFromBrowser = browserSocket.getInputStream();
			outToBrowser = browserSocket.getOutputStream();
		} catch (IOException e) {
			log.error("failed to open stream on browser socket", e);
			return;
		}

		int retry = 0;
		int processTimes = 0;
		while (browserSocket.isConnected()) {
			int availableBytes = 0;
			try {
				availableBytes = inFromBrowser.available();
			} catch (IOException e) {
				log.error(e.getMessage());
				break;
			}
			if (availableBytes > 0) {
				MethodProcesser mp;
				try {
					mp = MethodProcesser.getIns(inFromBrowser, outToBrowser);
					mp.process();
				} catch (ProxyException e) {
					writeErrorResponse(outToBrowser, e);
				}

				processTimes++;
			} else {
				if (retry > 0) {
					break;
				}
				try {
					log.info("local proxy thread wait");
					Thread.sleep(150);
					retry++;
				} catch (InterruptedException e) {
					log.info(e.getMessage());
					break;
				}
			}
		}

		try {
			inFromBrowser.close();
			outToBrowser.close();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		log.info(Thread.currentThread().getName() + " end, it proceed "
				+ processTimes + " requests from browser");
	}

	private void writeErrorResponse(OutputStream outToBrowser,
			ProxyException proxyException) {
		log.info("wirite error page for: " + proxyException.getMessage());
		PrintWriter w = new PrintWriter(new OutputStreamWriter(outToBrowser));
		w.write("HTTP/1.1 500 Internal Server Error\r\n");
		w.write("Content-Type: text/html; charset=utf-8\r\n");
		w.write("Connection: close\r\n");
		w.write("\r\n");

		w.write("<html>");
		w.write("<head><title>HTTP 500 Internal Server Error</title><head>");
		w.write("<body><pre>");
		proxyException.printStackTrace(w);
		w.write("</pre></body>");
		w.write("</html>");
		w.flush();
		w.close();
	}

}