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
import java.io.StringWriter;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.PathTool;
import org.codehaus.plexus.util.StringUtils;

/**
 * Goal runs Velocity on the files in the specified directory.
 *
 * @description                  Runs Velocity on the files in the specified
 *                               directory.
 * @goal                         filter
 * @phase                        pre-site
 * @requiresDependencyResolution runtime
 * @configurator                 include-project-dependencies
 */
public class FilterMojo extends AbstractMojo {

    /**
     * Velocity template.
     *
     * @parameter expression="${htmlfiltersite.velocityTemplate}"
     *            default-value="${basedir}/src/site/htmlfiltersite.vm"
     */
    private File velocityTemplate;

    /**
     * Location of the source directory.
     *
     * @parameter expression="${htmlfiltersite.velocitySourceDirectory}"
     *            default-value="${basedir}/src/site/html"
     */
    private File sourceDirectory;

    /**
     * Location of the output directory.
     *
     * @parameter expression="${htmlfiltersite.velocityOutputDirectory}"
     *            default-value="${project.build.directory}/generated-site/resources"
     */
    private File outputDirectory;

    /**
     * Match pattern for the files to be processed..
     *
     * @parameter expression="${htmlfiltersite.velocityFilePattern}"
     *            default-value="*.html"
     */
    private String filePattern;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * Set the source directory.
     *
     * @param sourceDirectory the sourceDirectory to set
     */
    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    /**
     * Set the output directory.
     *
     * @param outputDirectory the outputDirectory to set
     */
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Set the file pattern.
     *
     * @param filePattern the filePattern to set
     */
    public void setFilePattern(String filePattern) {
        this.filePattern = filePattern;
    }

    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute()
     */
    public void execute() throws MojoExecutionException {
        VelocityEngine ve = new VelocityEngine();

        try {
            ve.init();
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to initialize Velocity engine", e);
        }

        VelocityContext context = new VelocityContext();

        Map attributes = new HashMap();

        if (attributes.get("project") == null) {
            attributes.put("project", project);
        }

// if (attributes.get("inputEncoding") == null) {
// attributes.put("inputEncoding", getInputEncoding());
// }
//
// if (attributes.get("outputEncoding") == null) {
// attributes.put("outputEncoding", getOutputEncoding());
// }

        attributes.put("outputEncoding", "UTF-8");

        // Put any of the properties in directly into the Velocity attributes
        attributes.putAll(project.getProperties());

        attributes.put("currentDate", new Date());

        attributes.put("lastPublished", new SimpleDateFormat("dd MMM yyyy").format(new Date()));

        Locale locale = Locale.getDefault(); // siteRenderingContext.getLocale();

        attributes.put("locale", locale);

        context = createContext(attributes);

        Template template = null;

        try {
            template = ve.getTemplate("src/site/htmlfiltersite.vm");
        } catch (ResourceNotFoundException rnfe) {
            rnfe.printStackTrace();
            throw new MojoExecutionException("couldn't find the template");
        } catch (ParseErrorException pee) {
            pee.printStackTrace();
            throw new MojoExecutionException("problem parsing the template");
        } catch (MethodInvocationException mie) {
            throw new MojoExecutionException("something invoked in the template threw an exception");
        } catch (Exception e) {
            throw new MojoExecutionException("Random exception");
        }

        StringWriter sw = new StringWriter();

        try {
            template.merge(context, sw);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to merge Velocity", e);
        }

        getLog().info(sw.toString());
    }

    /**
     * DOCUMENT ME!
     *
     * @param  attributes siteRenderingContext DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private VelocityContext createContext(Map attributes) {
        VelocityContext context = new VelocityContext();

        // ----------------------------------------------------------------------
        // Data objects
        // ----------------------------------------------------------------------

        context.put("relativePath", ".");

        String title = "";

        title = "doc title"; // sink.getTitle()

        context.put("title", title);

        context.put("bodyContent", "my body"); // sink.getBody()

        String currentFileName = "downloads.html"; // renderingContext.getOutputName().replace('\\', '/');

        context.put("currentFileName", currentFileName);

        context.put("alignedFileName", PathTool.calculateLink(currentFileName, ".")); // renderingContext.getRelativePath()));

        // Add user properties
        if (attributes != null) {
            for (Iterator i = attributes.keySet().iterator(); i.hasNext();) {
                String key = (String) i.next();

                context.put(key, attributes.get(key));
            }
        }

        // ----------------------------------------------------------------------
        // Tools
        // ----------------------------------------------------------------------

        context.put("PathTool", new PathTool());

        context.put("FileUtils", new FileUtils());

        context.put("StringUtils", new StringUtils());

// context.put("i18n", i18n);

        return context;
    }
}
