package com.ibramaswadeh.bydweather;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.json.JSONException;

/** Formats failures by operation without exposing coordinates or server response bodies. */
final class WeatherErrors {
    enum Stage { FORECAST, ASTRONOMY, AIR_QUALITY, RESPONSE, WIDGET }

    static final class UpdateFailure extends IOException {
        final Stage stage;
        UpdateFailure(Stage stage, Throwable cause) {
            super(stage.name(), cause);
            this.stage = stage;
        }
    }

    static final class HttpFailure extends IOException {
        final int status;
        HttpFailure(int status) {
            super("HTTP " + status);
            this.status = status;
        }
    }

    private WeatherErrors() { }

    static String describe(Stage stage, Throwable error) {
        UpdateFailure update = find(error, UpdateFailure.class);
        if (update != null) stage = update.stage;
        if (stage == Stage.WIDGET) {
            if (find(error, SecurityException.class) != null) {
                return "BYD widget update failed: access was denied. Check app permissions and firmware compatibility.";
            }
            return "BYD widget update failed: the saved weather could not be verified. Check that the stock BYD weather app is installed.";
        }
        String operation = stage == Stage.ASTRONOMY ? "Sunrise/sunset" : stage == Stage.AIR_QUALITY
                ? "Air-quality" : "Weather";
        if (find(error, UnknownHostException.class) != null) {
            return operation + " download failed: api.met.no could not be resolved (DNS). Check the car's internet connection.";
        }
        if (find(error, SocketTimeoutException.class) != null) {
            return operation + " download timed out (api.met.no). Check the car's internet connection and retry.";
        }
        if (find(error, SSLException.class) != null) {
            return operation + " download failed: the secure connection to api.met.no could not be established. Check the car's date, time, and internet connection.";
        }
        if (find(error, ConnectException.class) != null) {
            return operation + " download failed: a connection to api.met.no could not be opened. Check network access and retry.";
        }
        HttpFailure http = find(error, HttpFailure.class);
        if (http != null) {
            if (http.status == 429) return operation + " request limit reached (HTTP 429). Wait a few minutes before retrying.";
            if (http.status == 401 || http.status == 403) {
                return operation + " service refused the request (HTTP " + http.status + "). Check network access or update BYD Weather.";
            }
            if (http.status >= 500) return operation + " service is temporarily unavailable (HTTP " + http.status + "). Retry in a few minutes.";
            return operation + " service rejected the request (HTTP " + http.status + "). Retry or update BYD Weather.";
        }
        if (stage == Stage.RESPONSE || find(error, JSONException.class) != null) {
            return operation + " data is incomplete or could not be read. Check the car's date and time, then retry or update BYD Weather.";
        }
        if (find(error, IOException.class) != null) {
            return operation + " download failed: the connection was interrupted. Check internet access and retry.";
        }
        return operation + " update failed. Retry or update BYD Weather.";
    }

    private static <T extends Throwable> T find(Throwable error, Class<T> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return type.cast(current);
            if (current.getCause() == current) break;
        }
        return null;
    }
}
