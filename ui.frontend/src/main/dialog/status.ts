/// <reference path="./types.ts" />

class CollabStatus {
    uid: string;
    pagePath: string;
    userId: string;
    currentLeases: Map<string, Lease> = new Map();
    currentUsers: User[] = [];
    currentLease: string;
}

const collabStatus = new CollabStatus();
