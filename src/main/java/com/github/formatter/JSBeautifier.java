package com.github.formatter;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.Properties;
import javax.swing.text.BadLocationException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResource;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResourceCollection;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.WriterFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * A Maven plugin mojo to format Java source code using the Eclipse code
 * formatter.
 *
 * Mojo parameters allow customizing formatting by specifying the config XML
 * file, line endings, compiler version, and source code locations. Reformatting
 * source files is avoided using an md5 hash of the content, comparing to the
 * original hash to the hash after formatting and a cached hash.
 *
 * @author jecki
 * @author Matt Blanchette
 */
/**
 * @author praveen.kailas@gmail.com
 *
 */
/**
 * Created by Aakash on 4/17/2015.
 * @author aakash01.nitb@gmail.com
 */

/**
 * Echos an object string to the output screen.
 *
 * @goal format
 * @requiresProject false
 */
public class JSBeautifier extends AbstractMojo {

    private static final String CACHE_PROPERTIES_FILENAME = "maven-js-formatter-cache.properties";
    private static final String[] DEFAULT_INCLUDES = new String[] { "**/*.js" };

    private Context context = null;
    private Scriptable scope = null;

    /**
     * Project's target directory as specified in the POM.
     *
     * @parameter expression="${project.build.directory}"
     * @readonly
     * @required
     */
    private File targetDirectory;
    /**
     * Project's base directory.
     *
     * @parameter expression="${basedir}"
     * @readonly
     * @required
     */
    private File basedir;

    /**
     * Location of the JS source files to format.
     *
     * @parameter
     */
    private File[] directories;

    /**
     * List of fileset patterns for JS source locations to include in formatting.
     * Patterns are relative to the project source and test source directories.
     * When not specified, the default include is <code>**&#47;*.js</code>
     *
     * @parameter
     */
    private String[] includes;

    /**
     * List of fileset patterns for JS source locations to exclude from formatting.
     * Patterns are relative to the project source and test source directories.
     * When not specified, there is no default exclude.
     *
     * @parameter
     */
    private String[] excludes;

    /**
     * The file encoding used to read and write source files.
     * When not specified and sourceEncoding also not set,
     * default is platform file encoding.
     *
     * @parameter default-value="${project.build.sourceEncoding}"
     */
    private String encoding;

    /**
     * Whether the formatting is skipped.
     *
     * @parameter default-value="false" expression="${skipFormat}"
     * @since 0.5
     */
    private Boolean skipFormatting;

    private PlexusIoFileResourceCollection collection;

    private static int count = 0;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skipFormatting) {
            getLog().info("Formatting is skipped");
            return;
        }

        long startClock = System.currentTimeMillis();

        createResourceCollection();

        List files = new ArrayList();
        try {

            if (directories != null) {
                for (File directory : directories) {
                    if (directory.exists() && directory.isDirectory()) {
                        collection.setBaseDir(directory);
                        addCollectionFiles(files);
                    }
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to find files using includes/excludes", e);
        }

        int numberOfFiles = files.size();
        Log log = getLog();
        log.info("Number of javascript files to be formatted: " + numberOfFiles);

        if (numberOfFiles > 0) {
            try {
                initContext();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            ResultCollector rc = new ResultCollector();
            Properties hashCache = readFileHashCacheFile();

            String basedirPath = getBasedirPath();
            for (int i = 0, n = files.size(); i < n; i++) {
                File file = (File) files.get(i);
                formatFile(file, rc, hashCache, basedirPath);
            }

            storeFileHashCache(hashCache);

            long endClock = System.currentTimeMillis();

            log.info("Successfully formatted: " + rc.successCount + " file(s)");
            log.info("Fail to format        : " + rc.failCount + " file(s)");
            log.info("Skipped               : " + rc.skippedCount + " file(s)");
            log.info("Approximate time taken: " + ((endClock - startClock) / 1000) + "s");
        }

    }

    /**
     * Create a {@link PlexusIoFileResourceCollection} instance to be used by this mojo.
     * This collection uses the includes and excludes to find the source files.
     */
    void createResourceCollection() {
        collection = new PlexusIoFileResourceCollection();
        if (includes != null && includes.length > 0) {
            collection.setIncludes(includes);
        } else {
            collection.setIncludes(DEFAULT_INCLUDES);
        }
        collection.setExcludes(excludes);
        collection.setIncludingEmptyDirectories(false);

        IncludeExcludeFileSelector fileSelector = new IncludeExcludeFileSelector();
        fileSelector.setIncludes(DEFAULT_INCLUDES);
        collection.setFileSelectors(new FileSelector[] { fileSelector });

    }

    /**
     * Add source files from the {@link PlexusIoFileResourceCollection} to the files list.
     *
     * @param files
     * @throws IOException
     */
    void addCollectionFiles(List files) throws IOException {
        Iterator resources = collection.getResources();
        while (resources.hasNext()) {
            PlexusIoFileResource resource = (PlexusIoFileResource) resources.next();
            files.add(resource.getFile());
        }
    }

    private void storeFileHashCache(Properties props) {
        File cacheFile = new File(targetDirectory, CACHE_PROPERTIES_FILENAME);
        try {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(cacheFile));
            props.store(out, null);
        } catch (FileNotFoundException e) {
            getLog().warn("Cannot store file hash cache properties file", e);
        } catch (IOException e) {
            getLog().warn("Cannot store file hash cache properties file", e);
        }
    }

    private Properties readFileHashCacheFile() {
        Properties props = new Properties();
        Log log = getLog();
        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        } else if (!targetDirectory.isDirectory()) {
            log.warn("Something strange here as the " + "supposedly target directory is not a directory.");
            return props;
        }

        File cacheFile = new File(targetDirectory, CACHE_PROPERTIES_FILENAME);
        if (!cacheFile.exists()) {
            return props;
        }

        try {
            props.load(new BufferedInputStream(new FileInputStream(cacheFile)));
        } catch (FileNotFoundException e) {
            log.warn("Cannot load file hash cache properties file", e);
        } catch (IOException e) {
            log.warn("Cannot load file hash cache properties file", e);
        }
        return props;
    }

    private String getBasedirPath() {
        try {
            return basedir.getCanonicalPath();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * @param file
     * @param rc
     * @param hashCache
     * @param basedirPath
     */
    private void formatFile(File file, ResultCollector rc, Properties hashCache, String basedirPath) {
        try {
            doFormatFile(file, rc, hashCache, basedirPath);
        } catch (IOException e) {
            rc.failCount++;
            getLog().warn(e);
        } catch (BadLocationException e) {
            rc.failCount++;
            getLog().warn(e);
        }
    }

    /**
     * @param str
     * @return
     * @throws UnsupportedEncodingException
     */
    private String md5hash(String str) throws UnsupportedEncodingException {
        return DigestUtils.md5Hex(str.getBytes(encoding));
    }

    /**
     * Format individual file.
     *
     * @param file
     * @param rc
     * @param hashCache
     * @param basedirPath
     * @throws IOException
     * @throws BadLocationException
     */
    private void doFormatFile(File file, ResultCollector rc, Properties hashCache, String basedirPath)
                   throws IOException, BadLocationException {
        Log log = getLog();
        log.debug("Processing file: " + file);

        Scriptable options = null;
        Map<String, Object> prefs = new HashMap<String, Object>();

        String code = readFileAsString(file);
        String originalHash = md5hash(code);

        String canonicalPath = file.getCanonicalPath();
        String path = canonicalPath.substring(basedirPath.length());
        String cachedHash = hashCache.getProperty(path);
        if (cachedHash != null && cachedHash.equals(originalHash)) {
            rc.skippedCount++;
            log.debug("File is already formatted.");
            return;
        }

        prefs = getPreferences();

        options = context.newObject(scope);

        scope.put("contents", scope, code);

        for (String key : prefs.keySet()) {
            options.put(key, options, prefs.get(key));
        }

        scope.put("opts", scope, options);

        context.evaluateString(scope, "result = global.js_beautify(contents, opts);", "JSBeautify", 1, null);

        Object result = scope.get("result", scope);
        if (result == null) {
            rc.failCount++;
            return;
        }
        String formattedCode = result.toString();
        String formattedHash = md5hash(formattedCode);
        hashCache.setProperty(path, formattedHash);

        if (originalHash.equals(formattedHash)) {
            rc.skippedCount++;
            log.debug("Equal hash code. Not writing result to file.");
            return;
        }

        writeStringToFile(formattedCode, file);
        rc.successCount++;
        getLog().info(" Formatted file --  " + file.getAbsolutePath());

    }

    /**
     * Read the given file and return the content as a string.
     *
     * @param file
     * @return
     * @throws java.io.IOException
     */
    private String readFileAsString(File file) throws java.io.IOException {
        StringBuilder fileData = new StringBuilder(1000);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(ReaderFactory.newReader(file, encoding));
            char[] buf = new char[1024];
            int numRead = 0;
            while ((numRead = reader.read(buf)) != -1) {
                String readData = String.valueOf(buf, 0, numRead);
                fileData.append(readData);
                buf = new char[1024];
            }
        } finally {
            IOUtil.close(reader);
        }
        return fileData.toString();
    }

    /**
     * Write the given string to a file.
     *
     * @param str
     * @param file
     * @throws IOException
     */
    private void writeStringToFile(String str, File file) throws IOException {
        if (!file.exists() && file.isDirectory()) {
            return;
        }

        BufferedWriter bw = null;
        try {
            bw = new BufferedWriter(WriterFactory.newWriter(file, encoding));
            bw.write(str);
        } finally {
            IOUtil.close(bw);
        }
    }

    /**
     * @throws java.io.FileNotFoundException
     *
     */
    private void initContext() throws FileNotFoundException {
        // try {
        context = Context.enter();
        context.setLanguageVersion(Context.VERSION_1_6);
        scope = context.initStandardObjects();
        /*String jsBeautifierCode = getFileContents(new FileInputStream(new File(
                       "./beautify.js")));*/
        InputStream stream = getClass().getClassLoader().getResourceAsStream("beautify.js");
        String jsBeautifierCode = getFileContents(stream);
        context.evaluateString(scope, "var global = {};", "global", 1, null);
        context.evaluateString(scope, jsBeautifierCode, "JSBeautify", 1, null);

      /*} catch (Exception e) {
         e.printStackTrace();
      }*/

    }

    private String getFileContents(InputStream stream) {
        StringBuffer contents = new StringBuffer("");
        try {
            byte[] readBytes = new byte[1024];
            int i = 0;
            while ((i = stream.read(readBytes)) > 0) {
                contents.append(new String(readBytes, 0, i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contents.toString();
    }

    private Map<String, Object> getPreferences() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("indent_size", 4);
        map.put("preserve_newlines", true);
        map.put("packers", true);
        return map;
    }

    private class ResultCollector {
        private int successCount;
        private int failCount;
        private int skippedCount;
    }

    public static void main(String args[]){
        JSBeautifier jsBeautifier = new JSBeautifier();
        InputStream stream = JSBeautifier.class.getClassLoader().getResourceAsStream("beautify.js");
        String jsBeautifierCode = jsBeautifier.getFileContents(stream);
        System.out.print(jsBeautifierCode);
    }
}
