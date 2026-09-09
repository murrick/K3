/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 */
(function (window, document) {
    'use strict';

    var API_HOST = String(window.KANGER_API_HOST || '').replace(/\/$/, '');
    var POLL_INTERVAL_MS = 10000;
    var banner = null;

    if (!API_HOST || typeof window.fetch !== 'function') {
        return;
    }

    function ensureBanner() {
        if (banner) {
            return banner;
        }
        banner = document.createElement('div');
        banner.id = 'server-maintenance-notice';
        banner.setAttribute('role', 'status');
        banner.setAttribute('aria-live', 'polite');
        banner.style.position = 'fixed';
        banner.style.top = '0';
        banner.style.left = '0';
        banner.style.right = '0';
        banner.style.zIndex = '2147483647';
        banner.style.padding = '10px 16px';
        banner.style.textAlign = 'center';
        banner.style.fontFamily = 'Helvetica, Arial, sans-serif';
        banner.style.fontSize = '14px';
        banner.style.fontWeight = '600';
        banner.style.background = '#fff3cd';
        banner.style.color = '#5f4b00';
        banner.style.borderBottom = '1px solid #e0c96a';
        banner.style.boxShadow = '0 2px 5px rgba(0,0,0,.12)';
        banner.style.pointerEvents = 'none';
        banner.style.display = 'none';
        document.body.appendChild(banner);
        return banner;
    }

    function render(maintenance) {
        var element = ensureBanner();
        if (!maintenance || !maintenance.active) {
            element.textContent = '';
            element.style.display = 'none';
            return;
        }

        var deadline = Number(maintenance.deadline_epoch_millis || 0);
        if (!Number.isFinite(deadline) || deadline <= 0) {
            element.textContent = 'Server maintenance is scheduled.';
        } else {
            element.textContent = 'Server maintenance is scheduled for '
                    + new Date(deadline).toLocaleString() + '.';
        }
        element.style.display = 'block';
    }

    async function poll() {
        try {
            var response = await window.fetch(API_HOST + '/maintenance', {
                method: 'GET',
                headers: {'Accept': 'application/json'},
                cache: 'no-store',
                credentials: 'omit'
            });
            if (!response.ok) {
                return;
            }
            var data = await response.json();
            render(data && data.maintenance);
        } catch (ignored) {
            // Preserve the last successfully received notice during a transient failure.
        }
    }

    poll();
    window.setInterval(poll, POLL_INTERVAL_MS);
}(window, document));
