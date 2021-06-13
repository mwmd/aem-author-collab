/// <reference path="./types.ts" />
/// <reference path="./util.ts" />
/// <reference path="./editor.ts" />

const CQ_ANNOTATIONS_INFIX = "/cq:annotations/";
const CQ_ANNOTATIONS_SUFFIX = "/cq:annotations";

declare var Granite: any;

function inAnnotationLayer() {

    return Granite.author && Granite.author.layerManager
        && Granite.author.layerManager.getCurrentLayerName() == "Annotate";
}

function isAnnotationPath(path: string) {

    if (path && path.indexOf(CQ_ANNOTATIONS_SUFFIX) >= 0) {
        return true;
    }
    return false;
}

function wireEditable(config: any) {

    const componentPath = config.path.substr(0, config.path.lastIndexOf(CQ_ANNOTATIONS_INFIX));
    const editable = getEditable(componentPath);
    if (editable && editable.dom) {
        config.posEl = editable.dom;
    } else {
        debug("Editable for annotation not found: " + componentPath, config);
    }
}

function updateAnnotation(annotation: any, data: any) {

    trace("Updating annotation", annotation, data);

    // clear sketches	
    if (annotation.sketches) {
        for (const sketch of annotation.sketches) {
            sketch.destroy();
        }
        annotation.sketches = [];
    }
    // update options
    annotation["$comment"].val(data.text);
    const opts = annotation.options;
    opts.text = data.text;
    opts.lastModified = data["jcr:lastModified"];
    opts.lastModifiedBy = data["jcr:lastModifiedBy"];
    opts.offsetX = data.x;
    opts.offsetY = data.y;
    opts.color = data.color ? data.color : "blue";
    opts.sketches = data.shapes ? data.shapes : {};
    // re-wire to editable
    wireEditable(opts);
    if (opts.posEl) {
        annotation["$posEl"] = opts.posEl;
    }
    // refresh UI
    annotation._updateMarkText();
    annotation._setUIColor(annotation._getColorHex(opts.color));
    annotation.reposition();
    // re-init sketches
    annotation._initSketches();
}

function removeAnnotation(annotation: any) {

    if (annotation && annotation._destroy) {
        trace("Removing annotation", annotation);
        annotation._destroy();
    } else {
        trace("Unable to remove annotation", annotation);
    }
}

function addAnnotation(path: string, data: any) {

    trace("Adding annotation at " + path, data);

    const annotate = Granite.author.annotate;

    const config = {
        name: path.substr(path.lastIndexOf("/") + 1),
        path: path,
        lastModified: data["jcr:lastModified"],
        lastModifiedBy: data["jcr:lastModifiedBy"],
        offsetX: data.x,
        offsetY: data.y,
        color: data.color ? data.color : "blue",
        sketches: data.shapes ? data.shapes : {},
        posEl: null
    };
    wireEditable(config);
    annotate.ItemsController.add(new annotate.Annotation(config, undefined, annotate["$container"], config.posEl));
}

function refreshAnnotations(info: AnnotationInfo) {

    // update badge
    let anyUpdate = false;
    if (Granite.author.ui && Granite.author.ui.globalBar) {
        const badge = Granite.author.ui.globalBar.annotationBadge;
        if (badge) {
            const count = info && info.count ? info.count : 0;
            anyUpdate = count > badge._val;
            badge.setValue(count);
            if (count <= 0) {
                badge.setValue(0);
                if (badge.element) {
                    badge.element.find("coral-button-label").empty();
                }
            }
            trace("Updated annotation badge to " + count);
        }
    }

    // update pageInfo
    if (Granite.author.pageInfo) {
        Granite.author.pageInfo.annotations = info && info.components ? info.components : [];
    }

    if (inAnnotationLayer()) {

        // apply annotation data
        const ns = Granite.author.annotate;

        // first load all annotations from server
        const loadedAnnotations = new Map<string, any>();
        const promises = [];
        if (info && info.components) {
            for (const path of info.components) {
                promises.push(ns.persistence.readAnnotations(path)
                    .done((response: any) => {
                        const data = JSON.parse(response);
                        $.each(data, function(name: string, annotation) {
                            if (name != "jcr:primaryType") {
                                if (annotation) {
                                    loadedAnnotations.set(path + CQ_ANNOTATIONS_INFIX + name, annotation);
                                }
                            }
                        });
                    })
                );
            }
        }
        Promise.all(promises).then(() => {
            trace("Updating visible annotations, loaded from server: " + loadedAnnotations.size);
            // Loop through all browser annotations
            // copy because we will modify the source array
            const browserAnnotations = [...ns.ItemsController.items];
            for (const browserAnnotation of browserAnnotations) {
                // skip sketches				
                if (browserAnnotation.annotation || !browserAnnotation._destroy) {
                    trace("Skipping sketch", browserAnnotation);
                    continue;
                }
                const path = browserAnnotation.options.path;
                const loadedAnnotation = loadedAnnotations.get(path);
                if (loadedAnnotation) {
                    // still exists, UPDATE			
                    updateAnnotation(browserAnnotation, loadedAnnotation);
                    // remove from loaded annotations, because it has been processed
                    loadedAnnotations.delete(path);
                } else {
                    // doesn't exist anymore, REMOVE
                    removeAnnotation(browserAnnotation);
                }
            }
            // add remaining server annotations
            loadedAnnotations.forEach((value: any, key: string) => {
                addAnnotation(key, value);
            });
                        
            // TODO Layer.handleItemsBeyondVisibleArea
        });
    } else {
        if (anyUpdate) {
            // display notice
            const w: any = $(window);
            trace("Displaying annotation update notice");
            w.adaptTo("foundation-ui").notify("Annotations", "Updated Annotations are available.", "info");
        }
    }
}
