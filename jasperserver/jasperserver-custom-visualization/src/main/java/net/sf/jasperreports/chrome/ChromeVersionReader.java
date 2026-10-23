// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package net.sf.jasperreports.chrome;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.exceptions.ChromeServiceException;
import com.github.kklisura.cdt.services.impl.ChromeServiceImpl;
import com.github.kklisura.cdt.services.types.ChromeVersion;

/** Reads only the local browser's version metadata with forward-compatible fields. */
final class ChromeVersionReader {
    private static final ObjectReader VERSION_READER = new ObjectMapper()
            .readerFor(ChromeVersion.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private ChromeVersionReader() { }

    static ChromeVersion read(ChromeService service) {
        if (!(service instanceof ChromeServiceImpl)) return service.getVersion();
        ChromeServiceImpl local = (ChromeServiceImpl) service;
        HttpURLConnection connection = null;
        try {
            URI endpoint = new URI("http", null, local.getHost(), local.getPort(), "/json/version", null, null);
            connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Chrome version endpoint returned HTTP " + connection.getResponseCode());
            }
            ChromeVersion version;
            try (InputStream stream = connection.getInputStream()) {
                version = VERSION_READER.readValue(stream);
            }
            String address = version == null ? null : version.getWebSocketDebuggerUrl();
            if (address == null || address.trim().isEmpty()) {
                throw new IOException("Chrome version metadata has no browser WebSocket URL");
            }
            URI socket = new URI(address);
            if (!("ws".equals(socket.getScheme()) || "wss".equals(socket.getScheme())) || socket.getHost() == null) {
                throw new IOException("Chrome version metadata has an invalid browser WebSocket URL");
            }
            return version;
        } catch (IOException | URISyntaxException e) {
            throw new ChromeServiceException("Cannot read Chrome version metadata", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
