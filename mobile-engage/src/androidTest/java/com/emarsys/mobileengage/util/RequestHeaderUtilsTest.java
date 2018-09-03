package com.emarsys.mobileengage.util;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import com.emarsys.core.DeviceInfo;
import com.emarsys.core.provider.timestamp.TimestampProvider;
import com.emarsys.core.provider.uuid.UUIDProvider;
import com.emarsys.core.util.HeaderUtils;
import com.emarsys.mobileengage.BuildConfig;
import com.emarsys.mobileengage.RequestContext;
import com.emarsys.mobileengage.config.MobileEngageConfig;
import com.emarsys.mobileengage.storage.AppLoginStorage;
import com.emarsys.mobileengage.storage.MeIdSignatureStorage;
import com.emarsys.mobileengage.storage.MeIdStorage;
import com.emarsys.mobileengage.testUtil.SharedPrefsUtils;
import com.emarsys.test.util.TimeoutUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestHeaderUtilsTest {
    private static final String APPLICATION_CODE = "applicationCode";
    private static final String APPLICATION_PASSWORD = "applicationPassword";

    private RequestContext debugRequestContext;
    private RequestContext releaseRequestContext;
    private Context context;

    @Rule
    public TestRule timeout = TimeoutUtils.getTimeoutRule();

    @Before
    public void setup() {
        SharedPrefsUtils.deleteMobileEngageSharedPrefs();

        context = InstrumentationRegistry.getTargetContext();

        String meId = "meid";
        String meIdSignature = "meidsignature";
        MeIdStorage meIdStorage = new MeIdStorage(context);
        meIdStorage.set(meId);
        MeIdSignatureStorage meIdSignatureStorage = new MeIdSignatureStorage(context);
        meIdSignatureStorage.set(meIdSignature);

        MobileEngageConfig config = mock(MobileEngageConfig.class);
        when(config.getApplicationCode()).thenReturn(APPLICATION_CODE);

        UUIDProvider uuidProvider = mock(UUIDProvider.class);
        when(uuidProvider.provideId()).thenReturn("REQUEST_ID");

        TimestampProvider timestampProvider = mock(TimestampProvider.class);
        when(timestampProvider.provideTimestamp()).thenReturn(100_000L);

        DeviceInfo debugDeviceInfo = mock(DeviceInfo.class);
        when(debugDeviceInfo.isDebugMode()).thenReturn(true);

        debugRequestContext = new RequestContext(
                APPLICATION_CODE,
                APPLICATION_PASSWORD,
                debugDeviceInfo,
                mock(AppLoginStorage.class),
                meIdStorage,
                meIdSignatureStorage,
                timestampProvider,
                uuidProvider);

        DeviceInfo releaseDeviceInfo = mock(DeviceInfo.class);
        when(releaseDeviceInfo.isDebugMode()).thenReturn(false);

        releaseRequestContext = new RequestContext(
                APPLICATION_CODE,
                APPLICATION_PASSWORD,
                releaseDeviceInfo,
                mock(AppLoginStorage.class),
                meIdStorage,
                meIdSignatureStorage,
                timestampProvider,
                uuidProvider);


    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateBaseHeaders_V2_configShouldNotBeNull() {
        RequestHeaderUtils.createBaseHeaders_V2(null);
    }

    @Test
    public void testCreateBaseHeaders_V2_shouldReturnCorrectMap() {
        Map<String, String> expected = new HashMap<>();
        expected.put("Authorization", HeaderUtils.createBasicAuth(releaseRequestContext.getApplicationCode(), releaseRequestContext.getApplicationPassword()));

        Map<String, String> result = RequestHeaderUtils.createBaseHeaders_V2(releaseRequestContext);

        assertEquals(expected, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateBaseHeaders_V3_requestContextShouldNotBeNull() {
        RequestHeaderUtils.createBaseHeaders_V3(null);
    }

    @Test
    public void testCreateBaseHeaders_V3_shouldReturnCorrectMap() {
        String meId = "meid";
        String meIdSignature = "meidsignature";
        MeIdStorage meIdStorage = new MeIdStorage(context);
        meIdStorage.set(meId);
        MeIdSignatureStorage meIdSignatureStorage = new MeIdSignatureStorage(context);
        meIdSignatureStorage.set(meIdSignature);

        MobileEngageConfig config = mock(MobileEngageConfig.class);
        when(config.getApplicationCode()).thenReturn(APPLICATION_CODE);

        UUIDProvider uuidProvider = mock(UUIDProvider.class);
        when(uuidProvider.provideId()).thenReturn("REQUEST_ID");

        TimestampProvider timestampProvider = mock(TimestampProvider.class);
        when(timestampProvider.provideTimestamp()).thenReturn(100_000L);

        RequestContext requestContext = new RequestContext(
                APPLICATION_CODE,
                APPLICATION_PASSWORD,
                mock(DeviceInfo.class),
                mock(AppLoginStorage.class),
                meIdStorage,
                meIdSignatureStorage,
                timestampProvider,
                uuidProvider);

        Map<String, String> expected = new HashMap<>();
        expected.put("X-ME-ID", meId);
        expected.put("X-ME-ID-SIGNATURE", meIdSignature);
        expected.put("X-ME-APPLICATIONCODE", APPLICATION_CODE);

        Map<String, String> result = RequestHeaderUtils.createBaseHeaders_V3(requestContext);

        assertEquals(expected, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateDefaultHeaders_configShouldNotBeNull() {
        RequestHeaderUtils.createDefaultHeaders(null);
    }

    @Test
    public void testCreateDefaultHeaders_returnedValueShouldNotBeNull() {
        assertNotNull(RequestHeaderUtils.createDefaultHeaders(debugRequestContext));
    }

    @Test
    public void testCreateDefaultHeaders_debug_shouldReturnCorrectMap() {
        Map<String, String> expected = new HashMap<>();
        expected.put("Content-Type", "application/json");
        expected.put("X-MOBILEENGAGE-SDK-VERSION", BuildConfig.VERSION_NAME);
        expected.put("X-MOBILEENGAGE-SDK-MODE", "debug");

        Map<String, String> result = RequestHeaderUtils.createDefaultHeaders(debugRequestContext);

        assertEquals(expected, result);
    }

    @Test
    public void testCreateDefaultHeaders_release_shouldReturnCorrectMap() {
        Map<String, String> expected = new HashMap<>();
        expected.put("Content-Type", "application/json");
        expected.put("X-MOBILEENGAGE-SDK-VERSION", BuildConfig.VERSION_NAME);
        expected.put("X-MOBILEENGAGE-SDK-MODE", "production");

        Map<String, String> result = RequestHeaderUtils.createDefaultHeaders(releaseRequestContext);

        assertEquals(expected, result);
    }

}