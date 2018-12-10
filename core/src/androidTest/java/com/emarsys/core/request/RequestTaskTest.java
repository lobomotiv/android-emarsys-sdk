package com.emarsys.core.request;

import com.emarsys.core.CoreCompletionHandler;
import com.emarsys.core.connection.ConnectionProvider;
import com.emarsys.core.database.repository.Repository;
import com.emarsys.core.database.repository.SqlSpecification;
import com.emarsys.core.provider.timestamp.TimestampProvider;
import com.emarsys.core.request.model.RequestMethod;
import com.emarsys.core.request.model.RequestModel;
import com.emarsys.testUtil.TimeoutUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import static com.emarsys.testUtil.TestUrls.DENNA_ECHO;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RequestTaskTest {

    private static final String WRONG_URL = "https://localhost/missing";
    public static final String NETWORKING_TIME = "networking_time";
    public static final String IN_DATABASE = "in_database_time";
    private static final long TIMESTAMP_1 = 600;
    private static final long TIMESTAMP_2 = 1600;

    private RequestModel requestModel;

    private CoreCompletionHandler coreCompletionHandler;
    private ConnectionProvider connectionProvider;
    private Repository<Map<String, Object>, SqlSpecification> logRepository;
    private TimestampProvider timestampProvider;

    @Rule
    public TestRule timeout = TimeoutUtils.getTimeoutRule();

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        requestModel = mock(RequestModel.class);
        coreCompletionHandler = mock(CoreCompletionHandler.class);
        connectionProvider = new ConnectionProvider();
        logRepository = mock(Repository.class);
        timestampProvider = mock(TimestampProvider.class);
        when(timestampProvider.provideTimestamp()).thenReturn(TIMESTAMP_1, TIMESTAMP_2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_requestModelMustNotBeNull() {
        new RequestTask(
                null,
                coreCompletionHandler,
                connectionProvider,
                logRepository,
                timestampProvider);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_completionHandlerMustNotBeNull() {
        new RequestTask(
                requestModel,
                null,
                connectionProvider,
                logRepository,
                timestampProvider);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_connectionProviderMustNotBeNull() {
        new RequestTask(
                requestModel,
                coreCompletionHandler,
                null,
                logRepository,
                timestampProvider);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_logRepositoryMustNotBeNull() {
        new RequestTask(
                requestModel,
                coreCompletionHandler,
                connectionProvider,
                null,
                timestampProvider);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_timestampProviderMustNotBeNull() {
        new RequestTask(
                requestModel,
                coreCompletionHandler,
                connectionProvider,
                logRepository,
                null);
    }

    @Test
    public void testDoInBackground_savesInDatabaseTime() {
        RequestModel requestModel = new RequestModel(DENNA_ECHO,
                RequestMethod.GET,
                null,
                new HashMap<String, String>(),
                400L,
                Long.MAX_VALUE,
                "123");

        RequestTask requestTask = new RequestTask(
                requestModel,
                coreCompletionHandler,
                connectionProvider,
                logRepository,
                timestampProvider);

        requestTask.doInBackground();

        Map<String, Object> inDbMetric = createMetricFromRequestModel(
                requestModel,
                IN_DATABASE,
                requestModel.getTimestamp(),
                TIMESTAMP_1);

        verify(logRepository).add(inDbMetric);
    }

    @Test
    public void testDoInBackground_savesNetworkingTime() {
        RequestModel requestModel = new RequestModel(DENNA_ECHO,
                RequestMethod.GET,
                null,
                new HashMap<String, String>(),
                400L,
                Long.MAX_VALUE,
                "123");

        RequestTask requestTask = new RequestTask(
                requestModel,
                coreCompletionHandler,
                connectionProvider,
                logRepository,
                timestampProvider);

        requestTask.doInBackground();

        Map<String, Object> networkingMetric = createMetricFromRequestModel(
                requestModel,
                NETWORKING_TIME,
                TIMESTAMP_1,
                TIMESTAMP_2);

        verify(logRepository).add(networkingMetric);
    }

    @Test
    public void testDoInBackground_savesOnlyInDatabaseTime_inCaseOfNetworkingError() {
        RequestModel requestModel = new RequestModel(
                WRONG_URL,
                RequestMethod.GET,
                null,
                new HashMap<String, String>(),
                400L,
                Long.MAX_VALUE,
                "123");

        RequestTask requestTask = new RequestTask(
                requestModel,
                coreCompletionHandler,
                connectionProvider,
                logRepository,
                timestampProvider);

        requestTask.doInBackground();

        Map<String, Object> inDbMetric = createMetricFromRequestModel(
                requestModel,
                IN_DATABASE,
                requestModel.getTimestamp(),
                TIMESTAMP_1);

        verify(logRepository).add(inDbMetric);
        verifyNoMoreInteractions(logRepository);
    }

    @Test
    public void testDoInBackground_shouldBeResilientToRuntimeExceptions() throws IOException {
        String id = "123";
        connectionProvider = mock(ConnectionProvider.class);
        RequestModel requestModel = new RequestModel(
                WRONG_URL,
                RequestMethod.GET,
                null,
                new HashMap<String, String>(),
                400L,
                Long.MAX_VALUE,
                id);

        Exception runtimeException = new RuntimeException("Sneaky exception");
        HttpsURLConnection connection = mock(HttpsURLConnection.class);
        Mockito.doThrow(runtimeException).when(connection).connect();

        when(connectionProvider.provideConnection(requestModel)).thenReturn(connection);

        RequestTask requestTask = new RequestTask(requestModel, coreCompletionHandler, connectionProvider, logRepository, timestampProvider);

        try {
            requestTask.doInBackground();
        } catch (Exception e) {
            Assert.fail("Request Task should handle exception: " + e.getMessage());
        }
    }

    private Map<String, Object> createMetricFromRequestModel(RequestModel requestModel, String metricName, long start, long end) {
        HashMap<String, Object> metric = new HashMap<>();
        metric.put("request_id", requestModel.getId());
        metric.put(metricName, end - start);
        metric.put("url", requestModel.getUrl().toString());
        return metric;
    }

}