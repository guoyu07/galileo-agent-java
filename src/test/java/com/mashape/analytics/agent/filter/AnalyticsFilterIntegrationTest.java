package com.mashape.analytics.agent.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.zeromq.ZMQ;

import com.google.gson.Gson;
import com.mashape.apianalytics.agent.filter.ApianalyticsFilter;
import com.mashape.apianalytics.agent.modal.Entry;
import com.mashape.apianalytics.agent.modal.Message;

public class AnalyticsFilterIntegrationTest {

	private static Server server;
	private static String analyticsData;
	private static AtomicBoolean dataRecieved = new AtomicBoolean(false);

	@BeforeClass
	public static void setup() {
		System.setProperty("analytics.token", "YOUR_SERVICE_TOKEN");
		System.setProperty("analytics.enabled.flag", "true");

		try {
			startZMQServer();
			startJettyServer();
			Thread.sleep(2000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void test() throws Exception {
		HttpClient client = new HttpClient();
		client.start();
		ContentResponse res = client.GET("http://127.0.0.1:8083/");
		client.stop();
		while (!dataRecieved.get()) {
		}
		Message message = new Gson().fromJson(analyticsData, Message.class);
		assertNotNull(message);
		assertNotNull(message.getServiceToken());
		assertNotNull(message.getHar());
		assertNotNull(message.getHar().getLog());
		assertNotNull(message.getHar().getLog().getCreator());
		assertTrue(message.getHar().getLog().getEntries().size() > 0);
		Entry entry = message.getHar().getLog().getEntries().get(0);
		assertNotNull(entry);
		assertNotNull(entry.getStartedDateTime());
		assertNotNull(entry.getRequest());
		assertNotNull(entry.getResponse());
		assertNotNull(entry.getTimings());
		assertEquals("127.0.0.1", entry.getClientIPAddress());
		assertEquals("127.0.0.1", entry.getServerIPAddress());
		
		assertNotNull(entry.getRequest().getMethod());
		assertNotNull(entry.getRequest().getUrl());
		assertNotNull(entry.getRequest().getHttpVersion());
		assertNotNull(entry.getRequest().getHeaders());
		assertNotNull(entry.getRequest().getHeadersSize());
		assertNotNull(entry.getRequest().getContent());
		assertNotNull(entry.getRequest().getBodySize());

	}

	@AfterClass
	public static void tearDown() throws Exception {
		if (server != null)
			server.stop();
	}

	private static void startZMQServer() throws InterruptedException {
		ExecutorService excecutor = Executors.newSingleThreadExecutor();
		excecutor.execute(new Runnable() {

			@Override
			public void run() {
				ZMQ.Context context = ZMQ.context(1);
				ZMQ.Socket receiver = context.socket(ZMQ.PULL);
				receiver.bind("tcp://*:5555");

				while (!Thread.currentThread().isInterrupted()) {
					byte[] request = receiver.recv(0);
					analyticsData = new String(request).trim();
					break;
				}
				receiver.close();
				context.term();
				dataRecieved.set(true);
			}
		});

	}

	private static void startJettyServer() {
		ExecutorService excecutor = Executors.newSingleThreadExecutor();
		excecutor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					Server server = new Server(8083);
					ServletContextHandler context = new ServletContextHandler(
							ServletContextHandler.NO_SESSIONS);
					context.setContextPath("/");
					server.setHandler(context);

					ServletHandler handler = new ServletHandler();
					ServletHolder holder = new ServletHolder(new TestServlet());
					context.addServlet(holder, "/*");

					FilterHolder fh = handler.addFilterWithMapping(
							ApianalyticsFilter.class, "/*",
							EnumSet.of(DispatcherType.REQUEST));
					Map<String, String> map = new HashMap<String, String>();
					map.put("analytics.server.url", "127.0.0.1");
					map.put("analytics.server.port", "5555");
					fh.setInitParameters(map);
					context.addFilter(fh, "/*",
							EnumSet.of(DispatcherType.REQUEST));
					server.start();
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		});

	}
}