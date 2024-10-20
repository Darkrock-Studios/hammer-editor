# Software Architecture

In theory each layer of the architecture should only reference entities from the layer below it. In general they should avoid accessing entities from the layer they belong to.

This is all a work in progress, so how strictly this is adhered to is in flux.

## Client Architecture

### Multiplatform Code
```mermaid
flowchart TD
	subgraph Common
		direction TB
		Data_Repositories[Data Repositories]
		Application_Components[Application Components]
	end

	subgraph Platform_Specific
		direction TB
		User_Interface[User Interface]
	end

	Data_Repositories --> Application_Components
	Application_Components --> User_Interface
%% Styling for grouping and border thickness
	classDef commonGroup fill: none, stroke: #00ff00, stroke-width: 2px;
	classDef platformSpecificGroup fill: none, stroke: #f8961e, stroke-width: 2px;
	class Common commonGroup;
	class Platform_Specific platformSpecificGroup;
```

The architecture broadly breaks down into two categories, `common` code that compiles and runs on
all supported platforms, and `platform specific` which much be implemented for each of the client
platforms.

The majority of code falls under `common` with only the UI layers and some glue code having to be
reimplemented per platform.

### Architecture Layers
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
