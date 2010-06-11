package ptp.net.http;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.log4j.Logger;

import ptp.Config;
import ptp.net.ProxyException;
import ptp.net.mp.MethodProcesser;
import ptp.util.URLUtil;

public class RequestHttpMessage extends HttpMessage {
	private static Logger log = Logger.getLogger(RequestHttpMessage.class);
	private static int buff_size = Integer.parseInt(Config.getIns().getValue(
			"ptp.local.buff.size", "102400"));

	protected MethodProcesser.MethodType methodType;

	protected String resource;

	protected String host;

	protected int port;

	@Override
	protected void readHttpHeaders(InputStream in) throws ProxyException {
		super.readHttpHeaders(in, (byte) 0);
		String[] tokens = this.firstLine.split("\\s");
		try {
			this.methodType = MethodProcesser.MethodType.valueOf(tokens[0]);
		} catch(IllegalArgumentException e) {
			throw new ProxyException(e);
		}
		this.resource = URLUtil.getResource(tokens[1]);
		URL requestURL = null;
		try {
			requestURL = new URL(tokens[1]);
			log.info(this.methodType + ": " + requestURL.toString());
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
		}

		host = requestURL.getHost();
		host = Config.getIns().getIp(host);

		port = requestURL.getPort() != -1 ? requestURL.getPort() : 80;

		this.version = tokens[2];

		this.firstLine = this.methodType.toString() + " " + this.resource + " "
				+ this.version;
	}

	public MethodProcesser.MethodType getMethodType() {
		return this.methodType;
	}

	public String getResource() {
		return this.resource;
	}

	public String getVersion() {
		return this.version;
	}

	public String getHost() {
		return this.host;
	}

	public int getPort() {
		return this.port;
	}

	@Override
	protected void readHttpBody(InputStream in) throws ProxyException {
		if (this.headers.containsKey("Content-Length")) {
			int contentLength = Integer.parseInt(this.headers
					.get("Content-Length"));

			FileOutputStream bodyDataTmpFOS = this
					.getBodyDataFileOutputStream();

			byte[] buff = new byte[buff_size];

			int readCount = 0;

			try {
				while (readCount < contentLength) {
					int thisReadCount = in.read(buff, 0, buff_size);
					bodyDataTmpFOS.write(buff, 0, thisReadCount);
					readCount += thisReadCount;
					log.debug("postContentReadCount: " + readCount);
				}
				bodyDataTmpFOS.close();
			} catch (IOException e) {
				throw new ProxyException(e);
			}
		}
	}
}