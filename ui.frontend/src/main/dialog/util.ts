class Settings {
    logTrace = false;
    logDebug = false;
}

const logSettings = new Settings();

function wait(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function debug(message: string, obj?: any) {
    if (logSettings.logDebug) {
        if (obj) {
            console.log(message, obj);
        } else {
            console.log(message);
        }
    }
}

function trace(message: string, obj?: any, obj2?: any, obj3?: any) {
    if (logSettings.logTrace) {
        if (obj) {           
            if (obj2) {
                if (obj3) {
                    console.log(message, obj, obj2, obj3);
                } else {
                    console.log(message, obj, obj2);
                }
            } else {
                console.log(message, obj);
            }
        } else {
            console.log(message);
        }
    }
}

function error(message: string, obj?: any) {
    if (obj) {
        console.error(message, obj);
    } else {
        console.error(message);
    }
}

function generateUid(): string {

    return Math.random().toString(36).substr(2, 9);
}

function enableDebug() {
    logSettings.logDebug = true;
}

function enableTrace() {
    logSettings.logTrace = true;
    enableDebug();
}

const w: any = window;
w.collabDebug = enableDebug;
w.collabTrace = enableTrace;
