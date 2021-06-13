/// <reference path="./status.ts" />

const BEACON_URL = "/bin/aem-author-collab/beacon";

let isClosing = false;

function initBeacon() {

    $(window).on('beforeunload', function() {
        isClosing = true;
    });
    $(document).on("visibilitychange", function() {
        if (document.visibilityState == "hidden") {
            if (isClosing && collabStatus.pagePath && collabStatus.uid) {
                const data = {
                    uid: collabStatus.uid,
                    pagePath: collabStatus.pagePath
                };
                navigator.sendBeacon(BEACON_URL, JSON.stringify(data));
            }
        }
    });
} 
