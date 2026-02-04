/*
 * Copyright 2002-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sonatype.nexus.bootstrap.application;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.inject.Provider;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

public class JavaxProviderDefaultListableBeanFactory
    extends DefaultListableBeanFactory
{
  public JavaxProviderDefaultListableBeanFactory(final BeanFactory beanFactory) {
    super(beanFactory);
  }

  @Override
  @Nullable
  public Object resolveDependency(
      final DependencyDescriptor descriptor,
      @Nullable final String requestingBeanName,
      @Nullable final Set<String> autowiredBeanNames,
      @Nullable final TypeConverter typeConverter) throws BeansException
  {
    descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
    if (Provider.class == descriptor.getDependencyType()) {
      return new ProviderFactory().createDependencyProvider(descriptor, requestingBeanName);
    }
    return super.resolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
  }

  private class ProviderFactory
      implements Serializable
  {

    public Object createDependencyProvider(final DependencyDescriptor descriptor, @Nullable final String beanName) {
      return new ProviderProvider(descriptor, beanName);
    }

    private class ProviderProvider
        implements Provider<Object>, ObjectProvider<Object>
    {

      @Override
      @Nullable
      public Object get() throws BeansException {
        return getValue();
      }

      private final DependencyDescriptor descriptor;

      private final boolean optional;

      @Nullable
      private final String beanName;

      public ProviderProvider(final DependencyDescriptor descriptor, @Nullable final String beanName) {
        this.descriptor = createNestedDependencyDescriptor(descriptor);
        this.optional = (this.descriptor.getDependencyType() == Optional.class);
        this.beanName = beanName;
      }

      @Override
      public Object getObject() throws BeansException {
        if (this.optional) {
          return invokeCreateOptionalDependency(this.descriptor, this.beanName);
        }
        Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
        if (result == null) {
          throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
        }
        return result;
      }

      @Override
      public Object getObject(final Object... args) throws BeansException {
        if (this.optional) {
          return invokeCreateOptionalDependency(this.descriptor, this.beanName, args);
        }
        DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor)
        {
          @Override
          public Object resolveCandidate(
              final String beanName,
              final Class<?> requiredType,
              final BeanFactory beanFactory)
          {
            return beanFactory.getBean(beanName, args);
          }
        };
        Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
        if (result == null) {
          throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
        }
        return result;
      }

      @Override
      @Nullable
      public Object getIfAvailable() throws BeansException {
        try {
          if (this.optional) {
            return invokeCreateOptionalDependency(this.descriptor, this.beanName);
          }
          DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor)
          {
            @Override
            public boolean isRequired() {
              return false;
            }
          };
          return doResolveDependency(descriptorToUse, this.beanName, null, null);
        }
        catch (ScopeNotActiveException ex) {
          // Ignore resolved bean in non-active scope
          return null;
        }
      }

      @Override
      public void ifAvailable(final Consumer<Object> dependencyConsumer) throws BeansException {
        Object dependency = getIfAvailable();
        if (dependency != null) {
          try {
            dependencyConsumer.accept(dependency);
          }
          catch (ScopeNotActiveException ex) {
            // Ignore resolved bean in non-active scope, even on scoped proxy invocation
          }
        }
      }

      @Override
      @Nullable
      public Object getIfUnique() throws BeansException {
        DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor)
        {
          @Override
          public boolean isRequired() {
            return false;
          }

          @Override
          @Nullable
          public Object resolveNotUnique(final ResolvableType type, final Map<String, Object> matchingBeans) {
            return null;
          }
        };
        try {
          if (this.optional) {
            return invokeCreateOptionalDependency(descriptorToUse, this.beanName);
          }
          return doResolveDependency(descriptorToUse, this.beanName, null, null);
        }
        catch (ScopeNotActiveException ex) {
          // Ignore resolved bean in non-active scope
          return null;
        }
      }

      @Override
      public void ifUnique(final Consumer<Object> dependencyConsumer) throws BeansException {
        Object dependency = getIfUnique();
        if (dependency != null) {
          try {
            dependencyConsumer.accept(dependency);
          }
          catch (ScopeNotActiveException ex) {
            // Ignore resolved bean in non-active scope, even on scoped proxy invocation
          }
        }
      }

      @Nullable
      protected Object getValue() throws BeansException {
        if (this.optional) {
          return invokeCreateOptionalDependency(this.descriptor, this.beanName);
        }
        return doResolveDependency(this.descriptor, this.beanName, null, null);
      }

      @Override
      public Stream<Object> stream() {
        return resolveStream(false);
      }

      @Override
      public Stream<Object> orderedStream() {
        return resolveStream(true);
      }

      @SuppressWarnings({"rawtypes", "unchecked"})
      private Stream<Object> resolveStream(final boolean ordered) {
        Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
        return (result instanceof Stream stream ? stream : Stream.of(result));
      }
    }
  }

  private Object invokeCreateOptionalDependency(
      final DependencyDescriptor descriptor,
      @Nullable final String beanName,
      final Object... args)
  {
    try {
      Method method = DefaultListableBeanFactory.class.getDeclaredMethod("createOptionalDependency",
          DependencyDescriptor.class, String.class, Object[].class);

      method.setAccessible(true);
      return method.invoke(this, descriptor, beanName, args);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private DependencyDescriptor createNestedDependencyDescriptor(final DependencyDescriptor descriptor) {
    try {
      for (Class<?> clazz : DefaultListableBeanFactory.class.getDeclaredClasses()) {
        if (clazz.getSimpleName().equals("NestedDependencyDescriptor")) {
          Constructor<?> ctor = clazz.getConstructor(DependencyDescriptor.class);
          ctor.setAccessible(true);
          return (DependencyDescriptor) ctor.newInstance(descriptor);
        }
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    throw new RuntimeException();
  }
}
