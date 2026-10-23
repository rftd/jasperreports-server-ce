package net.sf.jasperreports.customvisualization.export;

import net.sf.jasperreports.web.util.WebHtmlResourceHandler;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CVMapResourceTest {
    @Test
    public void servesOnlyBundledAssetsThroughTheClasspathEndpoint() {
        for (String base : new String[] {"/jasperserver", "https://example.com/proxy/openjrs"}) {
            WebHtmlResourceHandler handler = new WebHtmlResourceHandler(base + "/rest_v2/resources/reports/Map_files/{0}");
            for (String resource : new String[] {"org/openjrs/maps/openjrs-osm-map.js", "org/openjrs/maps/leaflet.css"}) {
                assertEquals(base + "/reportresource?resource=" + resource,
                        CVElementJsonHandler.getResourceURL(resource, handler));
                assertEquals(resource, CVElementJsonHandler.getResourceURL(resource, null));
            }
            assertEquals(base + "/rest_v2/resources/reports/Map_files/report.js",
                    CVElementJsonHandler.getResourceURL("report.js", handler));
            assertEquals("https://example.com/report.js",
                    CVElementJsonHandler.getResourceURL("https://example.com/report.js", handler));
        }
    }
}
