// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 OpenJRS contributors.
define('openjrs-cv-smoke', [], function () {
    return function (data) {
        var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('width', data.width);
        svg.setAttribute('height', data.height);
        var circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('cx', '45');
        circle.setAttribute('cy', '45');
        circle.setAttribute('r', '30');
        circle.setAttribute('fill', '#1264a3');
        svg.appendChild(circle);
        document.getElementById(data.id).appendChild(svg);
        window.componentRendered = true;
    };
});
