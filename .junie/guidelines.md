# Project Guidelines

This is a monolithic repository for a project containing both Client and Server components
of our application.

## About

This project is a tool for writing stories. It has a multiplatform client, as well as a server component which
can be used to sync data between all of the clients.

## Project Structure

Here is what each of the modules do:

- `base` these are data classes shared by both client and server
- `common` this is the core business logic for the clients. The bulk of the client code lives in here and is platform
  agnostic
- `composeUi` this is the UI layer for the clients, written in Compose Multiplatform
- `desktop` this is the JVM Desktop clients for windows, MacOs and Linux
- `android` this is the Android client
- `server` this is the server component. The server acts mainly as a go-between for syncing data between clients. It
  also has a small web front-end, but is mostly a CRUD API server.

## Testing

We have a growing number of tests, so we should consider if we can write tests as we add new code,
and we should always run our existing tests after making changes.

## Adding String resources

When you add a string resource to any of the `strings.xml` files for the client, you need to re-run
the `generateMR` gradle task to generate the `MR.strings` reference to the new string.