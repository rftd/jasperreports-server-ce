# OpenJRS OpenStreetMap renderer

The renderer uses the existing Community Custom Visualization integration and
bundles Leaflet 1.9.4 (BSD-2-Clause). The renderer wrapper is AGPL-3.0-or-later;
Leaflet's license remains in LICENSE-Leaflet.txt and its banner is retained.

Source: https://registry.npmjs.org/leaflet/-/leaflet-1.9.4.tgz
Upstream: https://github.com/Leaflet/Leaflet/tree/v1.9.4
Archive SHA-512 (npm integrity, verified before extraction):
`nxS1ynzJOmOlHp+iL3FyWqK89GtNL8U8rvlMOsQdTTssxZwCXh8N2NB3GDQOL+YR3XnWyZAxwQixURb+FA74PA==`

The original dist/leaflet.js is embedded, unchanged except removal of its source
map directive, inside a CommonJS scope in openjrs-osm-map.js. This avoids changing
the viewer's AMD configuration or downloading code from a CDN at runtime.
CSS/license text retain the upstream content with normalized LF and trailing whitespace. The basic map uses no marker/layer-control
images. Pan and zoom controls and OpenStreetMap attribution remain visible.

Default tiles: https://tile.openstreetmap.org/{z}/{x}/{y}.png
Copyright: OpenStreetMap contributors (ODbL).
https://www.openstreetmap.org/copyright
https://operations.osmfoundation.org/policies/tiles/

Requests to the tile provider occur only when a map is displayed or exported.
The renderer does not send a vendor heartbeat. Do not bulk-download or prefetch
public OSM tiles; deployments with sustained traffic must choose an appropriate
tile service. The JVM property openjrs.map.tile.url can configure another tile
endpoint; its attribution and usage requirements must also be respected.
