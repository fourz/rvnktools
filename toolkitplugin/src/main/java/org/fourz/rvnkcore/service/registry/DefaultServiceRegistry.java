package org.fourz.rvnkcore.service.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.fourz.rvnkcore.api.exception.ServiceException;
import org.fourz.rvnkcore.util.log.LogManager;
import org.bukkit.plugin.Plugin;

/**
 * Implementation of the ServiceRegistry interface.
 * Provides service registration, discovery, and lifecycle management.
 */
public class DefaultServiceRegistry implements ServiceRegistry {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final LogManager logger;
    private final Plugin plugin;
    
    public DefaultServiceRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, getClass());
    }
    
    @Override
    public <T> void registerService(Class<T> serviceClass, T instance) throws ServiceException {
        if (serviceClass == null || instance == null) {
            throw new ServiceException("Service class and instance cannot be null");
        }
        
        if (!serviceClass.isInstance(instance)) {
            throw new ServiceException("Service instance must implement the service interface");
        }
        
        if (services.containsKey(serviceClass)) {
            throw new ServiceException("Service already registered: " + serviceClass.getName());
        }
        
        services.put(serviceClass, instance);
        logger.info("Registered service: " + serviceClass.getSimpleName());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) throws ServiceException {
        if (serviceClass == null) {
            throw new ServiceException("Service class cannot be null");
        }
        
        Object service = services.get(serviceClass);
        if (service == null) {
            throw new ServiceException("Service not found: " + serviceClass.getName());
        }
        
        return (T) service;
    }
    
    @Override
    public boolean hasService(Class<?> serviceClass) {
        return serviceClass != null && services.containsKey(serviceClass);
    }
    
    @Override
    public boolean unregisterService(Class<?> serviceClass) {
        if (serviceClass == null || !services.containsKey(serviceClass)) {
            return false;
        }
        
        Object service = services.remove(serviceClass);
        if (service instanceof AutoCloseable) {
            try {
                ((AutoCloseable) service).close();
                logger.info("Service closed: " + serviceClass.getSimpleName());
            } catch (Exception e) {
                logger.error("Error closing service: " + serviceClass.getSimpleName(), e);
            }
        }
        
        logger.info("Unregistered service: " + serviceClass.getSimpleName());
        return true;
    }
    
    @Override
    public String[] getRegisteredServices() {
        return services.keySet().stream()
                .map(Class::getName)
                .toArray(String[]::new);
    }
    
    @Override
    public void shutdown() {
        logger.info("Shutting down ServiceRegistry...");
        
        // Create a copy to avoid concurrent modification
        Class<?>[] serviceClasses = services.keySet().toArray(new Class<?>[0]);
        
        // Shutdown services in reverse order of registration
        for (int i = serviceClasses.length - 1; i >= 0; i--) {
            unregisterService(serviceClasses[i]);
        }
        
        services.clear();
        logger.info("ServiceRegistry shutdown complete");
    }
}