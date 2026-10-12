package com.ibramaswadeh.bydweather;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherErrorsTest {
    @Test public void wrappedDnsFailureNamesTheForecastHostWithoutLeakingRequestData() {
        String message = WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new IOException("private-request-data", new UnknownHostException("private-request-data")));
        assertTrue(message.contains("DNS"));
        assertTrue(message.contains("api.met.no"));
        assertFalse(message.contains("private-request-data"));
    }

    @Test public void distinguishesTimeoutAndSecureConnectionFailure() {
        assertTrue(WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new SocketTimeoutException()).contains("timed out"));
        String message = WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new SSLHandshakeException("certificate detail"));
        assertTrue(message.contains("secure connection"));
        assertTrue(message.contains("date, time"));
        assertTrue(WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new java.net.ConnectException()).contains("could not be opened"));
    }

    @Test public void distinguishesApiLimitsRefusalsAndServerFailures() {
        assertTrue(WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new WeatherErrors.HttpFailure(429)).contains("request limit"));
        assertTrue(WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new WeatherErrors.HttpFailure(403)).contains("refused"));
        assertTrue(WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new WeatherErrors.HttpFailure(503)).contains("temporarily unavailable"));
        assertTrue(WeatherErrors.describe(WeatherErrors.Stage.FORECAST,
                new WeatherErrors.HttpFailure(400)).contains("HTTP 400"));
    }

    @Test public void parsingAndWidgetFailuresIdentifyTheirDifferentSteps() {
        String parsing = WeatherErrors.describe(WeatherErrors.Stage.RESPONSE,
                new JSONException("latitude=private-request-data"));
        assertTrue(parsing.contains("Weather data"));
        assertFalse(parsing.contains("private-request-data"));
        String widget = WeatherErrors.describe(WeatherErrors.Stage.WIDGET,
                new SecurityException("provider-private-data"));
        assertTrue(widget.contains("BYD widget"));
        assertTrue(widget.contains("access was denied"));
        assertFalse(widget.contains("provider-private-data"));
    }
}
