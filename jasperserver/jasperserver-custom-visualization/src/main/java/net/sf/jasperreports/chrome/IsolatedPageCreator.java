/*
 * JasperReports - Free Java Reporting Library.
 * Copyright (C) 2001 - 2023 Cloud Software Group, Inc. All rights reserved.
 * http://www.jaspersoft.com
 *
 * Unless you have purchased a commercial license agreement from Jaspersoft,
 * the following license terms apply:
 *
 * This program is part of JasperReports.
 *
 * JasperReports is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JasperReports is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JasperReports. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.jasperreports.chrome;

// OpenJRS adaptation (2026): JR7 Community integration; see this module's README.

import java.lang.reflect.Method;
import java.io.IOException;
import java.util.List;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.github.kklisura.cdt.protocol.commands.Target;
import com.github.kklisura.cdt.services.ChromeDevToolsService;
import com.github.kklisura.cdt.services.ChromeService;
import com.github.kklisura.cdt.services.WebSocketService;
import com.github.kklisura.cdt.services.config.ChromeDevToolsServiceConfiguration;
import com.github.kklisura.cdt.services.impl.ChromeDevToolsServiceImpl;
import com.github.kklisura.cdt.services.impl.ChromeServiceImpl;
import com.github.kklisura.cdt.services.impl.WebSocketServiceImpl;
import com.github.kklisura.cdt.services.invocation.CommandInvocationHandler;
import com.github.kklisura.cdt.services.types.ChromeTab;
import com.github.kklisura.cdt.services.utils.ProxyUtils;

import net.sf.jasperreports.engine.JRRuntimeException;

/**
 * @author Lucian Chirita (lucianc@users.sourceforge.net)
 */
public class IsolatedPageCreator implements PageCreator
{

    private static final Log log = LogFactory.getLog(IsolatedPageCreator.class);

    private static final IsolatedPageCreator INSTANCE = new IsolatedPageCreator();

    public static IsolatedPageCreator instance()
    {
        return INSTANCE;
    }

    @Override
    public <T> T runInPage(ChromeInstanceHandle chromeInstance, Function<ChromeDevToolsService, T> tabAction)
    {
        return chromeInstance.runWithChromeInstance(chromeService ->
        {
            try(ChromeDevToolsServiceImpl browserDevToolsService = getBrowserDevToolsService(chromeService))
            {
                Target target = browserDevToolsService.getTarget();
                String browserContextId = target.createBrowserContext();
                try
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("created browser context " + browserContextId);
                    }

                    String targetId = target.createTarget(ChromeServiceImpl.ABOUT_BLANK_PAGE,
                            null, null,
                            browserContextId,
                            null, null, null);
                    try
                    {
                        if (log.isDebugEnabled())
                        {
                            log.debug("created tab " + targetId);
                        }

                        ChromeTab tab = getTab(chromeService, targetId);
                        try(ChromeDevToolsService devToolsService = getDevToolsService(tab.getWebSocketDebuggerUrl()))
                        {
                            return tabAction.apply(devToolsService);
                        }
                    }
                    finally
                    {
                        target.closeTarget(targetId);
                        if (log.isDebugEnabled())
                        {
                            log.debug("closed tab " + targetId);
                        }
                    }
                }
                finally
                {
                    target.disposeBrowserContext(browserContextId);
                    if (log.isDebugEnabled())
                    {
                        log.debug("disposed browser context " + browserContextId);
                    }
                }
            }
        });
    }

    protected ChromeTab getTab(ChromeService chromeService, String tabId)
    {
        ChromeTab tab = null;
        for (ChromeTab chromeTab : chromeService.getTabs())
        {
            if (tabId.equals(chromeTab.getId()))
            {
                tab = chromeTab;
                break;
            }
        }
        if (tab == null)
        {
            throw new JRRuntimeException("Did not find Chrome tab " + tabId);
        }
        return tab;
    }

    protected ChromeDevToolsServiceImpl getBrowserDevToolsService(ChromeService chromeService)
    {
        return getDevToolsService(ChromeVersionReader.read(chromeService).getWebSocketDebuggerUrl());
    }

    protected ChromeDevToolsServiceImpl getDevToolsService(String webSocketUrl)
    {
        try
        {
            ChromeDevToolsServiceConfiguration devToolsServiceConfiguration =
                    new ChromeDevToolsServiceConfiguration();

            // CDP is a native client connection. Tyrus adds a browser Origin by
            // default, which current Chrome rejects. Omit that synthetic header
            // rather than relaxing Chrome's origin policy or sandbox.
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                    .configurator(new ClientEndpointConfig.Configurator()
                    {
                        @Override
                        public void beforeRequest(Map<String, List<String>> headers)
                        {
                            headers.keySet().removeIf("Origin"::equalsIgnoreCase);
                        }
                    }).build();
            Session session = WebSocketServiceImpl.getWebSocketContainer().connectToServer(new Endpoint()
            {
                @Override
                public void onOpen(Session openedSession, EndpointConfig endpointConfig)
                {
                    // The CDT service installs the message handler on this session.
                }
            }, config, new URI(webSocketUrl));
            WebSocketService wsService = new WebSocketServiceImpl(session);

            CommandInvocationHandler invocationHandler = new CommandInvocationHandler();
            Map<Method, Object> cache = new ConcurrentHashMap<>();
            ChromeDevToolsServiceImpl devToolsService = ProxyUtils.createProxyFromAbstract(
                    ChromeDevToolsServiceImpl.class,
                    new Class[] {WebSocketService.class, ChromeDevToolsServiceConfiguration.class},
                    new Object[] {wsService, devToolsServiceConfiguration},
                    (proxy, method, args) -> cache.computeIfAbsent(method,
                            key -> ProxyUtils.createProxy(method.getReturnType(), invocationHandler)
            ));
            invocationHandler.setChromeDevToolsService(devToolsService);
            return devToolsService;
        }
        catch (URISyntaxException | DeploymentException | IOException e)
        {
            throw new JRRuntimeException(e);
        }
    }

}
