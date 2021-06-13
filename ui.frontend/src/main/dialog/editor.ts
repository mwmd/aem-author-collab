/// <reference path="./types.ts" />
/// <reference path="./util.ts" />
/// <reference path="./ajax.ts" />
/// <reference path="./users.ts" />
/// <reference path="./annotations.ts" />
/// <reference path="./status.ts" />
/// <reference path="./sse.ts" />

const JCR_CONTENT = "/jcr:content";
const JCR_CONTENT_MANGLED = "/_jcr_content/";
const COLLAB_LEASED = "collab-leased";
const SS_USER = "granite.shell.badge.user";
const WAIT_AFTER_LAST_PERSISTENCE = 2500;

declare var Granite: any;
declare var Coral: any;

const hasEditor = Granite.author && Granite.author.editables;
const processedUpdates = new Map<string, number>();

const recentUpdates = new Map<string, number>();
let lastPersistence = null;

function getEditable(path: string) {

    const result = Granite.author.editables.find(path);
    if (result && result.length) {
        return result[0];
    }
    return null;
}

function getEditableParent(path: string) {

    let editable = null;
    if (path && path.indexOf(JCR_CONTENT) > -1) {
        // try with path and then on each parent
        let cmpPath = path;
        do {
            cmpPath = cmpPath.substring(0, cmpPath.lastIndexOf("/"));
            editable = getEditable(cmpPath);
            if (editable) {
                return editable;
            }
        } while (!cmpPath.endsWith(JCR_CONTENT));
    }
}

function getEditableOrParent(path: string) {

    let editable = getEditable(path);
    if (!editable) {
        editable = getEditableParent(path);
    }
    return editable;
}

function pushRefreshDelay() {

    trace("Starting refresh delay");
    lastPersistence = performance.now();
}

function hasPendingDelay(): boolean {

    return lastPersistence && performance.now() - lastPersistence < WAIT_AFTER_LAST_PERSISTENCE;
}

function hasRecentUpdate(path: string): boolean {

    const time = recentUpdates.get(path);
    return time && performance.now() - time < WAIT_AFTER_LAST_PERSISTENCE;
}

function refreshEditable(editable: any): Promise<any> {

    const waitingTime = WAIT_AFTER_LAST_PERSISTENCE - performance.now() + lastPersistence;
    if (waitingTime > 0) {
        trace("waiting after last persistence: " + waitingTime);
        return wait(waitingTime).then(function() {
            return refreshEditable(editable);
        });
    } else {
        // check if applied update since
        if (hasRecentUpdate(editable.path)) {
            trace("skipping refresh due to recent update", editable);
        } else {
            trace("refreshing editable", editable);
            return Granite.author.editableHelper.doRefresh(editable).promise();
        }
    }
}

function highlightUpdated(path: string) {

    const e = getEditable(path);
    if (e && e.overlay) {
        trace("Highlighting: " + path);
        const dom = e.overlay.dom;
        if (dom) {
            dom.addClass("collab-highlighted");
            dom.fadeTo(700, 0.1, function() {
                dom.removeClass("collab-highlighted");
                dom.css("opacity", 1);
            });
        }
    } else {
        // can be normal, e.g. in a remove operation
        trace("Editable not found: " + path);
    }
}

function applyUpdate(updates: Update[]) {

    if (!hasEditor || !updates || !updates.length) {
        return;
    }

    // apply update

    const refreshEditables = new Set<any>();
    const refreshEditablePaths = new Set<string>();
    const highlightPaths = new Set<string>();
    let refreshAll = false;
    for (const update of updates) {

        trace("Applying update", update);

        if (!refreshAll) {

            if (update.paths) {
                for (const path of update.paths) {
                    // only highlight non-annotation updates
                    if (!isAnnotationPath(path)) {
                        highlightPaths.add(path);
                    }
                }
            }
            if (update.refreshPaths) {
                for (const refreshPath of update.refreshPaths) {
                    // skip annotations
                    if (isAnnotationPath(refreshPath)) {
                        debug("Skipping unexpected annotation path");
                        continue;
                    }
                    const e = getEditableOrParent(refreshPath);
                    if (e) {
                        if (refreshEditablePaths.has(e.path)) {
                            trace("already refreshing, ignoring " + e.path);
                        } else {
                            // wait longer, if update was posted to exactly this refresh path
                            refreshEditables.add(e);
                            refreshEditablePaths.add(e.path);
                            trace("added editable to refresh", e);
                        }
                    } else {
                        trace("editable or parent not found, fallback to refreshAll", refreshPath);
                        refreshAll = true;
                        break;
                    }
                }
            }
        }
    }

    const promises: Promise<void>[] = [];
    if (refreshAll) {
        trace("Refreshing all top level editables");
        for (const e of Granite.author.editables) {
            if (!e.getParent()) {
                promises.push(refreshEditable(e));
            }
        }
    } else if (refreshEditables.size) {
        // reload single editables
        refreshEditables.forEach(function(e) {
            promises.push(refreshEditable(e));
        });
    }

    Promise.all(promises).then(function() {
        // apply highlighting
        highlightPaths.forEach(function(path) {
            highlightUpdated(path);
        });
        // process the most recent annotations
        refreshAnnotations(updates[updates.length - 1].annotations);
    });
}

function getLayerName(): string {

    if (Granite && Granite.author && Granite.author.layerManager) {
        return Granite.author.layerManager.getCurrentLayerName();
    }
    return null;
}

function inEditLayer(): boolean {

    return getLayerName() == "Edit";
}

function inLayoutLayer(): boolean {

    return getLayerName() == "Layouting";
}

function hideLayoutButtons(editable: any) {

    const toolbar = Granite.author.EditorFrame.editableToolbar;
    if (toolbar) {
        const layoutActions = toolbar.config.actions;
        toolbar.config.actions = { "PARENT": Granite.author.EditorFrame.editableToolbar.config.actions.PARENT };
        toolbar.render(editable);
        toolbar.config.actions = layoutActions;
    }
}

function markLeased(editable: any, user: User) {

    debug("Marking leased for " + user.name + " : " + editable.path);
    if (editable.overlay && editable.overlay.dom) {
        const overlayDom = editable.overlay.dom;
        if (overlayDom.hasClass(COLLAB_LEASED)) {
            // skip, already marked
            return;
        }
        overlayDom.addClass(COLLAB_LEASED);
        if (!overlayDom.find("div.collab-centered").length) {
            const html = '<div class="collab-centered">'
                + getProfilePicture(user) + '<br />'
                + '<img src="/etc.clientlibs/aem-author-collab/clientlibs/clientlib-dialog/resources/images/spinner_182.png" '
                + 'class="collab-spinner" /></div>';
            overlayDom.prepend(html);
        }
    }
    editable.setDisabled(true);
    if (Granite.author.EditorFrame && Granite.author.EditorFrame.editableToolbar) {
        if ($("#EditableToolbar button[data-path='" + editable.path + "']").length) {
            // Toolbar pointing at leased component
            debug("Closing toolbar");
            Granite.author.EditorFrame.editableToolbar.close();
        }
    }
}

function markReleased(editable: any) {

    debug("Marking released : " + editable.path);
    if (editable.overlay && editable.overlay.dom) {
        const overlayDom = editable.overlay.dom;
        if (overlayDom.hasClass(COLLAB_LEASED)) {
            overlayDom.removeClass(COLLAB_LEASED);
            overlayDom.find("div.collab-centered").remove();
        }
    }
    editable.setDisabled(false);
}

function showRejectedWarning() {

    if (Coral && Coral.Dialog) {
        return wait(1500).then(function(
            debug("Showing alert");
            const alertWindow = new Coral.Dialog().set({
                id: "rejected-alert",
                header: {
                    innerHTML: "Edit in progress"
                },
                content: {
                    innerHTML: "Another user is editing this content and could overwrite your changes."
                },
                footer: {
                    innerHTML: '<button is="coral-button" variant="primary" coral-close="" '
                        + 'class="coral-Button coral-Button--primary" size="M">'
                        + '<coral-button-label>Ok</coral-button-label></button>'
                },
                variant: "warning"
            });
            document.body.appendChild(alertWindo
            alertWindow.show();
        });
    }
}

function checkAndStoreUpdateTime(refreshPath: string, time: number) {

    const lastProcessed = processedUpdates.get(refreshPath);
    if (lastProcessed && lastProcessed >= time) {
        // update is already applied
        trace("Update is already processed, ignoring: " + refreshPath);
        return false;
    } else {
        processedUpdates.set(refreshPath, time);
        return true;
    }
}

function trackNewUpdate(update: Update): boolean {

    let anyNew = false;
    if (update.refreshPaths && update.refreshPaths.length) {
        for (const refreshPath of update.refreshPaths) {
            if (checkAndStoreUpdateTime(refreshPath, update.time)) {
                anyNew = true;
            }
        }
    } else {
        // update without refresh paths, i.e. annotations only
        if (checkAndStoreUpdateTime("", update.time)) {
            anyNew = true;
        }
    }
    return anyNew;
}

function processUpdates(updates: Update[]) {

    if (!hasEditor || !updates) {
        return;
    }

    const applyUpdates = [];
    for (const update of updates) {
        if (trackNewUpdate(update)) {
            applyUpdates.push(update);
        }
    }
    applyUpdate(applyUpdates);
}

function processAllLeasesAndUpdates(leases: Lease[], updates: Update[]) {

    debug("processing leases and updates");

    // updates
    processUpdates(updates);

    // add all current leases, remove all outdated ones	
    collabStatus.currentLeases.clear();
    if (leases) {
        if (hasEditor) {
            for (const e of Granite.author.editables) {
                const editablePath = e.path;
                let lease = null;
                for (const l of leases) {
                    if (l.path == editablePath) {
                        lease = l;
                        break;
                    }
                }
                if (lease && e.overlay && e.overlay.dom) {
                    markLeased(e, lease.user);
                    collabStatus.currentLeases.set(lease.path, lease);
                    continue;
                }
                if (e.overlay && e.overlay.dom && e.overlay.dom.hasClass(COLLAB_LEASED)) {
                    debug("Releasing " + editablePath);
                    markReleased(e);
                }
            }
        } else {
            // no UI update, but status updated
            for (const lease of leases) {
                collabStatus.currentLeases.set(lease.path, lease);
            }
        }
    }
}

function refreshEditorUI() {

    if (hasEditor) {
        // mark current leases
        for (const e of Granite.author.editables) {
            const lease = collabStatus.currentLeases.get(e.path);
            if (lease) {
                trace("Refreshing lease mark for " + lease.path);
                markLeased(e, lease.user);
            }
        }
        // list current users		
        processAllUsers(collabStatus.currentUsers);
    }
}

function processLeases(leases: Lease[], incremental?: boolean) {

    const oldLeases = collabStatus.currentLeases;
    if (!incremental) {
        collabStatus.currentLeases = new Map();
    }
    if (leases) {
        for (const lease of leases) {
            collabStatus.currentLeases.set(lease.path, lease);
            if (hasEditor) {
                const editable = getEditable(lease.path);
                if (!editable) {
                    error("Cannot apply lease, editable not found: " + lease.path);
                } else {
                    markLeased(editable, lease.user);
                }
            }
        }
    }
    if (!incremental && hasEditor && oldLeases && oldLeases.size) {
        oldLeases.forEach(function(oldLease) {
            let keep = false;
            for (const lease of leases) {
                if (oldLease.path == lease.path) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                const editable = getEditable(oldLease.path);
                if (!editable) {
                    error("Cannot remove lease, editable not found: " + oldLease.path);
                } else {
                    markReleased(editable);
                }
            }
        });
    }
}

function processReleases(releases: Release[]) {

    if (releases) {
        for (const release of releases) {
            if (release.paths) {
                for (const path of release.paths) {
                    collabStatus.currentLeases.delete(path);
                    if (hasEditor) {
                        const editable = getEditable(path);
                        if (!editable) {
                            debug("Cannot apply release, editable not found: " + path);
                        } else {
                            markReleased(editable);
                        }
                    }
                }
            }
        }
    }
}

function warnIfRejected(rejected: boolean) {

    if (rejected) {
        debug("Lease rejected");
        collabStatus.currentLease = null;
        showRejectedWarning();
    }
}

function initEditor() {

    // page editing
    const $doc = $(document);
    collabStatus.uid = generateUid();

    $doc.on("cq-editor-loaded", function() {

        debug("EDITOR LOADED");
        collabStatus.pagePath = Granite.author.pageInfo.status.path;

        if (!collabStatus.pagePath) {
            error("No page path detected");
            return;
        }
        const userId = sessionStorage.getItem(SS_USER);
        if (userId) {
            collabStatus.userId = userId;
        }
        setupSSE();
        startPing();

        $doc.on("dialog-ready", function() {
            const activeEditable = $("[data-type='Editable'].is-active");
            if (activeEditable.length) {
                const leasePath = activeEditable.data("path");
                debug("LEASE: " + leasePath);
                collabStatus.currentLease = leasePath;
                sendLease(leasePath, warnIfRejected);
            } else {
                debug("lease path not located");
            }
        });

        $doc.on("dialog-closed", function() {
            debug("RELEASE");
            sendRelease();
        });

        $doc.on("cq-overlays-create editor-frame-mode-changed cq-layer-activated", function() {
            debug("REFRESH UI");
            refreshEditorUI();
        });

        $doc.on("focusin", function(e) {
            if (inLayoutLayer()) {
                trace("CHECK TOOLBAR", e);
                const selection = Granite.author.selection;
                if (selection.selected && selection.selected.length) {
                    const editable = selection.selected[0];
                    if (editable.overlay && editable.overlay.dom && editable.overlay.dom.hasClass(COLLAB_LEASED)) {
                        trace("hiding buttons for: " + editable.path);
                        hideLayoutButtons(editable);
                    }
                }
            }
        });

        $doc.on("cq-persistence-after-create cq-persistence-after-delete "
            + "cq-persistence-after-move cq-persistence-after-update "
            + "cq-persistence-before-create cq-persistence-before-delete "
            + "cq-persistence-before-move cq-persistence-before-update ", function() {
                pushRefreshDelay();
            });

        $doc.on("cq-editables-update", function(e: any) {
            if (e.editables && e.editables.length && hasPendingDelay()) {
                for (const editable of e.editables) {
                    const path = editable.path;
                    if (path) {
                        recentUpdates.set(path, performance.now());
                        trace("Detected update for " + path);
                    }
                }
            }
        });



    });

    // full-page dialog editing
    if ($(document.documentElement).hasClass("cq-dialog-page")) {

        const mainForm = $("form[data-cq-dialog-pageeditor]");
        if (mainForm) {
            debug("DIALOG LOADED, LEASE");
            const leasePath = mainForm.attr("action");
            collabStatus.pagePath = leasePath.substr(0, leasePath.indexOf(JCR_CONTENT_MANGLED));

            if (!collabStatus.pagePath) {
                error("No page path detected");
                return;
            }
            $("form.cq-dialog.foundation-form").on("dialog-beforeclose", function() {
                sendRelease();
                debug("RELEASE");
            });
            collabStatus.currentLease = leasePath;
            sendLease(leasePath, warnIfRejected);
        }
    }
}
