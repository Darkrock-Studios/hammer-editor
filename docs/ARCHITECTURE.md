# Software Architecture

In theory each layer of the architecture should only reference entities from the layer below it. In general they should avoid accessing entities from the layer they belong to.

This is all a work in progress, so how strictly this is adhered to is in flux.

## Client Architecture

```mermaid
flowchart TD
    UI["fa:fa-desktop UI Layer"]
    Components["fa:fa-cogs Component Layer"]
    Services["fa:fa-server Service Layer"]
    Repositories["fa:fa-database Repository Layer"]
    Datasources["fa:fa-database Datasource Layer"]

    %% Edge connections between nodes
    UI --> Components
    Components --> Services
    Components --> Repositories
    Services --> Repositories
    Repositories --> Datasources

    %% Styling for layers
    style UI fill:#33AB00, stroke:#338800, color:#FFFFFF
    style Components fill:#227700, stroke:#225500, color:#FFFFFF
    style Services fill:#FF3D00, stroke:#FF3D00, color:#FFFFFF
    style Repositories fill:#DD2C00, stroke:#DD2C00, color:#FFFFFF
    style Datasources fill:#6666FC, stroke:#1111AA, color:#FFFFFF
```

### UI
The UI is a dumb and stateless as possible, each platform can have it's own implementation of this layer.

### Component
(_AKA: ViewModel in other architectures_)

These have a one-to-one relationship with UI elements. They are stateful and contain business logic.

### ~~Use Cases (not used at the moment)~~
~~Contains business logic, is stateless and can combine Services and Repositories~~

### Services
These are stateful and may combine multiple Repositories.

### Data Repositories
These use stateful and responsible for transforming, validating, and caching data from the Datasources, and vending it to the layers above.

### Datasource
Stateless entities for accessing Data.

## Server Architecture

```mermaid
flowchart TD
    Routes["fa:fa-desktop Routes"]
    Repositories["fa:fa-database Repository Layer"]
    Datasources["fa:fa-database Datasource Layer"]

    %% Edge connections between nodes
    Routes --> Repositories
    Repositories --> Datasources

    %% Styling for layers
    style Routes fill:#33AB00, stroke:#338800, color:#FFFFFF
    style Repositories fill:#DD2C00, stroke:#DD2C00, color:#FFFFFF
    style Datasources fill:#6666FC, stroke:#1111AA, color:#FFFFFF
```

### Routes
These are the HTTP handlers that define the various endpoints. They unmarshal data from HTTP requests, call into Repositories, and then marshal data back into HTTP responses.

### Repositories
These use stateful and responsible for transforming, validating, and caching data from the Datasources, and vending it to the layers above.

### Datasource
Stateless classes for accessing Data.