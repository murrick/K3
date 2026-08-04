(function (window) {
    'use strict';
    if (window.KANGER_API_HOST) {
        return;
    }
    var local = window.location.hostname === 'localhost'
            || window.location.hostname === '127.0.0.1'
            || window.location.hostname === '::1';
    window.KANGER_API_HOST = local
            ? 'http://localhost:1964'
            : 'https://api.kanger.org';
}(window));
