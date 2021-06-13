/// <reference path="./types.ts" />
/// <reference path="./editor.ts" />
/// <reference path="./status.ts" />

function slideInUsers(users: User[]) {

    if (!users.length) {
        return;
    }
    const user = users[0];
    users.shift();
    if (!user || !user.id) {
        slideInUsers(users);
        return;
    }

    // safety in case by now already existing
    let img = $("#collab-users img[data-userid='" + user.id + "']");
    if (img.length && img.width() > 0) {
        slideInUsers(users);
        return;
    }

    if (!img.length) {
        const html = '<img src="/bin/aem-author-collab/profile.' + user.id + '.png" alt="'
            + user.name + '" title="' + user.name + '" data-userid="' + user.id + '" />';
        $("#collab-users").append(html);
        img = $("#collab-users img:hidden");
    }
    img.width(0).show().animate({
        width: 40
    }, 500, function() {
        slideInUsers(users);
    });
}

function slideOutUsers(images: JQuery<HTMLElement>[]) {

    const img = images[images.length - 1];
    img.animate({
        width: 0
    }, 500, function() {
        img.remove();
        if (images.length > 1) {
            images.pop();
            slideOutUsers(images);
        }
    });
}

function removeUsers(userIds: string[]) {

    const removeUserIds = [];
    for (const userId of userIds) {
        for (const currentUser of collabStatus.currentUsers) {
            if (currentUser.id == userId) {
                const img = $("#collab-users img[data-userid='" + userId + "']");
                if (img.length) {
                    img.data("pending-remove", "true");
                }
                removeUserIds.push(userId);
                break;
            }
        }
    }

    setTimeout(function() {
        const removeImages = [];
        for (const removeUserId of removeUserIds) {
            const img = $("#collab-users img[data-userid='" + removeUserId + "']");
            // still pending removal?
            if (img.length && img.data("pending-remove")) {
                // remove from ui
                removeImages.push(img);
                // remove from data	
                for (const user of collabStatus.currentUsers) {
                    if (user.id == removeUserId) {
                        const i = collabStatus.currentUsers.indexOf(user);
                        if (i > -1) {
                            collabStatus.currentUsers.splice(i, 1);
                        }
                        break;
                    }
                }
            }
        }
        if (removeImages.length) {
            slideOutUsers(removeImages);
        }
    }, 2000);
}

function processAllUsers(users: User[], incremental?: boolean) {

    if (!incremental) {
        collabStatus.currentUsers = users;
    }
    const usersRoot = $("#collab-users");

    if (!users || !users.length || !(inEditLayer() || inLayoutLayer())) {
        if (usersRoot.length) {
            usersRoot.remove();
        }
        return;
    }

    const isNew = !usersRoot.length;
    if (isNew) {
        $("#OverlayWrapper").prepend('<div id="collab-users"></div>');
    }

    const addImages = [];
    for (const user of users) {
        // check if it already exists
        const img = $("#collab-users img[data-userid='" + user.id + "']");
        if (img.length) {
            // user already exists, reset in case of pending removal
            img.removeData("pending-remove");
        } else {
            // user is new
            if (img.length == 0) {
                if (user.id == collabStatus.userId) {
                    // own user is always first
                    addImages.unshift(user);
                    if (incremental) {
                        collabStatus.currentUsers.unshift(user);
                    }
                } else {
                    addImages.push(user);
                    if (incremental) {
                        collabStatus.currentUsers.push(user);
                    }
                }
            }
        }
    }

    if (addImages.length) {
        slideInUsers(addImages);
    }

    // unless incremental update, remove all displayed users who aren't in passed data
    if (!isNew && !incremental) {
        const removeImages = [];
        // remove images of users who are missing
        $("#collab-users img[data-userid]").each(function() {
            let keep = false;
            const img = $(this);
            for (const user of users) {
                if (user.id == img.data("userid")) {
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                removeImages.push(img);
            }
        });
        if (removeImages.length) {
            slideOutUsers(removeImages);
        }
    }
}

function getProfilePicture(user: User) {

    if (user) {
        return '<img src="/bin/aem-author-collab/profile.' + user.id + '.png" class="collab-user" alt="'
            + user.name + '" title="' + user.name + '" />';
    } else {
        return "";
    }
}
