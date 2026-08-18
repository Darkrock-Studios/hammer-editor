/*
 * Security-scoped bookmark helper for the sandboxed Mac App Store build.
 *
 * Apple requires apps in the Mac App Store to save user files outside the
 * app container, in a location the user picked (App Review 2.4.5(i)). The
 * user-selected.read-write entitlement grants in-process access once a
 * folder is picked via NSOpenPanel, but that access doesn't survive
 * relaunch. To regain access on the next launch we have to persist a
 * security-scoped bookmark (NSURL bookmarkData with the security-scope
 * option) and resolve it at startup.
 *
 * This dylib exposes C functions JNA can call. Strings returned by
 * create/resolve/pick are heap-allocated and must be freed via
 * hammer_bookmark_free_string. Resolved NSURLs are retained internally
 * so stop_access can find them by path without the caller round-tripping
 * the NSURL pointer.
 *
 * It also hosts the first-run folder picker and its alerts. Those are
 * deliberately native rather than AWT/Swing: they run before the Compose
 * application starts, and initializing AWT first permanently wedges the
 * Tao backend's event loop (see hammer_dialog_pick_directory).
 */
#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

static NSMutableDictionary<NSString *, NSURL *> *
gAccessedUrls;
static NSLock *gLock;

__attribute__((constructor))
static void hammer_bookmarks_init(void) {
    gAccessedUrls = [[NSMutableDictionary alloc] init];
    gLock = [[NSLock alloc] init];
}

static char *copy_cstring(NSString *s) {
    if (s == nil) return NULL;
    const char *src = [s UTF8String];
    if (src == NULL) return NULL;
    size_t len = strlen(src) + 1;
    char *dst = (char *) malloc(len);
    if (dst == NULL) return NULL;
    memcpy(dst, src, len);
    return dst;
}

const char *hammer_bookmark_create(const char *path) {
    if (path == NULL) return NULL;
    @autoreleasepool {
        NSString *nsPath = [NSString stringWithUTF8String:path];
        if (nsPath == nil) return NULL;
        NSURL *url = [NSURL fileURLWithPath:nsPath isDirectory:YES];
        NSError *error = nil;
        NSData *data = [url bookmarkDataWithOptions:NSURLBookmarkCreationWithSecurityScope
                     includingResourceValuesForKeys:nil
                                      relativeToURL:nil
                                              error:&error];
        if (data == nil) {
            NSLog(@"[HammerBookmarks] bookmarkData failed for %@: %@", nsPath, error);
            return NULL;
        }
        NSString *b64 = [data base64EncodedStringWithOptions:0];
        return copy_cstring(b64);
    }
}

const char *hammer_bookmark_resolve_and_start(const char *base64Bookmark, int *outStale) {
    if (outStale != NULL) *outStale = 0;
    if (base64Bookmark == NULL) return NULL;
    @autoreleasepool {
        NSString *b64 = [NSString stringWithUTF8String:base64Bookmark];
        if (b64 == nil) return NULL;
        NSData *data = [[NSData alloc] initWithBase64EncodedString:b64 options:0];
        if (data == nil) {
            NSLog(@"[HammerBookmarks] bookmark base64 decode failed");
            return NULL;
        }
        BOOL stale = NO;
        NSError *error = nil;
        NSURL *url = [NSURL URLByResolvingBookmarkData:data
                                               options:NSURLBookmarkResolutionWithSecurityScope
                                         relativeToURL:nil
                                   bookmarkDataIsStale:&stale
                                                 error:&error];
        if (url == nil) {
            NSLog(@"[HammerBookmarks] URLByResolvingBookmarkData failed: %@", error);
            return NULL;
        }
        if (![url startAccessingSecurityScopedResource]) {
            NSLog(@"[HammerBookmarks] startAccessingSecurityScopedResource returned NO for %@", url);
            return NULL;
        }
        if (outStale != NULL) *outStale = stale ? 1 : 0;

        NSString *resolvedPath = [url path];
        [gLock lock];
        gAccessedUrls[resolvedPath] = url;
        [gLock unlock];
        return copy_cstring(resolvedPath);
    }
}

void hammer_bookmark_stop(const char *path) {
    if (path == NULL) return;
    @autoreleasepool {
        NSString *nsPath = [NSString stringWithUTF8String:path];
        if (nsPath == nil) return;
        NSURL *url = nil;
        [gLock lock];
        url = gAccessedUrls[nsPath];
        [gAccessedUrls removeObjectForKey:nsPath];
        [gLock unlock];
        if (url != nil) {
            [url stopAccessingSecurityScopedResource];
        }
    }
}

void hammer_bookmark_free_string(const char *s) {
    if (s != NULL) free((void *) s);
}

#pragma mark - First-run dialogs

/*
 * AppKit refuses to instantiate an NSWindow anywhere but thread 0, and both
 * dialogs below are NSWindows underneath. The JVM's `main` thread is not
 * thread 0 — on macOS the java launcher runs main on a pthread of its own
 * (JavaMain/ThreadJavaMain) and parks thread 0 in a CFRunLoop, which is what
 * makes AppKit usable at all. So we hop onto the main queue and block until
 * the user answers. That parked run loop is what services the queue; without
 * it this dispatch_sync would deadlock rather than return.
 */
static void run_on_main_sync(dispatch_block_t block) {
    if (pthread_main_np()) {
        block();
    } else {
        dispatch_sync(dispatch_get_main_queue(), block);
    }
}

/*
 * NSAlert and NSOpenPanel need an NSApplication instance and a foreground
 * activation policy to appear and take keyboard focus. Creating NSApp is all
 * we do — the main run loop is left untouched so the Tao backend can claim it
 * once startup finishes. (Letting AWT create NSApp instead is what breaks Tao:
 * AWT parks the AppKit run loop on its own thread, and Tao's loop on the main
 * thread then never receives events, so the app hangs with no window.)
 */
static void ensure_app(void) {
    [NSApplication sharedApplication];
    [NSApp setActivationPolicy:NSApplicationActivationPolicyRegular];
    [NSApp activateIgnoringOtherApps:YES];
}

static NSString *ns_or_nil(const char *s) {
    return s == NULL ? nil : [NSString stringWithUTF8String:s];
}

/*
 * Modal two-button alert. Returns 1 when the user chose the primary button
 * and 0 for the secondary button.
 */
int hammer_dialog_confirm(const char *title,
                          const char *message,
                          const char *primaryButton,
                          const char *secondaryButton) {
    __block int result = 0;
    run_on_main_sync(^{
        @autoreleasepool {
            ensure_app();
            NSAlert *alert = [[NSAlert alloc] init];
            alert.alertStyle = NSAlertStyleInformational;
            NSString *t = ns_or_nil(title);
            NSString *m = ns_or_nil(message);
            if (t != nil) alert.messageText = t;
            if (m != nil) alert.informativeText = m;
            NSString *primary = ns_or_nil(primaryButton);
            NSString *secondary = ns_or_nil(secondaryButton);
            if (primary != nil) [alert addButtonWithTitle:primary];
            if (secondary != nil) [alert addButtonWithTitle:secondary];
            // Buttons come back in the order added, starting at NSAlertFirstButtonReturn.
            result = ([alert runModal] == NSAlertFirstButtonReturn) ? 1 : 0;
        }
    });
    return result;
}

/*
 * Modal directory chooser. Returns the selected path (caller frees via
 * hammer_bookmark_free_string) or NULL if the user cancelled.
 *
 * Under the sandbox this is the Powerbox panel, and it is the only thing that
 * can widen our file access — picking here is what makes the subsequent
 * hammer_bookmark_create call succeed.
 */
const char *hammer_dialog_pick_directory(const char *title,
                                         const char *message,
                                         const char *prompt) {
    __block char *result = NULL;
    run_on_main_sync(^{
        @autoreleasepool {
            ensure_app();
            NSOpenPanel *panel = [NSOpenPanel openPanel];
            panel.canChooseFiles = NO;
            panel.canChooseDirectories = YES;
            panel.allowsMultipleSelection = NO;
            panel.canCreateDirectories = YES;
            NSString *t = ns_or_nil(title);
            NSString *m = ns_or_nil(message);
            NSString *p = ns_or_nil(prompt);
            if (t != nil) panel.title = t;
            if (m != nil) panel.message = m;
            if (p != nil) panel.prompt = p;
            // Deliberately no directoryURL: NSHomeDirectory() is the sandbox
            // container, and the panel already defaults to the real ~/Documents.
            if ([panel runModal] != NSModalResponseOK) return;
            NSURL *url = panel.URL;
            if (url == nil) return;
            result = copy_cstring([url path]);
        }
    });
    return result;
}
