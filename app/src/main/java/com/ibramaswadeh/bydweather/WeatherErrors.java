package com.ibramaswadeh.bydweather;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.json.JSONException;

/** Formats update failures by the operation that failed, without exposing request data. */
final class WeatherErrors {
    enum Stage { FORECAST, RESPONSE, WIDGET }

    static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }

    private WeatherErrors() { }

    static String describe(Stage stage, Throwable error) {
        if (stage == Stage.WIDGET) {
            if (contains(error, SecurityException.class)) {
                return "BYD widget update failed: access was denied. Check app permissions and firmware compatibility.";
            }
            return "BYD widget update failed: the saved weather could not be verified. Check that the stock BYD weather app is installed.";
        }
        if (contains(error, UnknownHostException.class)) {
            return "Weather download failed: api.open-meteo.com could not be resolved (DNS). Check the car's internet connection.";
        }
        if (contains(error, SocketTimeoutException.class)) {
            return "Weather download timed out. Check the car's internet connection and retry.";
        }
        if (contains(error, SSLException.class)) {
            return "Weather download failed: the secure connection could not be established. Check the car's date, time, and internet connection.";
        }
        if (contains(error, ConnectException.class)) {
            return "Weather download failed: a connection to the weather server could not be opened. Check network access and retry.";
        }
        HttpFailure http = findHttpFailure(error);
        if (http != null) {
            if (http.status == 429) return "Weather service request limit reached (HTTP 429). Retry in a few minutes.";
            if (http.status == 401 || http.status == 403) {
                return "Weather service refused the request (HTTP " + http.status + "). Check network access and retry.";
            }
            if (http.status >= 500) return "Weather service is temporarily unavailable (HTTP " + http.status + "). Retry in a few minutes.";
            return "Weather service rejected the request (HTTP " + http.status + "). Retry or update BYD Weather.";
        }
        if (stage == Stage.RESPONSE || contains(error, JSONException.class)) {
            return "Forecast data could not be read or is incomplete. Retry or update BYD Weather.";
        }
        if (contains(error, IOException.class)) {
            return "Weather download failed: the connection was interrupted. Check internet access and retry.";
        }
        return "Weather update failed during the forecast request (" + error.getClass().getSimpleName() + "). Retry the update.";
    }

    private static boolean contains(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
            if (current.getCause() == current) break;
        }
        return false;
    }

    private static HttpFailure findHttpFailure(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof HttpFailure) return (HttpFailure) current;
            if (current.getCause() == current) break;
        }
        return null;
    }
}
