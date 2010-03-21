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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import org.jdom.input.SAXBuilder;

import org.jdom.output.Format;

import org.jdom.xpath.XPath;

/**
 * Goal runs Velocity on the files in the specified directory.
 *
 * @description                  Runs Velocity on the files in the specified
 *                               directory.
 * @goal                         filter
 * @phase                        pre-site
 * @requiresDependencyResolution runtime
 */
public class FilterMojo extends AbstractMojo {

    /**
     * Velocity template for filtering.
     *
     * @parameter expression="${htmlfiltersite.filterTemplate}"
     *            default-value="${basedir}/src/site/filter-template.vm"
     */
    private File filterTemplate;

    /**
     * Velocity template.
     *
     * @parameter expression="${htmlfiltersite.filterProperties}"
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
     * @param filterMacros filterTemplate the filterTemplate to set
     */
    public void setFilterMacros(File filterMacros) {
        this.filterTemplate = filterMacros;
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
            template = ve.getTemplate(getRelativeFilePath(new File(System.getProperty("user.dir")), filterTemplate));
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

        AttributeMap filterProps;

        if (filterProperties != null) {
            filterProps = parseConfigFile(filterProperties);
        } else {
            filterProps = new AttributeMap();
        }

        AttributeMap attributes = new AttributeMap();

        if (attributes.get("project") == null) {
            attributes.put("project", project);
        }

        attributes.put("outputEncoding", "UTF-8");

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
        File       sourceFile = new File(sourceDirectory, file);
        File       targetFile = new File(targetDirectory, file);
        FileWriter fileWriter = null;
        Reader     fileReader = null;

        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

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

            fileReader = new InputStreamReader(new FileInputStream(sourceFile), "UTF-8");
            sw         = new StringWriter();

            if (!ve.evaluate(context, sw, "htmlfilter-site", fileReader)) {
                throw new MojoExecutionException("Unable to evaluate html file " + sourceFile);
            }

            Document doc = parseXHTMLDocument(new StringReader(sw.toString()));

            context.put("title", getTitle(doc));

            String bodyContent = getBodyContent(doc);

            context.put("bodyContent", bodyContent);

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
        VelocityContext context      = new VelocityContext();

        // ----------------------------------------------------------------------
        // Data objects
        // ----------------------------------------------------------------------

        String          relativePath = PathTool.getRelativePath(sourceDirectory.getPath(), sourceFile.getPath());

        context.put("relativePath", relativePath);

        String currentFileName = sourceFile.getName();

        context.put("currentFileName", currentFileName);

        context.put("alignedFileName", PathTool.calculateLink(getRelativeFilePath(sourceDirectory, sourceFile), relativePath));

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
     * Parse a document from text in the reader.
     *
     * @param  reader the Reader from which to parse the document.
     *
     * @return the JDom document parsed from the XHTML file.
     *
     * @throws IOException   if the reader cannot be read.
     * @throws JDOMException if the reader cannot be parsed.
     */
    private Document parseXHTMLDocument(Reader reader) throws JDOMException, IOException {
        Document document = null;

        SAXBuilder builder = new SAXBuilder();

        builder.setEntityResolver(new DTDHandler());
        builder.setIgnoringElementContentWhitespace(true);
        builder.setIgnoringBoundaryWhitespace(true);
        document = builder.build(reader);

        return document;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  document DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private String getTitle(Document document) {
        Element element = null;

        element = selectSingleNode(document.getRootElement(), "/xhtml:html/xhtml:head/xhtml:title");
        if (element == null) {
            return null;
        }

        return element.getText();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  document DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private String getBodyContent(Document document) {
        Element element = null;

        element = selectSingleNode(document.getRootElement(), "/xhtml:html/xhtml:body");
        if (element == null) {
            return null;
        }

        return getElementContentsAsText(element);
    }

    /**
     * DOCUMENT ME!
     *
     * @param  element
     *
     * @return
     */
    private String getElementContentsAsText(Element element) {
        StringBuilder text     = new StringBuilder();
        HTMLOutputter writer   = new HTMLOutputter(Format.getPrettyFormat().setTextMode(Format.TextMode.TRIM_FULL_WHITE)
                                                       .setExpandEmptyElements(true));
        List<Element> children = getChildren(element);

        if (children.size() == 0) {
            return element.getText();
        }

        for (Element child : children) {
            StringWriter sw = new StringWriter();

            try {
                writer.output(child, sw);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            text.append(sw);
        }

        return text.toString();
    }

    /**
     * Wrapper around Element.getChildren() to suppress the type warning.
     *
     * @param  element the element whose children to get.
     *
     * @return a List of Elements representing the children of the specified
     *         element.
     */
    @SuppressWarnings("unchecked")
    private List<Element> getChildren(Element element) {
        return (List<Element>) element.getChildren();
    }

    /**
     * DOCUMENT ME!
     *
     * @param  element DOCUMENT ME!
     * @param  path    DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private Element selectSingleNode(Element element, String path) {
        try {
            XPath xpath = XPath.newInstance(path);

            xpath.addNamespace("xhtml", "http://www.w3.org/1999/xhtml");
            return (Element) xpath.selectSingleNode(element);
        } catch (JDOMException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
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
        AttributeMap list   = new AttributeMap();
        Reader       reader = null;
        Document     doc    = null;

        try {
            reader = new FileReader(filename);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to open properties file");
        }

        SAXBuilder builder = new SAXBuilder();

        builder.setEntityResolver(new DTDHandler());
        builder.setIgnoringElementContentWhitespace(true);
        builder.setIgnoringBoundaryWhitespace(true);
        try {
            doc = builder.build(reader);
        } catch (Exception e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to parse XML properties file " + filename, e);
        }

        List<Element> elementList = getChildren(doc.getRootElement());

        for (Element element : elementList) {
            String key   = element.getName();
            String value = getElementContentsAsText(element);

            list.put(key, value);
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
