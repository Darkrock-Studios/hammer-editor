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
 * This dylib exposes 4 C functions JNA can call. Strings returned by
 * create/resolve are heap-allocated and must be freed via
 * hammer_bookmark_free_string. Resolved NSURLs are retained internally
 * so stop_access can find them by path without the caller round-tripping
 * the NSURL pointer.
 */
#import <Foundation/Foundation.h>
#include <stdlib.h>
#include <string.h>

static NSMutableDictionary<NSString *, NSURL *> *gAccessedUrls;
static NSLock *gLock;

__attribute__((constructor))
static void hammer_bookmarks_init(void) {
gAccessedUrls =[[NSMutableDictionary alloc] init];
gLock =[[NSLock alloc] init];
}

static char *copy_cstring(NSString *s) {
if (s == nil) return NULL;
const char *src =[s UTF8String];
if (src == NULL) return NULL;
size_t len = strlen(src) + 1;
char *dst = (char *)malloc(len);
if (dst == NULL) return NULL;
memcpy(dst, src, len);
return dst;
}

const char *hammer_bookmark_create(const char *path) {
if (path == NULL) return NULL;
@autoreleasepool {
NSString *nsPath =[NSString stringWithUTF8String:path];
if (nsPath == nil) return NULL;
NSURL *url =[NSURL fileURLWithPath:nsPath isDirectory:YES];
NSError *error = nil;
NSData *data =[url bookmarkDataWithOptions:NSURLBookmarkCreationWithSecurityScope
includingResourceValuesForKeys:nil
relativeToURL:nil
error:&error];
if (data == nil) {
NSLog(@"[HammerBookmarks] bookmarkData failed for %@: %@", nsPath, error);
return NULL;
}
NSString *b64 =[data base64EncodedStringWithOptions:0];
return copy_cstring(b64);
}
}

const char *hammer_bookmark_resolve_and_start(const char *base64Bookmark, int *outStale) {
if (outStale != NULL) *outStale = 0;
if (base64Bookmark == NULL) return NULL;
@autoreleasepool {
NSString *b64 =[NSString stringWithUTF8String:base64Bookmark];
if (b64 == nil) return NULL;
NSData *data =[[NSData alloc] initWithBase64EncodedString:b64 options:0];
if (data == nil) {
NSLog(@"[HammerBookmarks] bookmark base64 decode failed");
return NULL;
}
BOOL stale = NO;
NSError *error = nil;
NSURL *url =[NSURL URLByResolvingBookmarkData:data
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

NSString *resolvedPath =[url path];
[gLock lock];
gAccessedUrls[resolvedPath] = url;
[gLock unlock];
return copy_cstring(resolvedPath);
}
}

void hammer_bookmark_stop(const char *path) {
if (path == NULL) return;
@autoreleasepool {
NSString *nsPath =[NSString stringWithUTF8String:path];
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
if (s != NULL) free((void *)s);
}
