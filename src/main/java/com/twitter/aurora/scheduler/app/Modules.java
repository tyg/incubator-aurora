package com.twitter.aurora.scheduler.app;

import com.google.inject.Module;
import com.google.inject.PrivateModule;

/**
 * A utility class for managing guice modules.
 */
final class Modules {

  private Modules() {
    // Utility class
  }

  private static Module instantiateModule(final Class<? extends Module> moduleClass) {
    try {
      return moduleClass.newInstance();
    } catch (InstantiationException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to instantiate module %s. Are you sure it has a no-arg constructor?",
              moduleClass.getName()),
          e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException(
          String.format(
              "Failed to instantiate module %s. Are you sure it's public?",
              moduleClass.getName()),
          e);
    }
  }

  // Defensively wrap each module provided on the command-line in a PrivateModule that only
  // exposes requested classes to ensure that we don't depend on surprise extra bindings across
  // different implementations.
  static Module wrapInPrivateModule(
      Class<? extends Module> moduleClass,
      final Iterable<Class<?>> exposedClasses) {

    final Module module = instantiateModule(moduleClass);
    return new PrivateModule() {
      @Override protected void configure() {
        install(module);
        for (Class<?> klass : exposedClasses) {
          expose(klass);
        }
      }
    };
  }

  static Module getModule(Class<? extends Module> moduleClass) {
    return instantiateModule(moduleClass);
  }
}
