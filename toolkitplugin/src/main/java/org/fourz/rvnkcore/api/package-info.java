/**
 * RVNKCore API Package
 * 
 * This package contains the public API interfaces for the RVNKCore framework.
 * These interfaces define the contracts between RVNKCore and client plugins,
 * ensuring a stable and versioned API for third-party integration.
 * 
 * The API is organized into several sub-packages:
 * - service: Service interfaces for business logic
 * - model: Data transfer objects and value objects
 * - event: Event interfaces for cross-plugin communication
 * - exception: Exception hierarchy for error handling
 * 
 * All implementations of these interfaces are internal to RVNKCore and
 * should not be directly used by client plugins. Instead, use the
 * ServiceRegistry to obtain service instances.
 */
package org.fourz.rvnkcore.api;
