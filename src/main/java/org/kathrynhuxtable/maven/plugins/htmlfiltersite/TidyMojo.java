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
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import org.codehaus.plexus.util.FileUtils;

import org.w3c.tidy.Configuration;
import org.w3c.tidy.Report;
import org.w3c.tidy.Tidy;

/**
 * Goal runs Velocity on the files in the specified directory.
 *
 * @description                  Runs JTidy on the files in the specified
 *                               directory.
 * @goal                         tidy
 * @phase                        pre-site
 * @requiresDependencyResolution runtime
 * @configurator                 include-project-dependencies
 */
public class TidyMojo extends AbstractMojo {

    /**
     * Location of the source directory.
     *
     * @parameter expression="${htmlfiltersite.tidySourceDirectory}"
     *            default-value="${basedir}/src/site/html"
     */
    private File tidySourceDirectory;

    /**
     * Location of the target directory. May be the same as the source
     * directory, in which case the original files will be overwritten.
     *
     * @parameter expression="${htmlfiltersite.tidyTargetDirectory}"
     *            default-value="${project.build.directory}/generated-site/resources"
     */
    private File tidyTargetDirectory;

    /**
     * Match pattern for the files to be processed..
     *
     * @parameter expression="${htmlfiltersite.tidyFilePattern}"
     *            default-value="*.html"
     */
    private String tidyFilePattern;

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * DOCUMENT ME!
     *
     * @param tidySourceDirectory the tidySourceDirectory to set
     */
    public void setTidySourceDirectory(File tidySourceDirectory) {
        this.tidySourceDirectory = tidySourceDirectory;
    }

    /**
     * DOCUMENT ME!
     *
     * @param tidyTargetDirectory the tidyTargetDirectory to set
     */
    public void setTidyTargetDirectory(File tidyTargetDirectory) {
        this.tidyTargetDirectory = tidyTargetDirectory;
    }

    /**
     * DOCUMENT ME!
     *
     * @param tidyFilePattern the tidyFilePattern to set
     */
    public void setTidyFilePattern(String tidyFilePattern) {
        this.tidyFilePattern = tidyFilePattern;
    }

    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute()
     */
    public void execute() throws MojoExecutionException {
        Tidy          tidy          = new Tidy();
        Configuration configuration = tidy.getConfiguration();

        try {
            validateDirectory(tidySourceDirectory);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new MojoExecutionException("Unable to read tidySourcedirectory: " + tidySourceDirectory, e);
        }

        // Configure Tidy.
        tidy.setXHTML(true);
        tidy.setCharEncoding(Configuration.UTF8);
        tidy.setIndentContent(true);
        tidy.setSmartIndent(true);
        tidy.setQuiet(true);

        // Ensure config is self-consistent.
        configuration.adjust();

        boolean    writeBack = (tidySourceDirectory.equals(tidyTargetDirectory));
        List<File> fileList  = getFileListing(tidySourceDirectory, tidyFilePattern);
        Errors     errors    = new Errors();

        for (File file : fileList) {
            tidyFile(tidy, file, errors, writeBack);
        }

        if (errors.totalErrors + errors.totalWarnings > 0) {
            Report.generalInfo(tidy.getErrout());
        }

        if (tidy.getErrout() != tidy.getStderr()) {
            tidy.getErrout().close();
        }
    }

    /**
     * Run Tidy on a single file.
     *
     * @param  tidy      the Tidy instance.
     * @param  file      the file to be tidied.
     * @param  errors    the Errors object to hold the error and warning totals.
     * @param  writeBack TODO
     *
     * @throws MojoExecutionException DOCUMENT ME!
     */
    private void tidyFile(Tidy tidy, File file, Errors errors, boolean writeBack) throws MojoExecutionException {
        File targetFile = getTargetFile(file, tidySourceDirectory, tidyTargetDirectory);

        if (writeBack) {
            try {
                targetFile = File.createTempFile("tidy", ".tmp", tidySourceDirectory);
            } catch (IOException e) {
                e.printStackTrace();
                throw new MojoExecutionException("Unable to create temporary Tidy output file for " + file, e);
            }
        } else {
            File targetParentDirectory = targetFile.getParentFile();

            if (!targetParentDirectory.exists()) {
                targetParentDirectory.mkdirs();
            }
        }

        try {
            tidy.parse(new FileInputStream(file), new FileOutputStream(targetFile));
            errors.totalWarnings += tidy.getParseWarnings();
            errors.totalErrors   += tidy.getParseErrors();
        } catch (FileNotFoundException fnfe) {
            Report.unknownFile(tidy.getErrout(), "htmlfilter-site:tidy", file.getAbsolutePath());
        }

        if (writeBack) {
            try {
                FileUtils.rename(targetFile, file);
            } catch (IOException e) {
                e.printStackTrace();
                throw new MojoExecutionException("Unable to rename temporary Tidy output file for " + file, e);
            }
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  sourceFile DOCUMENT ME!
     * @param  source     DOCUMENT ME!
     * @param  target     DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     *
     * @throws MojoExecutionException DOCUMENT ME!
     */
    private File getTargetFile(File sourceFile, File source, File target) throws MojoExecutionException {
        List<String> names = new ArrayList<String>();

        names.add(sourceFile.getName());
        File file = sourceFile.getParentFile();

        while (file != null && !file.equals(source)) {
            names.add(file.getName());
            file = file.getParentFile();
        }

        if (file == null) {
            throw new MojoExecutionException("File " + sourceFile + " is not a child of " + source);
        }

        file = target;
        for (int i = names.size() - 1; i >= 0; i--) {
            file = new File(file, names.get(i));
        }

        return file;
    }

    /**
     * Recursively walk a directory tree and return a List of all Files found;
     * the List is sorted using File.compareTo().
     *
     * @param  aStartingDir is a valid directory, which can be read.
     * @param  filePattern  DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    private List<File> getFileListing(File aStartingDir, String filePattern) {
        List<File> result       = new ArrayList<File>();
        File[]     filesAndDirs = aStartingDir.listFiles();
        List<File> filesDirs    = Arrays.asList(filesAndDirs);

        for (File file : filesDirs) {
            if (file.getName().matches(filePattern)) {
                result.add(file);
            }

            if (!file.isFile()) {
                // must be a directory - recursive call!
                List<File> deeperList = getFileListing(file, filePattern);

                result.addAll(deeperList);
            }
        }

        return result;
    }

    /**
     * Directory is valid if it exists, does not represent a file, and can be
     * read.
     *
     * @param  aDirectory DOCUMENT ME!
     *
     * @throws FileNotFoundException DOCUMENT ME!
     */
    private void validateDirectory(File aDirectory) throws FileNotFoundException {
        if (aDirectory == null) {
            throw new IllegalArgumentException("Directory should not be null.");
        }

        if (!aDirectory.exists()) {
            throw new FileNotFoundException("Directory does not exist: " + aDirectory);
        }

        if (!aDirectory.isDirectory()) {
            throw new IllegalArgumentException("Is not a directory: " + aDirectory);
        }

        if (!aDirectory.canRead()) {
            throw new IllegalArgumentException("Directory cannot be read: " + aDirectory);
        }
    }

    /**
     * Keep totals of errors and warnings.
     */
    private static class Errors {
        int totalErrors;
        int totalWarnings;

        /**
         * Creates a new Errors object.
         */
        public Errors() {
            totalErrors   = 0;
            totalWarnings = 0;
        }
    }
}
