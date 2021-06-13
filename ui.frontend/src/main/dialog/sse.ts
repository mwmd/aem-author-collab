/// <reference path="./types.ts" />
/// <reference path="./util.ts" />
/// <reference path="./status.ts" />
/// <reference path="./editor.ts" />
/// <reference path="./users.ts" />

let eventSource: EventSource;
let openedTime: number;

function setupSSE() {

    if (eventSource) {
        return;
    }

    if (EventSource && collabStatus.pagePath) {

        eventSource = new EventSource("/bin/aem-author-collab/sse?uid=" + collabStatus.uid + "&page=" + collabStatus.pagePath);
        eventSource.onopen = function() {
            if (openedTime) {
                const now = performance.now();
                trace("SSE opened [" + Math.trunc((now - openedTime) / 1000) + "sec]");
                openedTime = now;
            } else {
                trace("SSE opened [first]");
                openedTime = performance.now();
            }
        };
        eventSource.onmessage = function(e) {

            const msg = e.data;
            trace("Received: " + msg);
            if (msg) {
                const data: Message = JSON.parse(msg);
                const incremental = !data.setup;
                if (data.leases && data.leases.length) {
                    processLeases(data.leases, incremental);
                }
                if (data.releases && data.releases.length) {
                    processReleases(data.releases);
                }
                if (data.updates && data.updates.length) {
                    processUpdates(data.updates);
                }
                if (data.userEnter && data.userEnter.length) {
                    processAllUsers(data.userEnter, incremental);
                }
                if (data.userExit && data.userExit.length) {
                    removeUsers(data.userExit);
                }
            }
        };
        if (logSettings.logTrace) {
            eventSource.addEventListener("ping", function() {
                trace("SSE ping");
            });
        }
        debug("Established EventSource listener");
    } else {
        error("Unable to setup server connection");
    }
}
