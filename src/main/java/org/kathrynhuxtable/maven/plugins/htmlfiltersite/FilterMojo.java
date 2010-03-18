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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

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
     * @parameter expression="${htmlfiltersite.filterMacros}"
     *            default-value="${basedir}/src/site/htmlfiltersite-filter.vm"
     */
    private File filterMacros;

    /**
     * Velocity template.
     *
     * @parameter expression="${htmlfiltersite.filterProperties}"
     *            default-value="${basedir}/src/site/htmlfiltersite-filter.properties"
     */
    private File filterProperties;

    /**
     * Location of the source directory.
     *
     * @parameter expression="${htmlfiltersite.sourceDirectory}"
     *            default-value="${basedir}/src/site/html"
     */
    private File sourceDirectory;

    /**
     * Location of the output directory.
     *
     * @parameter expression="${htmlfiltersite.targetDirectory}"
     *            default-value="${project.build.directory}/generated-site/resources"
     */
    private File targetDirectory;

    /**
     * Match pattern for the files to be processed..
     *
     * @parameter expression="${htmlfiltersite.filePattern}"
     *            default-value="**\/*.html"
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
     * Set the target directory.
     *
     * @param targetDirectory the targetDirectory to set
     */
    public void setTargetDirectory(File targetDirectory) {
        this.targetDirectory = targetDirectory;
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
     * Set the filter macros file.
     *
     * @param filterMacros the filterMacros to set
     */
    public void setFilterMacros(File filterMacros) {
        this.filterMacros = filterMacros;
    }

    /**
     * Set the filter properties file.
     *
     * @param filterProperties the filterProperties to set
     */
    public void setFilterProperties(File filterProperties) {
        this.filterProperties = filterProperties;
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

        Template template = null;

        try {
            template = ve.getTemplate(getRelativeFilePath(new File(System.getProperty("user.dir")), filterMacros));
        } catch (ResourceNotFoundException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to locate template " + "htmlfiltersite.vm", e);
        } catch (ParseErrorException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Problem parsing the template", e);
        } catch (MethodInvocationException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Something invoked in the template threw an exception", e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Some random template parsing error occurred", e);
        }

        AttributeMap filterProps = parseConfigFile(filterProperties);

        AttributeMap attributes = new AttributeMap();

        if (attributes.get("project") == null) {
            attributes.put("project", project);
        }

        // Put any of the properties in directly into the Velocity attributes
        attributes.putAll(project.getProperties());

        attributes.put("currentDate", new Date());

        attributes.put("lastPublished", new SimpleDateFormat("dd MMM yyyy").format(new Date()));

        List<String> fileList = getFileList();

        for (String file : fileList) {
            filterFile(file, ve, template, attributes, filterProps);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  file
     * @param  ve          TODO
     * @param  template    ve
     * @param  attributes
     * @param  filterProps TODO
     *
     * @throws MojoExecutionException
     */
    private void filterFile(String file, VelocityEngine ve, Template template, AttributeMap attributes, AttributeMap filterProps)
        throws MojoExecutionException {
        File           sourceFile = new File(sourceDirectory, file);
        File           targetFile = new File(targetDirectory, file);
        FileWriter     fileWriter = null;
        BufferedReader fileReader = null;

        VelocityContext context = createContext(sourceFile, attributes);

        try {
            StringWriter sw = null;
            for (Object key : filterProps.keySet()) {
                sw = new StringWriter();
                if (!ve.evaluate(context, sw, "htmlfilter-site", new StringReader((String) filterProps.get(key)))) {
                    throw new MojoExecutionException("Unable to evaluate filter property " + key);
                }
                context.put((String) key, sw.toString());
            }

            fileReader = new BufferedReader(new FileReader(sourceFile));

            sw = new StringWriter();

            if (!ve.evaluate(context, sw, "htmlfilter-site", fileReader)) {
                throw new MojoExecutionException("Unable to evaluate html file " + sourceFile);
            }

            context.put("fileContent", sw.toString());

            fileWriter = new FileWriter(targetFile);

            template.merge(context, fileWriter);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to merge Velocity", e);
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException e) {
                }
            }

            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<String> getFileList() {
        List<String> fileList = null;

        try {
            fileList = FileUtils.getFileNames(sourceDirectory, filePattern, "", false, true);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return fileList;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  oldPath DOCUMENT ME!
     * @param  newPath DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private String getRelativeFilePath(File oldPath, File newPath) {
        List<String> names = new ArrayList<String>();

        oldPath = oldPath.getAbsoluteFile();
        newPath = newPath.getAbsoluteFile();

        while (newPath != null && !newPath.equals(oldPath)) {
            names.add(newPath.getName());
            newPath = newPath.getParentFile();
        }

        if (newPath == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = names.size() - 1; i >= 0; i--) {
            if (result.length() > 0) {
                result.append('/');
            }

            result.append(names.get(i));
        }

        return result.toString();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  sourceFile DOCUMENT ME!
     * @param  attributes siteRenderingContext DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private VelocityContext createContext(File sourceFile, AttributeMap attributes) {
        VelocityContext context = new VelocityContext();

        // ----------------------------------------------------------------------
        // Data objects
        // ----------------------------------------------------------------------

        context.put("relativePath", ".");

        String currentFileName = sourceFile.getName();

        context.put("currentFileName", currentFileName);

        context.put("alignedFileName", PathTool.calculateLink(currentFileName, ".")); // renderingContext.getRelativePath()));

        // Add global properties.
        if (attributes != null) {
            for (Object o : attributes.keySet()) {
                context.put((String) o, attributes.get(o));
            }
        }

        // ----------------------------------------------------------------------
        // Tools
        // ----------------------------------------------------------------------

        context.put("PathTool", new PathTool());

        context.put("FileUtils", new FileUtils());

        context.put("StringUtils", new StringUtils());

        return context;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  filename DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     *
     * @throws MojoExecutionException DOCUMENT ME!
     */
    private AttributeMap parseConfigFile(File filename) throws MojoExecutionException {
        AttributeMap list         = new AttributeMap();
        InputStream  configStream = null;
        Document     doc          = null;

        try {
            configStream = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to open properties file");
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder        db  = dbf.newDocumentBuilder();

            doc = db.parse(configStream);
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to parse XML properties file " + filename, e);
        } finally {
            try {
                configStream.close();
            } catch (IOException e) {
            }
        }

        doc.getDocumentElement().normalize();
        NodeList nodeList = doc.getFirstChild().getChildNodes();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element elem  = (Element) node;
                Node    child = elem.getFirstChild();
                String  value = null;

                if (child.getNodeType() == Node.TEXT_NODE) {
                    value = ((Text) child).getData();
                } else if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                    value = ((CDATASection) child).getData();
                }

                list.put(elem.getTagName(), value);
            }
        }

        return list;
    }

    /**
     * Simplify references to the attribute hash map.
     */
    private static class AttributeMap extends HashMap<Object, Object> {
        private static final long serialVersionUID = 1787343499009497124L;
    }
}
