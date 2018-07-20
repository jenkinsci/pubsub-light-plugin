//package org.jenkinsci.plugins.pubsub;
//
//import com.cloudbees.analytics.rest.client.APIFactory;
//import com.cloudbees.analytics.rest.client.api.EventsApi;
//import com.cloudbees.analytics.rest.client.model.JenkinsEvent;
//import com.cloudbees.analytics.sse.SseServlet;
//import hudson.model.FreeStyleBuild;
//import hudson.model.FreeStyleProject;
//import hudson.model.queue.QueueTaskFuture;
//import hudson.tasks.Fingerprinter;
//import net.sf.json.JSONArray;
//import net.sf.json.JSONObject;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.eclipse.jetty.server.Server;
//import org.eclipse.jetty.server.session.HashSessionManager;
//import org.eclipse.jetty.server.session.SessionHandler;
//import org.eclipse.jetty.servlet.ServletContextHandler;
//import org.eclipse.jetty.servlet.ServletHandler;
//import org.junit.After;
//import org.junit.AfterClass;
//import org.junit.Before;
//import org.junit.BeforeClass;
//import org.junit.Rule;
//import org.junit.Test;
//import org.jvnet.hudson.test.CreateFileBuilder;
//import org.jvnet.hudson.test.JenkinsRule;
//
//import javax.ws.rs.client.Client;
//import javax.ws.rs.client.ClientBuilder;
//import javax.ws.rs.client.ClientRequestContext;
//import javax.ws.rs.client.ClientRequestFilter;
//import javax.ws.rs.client.Entity;
//import javax.ws.rs.client.WebTarget;
//import javax.ws.rs.core.NewCookie;
//import javax.ws.rs.core.Response;
//import javax.ws.rs.sse.InboundSseEvent;
//import javax.ws.rs.sse.SseEventSource;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//import java.util.Vector;
//import java.util.concurrent.Future;
//
//import static org.hamcrest.CoreMatchers.is;
//import static org.junit.Assert.assertThat;
//import static org.junit.Assert.assertTrue;
//
//public class RedisPubsubBusToSseServletIT {
//    private static final Logger LOGGER = LogManager.getLogger(RedisPubsubBusToSseServletIT.class);
//    private static final String JOB_CHANNEL = "job";
//
//    @Rule
//    public JenkinsRule j = new JenkinsRule();
//
//    private static final String SSE_SERVLET_PATH = "sse-gateway";
//    private static final Integer JETTY_PORT = 28080;
//
//    /**
//     * Collects events that are supposed to be sent to the service.
//     */
//    private final List<JenkinsEvent> events = new Vector<>();
//
//    private static Server server;
//    private PubsubBus pubsubBus;
//
//    @BeforeClass
//    public static void startJetty() throws Exception {
//        server = new Server(JETTY_PORT);
//
//        final ServletContextHandler contextHandler = new ServletContextHandler(server, "/*");
//
//        final ServletHandler servletHandler = new ServletHandler();
//        servletHandler.addServletWithMapping(SseServlet.class, "/" + SSE_SERVLET_PATH + "/*");
//        servletHandler.initialize();
//        contextHandler.setServletHandler(servletHandler);
//
//        final SessionHandler sessionHandler = new SessionHandler();
//        sessionHandler.setSessionManager(new HashSessionManager());
//        contextHandler.setSessionHandler(sessionHandler);
//
//        server.setHandler(contextHandler);
//        server.start();
//    }
//
//    @AfterClass
//    public static void stopJetty() throws Exception {
//        server.stop();
//    }
//
//    @Before
//    public void setUp() throws Exception {
//        APIFactory.setEventsApi(new EventsApi() {
//            @Override
//            public void storeEvent(JenkinsEvent e) {
//                events.add(e);
//            }
//        });
//
//        pubsubBus = PubsubBus.getBus("RedisPubsubBus");
//        assertTrue(pubsubBus instanceof RedisPubsubBus);
//    }
//
//    @After
//    public void tearDown() throws Exception {
//        pubsubBus.shutdown();
//    }
//
//    @Test
//    public void testAll() throws Exception {
//        final Client client = ClientBuilder.newBuilder().build();
//
//        final String clientId = UUID.randomUUID().toString();
//        final String connectUrl = String.format("http://localhost:%d/%s/connect?clientId=%s", JETTY_PORT, SSE_SERVLET_PATH, clientId);
//
//        LOGGER.info("connecting to SSE backend at url={}", connectUrl);
//        final WebTarget connectTarget = client.target(connectUrl);
//        final Response connectResponse = connectTarget.request().buildGet().invoke();
//        final Map<String, NewCookie> connectCookies = connectResponse.getCookies();
//        LOGGER.info("received connect response={}, session={}", connectResponse.readEntity(String.class), connectCookies);
//
//        final JSONObject configuration = new JSONObject();
//        configuration.element("dispatcherId", clientId);
//        final JSONArray channels = new JSONArray();
//        final JSONObject channel = new JSONObject();
//        channel.element("jenkins_channel", JOB_CHANNEL); // fingerprint events are RunMessages (ie. JobChannelMessages)
//        channels.element(channel);
//        configuration.element("subscribe", channels);
//
//        final String configureUrl = String.format("http://localhost:%d/%s/configure?clientId=%s", JETTY_PORT, SSE_SERVLET_PATH, clientId);
//        LOGGER.info("configuring SSE at url={}, clientId={}, configuration={}", connectUrl, clientId, configuration.toString());
//        final WebTarget configureTarget = client.target(configureUrl);
//        configureTarget.register(new CookieSettingFilter(connectCookies));
//        final Response configureResponse = configureTarget.request().buildPost(Entity.json(configuration.toString())).invoke();
//        LOGGER.info("received configuration response={}", configureResponse.readEntity(String.class));
//
//        final String listenUrl = String.format("http://localhost:%d/%s/listen/%s", JETTY_PORT, SSE_SERVLET_PATH, clientId);
//        LOGGER.info("listening with SSE client at url={}, clientId={}", listenUrl, clientId);
//        final WebTarget target = client.target(listenUrl);
//        target.register(new CookieSettingFilter(connectCookies));
//
//        final List<InboundSseEvent> events = new ArrayList<>();
//        // jersey SSE client
//        final SseEventSource sseEventSource = SseEventSource.target(target).build();
//        sseEventSource.register(event -> {
//            LOGGER.info("received event, name={}, data={}", event.getName(), event.readData(String.class));
//            events.add(event);
//        });
//        sseEventSource.open();
//        LOGGER.info("isSseEventSourceOpen={}", sseEventSource.isOpen());
//
//        FreeStyleProject f = j.createFreeStyleProject();
//        f.getBuildersList().add(new CreateFileBuilder("some.jar", "Something"));
//        f.getPublishersList().add(new Fingerprinter("*.jar"));
//        PausingPublisher pause = new PausingPublisher();
//        f.getPublishersList().add(pause);
//
//        // first time will show up as a new fingerprint
//        QueueTaskFuture<FreeStyleBuild> q = f.scheduleBuild2(0);
//
//        // wait until the fingerprint is recorded, then make sure it's there. it should be still building
//        pause.paused.block();
//        assertThat(f.isBuilding(), is(true));
//
//        // make sure build succees to the end
//        pause.block.signal();
//        j.assertBuildStatusSuccess(q.get());
//
//        events.clear();
//
//        // second time around, it'll show up as a reference of a fingerprint record
//        j.assertBuildStatusSuccess(f.scheduleBuild2(0).get());
//
//        Thread.sleep(10000);
//
//        sseEventSource.close();
//        new Future().cancel()
//        LOGGER.info(events);
//    }
//}
//
//class CookieSettingFilter implements ClientRequestFilter {
//    private final Map<String, NewCookie> connectCookies;
//
//    CookieSettingFilter(final Map<String, NewCookie> cookies) {
//        this.connectCookies = cookies;
//    }
//
//    @Override
//    public void filter(final ClientRequestContext requestContext) throws IOException {
//        final List<Object> cookies = new ArrayList<>();
//        for (final Map.Entry<String, NewCookie> cookie : connectCookies.entrySet()) {
//            cookies.add(cookie.getValue().toString());
//        }
//        requestContext.getHeaders().put("Cookie", cookies);
//    }
//}
