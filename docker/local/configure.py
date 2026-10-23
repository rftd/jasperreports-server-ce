#!/usr/bin/env python3
"""Generate disposable Buildomatic settings and the Tomcat JNDI context."""
import os
import re
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path('/src/jasperserver')
BUILDOMATIC = ROOT / 'buildomatic'


def property_value(value):
    # java.util.Properties uses ISO-8859-1 and interprets backslash escapes.
    result = []
    for c in value:
        if ord(c) > 126:
            encoded = c.encode('utf-16-be')
            result.extend('\\u%04x' % int.from_bytes(encoded[i:i+2], 'big')
                          for i in range(0, len(encoded), 2))
        else:
            result.append('\\' + c if c in '\\:=#! ' else c)
    return ''.join(result)


def configure():
    password = os.environ['OPENJRS_DB_PASSWORD']
    host = os.environ.get('OPENJRS_DB_HOST', 'postgres')
    if not re.fullmatch(r'[A-Za-z0-9.-]+', host):
        raise ValueError('Invalid database hostname')
    if not password or any(c in password for c in '\r\n\0'):
        raise ValueError('Database password must be nonempty and contain no newlines/NUL')
    settings = {
        'js-path': str(ROOT), 'appServerType': 'skipAppServerCheck',
        'appServerDir': '/tmp/openjrs-appserver', 'maven': '/usr/share/maven/bin/mvn',
        'maven.home': '/usr/share/maven', 'maven.build.type': 'community',
        'dbType': 'postgresql', 'dbHost': host, 'dbUsername': 'postgres',
        'dbPassword': password, 'buildArtifactVersionName': 'ci',
        'fafBuildArtifactVersionName': 'ci', 'tibco.driver.available': 'false',
        'deprecated.driver.available': 'false', 'SKIP_TEST_ARG': 'skipTests',
    }
    (BUILDOMATIC / 'default_master.properties').write_text(
        ''.join(k + '=' + property_value(v) + '\n' for k, v in settings.items()), encoding='ascii')
    Path('/tmp/openjrs-appserver').mkdir(exist_ok=True)


def export_runtime():
    output = Path('/var/openjrs/config')
    output.mkdir(parents=True, exist_ok=True)
    shutil.copytree(BUILDOMATIC / 'build_conf/default/webapp', output / 'webapp', dirs_exist_ok=True)
    context = ET.Element('Context')
    for name, database in [('jasperserver', 'jasperserver'), ('jasperserverAudit', 'jasperserver'),
                           ('sugarcrm', 'sugarcrm'), ('foodmart', 'foodmart')]:
        ET.SubElement(context, 'Resource', {
            'name': 'jdbc/' + name, 'auth': 'Container', 'type': 'javax.sql.DataSource',
            'factory': 'org.apache.tomcat.dbcp.dbcp2.BasicDataSourceFactory',
            'driverClassName': 'org.postgresql.Driver',
            'url': 'jdbc:postgresql://' + os.environ.get('OPENJRS_DB_HOST', 'postgres') + ':5432/' + database,
            'username': 'postgres', 'password': os.environ['OPENJRS_DB_PASSWORD'],
            'maxTotal': '40', 'maxIdle': '10', 'validationQuery': 'SELECT 1', 'testOnBorrow': 'true',
        })
    ET.ElementTree(context).write(output / 'context.xml', encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    import sys
    configure() if sys.argv[1:] != ['export'] else export_runtime()
