class User {

    name: string;
    id: string;
}

class AnnotationInfo {

    count: number;
    components: string[];
}

class Lease {

    path: string;
    user: User;
}

class Release {

    paths: string[];
}

class Update {

    paths: string[];
    refreshPaths: string[];
    time: number;
    annotations: AnnotationInfo;
}

interface Message {

    leases: Lease[];
    releases: Release[];
    updates: Update[];
    userExit: string[];
    userEnter: User[];
    setup: boolean;
}
