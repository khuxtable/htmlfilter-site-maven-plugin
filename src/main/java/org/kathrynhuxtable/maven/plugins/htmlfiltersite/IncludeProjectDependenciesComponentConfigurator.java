/*
 * Copyright (c) 2010 Kathryn Huxtable.
 *
 * This file is part of the Image Generator Maven plugin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * $Id$
 */
package org.kathrynhuxtable.maven.plugins.htmlfiltersite;

import java.io.File;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.classworlds.ClassRealm;

import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.converters.special.ClassRealmConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

/**
 * A custom ComponentConfigurator which adds the project's runtime classpath
 * elements to the class loader.
 *
 * <p>Taken from
 * http://old.nabble.com/Adding-project-dependencies-and-generated-classes-to-classpath-of-my-plugin-td18624435.html
 * </p>
 *
 * @author             Brian Jackson
 * @since              Aug 1, 2008 3:04:17 PM
 * @plexus.component   role="org.codehaus.plexus.component.configurator.ComponentConfigurator"
 *                     role-hint="include-project-dependencies"
 * @plexus.requirement role="org.codehaus.plexus.component.configurator.converters.lookup.ConverterLookup"
 *                     role-hint="default"
 */
public class IncludeProjectDependenciesComponentConfigurator extends AbstractComponentConfigurator {

    /**
     * @see org.codehaus.plexus.component.configurator.AbstractComponentConfigurator#configureComponent(java.lang.Object,
     *      org.codehaus.plexus.configuration.PlexusConfiguration,
     *      org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator,
     *      org.codehaus.classworlds.ClassRealm,
     *      org.codehaus.plexus.component.configurator.ConfigurationListener)
     */
    public void configureComponent(Object component, PlexusConfiguration configuration, ExpressionEvaluator expressionEvaluator,
            ClassRealm containerRealm, ConfigurationListener listener) throws ComponentConfigurationException {
        addProjectDependenciesToClassRealm(expressionEvaluator, containerRealm);

        converterLookup.registerConverter(new ClassRealmConverter(containerRealm));

        ObjectWithFieldsConverter converter = new ObjectWithFieldsConverter();

        converter.processConfiguration(converterLookup, component, containerRealm.getClassLoader(), configuration,
                                       expressionEvaluator, listener);
    }

    /**
     * Add the project dependencies to the class realm so that they are
     * accessible.
     *
     * @param  expressionEvaluator the expression evaluator.
     * @param  containerRealm      the container realm.
     *
     * @throws ComponentConfigurationException if an error occurs.
     */
    @SuppressWarnings("unchecked")
    private void addProjectDependenciesToClassRealm(ExpressionEvaluator expressionEvaluator, ClassRealm containerRealm)
        throws ComponentConfigurationException {
        List<String> runtimeClasspathElements;

        try {
            // noinspection unchecked
            runtimeClasspathElements = (List<String>) expressionEvaluator.evaluate("${project.runtimeClasspathElements}");
        } catch (ExpressionEvaluationException e) {
            throw new ComponentConfigurationException("There was a problem evaluating: ${project.runtimeClasspathElements}", e);
        }

        // Add the project dependencies to the ClassRealm
        final URL[] urls = buildURLs(runtimeClasspathElements);

        for (URL url : urls) {
            containerRealm.addConstituent(url);
        }
    }

    /**
     * Build an array of URLs representing the project dependencies.
     *
     * @param  runtimeClasspathElements a list of Strings of runtime classpath
     *                                  elements.
     *
     * @return an array of URLs for the classpath elements.
     *
     * @throws ComponentConfigurationException if an error occurs.
     */
    private URL[] buildURLs(List<String> runtimeClasspathElements) throws ComponentConfigurationException {
        // Add the projects classes and dependencies
        List<URL> urls = new ArrayList<URL>(runtimeClasspathElements.size());

        for (String element : runtimeClasspathElements) {
            try {
                final URL url = new File(element).toURI().toURL();

                urls.add(url);
            } catch (MalformedURLException e) {
                throw new ComponentConfigurationException("Unable to access project dependency: " + element, e);
            }
        }

        // Add the plugin's dependencies (so Trove stuff works if Trove isn't on
        return urls.toArray(new URL[urls.size()]);
    }
}
