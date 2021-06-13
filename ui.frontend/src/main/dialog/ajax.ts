/// <reference path="./util.ts" />
/// <reference path="./types.ts" />
/// <reference path="./status.ts" />


const PING_INTERVAL = 35000;
const PING_INTERVAL_RECOVER = 2000;

let nextCall: number;

interface LeaseResponse {

    rejected: boolean;
}

interface LeaseRequest {

    uid: string;
    leasePath: string;
    release: boolean;
}

function newRequest(): LeaseRequest {

    const leaseRequest: LeaseRequest = {
        uid: collabStatus.uid,
        leasePath: undefined,
        release: undefined
    };

    if (collabStatus.currentLease) {
        leaseRequest.leasePath = collabStatus.currentLease;
    }
    return leaseRequest;
}

function send(leaseRequest: LeaseRequest, done?: (rejected?: boolean) => void) {

    // cancel next call
    if (nextCall) {
        window.clearTimeout(nextCall);
        nextCall = null;
    }
    let pagePath = collabStatus.pagePath;
    if (!pagePath) {        
        // try to recover from pageinfo
        if (Granite.author.pageInfo && Granite.author.pageInfo.status) {
            pagePath = Granite.author.pageInfo.status.path;
            if (pagePath) {
                collabStatus.pagePath = pagePath;                
            } else {
                error("Unable to send heartbeat, no page path; retrying shortly");
                nextCall = window.setTimeout(function() {
                    send(newRequest());
                }, PING_INTERVAL_RECOVER);
                return;
            }           
        }
    }
    const json = JSON.stringify(leaseRequest);
    trace("Sending Ajax payload", json);
    $.post({
        url: pagePath + ".author-collab.json?:operation=nop",
        data: json,
        processData: false,
        dataType: "json",
        contentType: "application/json"
    }).done(function(data: LeaseResponse) {
        // setup next ajax ping
        nextCall = window.setTimeout(function() {
            send(newRequest());
        }, PING_INTERVAL);
        if (done) {
            done(data.rejected);
        }
    });
}

function startPing() {

    nextCall = window.setTimeout(function() {
        trace("Trigger Ajax Ping");
        send(newRequest());
    }, PING_INTERVAL);
}

function sendLease(leasePath: string, done?: (rejected: boolean) => void) {

    const request = newRequest();
    request.leasePath = leasePath;
    send(request, done);
}

function sendRelease(done?: () => void) {

    collabStatus.currentLease = null;
    const request = newRequest();
    request.leasePath = null;
    request.release = true;
    send(request, done);
}
