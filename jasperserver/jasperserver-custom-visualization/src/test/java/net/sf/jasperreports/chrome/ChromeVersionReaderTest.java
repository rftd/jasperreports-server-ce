// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
package net.sf.jasperreports.chrome;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
import com.github.kklisura.cdt.services.exceptions.ChromeServiceException;
import com.github.kklisura.cdt.services.impl.ChromeServiceImpl;
import com.github.kklisura.cdt.services.types.ChromeVersion;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChromeVersionReaderTest {
    private ChromeVersion read(int status, String json) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json/version", exchange -> {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            return ChromeVersionReader.read(new ChromeServiceImpl("127.0.0.1", server.getAddress().getPort()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void acceptsNewVersionMetadataWithoutLosingTheDevToolsEndpoint() throws Exception {
        ChromeVersion version = read(200, "{\"Browser\":\"Chrome/156.0\",\"Protocol-Version\":\"1.3\","
                + "\"File-Path-Style\":\"posix\",\"futureMetadata\":{\"extra\":true},"
                + "\"webSocketDebuggerUrl\":\"ws://127.0.0.1:9222/devtools/browser/test\"}");
        assertEquals("Chrome/156.0", version.getBrowser());
        assertEquals("1.3", version.getProtocolVersion());
        assertEquals("ws://127.0.0.1:9222/devtools/browser/test", version.getWebSocketDebuggerUrl());
    }

    @Test(expected = ChromeServiceException.class)
    public void rejectsMissingEndpoint() throws Exception { read(200, "{\"File-Path-Style\":\"posix\"}"); }

    @Test(expected = ChromeServiceException.class)
    public void rejectsNonWebSocketEndpoint() throws Exception {
        read(200, "{\"webSocketDebuggerUrl\":\"https://example.com/\"}");
    }

    @Test(expected = ChromeServiceException.class)
    public void rejectsMalformedMetadata() throws Exception { read(200, "{broken}"); }

    @Test(expected = ChromeServiceException.class)
    public void rejectsHttpErrors() throws Exception { read(500, "{\"error\":\"test\"}"); }
}
