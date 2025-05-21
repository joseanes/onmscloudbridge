# OpenNMS Cloud Bridge - Core Principles and Architecture

## IMPORTANT: Claude's Operational Protocol

1. **READ THIS FILE FIRST**: At the beginning of each session, read this entire file first
2. **UPDATE THIS FILE**: After each session, update this file with new learnings without prompting
3. **PRESERVE WORKING CODE**: Never break or remove functioning code - especially auto-start capabilities
4. **THOROUGH TESTING**: Test all changes against the core objectives listed below
5. **INCREMENTAL CHANGES**: Make small, testable changes rather than major rewrites.
6.  Do not commit to github until asked to do so.

## Primary Objectives

1. **Auto-Starting Architecture**: The application MUST automatically start discovery and collection processes on startup if providers are configured. NO MANUAL INTERVENTION should be required to resume operations after restart.

2. **Cloud Infrastructure Monitoring**: The application bridges cloud providers (AWS, Azure, GCP) to OpenNMS monitoring system by:
   - Discovering cloud resources (VMs, instances, etc.) automatically
   - Collecting metrics (CPU, memory, network, etc.) on schedule
   - Sending the data to OpenNMS for monitoring without user intervention

3. **Persistent Configuration**: All configuration is preserved between restarts:
   - Cloud provider credentials and settings
   - Discovery and collection schedules
   - Monitored resources
   - OpenNMS connection settings
   - User preferences and settings

## Architectural Principles

1. **Service-Oriented Design**:
   - Clear separation between API interfaces and implementations
   - Provider-based design for extensibility
   - Scheduled services for continuous operation

2. **Web-Based Management**:
   - RESTful API for all operations
   - Web UI for configuration and status monitoring
   - Comprehensive logging for operations tracking

3. **Core Components**:
   - **CloudProvider**: Interface for cloud provider implementations
   - **DiscoveryService**: Discovers resources in cloud providers
   - **CollectionService**: Collects metrics from cloud resources
   - **SchedulerService**: Manages scheduled discovery and collection
   - **CollectionLogService**: Stores and retrieves collection logs
   - **DiscoveryLogService**: Stores and retrieves discovery logs

## Implementation Requirements

1. **API Paths**: All API endpoints MUST use the `/bridge/api/` context path prefix
   - All REST controllers are accessible with the `/bridge/api/` prefix automatically via Spring's PathMatchConfigurer
   - Frontend calls to `/bridge/api/[resource]/...`
   - Configuration in application.yml: `server.servlet.context-path: /bridge`
   - Path prefix configuration in WebConfig.java: `configurer.addPathPrefix("api", HandlerTypePredicate.forAnnotation(RestController.class));`
Based on my analysis of the frontend code and backend controllers, here's what I found about
  API URLs in the project:

  1. API URL Structure:
    - The base context path is /bridge (defined in application.yml)
    - All API endpoints use /bridge/api/ prefix
    - Each controller has its own specific path
    - Controllers can be mapped with simple paths like "/opennms" and Spring automatically prefixes them with "/api/"
  2. Main API endpoints used in the frontend:
    - /bridge/api/dashboard/summary - Dashboard data
    - /bridge/api/cloud-providers - Cloud provider management
    - /bridge/api/discovery/jobs - Discovery jobs
    - /bridge/api/collection/jobs - Collection jobs
    - /bridge/api/opennms/connection - OpenNMS connection
    - /bridge/api/opennms/test-connection - Test OpenNMS connection
    - /bridge/api/discovery/start?providerId={id} - Start discovery
    - /bridge/api/collection/start?providerId={id} - Start collection
    - /bridge/api/discovery/logs/{provider} - Discovery logs
    - /bridge/api/collection/logs/{provider} - Collection logs
  3. Controller Mappings:
    - CloudProviderController: /cloud-providers (accessible via /bridge/api/cloud-providers)
    - DiscoveryController: /discovery (accessible via /bridge/api/discovery)
    - DashboardController: /dashboard (accessible via /bridge/api/dashboard)
    - CollectionController: /collection (accessible via /bridge/api/collection)
    - OpenNMSController: /opennms (accessible via /bridge/api/opennms)
    - DiscoveryLogController: /discovery-logs (accessible via /bridge/api/discovery-logs)
    - CollectionLogController: /collection/logs (accessible via /bridge/api/collection/logs)

2. **UI Features**:
   - Discovery and collection status dashboards with real-time updates
   - Provider configuration screens with credential management
   - Resource display with filtering and sorting capabilities
   - Logs and status history with modal dialogs for detailed views
   - Error handling with informative user feedback

3. **JavaScript Patterns**:
   - Modal dialogs created dynamically with proper styling
   - Tables for displaying logs and operational data
   - Color-coding for status indicators (success, error, warning)
   - Asynchronous API interactions with loading indicators
   - Consistent error handling and display

4. **Critical Features to Preserve**:
   - Auto-start capability for discovery and collection
   - Collection logs modal functionality
   - Discovery logs modal functionality
   - Provider configuration persistence
   - Scheduled operations management

## Testing Principles

1. Before changing any code, always understand the entire workflow and component interaction
2. Test changes thoroughly to ensure they don't disrupt existing functionality
3. Begin by reading and understanding existing code patterns before writing new ones
4. Focus on preserving automatic startup and scheduling capabilities
5. When fixing one component, ensure all related components still work correctly

## Common Commands

- Run application: `java -jar target/bridge-webapp-1.0.0-SNAPSHOT.jar`
- Build project: `mvn clean package`
- Run tests: `mvn test`

## Lessons Learned

1. **Context Path Awareness**: Always maintain the `/bridge` context path in all URLs and controller mappings
2. **API Path Prefixing**: The application uses Spring's `PathMatchConfigurer` to automatically prefix all REST controllers with "/api/", making them accessible at "/bridge/api/*" paths
3. **Modal Implementation**: Modals require both JavaScript functionality and proper CSS styling to work
4. **Error Resilience**: Frontend code should handle backend errors gracefully with useful messages
5. **Existing Code Respect**: Always understand the code you're modifying before making changes
6. **Auto-Start Priority**: Never disable or break the automatic discovery and collection startup
7. **Testing Before Committing**: Always test changes in the actual application before considering them complete
8. **Provider ID Normalization**: The application uses several naming schemes for the same providers. Always normalize provider IDs using the `normalizeProviderId` method in MockCollectionService to prevent duplicates.
9. **Collection Settings Persistence**: Provider collection intervals are stored in "config/provider-settings.properties" using the ProviderSettingsService. The file must be properly accessed and updated to maintain intervals between restarts.
10. **Provider Registry Management**: Cloud providers are registered in the BeanConfig class, but collection settings management happens across several services - always consult the MockCollectionService for interval handling.
11. **Double Path Prefixing**: Be careful to avoid double-prefixing paths in controller mappings. The frontend expects "/bridge/api/*" but controllers should use simple paths like "/opennms" since Spring will automatically add the "/api" prefix via WebConfig.
12. **Integration Configuration**: The OpenNMSController has an IntegrationConfig class that handles various application settings including the "disableMockProviders" setting which toggles between mock and real providers. Any properties sent from the frontend must have corresponding fields in the backend model.
13. **Persistent Integration Configuration**: The application uses IntegrationConfigService to persist integration settings between restarts. Settings are stored in "config/integration-config.properties" and reloaded on startup.
14. **Dynamic Provider Management**: When toggling between mock and real providers with the "disableMockProviders" setting, the provider list is updated at runtime and the setting is persisted for application restarts.

## Common Issues to Avoid

1. **Incorrect API Paths**: Using `/api/` prefix in controller mappings when it's already added by Spring's PathMatchConfigurer
2. **Missing WebConfig Configuration**: Removing or modifying the `configurePathMatch` method in WebConfig, which is essential for API path routing
3. **Missing Modal Styles**: Creating modals without proper CSS styling
4. **Breaking Auto-Start**: Modifying the startup process in a way that prevents automatic operations
5. **Incompatible Changes**: Making UI changes that break backend integration
6. **Overriding Working Code**: Replacing functional code with incomplete implementations
7. **Duplicate Provider Entries**: Not normalizing provider IDs, causing duplicate entries in collection/discovery tables
8. **Lost Collection Settings**: Not persisting provider-specific collection intervals properly
9. **Lost Integration Settings**: Not persisting integration configuration between restarts

## Provider and Collection Settings Architecture

### Provider Registration

1. **Cloud Provider Interface**: All providers implement the CloudProvider interface
2. **Provider Registration**: Cloud providers are registered in BeanConfig.cloudProviders
3. **Provider IDs**: 
   - AWS default provider: `aws-default`
   - Mock AWS provider: `aws-mock`
   - Legacy provider IDs that should be normalized: `awsCloudProvider`, `mockAwsCloudProvider`

### Collection Settings Management

1. **Provider-Specific Settings**:
   - Intervals stored in `config/provider-settings.properties`
   - Format: `{providerId}.collectionInterval=15` (for 15 minutes)
   - Managed by `ProviderSettingsService`

2. **Settings Flow**:
   - Updates go to `CollectionController.updateProviderSchedule()`
   - This calls `schedulerService.updateProviderCollectionInterval()`
   - Also persisted via `providerSettingsService.setCollectionInterval()`
   - Settings are loaded on startup and read during collection scheduling

3. **Provider ID Normalization**:
   - Critical for preventing duplicates in collection status UI
   - Implemented in `MockCollectionService.normalizeProviderId()`
   - Maps special/legacy provider IDs to standard ones
 
### Important Implementation Notes:

1. Collection interval settings are managed in three places:
   - In-memory in `MockCollectionService.providerIntervals` Map
   - On disk in `config/provider-settings.properties`
   - In SchedulerService for controlling actual scheduling

2. When updating collection intervals, all three need to be updated.

3. Provider ID normalization is essential for consistent operation and preventing duplicate UI entries.