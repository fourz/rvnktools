package org.fourz.rvnkcore.service.registry;

import org.fourz.rvnkcore.api.exception.ServiceException;

/**
 * Registry for managing services within the RVNKCore framework.
 * 
 * The ServiceRegistry provides a centralized location for registering,
 * discovering, and managing service instances. It supports both singleton
 * and factory-based service registration patterns.
 */
public interface ServiceRegistry {
    
    /**
     * Registers a service instance with the registry.
     * 
     * @param <T> The service type
     * @param serviceClass The service interface class
     * @param instance The service implementation instance
     * @throws ServiceException If registration fails or service already exists
     */
    <T> void registerService(Class<T> serviceClass, T instance) throws ServiceException;
    
    /**
     * Gets a service instance from the registry.
     * 
     * @param <T> The service type
     * @param serviceClass The service interface class
     * @return The service instance
     * @throws ServiceException If the service is not found or cannot be created
     */
    <T> T getService(Class<T> serviceClass) throws ServiceException;
    
    /**
     * Checks if a service is registered.
     * 
     * @param serviceClass The service interface class
     * @return true if the service is registered, false otherwise
     */
    boolean hasService(Class<?> serviceClass);
    
    /**
     * Unregisters a service from the registry.
     * 
     * @param serviceClass The service interface class
     * @return true if the service was removed, false if it wasn't registered
     */
    boolean unregisterService(Class<?> serviceClass);
    
    /**
     * Gets the names of all registered services.
     * 
     * @return Array of service class names
     */
    String[] getRegisteredServices();
    
    /**
     * Shuts down the service registry and all registered services.
     * 
     * This method should be called during plugin shutdown to ensure
     * all services are properly cleaned up.
     */
    void shutdown();
}
